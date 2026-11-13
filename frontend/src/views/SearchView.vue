<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CommunitySidebar from '@/components/CommunitySidebar.vue'
import SearchBox from '@/components/SearchBox.vue'
import { searchPublicContent } from '@/api/search'
import { useInfiniteFeed } from '@/composables/useInfiniteFeed'
import type { SearchHit, SearchKind, SearchSort } from '@/types/search'
import { cardImageSource, cardImageSrcset, CARD_IMAGE_SIZES } from '@/utils/imageVariants'

const KIND_LABELS: Record<SearchKind, string> = {
  GUIDE: '攻略',
  CITY: '目的地',
  SCENIC_SPOT: '景点',
  CREATOR: '作者',
  TAG: '话题'
}
const KIND_ORDER: SearchKind[] = ['CITY', 'SCENIC_SPOT', 'GUIDE', 'CREATOR', 'TAG']
const KIND_TABS: Array<{ value: SearchKind | ''; label: string }> = [
  { value: '', label: '全部' },
  ...(['GUIDE', 'CITY', 'SCENIC_SPOT', 'CREATOR', 'TAG'] as SearchKind[]).map(kind => ({ value: kind, label: KIND_LABELS[kind] }))
]
const SORT_TABS: Array<{ value: SearchSort; label: string }> = [
  { value: 'relevance', label: '相关性' },
  { value: 'latest', label: '最新' }
]
const PAGE_SIZE = 24

const route = useRoute()
const router = useRouter()

const keyword = ref('')
const kind = ref<SearchKind | ''>('')
const sort = ref<SearchSort>('relevance')
const draft = ref('')
const brokenCovers = ref<Set<string>>(new Set())

const feed = useInfiniteFeed<SearchHit>({
  fetchPage: (cursor, signal) => keyword.value ?
    searchPublicContent(
      {
        keyword: keyword.value,
        kind: kind.value || undefined,
        sort: sort.value,
        size: PAGE_SIZE,
        cursor
      },
      signal
    ) : Promise.resolve({ records: [], nextCursor: null, hasMore: false }),
  keyOf: item => item.kind + ':' + item.id,
  cacheKey: () => [keyword.value, kind.value, sort.value].join('|'),
  cacheScope: () => 'public'
})

function normalizeKind(value: unknown): SearchKind | '' {
  return typeof value === 'string' && KIND_ORDER.includes(value as SearchKind) ? (value as SearchKind) : ''
}
function normalizeSort(value: unknown): SearchSort {
  return value === 'latest' ? 'latest' : 'relevance'
}

const active = computed(() => keyword.value.length > 0)
const items = computed(() => feed.items.value)

interface ResultGroup {
  kind: SearchKind
  label: string
  items: SearchHit[]
}

/** 按 kind 分组，但组内严格保持服务器返回顺序；仅在“全部”下展示分组标题。 */
const groups = computed<ResultGroup[]>(() => {
  const buckets = new Map<SearchKind, SearchHit[]>()
  for (const hit of items.value) {
    if (!KIND_LABELS[hit.kind]) {
      continue
    }
    const bucket = buckets.get(hit.kind)
    if (bucket) {
      bucket.push(hit)
    } else {
      buckets.set(hit.kind, [hit])
    }
  }
  const order = kind.value
    ? ([kind.value, ...KIND_ORDER.filter(k => k !== kind.value)] as SearchKind[])
    : KIND_ORDER
  return order
    .filter(k => (buckets.get(k)?.length ?? 0) > 0)
    .map(k => ({ kind: k, label: KIND_LABELS[k], items: buckets.get(k) as SearchHit[] }))
})
const showGroupTitles = computed(() => !kind.value && groups.value.length > 1)

function buildQuery(patch: { keyword?: string; kind?: SearchKind | ''; sort?: SearchSort }): Record<string, string> {
  const query: Record<string, string> = {}
  const kw = patch.keyword ?? keyword.value
  const k = patch.kind ?? kind.value
  const s = patch.sort ?? sort.value
  if (kw) {
    query.keyword = kw
  }
  if (k) {
    query.kind = k
  }
  if (s !== 'relevance') {
    query.sort = s
  }
  return query
}

function runSearch(): void {
  void feed.loadFirst()
}

let appliedSignature = ''
function applyRouteQuery(): void {
  const query = route.query
  const nextKeyword = typeof query.keyword === 'string' ? query.keyword.trim() : ''
  const nextKind = normalizeKind(query.kind)
  const nextSort = normalizeSort(query.sort)
  const signature = [nextKeyword, nextKind, nextSort].join('|')
  if (signature === appliedSignature) {
    return
  }
  appliedSignature = signature
  keyword.value = nextKeyword
  kind.value = nextKind
  sort.value = nextSort
  draft.value = nextKeyword
  runSearch()
}

function onSubmit(value: string): void {
  const next = value.trim()
  if (!next) {
    if (!keyword.value) {
      draft.value = ''
      return
    }
    void router.push({ query: buildQuery({ keyword: '' }) })
    return
  }
  if (next === keyword.value) {
    void feed.retry()
    return
  }
  void router.push({ query: buildQuery({ keyword: next }) })
}

function selectKind(next: SearchKind | ''): void {
  if (next === kind.value) {
    return
  }
  void router.push({ query: buildQuery({ kind: next }) })
}

function selectSort(next: SearchSort): void {
  if (next === sort.value) {
    return
  }
  void router.push({ query: buildQuery({ sort: next }) })
}

function coverKeyOf(hit: SearchHit): string {
  return hit.kind + ':' + hit.id
}
function hasCover(hit: SearchHit): boolean {
  return Boolean(hit.coverImageUrl) && !brokenCovers.value.has(coverKeyOf(hit))
}
function onCoverError(hit: SearchHit): void {
  const next = new Set(brokenCovers.value)
  next.add(coverKeyOf(hit))
  brokenCovers.value = next
}
function initialOf(hit: SearchHit): string {
  return (hit.title || hit.username || '?').slice(0, 1)
}
function groupClass(target: SearchKind): string {
  return 'search-group search-group--' + target.toLowerCase().replace('_', '-')
}

const sentinel = ref<HTMLElement | null>(null)
let observer: IntersectionObserver | null = null
let sentinelVisible = false

function requestMore(): void {
  if (!keyword.value || !feed.hasMore.value || feed.loading.value || feed.loadingMore.value) {
    return
  }
  void feed.loadMore()
}

onMounted(() => {
  applyRouteQuery()
  if (typeof IntersectionObserver === 'undefined') {
    return
  }
  observer = new IntersectionObserver(
    entries => {
      sentinelVisible = entries.some(entry => entry.isIntersecting)
      if (sentinelVisible) {
        requestMore()
      }
    },
    { rootMargin: '320px 0px' }
  )
  if (sentinel.value) {
    observer.observe(sentinel.value)
  }
})

watch(sentinel, (element, previous) => {
  if (previous) observer?.unobserve(previous)
  if (element) observer?.observe(element)
}, { flush: 'post' })

watch(
  () => route.query,
  () => applyRouteQuery()
)

watch(
  () => feed.items.value.length,
  () => {
    if (!sentinelVisible || !feed.hasMore.value) {
      return
    }
    void nextTick(() => {
      if (sentinelVisible) {
        requestMore()
      }
    })
  }
)

onBeforeUnmount(() => {
  observer?.disconnect()
  observer = null
  feed.dispose()
})
</script>

<template>
  <div class="community-shell">
    <CommunitySidebar />
    <section class="community-main community-main--discovery">
      <header class="community-heading search-heading">
        <h1>搜索每一个值得出发的理由</h1>
        <p>输入关键词，找到攻略、目的地、景点、作者与话题。</p>
      </header>

      <div class="search-panel">
        <div class="search-box-slot">
          <SearchBox v-model="draft" @submit="onSubmit" />
        </div>

        <div class="search-filters">
          <div class="search-kind-tabs" role="group" aria-label="搜索类型">
            <button
              v-for="tab in KIND_TABS"
              :key="tab.value || 'all'"
              type="button"
              class="search-chip"
              :class="{ 'is-active': kind === tab.value }"
              :aria-pressed="kind === tab.value"
              @click="selectKind(tab.value)"
            >
              {{ tab.label }}
            </button>
          </div>
          <div class="search-sort" role="group" aria-label="排序方式">
            <button
              v-for="option in SORT_TABS"
              :key="option.value"
              type="button"
              class="search-sort__item"
              :class="{ 'is-active': sort === option.value }"
              :aria-pressed="sort === option.value"
              @click="selectSort(option.value)"
            >
              {{ option.label }}
            </button>
          </div>
        </div>
      </div>

      <div v-if="!active" class="search-state search-state--idle">
        <p class="search-state__title">开始你的下一次出发</p>
        <p class="search-state__text">输入城市、景点、攻略主题或作者名字，按回车或点击搜索。</p>
      </div>

      <div v-else-if="feed.loading.value" class="search-results" aria-busy="true" aria-label="正在搜索">
        <div v-for="n in 6" :key="'skeleton-' + n" class="search-card search-card--skeleton">
          <div class="search-card__cover search-skeleton-block"></div>
          <div class="search-card__body">
            <span class="search-skeleton-line"></span>
            <span class="search-skeleton-line search-skeleton-line--short"></span>
          </div>
        </div>
      </div>

      <div v-else-if="feed.error.value" class="search-state search-state--error" role="alert">
        <p class="search-state__title">搜索暂时不可用</p>
        <p class="search-state__text">{{ feed.error.value }}</p>
        <button type="button" class="search-action" @click="feed.retry()">重新搜索</button>
      </div>

      <div v-else-if="!items.length" class="search-state search-state--empty">
        <p class="search-state__title">没有找到与“{{ keyword }}”相关的内容</p>
        <p class="search-state__text">试试更短的关键词，或切换到其它类型。</p>
      </div>

      <div v-else class="search-content">
        <div v-for="group in groups" :key="group.kind" :class="groupClass(group.kind)">
          <h2 v-if="showGroupTitles" class="search-group__title">{{ group.label }}</h2>
          <div class="search-results">
            <RouterLink
              v-for="hit in group.items"
              :key="hit.kind + ':' + hit.id"
              :to="hit.href"
              class="search-card"
              :class="['search-card--' + hit.kind.toLowerCase().replace('_', '-'), { 'has-no-cover': !hasCover(hit) }]"
            >
              <div v-if="hit.kind !== 'TAG'" class="search-card__cover">
                <img
                  v-if="hasCover(hit)"
                  :src="cardImageSource(hit.coverImageUrl)"
                  :srcset="cardImageSrcset(hit.coverImageUrl)"
                  :sizes="hit.kind === 'GUIDE' ? CARD_IMAGE_SIZES : '88px'"
                  :alt="hit.title"
                  loading="lazy"
                  decoding="async"
                  @error="onCoverError(hit)"
                />
                <div v-else class="search-card__cover-fallback" aria-hidden="true">
                  <span>{{ initialOf(hit) }}</span>
                </div>
                <span class="search-card__kind">{{ KIND_LABELS[hit.kind] }}</span>
              </div>
              <div v-else class="search-card__tag-mark" aria-hidden="true">#</div>

              <div class="search-card__body">
                <p class="search-card__title">{{ hit.title }}</p>
                <p v-if="hit.subtitle" class="search-card__subtitle">{{ hit.subtitle }}</p>
                <p v-if="hit.username" class="search-card__meta">@{{ hit.username }}</p>
              </div>
            </RouterLink>
          </div>
        </div>

        <div v-if="feed.moreError.value" class="search-state search-state--inline" role="alert">
          <p class="search-state__text">{{ feed.moreError.value }}</p>
          <button type="button" class="search-action" @click="feed.retry()">重试</button>
        </div>
        <p v-else-if="feed.loadingMore.value" class="search-footnote">正在加载更多…</p>
        <p v-else-if="feed.exhausted.value" class="search-footnote">已经到底了，换个关键词再试试。</p>
        <div v-else-if="feed.hasMore.value" class="search-more">
          <button type="button" class="search-action" @click="feed.loadMore()">加载更多</button>
        </div>

        <div ref="sentinel" class="search-sentinel" aria-hidden="true"></div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.search-heading {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}
.search-panel {
  display: flex;
  flex-direction: column;
  gap: 0.875rem;
  margin-bottom: 1.5rem;
}
.search-box-slot {
  width: 100%;
}
.search-filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}
.search-kind-tabs {
  display: flex;
  gap: 0.375rem;
  overflow-x: auto;
  scrollbar-width: none;
  padding-bottom: 0.125rem;
}
.search-kind-tabs::-webkit-scrollbar {
  display: none;
}
.search-chip {
  min-height: var(--ui-control-height);
  flex: 0 0 auto;
  padding: 0.5rem 1rem;
  border: 1px solid var(--ui-border-subtle);
  border-radius: var(--ui-radius-pill);
  background: var(--ui-bg-surface);
  color: var(--ui-text-secondary);
  font-size: 0.875rem;
  line-height: 1.2;
  cursor: pointer;
  transition: background var(--ui-motion-fast) var(--ui-motion-ease),
    color var(--ui-motion-fast) var(--ui-motion-ease),
    border-color var(--ui-motion-fast) var(--ui-motion-ease);
}
.search-chip:hover {
  border-color: var(--ui-border-strong);
  color: var(--ui-text-primary);
}
.search-chip.is-active {
  background: var(--ui-accent);
  border-color: var(--ui-accent);
  color: var(--ui-accent-on);
}
.search-sort {
  display: flex;
  gap: 0.25rem;
  padding: 0.25rem;
  border-radius: var(--ui-radius-pill);
  background: var(--ui-bg-muted);
}
.search-sort__item {
  min-height: var(--ui-control-height);
  border: none;
  border-radius: var(--ui-radius-pill);
  background: transparent;
  padding: 0.375rem 0.875rem;
  font-size: 0.8125rem;
  color: var(--ui-text-secondary);
  cursor: pointer;
  transition: background var(--ui-motion-fast) var(--ui-motion-ease);
}
.search-sort__item.is-active {
  background: var(--ui-bg-surface);
  color: var(--ui-text-primary);
  box-shadow: var(--ui-shadow-sm);
}
.search-content {
  display: flex;
  flex-direction: column;
  gap: 1.75rem;
}
.search-group {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}
.search-group__title {
  margin: 0;
  font-size: 1rem;
  font-weight: 600;
  color: var(--ui-text-secondary);
}
.search-results {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(15rem, 1fr));
  gap: 1rem;
}
.search-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--ui-border-subtle);
  border-radius: var(--ui-radius-lg);
  background: var(--ui-bg-surface);
  color: inherit;
  text-decoration: none;
  transition: transform var(--ui-motion-fast) var(--ui-motion-ease),
    box-shadow var(--ui-motion-normal) var(--ui-motion-ease),
    border-color var(--ui-motion-fast) var(--ui-motion-ease);
}
.search-card:hover {
  transform: translateY(-2px);
  border-color: var(--ui-border-strong);
  box-shadow: var(--ui-shadow-md);
}
.search-card__cover {
  position: relative;
  aspect-ratio: 16 / 10;
  background: var(--ui-bg-muted);
  overflow: hidden;
}
.search-card__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.search-card__cover-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  font-family: var(--font-serif);
  font-size: 1.75rem;
  color: var(--ui-text-secondary);
  background: linear-gradient(135deg, var(--ui-bg-muted), var(--ui-accent-soft));
}
.search-card__kind {
  position: absolute;
  top: 0.5rem;
  left: 0.5rem;
  padding: 0.125rem 0.5rem;
  border-radius: var(--ui-radius-pill);
  background: color-mix(in srgb, var(--ui-bg-page) 82%, transparent);
  color: var(--ui-text-primary);
  font-size: 0.6875rem;
}
.search-card__tag-mark {
  display: flex;
  align-items: center;
  justify-content: center;
  aspect-ratio: 16 / 10;
  font-family: var(--font-serif);
  font-size: 2rem;
  color: var(--ui-accent);
  background: var(--ui-accent-soft);
}
.search-card__body {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  padding: 0.75rem 0.875rem 0.875rem;
  min-width: 0;
}
.search-card__title {
  margin: 0;
  font-size: 0.9375rem;
  font-weight: 600;
  line-height: 1.45;
  color: var(--ui-text-primary);
  overflow-wrap: anywhere;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.search-card__subtitle {
  margin: 0;
  font-size: 0.8125rem;
  line-height: 1.5;
  color: var(--ui-text-secondary);
  overflow-wrap: anywhere;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.search-card__meta {
  margin: 0;
  font-size: 0.75rem;
  color: var(--ui-text-secondary);
  overflow-wrap: anywhere;
}
.search-card--creator .search-card__cover,
.search-card--tag .search-card__cover {
  aspect-ratio: 16 / 10;
}
.search-card--tag {
  flex-direction: row;
  align-items: center;
}
.search-card--tag .search-card__tag-mark {
  flex: 0 0 4.5rem;
  aspect-ratio: 1 / 1;
}
.search-card__body {
  flex: 1 1 auto;
}
.search-card--skeleton {
  pointer-events: none;
}
.search-skeleton-block,
.search-skeleton-line {
  display: block;
  background: var(--ui-bg-muted);
  border-radius: var(--ui-radius-sm);
}
.search-skeleton-line {
  height: 0.75rem;
  margin-bottom: 0.5rem;
}
.search-skeleton-line--short {
  width: 55%;
}
.search-state {
  padding: 3rem 1.5rem;
  border: 1px dashed var(--ui-border-subtle);
  border-radius: var(--ui-radius-lg);
  background: var(--ui-bg-surface);
  text-align: center;
}
.search-state--inline {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.625rem;
  padding: 1.25rem;
}
.search-state--error {
  border-color: var(--ui-danger);
}
.search-state__title {
  margin: 0 0 0.375rem;
  font-size: 1.0625rem;
  font-weight: 600;
  color: var(--ui-text-primary);
  overflow-wrap: anywhere;
}
.search-state__text {
  margin: 0;
  font-size: 0.875rem;
  line-height: 1.6;
  color: var(--ui-text-secondary);
  overflow-wrap: anywhere;
}
.search-action {
  min-height: var(--ui-control-height);
  margin-top: 0.875rem;
  padding: 0.5rem 1.25rem;
  border: 1px solid var(--ui-accent);
  border-radius: var(--ui-radius-pill);
  background: var(--ui-accent);
  color: var(--ui-accent-on);
  font-size: 0.875rem;
  cursor: pointer;
  transition: background var(--ui-motion-fast) var(--ui-motion-ease);
}
.search-action:hover {
  background: var(--ui-accent-hover);
}
.search-footnote {
  margin: 0;
  padding: 0.5rem 0;
  text-align: center;
  font-size: 0.8125rem;
  color: var(--ui-text-secondary);
}
.search-more {
  display: flex;
  justify-content: center;
  padding: 0.25rem 0 0.5rem;
}
.search-sentinel {
  height: 1px;
  width: 100%;
}
.search-group--city .search-card,
.search-group--scenic-spot .search-card,
.search-group--creator .search-card {
  flex-direction: row;
  align-items: center;
}
.search-group--city .search-card__cover,
.search-group--scenic-spot .search-card__cover,
.search-group--creator .search-card__cover {
  width: 88px;
  flex: 0 0 88px;
  aspect-ratio: 1;
  margin: 12px;
  border-radius: var(--ui-radius-md);
}
.search-group--creator .search-card__cover { border-radius: 50%; }
.search-group--city .search-card__kind,
.search-group--scenic-spot .search-card__kind,
.search-group--creator .search-card__kind { display: none; }
@media (max-width: 640px) {
  .search-results {
    grid-template-columns: 1fr;
  }
  .search-group--guide .search-results { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
  .search-group--guide .search-card__body { padding: 12px; }
  .search-filters {
    flex-direction: column;
    align-items: stretch;
  }
  .search-sort {
    align-self: flex-start;
  }
  .search-state {
    padding: 2rem 1rem;
  }
}
@media (max-width: 400px) {
  .search-card__title {
    font-size: 0.9rem;
  }
}
@media (prefers-reduced-motion: reduce) {
  .search-card,
  .search-chip,
  .search-sort__item,
  .search-action {
    transition: none;
  }
  .search-card:hover {
    transform: none;
  }
}
</style>
