<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import type { ImagePreview } from '@/composables/useImageLightbox'
import { useImageTransform } from '@/composables/useImageTransform'
import { downloadManagedImage } from '@/utils/imageDownload'
import ImageViewerToolbar from './ImageViewerToolbar.vue'
import SiteIcon from './SiteIcon.vue'

const props = defineProps<{ preview: ImagePreview }>()
const emit = defineEmits<{ close: [] }>()
const dialog = ref<HTMLDialogElement | null>(null), stage = ref<HTMLElement | null>(null)
const photo = ref<HTMLImageElement | null>(null), motionLayer = ref<HTMLElement | null>(null)
const index = ref(props.preview.index), status = ref<'loading' | 'loaded' | 'error'>('loading')
const closing = ref(false), opening = ref(false), dragging = ref(false), smooth = ref(false)
const downloading = ref(false), downloadError = ref('')
const image = computed(() => props.preview.images[index.value]!)
const multiple = computed(() => props.preview.images.length > 1)
const transform = useImageTransform()
const { scale, rotation, x, y, percentage, isFit } = transform
const imageStyle = computed(() => ({
  width: `${transform.width.value}px`, height: `${transform.height.value}px`,
  transform: `translate(-50%, -50%) translate(${x.value}px, ${y.value}px) rotate(${rotation.value}deg) scale(${scale.value})`
}))
let overflow = '', disposed = false, switched = false, loadVersion = 0
let closeTimer: ReturnType<typeof setTimeout> | undefined
let smoothTimer: ReturnType<typeof setTimeout> | undefined
let animation: Animation | null = null, resizeObserver: ResizeObserver | undefined
let downloadController: AbortController | null = null
const motionAllowed = () => window.matchMedia?.('(prefers-reduced-motion: no-preference)').matches === true

function measure() {
  const rect = stage.value?.getBoundingClientRect()
  if (rect) transform.setViewport(rect.width, rect.height)
}
function cancelAnimation() { animation?.cancel(); animation = null; opening.value = false }
function animateArrival(fromSource: boolean) {
  cancelAnimation()
  if (!motionAllowed() || !motionLayer.value?.animate || !photo.value) return
  const target = photo.value.getBoundingClientRect(), source = fromSource ? props.preview.sourceRect : undefined
  if (!target.width || !target.height) return
  const valid = source && [source.left, source.top, source.width, source.height].every(Number.isFinite)
    && source.width > 0 && source.height > 0
  const start = valid
    ? `translate(${source.left + source.width / 2 - (target.left + target.width / 2)}px, ${source.top + source.height / 2 - (target.top + target.height / 2)}px) scale(${source.width / target.width}, ${source.height / target.height})`
    : 'scale(0.98)'
  opening.value = true
  animation = motionLayer.value.animate([
    { transform: start, opacity: valid ? .85 : 0 }, { transform: 'none', opacity: 1 }
  ], { duration: 260, easing: 'cubic-bezier(.2,.7,.2,1)' })
  animation.onfinish = () => { animation = null; opening.value = false }
}
async function loaded(event: Event, state: 'loaded' | 'error') {
  const target = event.target as HTMLImageElement
  if (target !== photo.value || target.dataset.previewIndex !== String(index.value)
    || target.getAttribute('src') !== image.value.src) return
  const version = ++loadVersion
  status.value = state
  if (state === 'error' || !target.naturalWidth || !target.naturalHeight) {
    transform.reset(); cancelAnimation(); return
  }
  transform.setImage(target.naturalWidth, target.naturalHeight)
  await nextTick()
  if (disposed || version !== loadVersion) return
  measure(); await nextTick()
  if (!disposed && version === loadVersion) animateArrival(!switched)
}
function transition(action: () => void) {
  cancelAnimation()
  smooth.value = motionAllowed()
  clearTimeout(smoothTimer)
  action()
  smoothTimer = setTimeout(() => { smooth.value = false }, 170)
}
function closeLightbox() {
  if (closing.value) return
  cancelAnimation()
  if (!motionAllowed()) { emit('close'); return }
  closing.value = true
  closeTimer = setTimeout(() => emit('close'), 180)
}
function clearPointers() { points.clear(); single = null; pinch = null; dragging.value = false }
function move(delta: number) {
  if (!multiple.value || closing.value) return
  cancelAnimation(); clearPointers(); loadVersion++; switched = true
  downloadController?.abort(); downloadController = null; downloading.value = false; downloadError.value = ''
  index.value = (index.value + delta + props.preview.images.length) % props.preview.images.length
  status.value = 'loading'; transform.reset(); smooth.value = false
}
function key(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.preventDefault(); event.stopImmediatePropagation(); closeLightbox()
  } else if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
    event.preventDefault(); move(event.key === 'ArrowLeft' ? -1 : 1)
  } else if (['+', '=', '-', '0', 'f', 'F'].includes(event.key) && !event.ctrlKey && !event.metaKey && !event.altKey && status.value === 'loaded') {
    event.preventDefault()
    transition(() => {
      if (event.key === '0') transform.zoomTo(1)
      else if (event.key.toLowerCase() === 'f') transform.fit()
      else transform.step(event.key === '-' ? -1 : 1)
    })
  } else if (event.key === 'Tab') {
    const buttons = Array.from(dialog.value?.querySelectorAll<HTMLButtonElement>('button:not(:disabled)') ?? [])
    const first = buttons[0], last = buttons.at(-1)
    if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus() }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
    else if (!dialog.value?.contains(document.activeElement)) { event.preventDefault(); first?.focus() }
  }
}

type Point = { x: number; y: number }
const points = new Map<number, Point>()
let single: { start: Point; origin: Point; fit: boolean; moved: boolean; pinched: boolean; touch: boolean } | null = null
let pinch: { distance: number; center: Point } | null = null
let suppressClick = false
function local(point: Point): Point {
  const rect = stage.value!.getBoundingClientRect()
  return { x: point.x - rect.left - rect.width / 2, y: point.y - rect.top - rect.height / 2 }
}
function pair() {
  const [a, b] = Array.from(points.values())
  return a && b ? { distance: Math.max(1, Math.hypot(a.x - b.x, a.y - b.y)), center: local({ x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 }) } : null
}
function pointerDown(event: PointerEvent) {
  if (event.button !== 0 || closing.value || points.size >= 2) return
  cancelAnimation(); smooth.value = false
  const point = { x: event.clientX, y: event.clientY }
  if (!points.size) {
    suppressClick = false
    single = { start: point, origin: { x: x.value, y: y.value }, fit: isFit.value, moved: false, pinched: false, touch: event.pointerType === 'touch' }
  }
  points.set(event.pointerId, point)
  if (points.size === 2) {
    pinch = pair(); suppressClick = true
    if (single) single.pinched = true
  }
  // Capture on the original target so a stationary click still targets the image.
  try { (event.target as Element).setPointerCapture?.(event.pointerId) } catch { /* Pointer already cancelled. */ }
}
function pointerMove(event: PointerEvent) {
  if (!points.has(event.pointerId)) return
  points.set(event.pointerId, { x: event.clientX, y: event.clientY })
  if (points.size === 2) {
    const next = pair()
    if (next && pinch && status.value === 'loaded') {
      transform.zoomTo(scale.value * next.distance / pinch.distance, pinch.center.x, pinch.center.y)
      transform.panTo(x.value + next.center.x - pinch.center.x, y.value + next.center.y - pinch.center.y)
    }
    pinch = next; dragging.value = true; suppressClick = true
  } else if (single) {
    const dx = event.clientX - single.start.x, dy = event.clientY - single.start.y
    if (Math.hypot(dx, dy) > 6) { single.moved = true; suppressClick = true }
    if (single.moved && !single.fit) {
      dragging.value = true; transform.panTo(single.origin.x + dx, single.origin.y + dy)
    }
  }
}
function pointerUp(event: PointerEvent) {
  if (!points.has(event.pointerId)) return
  const gesture = single
  const dx = event.clientX - (gesture?.start.x ?? event.clientX), dy = event.clientY - (gesture?.start.y ?? event.clientY)
  points.delete(event.pointerId); pinch = null; dragging.value = false
  if (points.size) {
    const remaining = Array.from(points.values())[0]!
    single = { start: remaining, origin: { x: x.value, y: y.value }, fit: false, moved: true, pinched: true, touch: true }
  } else {
    single = null
    if (gesture?.touch && gesture.fit && !gesture.pinched && Math.abs(dx) > 50 && Math.abs(dx) > Math.abs(dy) * 1.5) {
      suppressClick = true; move(dx < 0 ? 1 : -1)
    }
  }
}
function pointerCancel(event: PointerEvent) {
  if (points.has(event.pointerId)) { suppressClick = true; clearPointers() }
}
function clickImage(event: MouseEvent) {
  if (suppressClick) { suppressClick = false; return }
  if (status.value !== 'loaded') return
  const anchor = local({ x: event.clientX, y: event.clientY })
  transition(() => transform.step(1, anchor.x, anchor.y))
}
function clickBlank() {
  if (suppressClick) { suppressClick = false; return }
  closeLightbox()
}
function wheel(event: WheelEvent) {
  if (status.value !== 'loaded') return
  cancelAnimation(); smooth.value = false
  const anchor = local({ x: event.clientX, y: event.clientY })
  const pixels = event.deltaY * (event.deltaMode === 1 ? 16 : event.deltaMode === 2 ? transform.viewportHeight.value : 1)
  transform.zoomTo(scale.value * Math.exp(-Math.max(-250, Math.min(250, pixels)) / 450), anchor.x, anchor.y)
}
async function download() {
  if (downloading.value || status.value !== 'loaded') return
  const controller = new AbortController()
  downloadController = controller; downloading.value = true; downloadError.value = ''
  const timer = setTimeout(() => controller.abort(), 20000)
  try { await downloadManagedImage(image.value.src, controller.signal) }
  catch {
    if (!disposed && downloadController === controller) downloadError.value = '原图下载失败，请稍后重试。'
  } finally {
    clearTimeout(timer)
    if (downloadController === controller) { downloadController = null; downloading.value = false }
  }
}
onMounted(() => {
  overflow = document.body.style.overflow; document.body.style.overflow = 'hidden'
  if (typeof dialog.value?.showModal === 'function') dialog.value.showModal()
  else dialog.value?.setAttribute('open', '')
  dialog.value?.querySelector<HTMLButtonElement>('[aria-label="关闭图片预览"]')?.focus()
  measure()
  if (typeof ResizeObserver !== 'undefined' && stage.value) {
    resizeObserver = new ResizeObserver(() => {
      const rect = stage.value?.getBoundingClientRect()
      if (rect && (Math.abs(rect.width - transform.viewportWidth.value) > .5 || Math.abs(rect.height - transform.viewportHeight.value) > .5)) {
        cancelAnimation(); measure()
      }
    }); resizeObserver.observe(stage.value)
  }
  window.addEventListener('resize', measure)
  document.addEventListener('keydown', key, true)
})
onBeforeUnmount(() => {
  disposed = true; loadVersion++; clearTimeout(closeTimer); clearTimeout(smoothTimer)
  cancelAnimation(); clearPointers(); resizeObserver?.disconnect(); downloadController?.abort()
  document.removeEventListener('keydown', key, true); window.removeEventListener('resize', measure)
  dialog.value?.close?.(); document.body.style.overflow = overflow
  if (props.preview.trigger?.isConnected) props.preview.trigger.focus({ preventScroll: true })
})
</script>

<template>
  <Teleport to="body">
    <dialog ref="dialog" class="image-lightbox" :class="{ 'is-closing': closing, 'is-opening': opening }"
      role="dialog" aria-modal="true" aria-label="图片预览" @cancel.prevent="closeLightbox" @click.self="clickBlank">
      <header class="lightbox-header">
        <span class="lightbox-brand">山水间<span>旅行影像</span></span>
        <nav v-if="multiple" class="lightbox-navigation" aria-label="图片切换">
          <button type="button" aria-label="上一张" title="上一张（←）" @click="move(-1)">‹</button>
          <span aria-live="polite">{{ index + 1 }} / {{ preview.images.length }}</span>
          <button type="button" aria-label="下一张" title="下一张（→）" @click="move(1)">›</button>
        </nav>
        <button type="button" class="lightbox-close" aria-label="关闭图片预览" title="关闭（Esc）" @click="closeLightbox"><SiteIcon name="close" /></button>
      </header>
      <div ref="stage" class="lightbox-photo" :class="{ 'is-zoomed': status === 'loaded' && !isFit, 'is-dragging': dragging, 'is-smooth': smooth }"
        @pointerdown="pointerDown" @pointermove="pointerMove" @pointerup="pointerUp" @pointercancel="pointerCancel" @lostpointercapture="pointerCancel"
        @wheel.prevent="wheel" @click.self="clickBlank">
        <p v-if="status === 'loading'" role="status" class="lightbox-status">正在加载高清图片…</p>
        <p v-if="status === 'error'" role="alert" class="lightbox-status">图片加载失败，请切换图片或关闭后重试。</p>
        <div ref="motionLayer" class="lightbox-motion">
          <img :key="index" ref="photo" :data-preview-index="index" :src="image.src" :alt="image.alt" :style="imageStyle"
            :class="{ 'image-hidden': status !== 'loaded' || !transform.ready.value }" draggable="false"
            @load="loaded($event, 'loaded')" @error="loaded($event, 'error')" @click.stop="clickImage" @dragstart.prevent />
        </div>
      </div>
      <footer class="lightbox-footer" @click.self="clickBlank">
        <p class="lightbox-caption" aria-live="polite">{{ image.alt || '旅行影像' }}</p>
        <ImageViewerToolbar :loaded="status === 'loaded' && transform.ready.value" :percentage="percentage"
          :zoom-in-disabled="scale >= 4" :zoom-out-disabled="isFit" :downloading="downloading"
          @rotate-left="transition(() => transform.rotate(-1))" @rotate-right="transition(() => transform.rotate(1))"
          @zoom-out="transition(() => transform.step(-1))" @zoom-in="transition(() => transform.step(1))"
          @fit="transition(transform.fit)" @actual="transition(() => transform.zoomTo(1))" @download="download" />
        <p v-if="downloadError" role="alert" class="lightbox-download-error">{{ downloadError }}</p>
        <p class="lightbox-hint">点击放大 · 拖动查看 · 滚轮或双指缩放</p>
      </footer>
    </dialog>
  </Teleport>
</template>

<style scoped>
.image-lightbox {
  --color-cream: #121619;
  --color-cream-deep: #1e2428;
  --color-surface: #22292d;
  --color-ink: #edf0f0;
  --color-ink-soft: #bbc5c9;
  --color-pine: var(--ui-viewer-accent, #9bcdb2);
  --color-pine-deep: var(--ui-viewer-accent, #9bcdb2);
  --color-pine-soft: color-mix(in srgb, var(--ui-viewer-accent, #9bcdb2) 18%, #1e2428);
  --color-action: var(--ui-viewer-accent, #9bcdb2);
  --color-action-hover: var(--ui-viewer-accent, #9bcdb2);
  --color-on-action: var(--ui-viewer-on, #101819);
  position:fixed; inset:0; width:100%; height:100%; height:100dvh; max-width:none; max-height:none; margin:0; border:0; padding:max(16px, env(safe-area-inset-top)) max(24px, env(safe-area-inset-right)) max(16px, env(safe-area-inset-bottom)) max(24px, env(safe-area-inset-left)); background:var(--color-cream); color:var(--color-ink); overflow:hidden; overscroll-behavior:contain; z-index:1000; }
.image-lightbox[open] { display:grid; grid-template-rows:auto minmax(0,1fr) auto; gap:16px; }
.image-lightbox::backdrop { background:rgb(8 10 12 / .85); backdrop-filter:blur(12px); }
.lightbox-header { display:flex; align-items:center; justify-content:space-between; gap:12px; min-height:44px; }
.lightbox-brand { display:flex; gap:12px; align-items:baseline; font:600 20px var(--font-serif); color:var(--color-pine-deep); }
.lightbox-brand span { font:400 12px var(--font-sans); color:var(--color-ink-soft); }
.lightbox-navigation { display:flex; align-items:center; gap:8px; font-size:13px; font-variant-numeric:tabular-nums; }
.lightbox-navigation span { min-width:48px; text-align:center; }
.image-lightbox .lightbox-header button { display:grid; place-items:center; width:44px; height:44px; padding:0; border:1px solid color-mix(in srgb, var(--color-ink) 14%, transparent); border-radius:50%; background:color-mix(in srgb, var(--color-surface) 65%, transparent); color:var(--color-ink); cursor:pointer; transition:background 160ms ease; }
.lightbox-header button:hover { background:var(--color-pine-soft); }
.lightbox-navigation button { font-size:28px; line-height:1; }
.lightbox-close svg { width:20px; height:20px; }
.image-lightbox button:focus-visible { outline:3px solid var(--color-pine); outline-offset:3px; }
.lightbox-photo { position:relative; min-height:0; margin-inline:32px; overflow:hidden; touch-action:none; user-select:none; }
.is-opening .lightbox-photo { overflow:visible; }
.lightbox-motion { position:absolute; inset:0; pointer-events:none; transform-origin:center; }
.lightbox-photo img { position:absolute; left:50%; top:50%; display:block; max-width:none; max-height:none; object-fit:contain; transform-origin:center; pointer-events:auto; cursor:zoom-in; will-change:transform; box-shadow:0 8px 40px rgb(0 0 0 / .08); }
.lightbox-photo.is-zoomed img { cursor:grab; }
.lightbox-photo.is-dragging, .lightbox-photo.is-dragging img { cursor:grabbing; }
.lightbox-photo.is-smooth img { transition:transform 160ms cubic-bezier(.2,.7,.2,1); }
.lightbox-photo .image-hidden { visibility:hidden; }
.lightbox-status { position:absolute; inset:0; display:grid; place-content:center; margin:auto; max-width:85%; text-align:center; color:var(--color-ink-soft); font-size:14px; pointer-events:none; }
.lightbox-footer { display:flex; min-width:0; flex-direction:column; align-items:center; gap:12px; }
.lightbox-caption { max-width:min(70rem,100%); max-height:3em; overflow:auto; margin:0; text-align:center; overflow-wrap:anywhere; color:var(--color-ink); font-size:14px; line-height:1.5; }
.lightbox-hint { margin:0; color:var(--color-ink-soft); font-size:12px; }
.lightbox-download-error { margin:0; font-size:13px; color:var(--color-ink); }
.image-lightbox.is-closing { opacity:0; pointer-events:none; transition:opacity 180ms ease; }
@media (max-width:600px) {
  .image-lightbox { padding-inline:max(12px, env(safe-area-inset-left)) max(12px, env(safe-area-inset-right)); }
  .image-lightbox[open] { gap:12px; }
  .lightbox-photo { margin-inline:0; }
  .lightbox-brand { font-size:18px; }
  .lightbox-brand span { display:none; }
  .lightbox-navigation { gap:2px; }
  .lightbox-footer { gap:10px; }
  .lightbox-hint { font-size:11px; }
}
@media (prefers-reduced-motion:reduce) {
  .image-lightbox, .lightbox-photo img, .image-lightbox .lightbox-header button { animation:none!important; transition:none!important; }
}
</style>
