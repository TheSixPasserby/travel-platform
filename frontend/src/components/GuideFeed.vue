<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { fetchTags } from '@/api/guides'
import { fetchDestinations } from '@/api/destinations'
import { useGuideFeed, type FeedOrder } from '@/composables/useGuideFeed'
import DestinationPicker from './DestinationPicker.vue'
import GuideGrid from './GuideGrid.vue'
import type { Tag } from '@/types/guide'
import type { Destination } from '@/types/destination'

const props = withDefaults(defineProps<{ discovery?: boolean }>(), {
  discovery: false
})

const {
  keyword,
  destinationId,
  tagId,
  order,
  items,
  loading,
  loadingMore,
  error,
  moreError,
  hasMore,
  exhausted,
  loadMore,
  retry,
  search,
  selectOrder,
  selectTag,
  selectCity
} = useGuideFeed(props.discovery)

const tags = ref<Tag[]>([])
const cities = ref<Destination[]>([])
const topicsError = ref('')
let disposed = false
let topicsVersion = 0

const orders: Array<{ value: FeedOrder; label: string }> = [
  { value: 'latest', label: '最新' },
  { value: 'popular', label: '热门' }
]

const orderHint = computed(() =>
  order.value === 'latest' ? '按发布时间排列' : '按点赞与发布时间排列'
)

async function loadTopics(): Promise<void> {
  const request = ++topicsVersion
  topicsError.value = ''
  const results = await Promise.allSettled([
    fetchTags(),
    ...(props.discovery
      ? [fetchDestinations({ page: 1, size: 8, destinationType: 'CITY' })]
      : [])
  ])
  if (disposed || request !== topicsVersion) {
    return
  }
  const labels = results[0]
  if (labels?.status === 'fulfilled') {
    tags.value = labels.value as Tag[]
  } else {
    topicsError.value = '主题暂时无法加载'
  }
  const places = results[1]
  if (places?.status === 'fulfilled') {
    cities.value = (places.value as { records: Destination[] }).records
  } else if (props.discovery) {
    topicsError.value = '目的地或主题暂时无法加载'
  }
}

/* 预取哨兵：只在「进入视口」时触发一次，离开视口后才重新武装，避免连续连发。 */
const sentinel = ref<HTMLElement | null>(null)
let observer: IntersectionObserver | null = null
let armed = true

function canPrefetch(): boolean {
  return (
    hasMore.value && !loading.value && !loadingMore.value && !moreError.value
  )
}

function onIntersect(entries: IntersectionObserverEntry[]): void {
  const entry = entries[entries.length - 1]
  if (!entry) {
    return
  }
  if (!entry.isIntersecting) {
    armed = true
    return
  }
  if (!armed || !canPrefetch()) {
    return
  }
  armed = false
  void loadMore()
}

function startObserver(): void {
  if (typeof IntersectionObserver === 'undefined' || !sentinel.value) {
    return
  }
  observer = new IntersectionObserver(onIntersect, { rootMargin: '480px 0px' })
  observer.observe(sentinel.value)
}

function stopObserver(): void {
  observer?.disconnect()
  observer = null
}

watch(moreError, (value) => {
  // 追加失败后不自动重发，等用户点重试；重试成功后允许再次预取。
  if (!value) {
    armed = true
  }
})

/* 哨兵随首批数据出现/消失，ref 变化时重建观察器，否则首批渲染后永远不会预取。 */
watch(sentinel, (element) => {
  stopObserver()
  if (element) {
    startObserver()
  }
})

onMounted(() => {
  void loadTopics()
  startObserver()
})

onBeforeUnmount(() => {
  disposed = true
  topicsVersion += 1
  stopObserver()
})

async function onManualLoad(): Promise<void> {
  await loadMore()
}

async function onRetryMore(): Promise<void> {
  armed = true
  await retry()
}
</script>

<template>
  <div>
    <form
      v-if="!discovery"
      data-test="guide-search"
      class="feed-search"
      @submit.prevent="search()"
    >
      <div :class="discovery ? 'min-w-0 flex-1' : 'min-w-0'">
        <label
          :for="discovery ? 'discover-search' : 'guide-search-keyword'"
          class="field-label"
          >搜索标题或摘要</label
        >
        <input
          :id="discovery ? 'discover-search' : 'guide-search-keyword'"
          v-model="keyword"
          data-test="guide-keyword"
          type="search"
          class="field-input"
          placeholder="想去哪里，想看什么"
        />
      </div>
      <template v-if="!discovery">
        <DestinationPicker
          v-model="destinationId"
          label="目的地筛选"
          :allow-inactive-selection="true"
        />
        <div>
          <label for="guide-search-tag" class="field-label">旅行主题</label>
          <select
            id="guide-search-tag"
            v-model="tagId"
            class="field-input"
            @change="search()"
          >
            <option value="">全部主题</option>
            <option v-for="tag in tags" :key="tag.id" :value="tag.id">
              {{ tag.name }}
            </option>
          </select>
        </div>
        <div>
          <label for="guide-search-sort" class="field-label">排序</label>
          <select
            id="guide-search-sort"
            v-model="order"
            class="field-input"
            @change="search()"
          >
            <option value="latest">最新发布</option>
            <option value="popular">按热度（点赞）</option>
          </select>
        </div>
      </template>
      <button type="submit" class="btn-primary shrink-0" :disabled="loading">
        {{ loading ? '寻找中…' : '寻找攻略' }}
      </button>
    </form>

    <div v-if="discovery" class="discovery-controls">
      <nav
        aria-label="旅行内容排序"
        class="mt-7 flex flex-wrap items-center gap-x-6 gap-y-2 border-b border-pine/15 pb-3"
      >
        <button
          v-for="option in orders"
          :key="option.value"
          type="button"
          :data-test="`order-${option.value}`"
          class="feed-order"
          :class="{ selected: order === option.value }"
          :aria-pressed="order === option.value"
          :title="
            option.value === 'latest'
              ? '按发布时间排列'
              : '按点赞与发布时间排列'
          "
          @click="selectOrder(option.value)"
        >
          {{ option.label }}
        </button>
        <span class="sr-only">{{ orderHint }}</span>
      </nav>
      <div
        v-if="discovery"
        class="feed-topics my-5"
        aria-label="目的地与旅行主题"
      >
        <button
          v-for="city in cities"
          :key="'city-' + city.id"
          type="button"
          class="topic-pill"
          :aria-pressed="destinationId === city.id"
          @click="selectCity(city.id)"
        >
          {{ city.name }}
        </button>
        <button
          v-for="tag in tags.slice(0, 6)"
          :key="'tag-' + tag.id"
          type="button"
          :data-test="`topic-${tag.id}`"
          class="topic-pill"
          :aria-pressed="tagId === tag.id"
          @click="selectTag(tag.id)"
        >
          #{{ tag.name }}
        </button>
      </div>
    </div>

    <p v-if="topicsError" class="my-3 text-xs text-ink-soft">
      {{ topicsError }}
      <button
        type="button"
        class="min-h-11 text-pine underline"
        @click="loadTopics"
      >
        重试
      </button>
    </p>

    <div
      v-if="loading && !items.length"
      role="status"
      class="feed-loading"
      aria-label="正在翻开路上的故事"
    >
      <span class="sr-only">正在翻开路上的故事…</span>
      <div
        v-for="item in 8"
        :key="item"
        class="feed-skeleton"
        aria-hidden="true"
      />
    </div>
    <div v-else-if="error" role="alert" class="surface-card my-6 p-8">
      <p>{{ error }}</p>
      <button
        type="button"
        data-test="retry-guides"
        class="btn-ghost mt-4"
        @click="retry"
      >
        重新加载
      </button>
    </div>
    <div v-else-if="!items.length" class="surface-card my-6 p-10 text-center">
      <h2 class="font-serif text-2xl text-pine-deep">还没有找到攻略</h2>
      <p class="mt-3 text-sm text-ink-soft">
        试试其他地点或主题，也欢迎分享你的第一篇手记。
      </p>
      <RouterLink :to="{ name: 'guide-new' }" class="btn-ghost mt-5"
        >开始记录</RouterLink
      >
    </div>
    <template v-else>
      <p class="feed-count">
        已显示 {{ items.length }} 篇
        <template v-if="hasMore"> · 继续下滑自动加载</template>
      </p>
      <GuideGrid :guides="items" />

      <div ref="sentinel" class="feed-sentinel" aria-hidden="true"></div>
      <p v-if="loadingMore" role="status" class="feed-more-hint">
        正在加载更多…
      </p>
      <div v-else-if="moreError" role="alert" class="feed-more surface-card">
        <p>{{ moreError }}</p>
        <button
          type="button"
          data-test="retry-more"
          class="btn-ghost mt-3"
          @click="onRetryMore"
        >
          重试加载更多
        </button>
      </div>
      <p v-else-if="exhausted" class="feed-end">已经看完</p>
      <div v-else-if="hasMore" class="feed-more">
        <button
          type="button"
          data-test="load-more"
          class="btn-ghost"
          :disabled="loadingMore"
          @click="onManualLoad"
        >
          加载更多
        </button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.feed-search {
  display: flex;
  flex-wrap: wrap;
  align-items: end;
  gap: 1rem;
  margin-block: 1.5rem;
}
.feed-search > * {
  min-width: 0;
}
.feed-order {
  min-height: 44px;
  padding: 0.4rem 0;
  font-size: 1rem;
  color: var(--color-ink-soft);
  cursor: pointer;
}
.feed-order.selected {
  font-weight: 600;
  color: var(--color-pine-deep);
  box-shadow: 0 3px 0 var(--color-pine);
}
.topic-pill {
  min-height: 44px;
  padding: 8px 14px;
  border: 1px solid transparent;
  border-radius: 99px;
  background: var(--color-cream-deep);
  color: var(--color-ink-soft);
  font-size: 13px;
  cursor: pointer;
}
.topic-pill[aria-pressed='true'] {
  border-color: var(--color-pine);
  background: var(--color-pine-soft);
  color: var(--color-pine-deep);
}
.feed-count {
  margin-block: 1.25rem 1rem;
  font-size: 12px;
  letter-spacing: 0.02em;
  color: var(--color-ink-soft);
}
.feed-sentinel {
  height: 1px;
  margin-top: 1px;
}
.feed-more {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  margin-top: 2rem;
  padding: 1rem;
  text-align: center;
  font-size: 0.875rem;
  color: var(--color-ink-soft);
}
.feed-more-hint {
  margin-top: 2rem;
  text-align: center;
  font-size: 0.875rem;
  color: var(--color-ink-soft);
}
.feed-end {
  margin-top: 2rem;
  text-align: center;
  font-size: 0.8125rem;
  letter-spacing: 0.12em;
  color: var(--color-ink-soft);
}
.feed-end::before,
.feed-end::after {
  content: '';
  display: inline-block;
  width: 2.5rem;
  height: 1px;
  margin: 0 0.75rem;
  vertical-align: middle;
  background: color-mix(in srgb, var(--color-pine) 25%, transparent);
}
@media (min-width: 1024px) {
  .feed-search:has(#guide-search-keyword) {
    display: grid;
    grid-template-columns: 1.2fr 1fr 9rem 8rem auto;
  }
}
@media (max-width: 599px) {
  .feed-search {
    display: grid;
    grid-template-columns: 1fr;
  }
  .feed-search:has(#discover-search) {
    grid-template-columns: minmax(0, 1fr) auto;
  }
  .feed-order {
    font-size: 1rem;
  }
}
.feed-topics {
  display: flex;
  gap: 8px;
  min-height: 48px;
  overflow-x: auto;
  scrollbar-width: none;
  align-items: center;
}
.feed-topics::-webkit-scrollbar {
  display: none;
}
.feed-topics .topic-pill {
  flex-shrink: 0;
  white-space: nowrap;
}
.feed-loading {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 20px;
  min-height: calc(100dvh - 250px);
  margin-top: 20px;
  align-content: start;
}
.feed-skeleton {
  aspect-ratio: 2/3;
  border-radius: 18px;
  background: var(--color-cream-deep);
}
@media (max-width: 1199px) {
  .feed-loading {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
@media (max-width: 767px) {
  .feed-loading {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 16px;
  }
}
.discovery-controls {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-block: 20px;
}
.discovery-controls nav {
  display: flex;
  flex: none;
  gap: 8px;
  margin: 0;
  padding: 0;
  border: 0;
}
.discovery-controls .feed-topics {
  flex: 1;
  min-width: 0;
  margin: 0;
}
.discovery-controls .feed-order {
  padding: 8px 16px;
  border-radius: 99px;
  font-size: 14px;
}
.discovery-controls .feed-order.selected {
  box-shadow: none;
  background: var(--color-action);
  color: var(--color-on-action);
}
@media (max-width: 767px) {
  .discovery-controls {
    gap: 8px;
    margin-block: 8px;
  }
  .discovery-controls .feed-order {
    padding-inline: 13px;
  }
}
</style>
