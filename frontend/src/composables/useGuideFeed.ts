import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { fetchGuideFeed } from '@/api/guides'
import { useAuthStore } from '@/stores/auth'
import type { GuideSummary } from '@/types/guide'
import { useInfiniteFeed } from './useInfiniteFeed'

export type FeedOrder = 'latest' | 'popular'

const PAGE_SIZE = 12
const MAX_ITEMS = Number.POSITIVE_INFINITY
const SCROLL_ENTRIES = 6
/** 只记录最近几条列表的滚动位置，避免长会话无限增长。 */
const scrollMemory = new Map<string, number>()

/** 当前 history 条目位置，用于区分「返回同一个列表」与「新开一次列表」。 */
function entryPosition(): number | null {
  if (typeof window === 'undefined') {
    return null
  }
  const state = window.history.state as { position?: unknown } | null
  return state && typeof state.position === 'number' ? state.position : null
}

function scrollKey(filter: string, position: number | null): string {
  return filter + '@' + (position ?? 'fresh')
}

function rememberScroll(
  filter: string,
  position: number | null,
  top: number
): void {
  const id = scrollKey(filter, position)
  if (!scrollMemory.has(id) && scrollMemory.size >= SCROLL_ENTRIES) {
    const oldest = scrollMemory.keys().next().value
    if (oldest !== undefined) {
      scrollMemory.delete(oldest)
    }
  }
  scrollMemory.set(id, top)
}

function readScroll(filter: string, position: number | null): number | null {
  const value = scrollMemory.get(scrollKey(filter, position))
  return typeof value === 'number' && value > 0 ? value : null
}

/**
 * 公开发现流：筛选条件留在 URL（不再保留 page），内容用游标连续追加。
 * 单请求锁与版本号在 useInfiniteFeed 内，本层只负责草稿/已生效筛选、缓存隔离与返回滚动。
 */
export function useGuideFeed(discovery = false) {
  const route = useRoute()
  const router = useRouter()
  const auth = useAuthStore()
  const listPath = route.path
  const tokenScope = () => auth.token ?? 'anonymous'
  const keyword = ref('')
  const destinationId = ref<string | null>(null)
  const tagId = ref('')
  const defaultOrder: FeedOrder = discovery ? 'popular' : 'latest'
  const order = ref<FeedOrder>(defaultOrder)
  const applied = ref({
    keyword: '',
    destinationId: null as string | null,
    tagId: '',
    order: defaultOrder
  })

  const query = computed(() => {
    const text = (key: string) =>
      typeof route.query[key] === 'string' ? (route.query[key] as string) : ''
    const sort = text('sort')
    return {
      keyword: text('keyword').trim(),
      destinationId: text('destinationId') || null,
      tagId: text('tagId'),
      order: (sort === 'latest' || sort === 'popular'
        ? sort
        : defaultOrder) as FeedOrder
    }
  })

  const filterKey = computed(() => {
    const value = applied.value
    return JSON.stringify(value)
  })

  const feed = useInfiniteFeed<GuideSummary>({
    keyOf: (guide) => guide.id,
    maxItems: MAX_ITEMS,
    cacheScope: tokenScope,
    cacheKey: () => filterKey.value,
    fetchPage: (cursor, signal) =>
      fetchGuideFeed(
        {
          size: PAGE_SIZE,
          keyword: applied.value.keyword || undefined,
          destinationId: applied.value.destinationId || undefined,
          tagId: applied.value.tagId || undefined,
          sort: applied.value.order,
          cursor
        },
        signal
      )
  })

  let position: number | null = entryPosition()
  /** 只允许本次实例的首批命中短期缓存；筛选变化必须重新取数，避免旧筛选结果盖住新查询。 */
  let cacheAllowed = true
  let lastScroll = typeof window === 'undefined' ? 0 : window.scrollY
  let scrollFrame = 0
  let restored = false
  let restoring = false
  let active = true

  function onScroll(): void {
    if (scrollFrame || typeof window === 'undefined') {
      return
    }
    scrollFrame = requestAnimationFrame(() => {
      scrollFrame = 0
      if (typeof window !== 'undefined') {
        lastScroll = window.scrollY
      }
    })
  }

  function storeScroll(): void {
    if (typeof window === 'undefined') {
      return
    }
    const top = Math.round(window.scrollY || lastScroll)
    if (top > 0) {
      rememberScroll(tokenScope() + '|' + filterKey.value, position, top)
    }
  }

  /** 只在数据渲染后恢复一次，避免把新开的列表也拽到历史位置。 */
  async function scheduleRestore(): Promise<void> {
    if (restored || restoring || !active || feed.loading.value) return
    const target = readScroll(tokenScope() + '|' + filterKey.value, position)
    if (target === null) return
    const context = tokenScope() + '|' + filterKey.value + '|' + position
    restoring = true
    try {
      while (
        active &&
        route.path === listPath &&
        context === tokenScope() + '|' + filterKey.value + '|' + position
      ) {
        await nextTick()
        await new Promise<void>((resolve) =>
          requestAnimationFrame(() => resolve())
        )
        window.scrollTo({ top: target, left: 0, behavior: 'auto' })
        if (Math.abs(window.scrollY - target) < 3) {
          restored = true
          break
        }
        if (feed.loadingMore.value || feed.moreError.value) break
        if (!feed.hasMore.value) {
          restored = true
          break
        }
        await feed.loadMore()
      }
    } finally {
      restoring = false
    }
  }
  watch(
    [() => feed.items.value.length, () => feed.loadingMore.value],
    () => {
      if (!feed.loadingMore.value) void nextTick(() => scheduleRestore())
    },
    { flush: 'post' }
  )

  function applyDrafts(): void {
    const fromRoute = query.value
    keyword.value = fromRoute.keyword
    destinationId.value = fromRoute.destinationId
    tagId.value = fromRoute.tagId
    order.value = fromRoute.order
  }

  function snapshot(): void {
    applied.value = {
      keyword: keyword.value.trim(),
      destinationId: destinationId.value,
      tagId: tagId.value,
      order: order.value
    }
  }

  async function load(force = false): Promise<void> {
    snapshot()
    const skipCache = force || !cacheAllowed
    cacheAllowed = false
    await feed.loadFirst(skipCache)
    scheduleRestore()
  }

  watch(
    [() => route.fullPath, () => auth.token],
    () => {
      if (route.path !== listPath) return
      applyDrafts()
      position = entryPosition()
      restored = false
      void load()
    },
    { immediate: true }
  )

  /** 筛选不动时直接刷新当前列表，否则把筛选写回 URL 后由路由驱动首批。 */
  function navigate(): Promise<void> {
    const next: Record<string, string> = {}
    const text = keyword.value.trim()
    if (text) {
      next.keyword = text
    }
    if (destinationId.value) {
      next.destinationId = destinationId.value
    }
    if (tagId.value) {
      next.tagId = tagId.value
    }
    if (order.value !== defaultOrder) {
      next.sort = order.value
    }
    const target = { path: route.path, query: next }
    if (router.resolve(target).fullPath === route.fullPath) {
      return load(true)
    }
    storeScroll()
    return router.push(target).then(() => undefined)
  }

  function search(): Promise<void> {
    return navigate()
  }
  function selectOrder(value: FeedOrder): void {
    order.value = value
    void navigate()
  }
  function selectTag(value: string): void {
    tagId.value = tagId.value === value ? '' : value
    void navigate()
  }
  function selectCity(value: string): void {
    destinationId.value = destinationId.value === value ? null : value
    void navigate()
  }

  onMounted(() => {
    if (typeof window !== 'undefined') {
      window.addEventListener('scroll', onScroll, { passive: true })
    }
  })
  onBeforeRouteLeave(() => storeScroll())
  onBeforeUnmount(() => {
    active = false
    storeScroll()
    if (typeof window !== 'undefined') {
      window.removeEventListener('scroll', onScroll)
    }
    if (scrollFrame) {
      cancelAnimationFrame(scrollFrame)
    }
    feed.dispose()
  })

  return {
    keyword,
    destinationId,
    tagId,
    order,
    items: feed.items,
    loading: feed.loading,
    loadingMore: feed.loadingMore,
    error: feed.error,
    moreError: feed.moreError,
    hasMore: feed.hasMore,
    exhausted: feed.exhausted,
    limitReached: feed.limitReached,
    maxItems: feed.maxItems,
    loadMore: feed.loadMore,
    retry: feed.retry,
    refresh: feed.refresh,
    search,
    selectOrder,
    selectTag,
    selectCity
  }
}
