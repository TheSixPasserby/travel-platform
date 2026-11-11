import { computed, ref, shallowRef } from 'vue'
import { ApiError } from '@/api/client'

/** 一页连续加载结果，与后端 GuideFeedPage 契约一致。 */
export interface FeedPage<T> {
  records: T[]
  nextCursor: string | null
  hasMore: boolean
}

export interface InfiniteFeedOptions<T> {
  /** 拉取一页；cursor 为空表示首批，signal 用于取消过期请求。 */
  fetchPage: (cursor: string | undefined, signal: AbortSignal) => Promise<FeedPage<T>>
  /** 列表项稳定 ID，用于跨页去重。 */
  keyOf: (item: T) => string
  /** 缓存键（筛选条件、作者等），同一键在短期内直接复用。 */
  cacheKey?: () => string
  /** 缓存隔离域，默认按登录令牌指纹隔离。 */
  cacheScope?: () => string
  /** DOM 元素上限，默认 240。 */
  maxItems?: number
}

interface FeedCacheEntry<T> {
  at: number
  items: T[]
  nextCursor: string | null
  hasMore: boolean
}

/** 短期缓存：从详情返回时直接复用最近一屏，不再打接口。 */
export const FEED_CACHE_TTL_MS = 180_000
export const FEED_MAX_ITEMS = Number.POSITIVE_INFINITY
const FEED_CACHE_ENTRIES = 4
const feedCache = new Map<string, FeedCacheEntry<unknown>>()
let cachedScope = ''

export function clearFeedCache(): void {
  feedCache.clear()
  cachedScope = ''
}

function currentScope(read?: () => string): string {
  const scope = read?.() ?? 'public'
  if (scope !== cachedScope) {
    // 账号切换后，旧会话的内容快照不能再被新账号复用。
    feedCache.clear()
    cachedScope = scope
  }
  return scope
}

function cacheId(scope: string, key: string): string {
  return scope + '\u0000' + key
}

function readCache<T>(id: string): FeedCacheEntry<T> | null {
  const entry = feedCache.get(id) as FeedCacheEntry<T> | undefined
  if (!entry) {
    return null
  }
  if (Date.now() - entry.at > FEED_CACHE_TTL_MS) {
    feedCache.delete(id)
    return null
  }
  // 命中即刷新 LRU 顺序。
  feedCache.delete(id)
  feedCache.set(id, entry)
  return entry
}

function writeCache<T>(id: string, entry: FeedCacheEntry<T>): void {
  feedCache.delete(id)
  feedCache.set(id, entry as FeedCacheEntry<unknown>)
  while (feedCache.size > FEED_CACHE_ENTRIES) {
    const oldest = feedCache.keys().next().value
    if (oldest === undefined) {
      break
    }
    feedCache.delete(oldest)
  }
}

function isCancel(error: unknown): boolean {
  return error instanceof Error && (error.name === 'CanceledError' || error.name === 'AbortError')
}

function messageOf(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback
}

/**
 * 游标连续加载引擎：单请求锁 + 版本号/AbortController 防竞态、ID 去重、
 * 有上限的短期缓存、DOM 上限与失败保留旧列表。公共攻略流与创作者作品流共用。
 */
export function useInfiniteFeed<T>(options: InfiniteFeedOptions<T>) {
  const maxItems = options.maxItems ?? FEED_MAX_ITEMS
  const items = shallowRef<T[]>([])
  const loading = ref(false)
  const loadingMore = ref(false)
  const error = ref('')
  const moreError = ref('')
  const nextCursor = ref<string | null>(null)
  const hasMore = ref(false)
  const limitReached = computed(() => items.value.length >= maxItems)
  const exhausted = computed(
    () => !loading.value && !hasMore.value && !limitReached.value && items.value.length > 0
  )

  const seen = new Set<string>()
  let version = 0
  let controller: AbortController | null = null
  let disposed = false

  function cacheKey(): string {
    return cacheId(currentScope(options.cacheScope), options.cacheKey?.() ?? 'feed')
  }

  function saveSnapshot(): void {
    if (disposed || !items.value.length) {
      return
    }
    writeCache(cacheKey(), {
      at: Date.now(),
      items: items.value,
      nextCursor: nextCursor.value,
      hasMore: hasMore.value
    })
  }

  /** 合并一页：跳过重复 ID，超过上限截断并停止继续加载。 */
  function absorb(page: FeedPage<T>): number {
    const fresh: T[] = []
    for (const record of page.records) {
      const id = options.keyOf(record)
      if (!id || seen.has(id)) {
        continue
      }
      seen.add(id)
      fresh.push(record)
    }
    const merged = items.value.length ? [...items.value, ...fresh] : fresh
    const capped = merged.length > maxItems ? merged.slice(0, maxItems) : merged
    items.value = capped
    nextCursor.value = page.nextCursor
    hasMore.value =
      page.records.length > 0 &&
      page.hasMore &&
      Boolean(page.nextCursor) &&
      capped.length < maxItems
    return fresh.length
  }

  async function loadFirst(force = false): Promise<void> {
    const request = ++version
    controller?.abort()
    controller = new AbortController()
    const signal = controller.signal
    loading.value = true
    loadingMore.value = false
    error.value = ''
    moreError.value = ''
    items.value = []
    seen.clear()
    nextCursor.value = null
    hasMore.value = false
    try {
      if (!force) {
        const cached = readCache<T>(cacheKey())
        if (cached) {
          items.value = cached.items
          cached.items.forEach(item => seen.add(options.keyOf(item)))
          nextCursor.value = cached.nextCursor
          hasMore.value = cached.hasMore
          return
        }
      }
      const page = await options.fetchPage(undefined, signal)
      if (request !== version || disposed) {
        return
      }
      absorb(page)
      saveSnapshot()
    } catch (caught) {
      if (request !== version || disposed || isCancel(caught)) {
        return
      }
      error.value = messageOf(caught, '攻略暂时无法加载，请稍后重试')
    } finally {
      if (request === version) {
        loading.value = false
      }
    }
  }

  /** 追加下一批；并发调用共享同一次请求，失败保留旧列表。 */
  async function loadMore(): Promise<void> {
    if (loading.value || loadingMore.value || !hasMore.value || limitReached.value) {
      return
    }
    const cursor = nextCursor.value
    if (!cursor) {
      return
    }
    if (!controller) {
      controller = new AbortController()
    }
    const request = version
    const signal = controller.signal
    loadingMore.value = true
    moreError.value = ''
    try {
      const page = await options.fetchPage(cursor, signal)
      if (request !== version || disposed) {
        return
      }
      absorb(page)
      if (page.nextCursor === cursor) hasMore.value = false
      saveSnapshot()
    } catch (caught) {
      if (request !== version || disposed || isCancel(caught)) {
        return
      }
      moreError.value = messageOf(caught, '更多攻略暂时加载失败，请重试')
    } finally {
      if (request === version) {
        loadingMore.value = false
      }
    }
  }

  /** 失败重试：首批失败重新首批，追加失败只重试下一批。 */
  function retry(): Promise<void> {
    if (moreError.value && items.value.length) {
      moreError.value = ''
      return loadMore()
    }
    return loadFirst(true)
  }

  function refresh(): Promise<void> {
    return loadFirst(true)
  }

  function dispose(): void {
    disposed = true
    version += 1
    controller?.abort()
    controller = null
  }

  return {
    items,
    loading,
    loadingMore,
    error,
    moreError,
    hasMore,
    exhausted,
    limitReached,
    maxItems,
    loadFirst,
    loadMore,
    retry,
    refresh,
    dispose
  }
}
