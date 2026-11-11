<script setup lang="ts">
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  shallowRef,
  watch
} from 'vue'
import GuideCard from './GuideCard.vue'
import type { GuideSummary } from '@/types/guide'

const props = withDefaults(defineProps<{ guides: GuideSummary[] }>(), {
  guides: () => []
})

/**
 * 真瀑布流：渲染若干列容器，每篇攻略落进当前最矮的一列。
 * 只追加不回填，所以既有卡片不会在加载更多时换列；列内是普通文档流，
 * 不会像 grid 行高瀑布流那样留下整行空洞。图片比例由 GuideCard 预留在
 * 布局前就确定，因此首批放置也不需要等图片加载完成。
 */
const host = ref<HTMLElement | null>(null)
const columnCount = ref(2)
const viewportTop = ref(0)
const viewportHeight = ref(1000)
const renderedIds = new Set<string>()
const windowed = computed(() => props.guides.length > 100)
const visibleColumns = computed(() =>
  columns.value.map((column) => {
    if (!windowed.value) return { items: column, top: 0, bottom: 0 }
    void measureRevision.value
    const start = viewportTop.value - 1200
    const end = viewportTop.value + viewportHeight.value + 1200
    let offset = 0
    let top = 0
    let visibleHeight = 0
    const items: GuideSummary[] = []
    column.forEach((guide, index) => {
      const height =
        estimateOf(guide) + (index < column.length - 1 ? columnGap.value : 0)
      if (offset + height >= start && offset <= end) {
        items.push(guide)
        visibleHeight += height
      } else if (offset < start) top += height
      offset += height
    })
    return { items, top, bottom: Math.max(0, offset - top - visibleHeight) }
  })
)

function updateViewport(): void {
  viewportHeight.value = window.innerHeight
  viewportTop.value = host.value ? -host.value.getBoundingClientRect().top : 0
}

function markRendered(): void {
  for (const column of visibleColumns.value) {
    for (const guide of column.items) renderedIds.add(guide.id)
  }
}
const columns = shallowRef<GuideSummary[][]>([[], []])

let heights: number[] = [0, 0]
const placed: string[] = []
const placedSet = new Set<string>()
const columnOf = new Map<string, number>()
const estimated = new Map<string, number>()
const measured = new Map<string, number>()
const measureRevision = ref(0)
const observed = new WeakSet<Element>()
let heightObserver: ResizeObserver | null = null
let widthObserver: ResizeObserver | null = null
let windowListener: (() => void) | null = null
let hostWidth = 0

/** 列多时略放宽间距，列宽估算与 CSS 使用同一个值。 */
const columnGap = computed(() => (columnCount.value >= 4 ? 20 : 16))

/** 与 GuideCard.vue 中的比例表保持一致：同一篇攻略永远同一个图片比例。 */
const RATIOS = [4 / 5, 1, 3 / 4, 4 / 3]

function ratioFor(id: string): number {
  let hash = 0
  for (let index = 0; index < id.length; index += 1) {
    hash = (hash * 31 + id.charCodeAt(index)) % 100003
  }
  return RATIOS[hash % RATIOS.length]
}

function columnWidth(): number {
  const count = Math.max(1, columnCount.value)
  const gap = columnGap.value
  return hostWidth > 0
    ? Math.max(120, (hostWidth - gap * (count - 1)) / count)
    : 240
}

/** 估算卡片高度，只用于「新卡片进哪一列」，已有卡片一旦落列就不再移动。 */
function estimateOf(guide: GuideSummary): number {
  const key = String(guide.id)
  const known = measured.get(key)
  if (known) {
    return known
  }
  const title = Math.min(
    2,
    Math.max(1, Math.ceil((guide.title?.length ?? 0) / 11))
  )
  const summary = guide.summary
    ? Math.min(2, Math.max(1, Math.ceil(guide.summary.length / 16)))
    : 0
  const meta =
    guide.tags.length || guide.travelDays || guide.budgetPerPerson !== null
      ? 20
      : 0
  return columnWidth() / ratioFor(key) + 92 + title * 26 + summary * 21 + meta
}

function shortestIndex(list: number[]): number {
  let target = 0
  for (let index = 1; index < list.length; index += 1) {
    if (list[index] < list[target] - 0.5) {
      target = index
    }
  }
  return target
}

function resetPlacement(): void {
  const count = Math.max(1, columnCount.value)
  columns.value = Array.from({ length: count }, () => [])
  heights = new Array(count).fill(0)
  placed.length = 0
  placedSet.clear()
  columnOf.clear()
  estimated.clear()
}

function place(
  guide: GuideSummary,
  cols: GuideSummary[][],
  list: number[]
): void {
  const key = String(guide.id)
  if (placedSet.has(key)) {
    return
  }
  const target = shortestIndex(list)
  cols[target].push(guide)
  const height = estimateOf(guide)
  list[target] += height
  placed.push(key)
  placedSet.add(key)
  columnOf.set(key, target)
  estimated.set(key, height)
}

function rebuild(): void {
  resetPlacement()
  const cols = columns.value.map((column) => column.slice())
  const list = heights.slice()
  for (const guide of props.guides) {
    place(guide, cols, list)
  }
  columns.value = cols
  heights = list
}

/** 仅在 props 是既有列表的「前缀延长」时做增量追加，否则重建。 */
function sync(): void {
  const ids = props.guides.map((guide) => String(guide.id))
  const prefixKept =
    ids.length >= placed.length &&
    placed.every((id, index) => ids[index] === id)
  if (!prefixKept || columnCount.value !== columns.value.length) {
    rebuild()
    return
  }
  if (ids.length === placed.length) {
    return
  }
  const cols = columns.value.map((column) => column.slice())
  const list = heights.slice()
  for (const guide of props.guides) {
    place(guide, cols, list)
  }
  columns.value = cols
  heights = list
}

/**
 * 断点按容器实测宽度取：>=1450 五列、>=1000 四列、
 * >=680 三列，其余两列。390/360 手机因此稳定两列，1440 桌面四列。
 */
function measureWidth(): void {
  const element = host.value
  const fallback =
    typeof window === 'undefined' ? 0 : Math.max(0, window.innerWidth - 40)
  const width =
    element && element.clientWidth > 0 ? element.clientWidth : fallback
  if (Math.abs(hostWidth - width) > 2) measured.clear()
  hostWidth = width
  const next = width >= 1450 ? 5 : width >= 1000 ? 4 : width >= 680 ? 3 : 2
  if (next !== columnCount.value) {
    columnCount.value = next
    rebuild()
  }
}

function observeCards(): void {
  if (!heightObserver || !host.value) {
    return
  }
  for (const element of host.value.querySelectorAll<HTMLElement>(
    '[data-guide-id]'
  )) {
    if (observed.has(element)) {
      continue
    }
    observed.add(element)
    heightObserver.observe(element)
  }
}

function onCardResize(entries: ResizeObserverEntry[]): void {
  for (const entry of entries) {
    const id = (entry.target as HTMLElement).dataset.guideId
    const height =
      entry.borderBoxSize?.[0]?.blockSize ?? entry.contentRect.height
    if (!id || !height || measured.has(id)) {
      continue
    }
    const column = columnOf.get(id)
    const base = estimated.get(id)
    if (
      column === undefined ||
      base === undefined ||
      column >= heights.length
    ) {
      continue
    }
    measured.set(id, height)
    // 已落列的卡片不移动，只把真实高度差计入列高，供后续追加参考。
    heights[column] += height - base
    measureRevision.value++
  }
}

watch(
  () => props.guides,
  () => {
    sync()
    void nextTick(() => {
      observeCards()
      markRendered()
    })
  }
)

onMounted(() => {
  measureWidth()
  updateViewport()
  sync()
  if (typeof ResizeObserver !== 'undefined') {
    widthObserver = new ResizeObserver(() => measureWidth())
    if (host.value) {
      widthObserver.observe(host.value)
    }
    heightObserver = new ResizeObserver(onCardResize)
  }
  if (typeof window !== 'undefined') {
    windowListener = () => {
      measureWidth()
      updateViewport()
    }
    window.addEventListener('resize', windowListener, { passive: true })
    window.addEventListener('scroll', updateViewport, { passive: true })
  }
  void nextTick(() => {
    observeCards()
    markRendered()
  })
})

watch(
  visibleColumns,
  (value) => {
    const active = document.activeElement as HTMLElement | null
    const card = active?.closest<HTMLElement>('[data-guide-id]')
    if (
      card &&
      host.value?.contains(card) &&
      !value.some((column) =>
        column.items.some((guide) => guide.id === card.dataset.guideId)
      )
    ) {
      host.value.focus({ preventScroll: true })
    }
    void nextTick(() => {
      observeCards()
      markRendered()
    })
  },
  { flush: 'pre' }
)

onBeforeUnmount(() => {
  widthObserver?.disconnect()
  heightObserver?.disconnect()
  widthObserver = null
  heightObserver = null
  if (windowListener && typeof window !== 'undefined') {
    window.removeEventListener('resize', windowListener)
    window.removeEventListener('scroll', updateViewport)
  }
})
</script>

<template>
  <div
    ref="host"
    class="masonry"
    tabindex="-1"
    aria-label="旅行手记列表"
    :style="{ '--masonry-gap': columnGap + 'px' }"
  >
    <div
      v-for="(column, index) in visibleColumns"
      :key="index"
      class="masonry-column"
    >
      <div
        v-if="column.top"
        aria-hidden="true"
        :style="{ height: column.top + 'px', flex: 'none' }"
      />
      <GuideCard
        v-for="guide in column.items"
        :key="guide.id"
        :guide="guide"
        :data-guide-id="guide.id"
        :animate="!renderedIds.has(guide.id)"
        :style="{
          marginBottom:
            columns[index]?.at(-1)?.id === guide.id ? '0' : columnGap + 'px'
        }"
      />
      <div
        v-if="column.bottom"
        aria-hidden="true"
        :style="{ height: column.bottom + 'px', flex: 'none' }"
      />
    </div>
  </div>
</template>

<style scoped>
.masonry {
  display: flex;
  align-items: flex-start;
  gap: var(--masonry-gap, 1rem);
}
.masonry-column {
  display: flex;
  flex: 1 1 0;
  flex-direction: column;
  gap: 0;
  min-width: 0;
}
</style>
