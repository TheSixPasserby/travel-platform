<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import GuideComments from './GuideComments.vue'

/**
 * 详情页右侧评论抽屉。
 *
 * - 常驻挂载，只切换可见性：关闭不会卸载 GuideComments，评论草稿因此保留；
 * - >=1440px 为非模态侧栏：无遮罩、不锁 body 滚动、不做 focus trap，正文仍可阅读；
 * - 更窄视口退化为模态抽屉：遮罩 + Esc + focus trap + 焦点恢复 + body 滚动锁；
 * - 顶部触控右滑可关闭；高度用 100dvh，兼容移动端软键盘。
 */
const props = withDefaults(
  defineProps<{
    open: boolean
    guideId: string
    /** 与服务端同步的评论总数，打开时展示在标题旁。 */
    commentCount?: number
    guideAuthorId?: string
  }>(),
  { commentCount: 0, guideAuthorId: undefined }
)

const emit = defineEmits<{
  close: []
  'count-changed': [value: number]
}>()

const panel = ref<HTMLElement | null>(null)
const wide = ref(false)
/** 关闭动画期间仍需保留 DOM，故用独立可见性标记。 */
const visible = ref(props.open)
const viewportHeight = ref(window.visualViewport?.height ?? window.innerHeight)
const viewportOffset = ref(window.visualViewport?.offsetTop ?? 0)
function updateViewport(): void {
  viewportHeight.value = window.visualViewport?.height ?? window.innerHeight
  viewportOffset.value = window.visualViewport?.offsetTop ?? 0
}

const total = computed(() => props.commentCount)
/** 宽屏非模态、窄屏模态，决定角色语义与交互约束。 */
const modal = computed(() => !wide.value)

function readWide(): boolean {
  if (
    typeof window === 'undefined' ||
    typeof window.matchMedia !== 'function'
  ) {
    return false
  }
  try {
    return window.matchMedia('(min-width: 1440px)').matches
  } catch {
    return false
  }
}

let media: MediaQueryList | null = null
function onMediaChange(event: MediaQueryListEvent): void {
  wide.value = event.matches
  if (!props.open) {
    return
  }
  if (event.matches) {
    unlockScroll()
  } else {
    lockScroll()
    void focusPanel()
  }
}

/* ---------------------------- body 滚动锁（仅模态） ---------------------------- */

let scrollLocked = false
let previousOverflow = ''

function lockScroll(): void {
  if (typeof document === 'undefined' || scrollLocked) {
    return
  }
  previousOverflow = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  scrollLocked = true
}

function unlockScroll(): void {
  if (typeof document === 'undefined' || !scrollLocked) {
    return
  }
  document.body.style.overflow = previousOverflow
  scrollLocked = false
}

/* ------------------------------ 焦点管理（仅模态） ------------------------------ */

let restoreTarget: HTMLElement | null = null

function focusable(): HTMLElement[] {
  const root = panel.value
  if (!root) {
    return []
  }
  return Array.from(
    root.querySelectorAll<HTMLElement>(
      'button:not(:disabled), [href], input:not(:disabled), textarea:not(:disabled), select:not(:disabled), [tabindex]:not([tabindex="-1"])'
    )
  )
}

async function focusPanel(): Promise<void> {
  await nextTick()
  const first = focusable()[0] ?? panel.value
  first?.focus({ preventScroll: true })
}

function restoreFocus(): void {
  const target = restoreTarget
  restoreTarget = null
  if (
    target &&
    typeof target.focus === 'function' &&
    document.contains(target)
  ) {
    target.focus({ preventScroll: true })
  }
}

function onKeydown(event: KeyboardEvent): void {
  if (!props.open || document.querySelector('dialog.image-lightbox[open]')) {
    return
  }
  if (event.key === 'Escape') {
    event.preventDefault()
    emit('close')
    return
  }
  if (!modal.value || event.key !== 'Tab') {
    return
  }
  const items = focusable()
  if (!items.length) {
    event.preventDefault()
    panel.value?.focus({ preventScroll: true })
    return
  }
  const first = items[0]
  const last = items[items.length - 1]
  const active = document.activeElement as HTMLElement | null
  if (!active || !panel.value?.contains(active)) {
    event.preventDefault()
    first.focus({ preventScroll: true })
    return
  }
  if (event.shiftKey && active === first) {
    event.preventDefault()
    last.focus({ preventScroll: true })
  } else if (!event.shiftKey && active === last) {
    event.preventDefault()
    first.focus({ preventScroll: true })
  }
}

/* --------------------------------- 触控右滑关闭 -------------------------------- */

let touchStart: { x: number; y: number } | null = null

function onTouchStart(event: TouchEvent): void {
  const point = event.touches[0]
  touchStart = point ? { x: point.clientX, y: point.clientY } : null
}

function onTouchEnd(event: TouchEvent): void {
  const start = touchStart
  touchStart = null
  const point = event.changedTouches[0]
  if (!start || !point) {
    return
  }
  const dx = point.clientX - start.x
  const dy = point.clientY - start.y
  if (dx > 60 && dx > Math.abs(dy) * 1.5) {
    emit('close')
  }
}

/* ----------------------------------- 生命周期 ---------------------------------- */

function openDrawer(): void {
  visible.value = true
  wide.value = readWide()
  restoreTarget = document.activeElement as HTMLElement | null
  updateViewport()
  if (!wide.value) {
    lockScroll()
    void focusPanel()
  }
}

function closeDrawer(): void {
  visible.value = false
  if (!wide.value || panel.value?.contains(document.activeElement))
    restoreFocus()
  unlockScroll()
}

watch(
  () => props.open,
  (open) => {
    if (open) {
      openDrawer()
    } else {
      closeDrawer()
    }
  },
  { immediate: true }
)

function onMountedSetup(): void {
  wide.value = readWide()
  if (
    typeof window !== 'undefined' &&
    typeof window.matchMedia === 'function'
  ) {
    media = window.matchMedia('(min-width: 1440px)')
    media.addEventListener?.('change', onMediaChange)
  }
  document.addEventListener('keydown', onKeydown, true)
  window.visualViewport?.addEventListener('resize', updateViewport)
  window.visualViewport?.addEventListener('scroll', updateViewport)
  if (props.open) {
    openDrawer()
  }
}
onMounted(onMountedSetup)

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown, true)
  media?.removeEventListener?.('change', onMediaChange)
  unlockScroll()
  window.visualViewport?.removeEventListener('resize', updateViewport)
  window.visualViewport?.removeEventListener('scroll', updateViewport)
})
</script>

<template>
  <div class="comment-drawer-root" :class="{ 'is-open': open }">
    <div
      v-if="modal"
      class="comment-drawer__scrim"
      :class="{ 'is-visible': open && visible }"
      data-test="comment-scrim"
      aria-hidden="true"
      @click="emit('close')"
    />

    <aside
      id="guide-comments-drawer"
      ref="panel"
      class="comment-drawer"
      :class="{ 'is-visible': visible, 'is-wide': wide }"
      :role="modal ? 'dialog' : 'region'"
      :aria-modal="modal ? 'true' : undefined"
      :aria-label="`评论，共 ${total} 条`"
      :aria-hidden="open ? undefined : 'true'"
      :inert="!open"
      :style="
        modal
          ? {
              height: viewportHeight + 'px',
              maxHeight: viewportHeight + 'px',
              top: viewportOffset + 'px'
            }
          : undefined
      "
      data-test="comment-drawer"
      :data-modal="modal ? 'true' : 'false'"
      tabindex="-1"
    >
      <header
        class="comment-drawer__header"
        @touchstart.passive="onTouchStart"
        @touchend.passive="onTouchEnd"
      >
        <div class="comment-drawer__title">
          <h2 class="font-serif text-lg text-pine-deep">评论</h2>
          <span class="tabular-nums text-sm text-ink-soft">{{ total }}</span>
        </div>
        <button
          type="button"
          class="comment-drawer__close"
          data-test="comment-drawer-close"
          aria-label="关闭评论"
          @click="emit('close')"
        >
          <svg
            viewBox="0 0 24 24"
            aria-hidden="true"
            class="h-4 w-4"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
          >
            <path d="M6 6l12 12M18 6 6 18" />
          </svg>
        </button>
      </header>

      <div class="comment-drawer__body">
        <GuideComments
          layout="drawer"
          :guide-id="guideId"
          :guide-author-id="guideAuthorId"
          @count-changed="emit('count-changed', $event)"
        />
      </div>
    </aside>
  </div>
</template>

<style scoped>
.comment-drawer-root {
  position: fixed;
  inset: 0;
  z-index: 60;
  pointer-events: none;
  overflow: hidden;
  visibility: hidden;
  transition: visibility 0s 260ms;
}

.comment-drawer-root.is-open {
  visibility: visible;
  transition-delay: 0s;
}

.comment-drawer__scrim {
  position: absolute;
  inset: 0;
  background: color-mix(in srgb, var(--color-scrim) 55%, transparent);
  opacity: 0;
  transition: opacity var(--ui-motion-normal) var(--ui-motion-ease);
  pointer-events: none;
}

.comment-drawer__scrim.is-visible {
  opacity: 1;
  pointer-events: auto;
}

.comment-drawer {
  position: absolute;
  top: 0;
  right: 0;
  display: flex;
  flex-direction: column;
  width: min(26rem, 100vw);
  height: 100dvh;
  max-height: 100dvh;
  overflow: hidden;
  background: var(--ui-bg-elevated);
  border-left: 1px solid color-mix(in srgb, var(--color-pine) 16%, transparent);
  box-shadow: -18px 0 48px -32px
    color-mix(in srgb, var(--color-pine-deep) 60%, transparent);
  transform: translateX(100%);
  opacity: 0;
  transition:
    transform var(--ui-motion-normal) var(--ui-motion-ease),
    opacity var(--ui-motion-normal) var(--ui-motion-ease);
  pointer-events: none;
  outline: none;
}

.comment-drawer.is-wide {
  top: 80px;
  right: 1.25rem;
  height: calc(100dvh - 96px);
  max-height: calc(100dvh - 96px);
  border: 1px solid color-mix(in srgb, var(--color-pine) 16%, transparent);
  border-radius: var(--radius-card);
  box-shadow: var(--ui-shadow-lg);
}

.comment-drawer.is-visible {
  transform: translateX(0);
  opacity: 1;
  pointer-events: auto;
}

.comment-drawer__header {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 1rem 1.1rem;
  border-bottom: 1px solid
    color-mix(in srgb, var(--color-pine) 12%, transparent);
  background: var(--ui-bg-elevated);
}

.comment-drawer__title {
  display: flex;
  align-items: baseline;
  gap: 0.4rem;
  min-width: 0;
}

.comment-drawer__title h2 {
  margin: 0;
}

.comment-drawer__close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2.75rem;
  height: 2.75rem;
  border: 1px solid color-mix(in srgb, var(--color-pine) 18%, transparent);
  border-radius: 999px;
  background: transparent;
  color: var(--color-ink-soft);
  cursor: pointer;
  transition:
    background-color 160ms ease,
    color 160ms ease;
}

.comment-drawer__close:hover {
  background: color-mix(in srgb, var(--color-pine-soft) 55%, transparent);
  color: var(--color-pine-deep);
}

.comment-drawer__close:focus-visible {
  outline: 2px solid var(--color-pine);
  outline-offset: 2px;
}

.comment-drawer__body {
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.comment-drawer__body > :deep(section) {
  flex: 1 1 auto;
  min-height: 0;
  height: 100%;
}

@media (max-width: 767px) {
  .comment-drawer {
    width: 100vw;
    border-left: 0;
  }

  .comment-drawer.is-wide {
    top: 0;
    right: 0;
    height: 100dvh;
    max-height: 100dvh;
    border-radius: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .comment-drawer,
  .comment-drawer__scrim {
    transition: none;
  }

  .comment-drawer {
    transform: none;
  }
}
</style>
