<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import type { ImagePreview } from '@/composables/useImageLightbox'
const props = defineProps<{ preview: ImagePreview }>()
const emit = defineEmits<{ close: [] }>()
const dialog = ref<HTMLDialogElement | null>(null)
const index = ref(props.preview.index), status = ref<'loading'|'loaded'|'error'>('loading')
const image = computed(() => props.preview.images[index.value]!)
const zoomed=ref(false)
const multiple = computed(() => props.preview.images.length > 1)
let overflow = '', touch: { x: number; y: number } | null = null
function move(delta: number) {
  if (!multiple.value) return
  index.value = (index.value + delta + props.preview.images.length) % props.preview.images.length
  status.value = 'loading';zoomed.value=false
}
function key(event: KeyboardEvent) {
  if (event.key === 'Escape') { event.preventDefault(); event.stopImmediatePropagation(); emit('close') }
  else if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') { event.preventDefault(); move(event.key === 'ArrowLeft' ? -1 : 1) }
  else if (event.key === 'Tab') {
    const buttons = Array.from(dialog.value?.querySelectorAll<HTMLButtonElement>('button:not(:disabled)') ?? [])
    const first = buttons[0], last = buttons.at(-1)
    if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus() }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
    else if (!dialog.value?.contains(document.activeElement)) { event.preventDefault(); first?.focus() }
  }
}
function start(event: TouchEvent) {
  if(zoomed.value)return
  const point = event.touches.length === 1 ? event.touches[0] : null
  touch = point ? { x: point.clientX, y: point.clientY } : null
}
function end(event: TouchEvent) {
  const point = event.changedTouches[0]
  if (touch && point && event.changedTouches.length === 1) {
    const dx = point.clientX - touch.x, dy = point.clientY - touch.y
    if (Math.abs(dx) > 50 && Math.abs(dx) > Math.abs(dy) * 1.5) move(dx < 0 ? 1 : -1)
  }
  touch = null
}
function loaded(event: Event, state: 'loaded'|'error') {
  const target = event.target as HTMLImageElement
  if (target.getAttribute('src') === image.value.src && target.dataset.previewIndex === String(index.value)) status.value = state
}
onMounted(() => {
  overflow = document.body.style.overflow; document.body.style.overflow = 'hidden'
  if (typeof dialog.value?.showModal === 'function') dialog.value.showModal()
  else dialog.value?.setAttribute('open', '')
  dialog.value?.querySelector<HTMLButtonElement>('button')?.focus()
  document.addEventListener('keydown', key, true)
})
onBeforeUnmount(() => {
  document.removeEventListener('keydown', key, true)
  dialog.value?.close?.(); document.body.style.overflow = overflow
  if (props.preview.trigger?.isConnected) props.preview.trigger.focus({ preventScroll: true })
})
</script>
<template>
  <Teleport to="body">
    <dialog ref="dialog" class="image-lightbox" role="dialog" aria-modal="true" aria-label="图片预览" @cancel.prevent="emit('close')" @click.self="emit('close')" @touchstart="start" @touchend="end" @touchcancel="touch=null">
      <button type="button" class="lightbox-close" aria-label="关闭图片预览" @click="emit('close')">关闭 ×</button>
      <button type="button" class="lightbox-zoom" :aria-pressed="zoomed" :aria-label="zoomed?'适应窗口':'放大图片'" @click="zoomed=!zoomed;touch=null">{{ zoomed?'适应窗口':'放大图片' }}</button>
      <figure class="lightbox-figure">
        <div class="lightbox-photo" :class="{'is-zoomed':zoomed}">
          <p v-if="status==='loading'" role="status" class="lightbox-status">正在加载高清图片…</p>
          <p v-if="status==='error'" role="alert" class="lightbox-status">图片加载失败，请切换图片或关闭后重试。</p>
          <img :key="index" :data-preview-index="index" :src="image.src" :alt="image.alt" :class="{ 'image-failed':status==='error' }" draggable="false" @load="loaded($event,'loaded')" @error="loaded($event,'error')" />
        </div>
        <figcaption aria-live="polite"><span v-if="multiple">{{ index+1 }} / {{ preview.images.length }} · </span>{{ image.alt || '旅行影像' }}</figcaption>
      </figure>
      <nav v-if="multiple" class="lightbox-navigation" aria-label="图片切换"><button type="button" aria-label="上一张" @click="move(-1)">← 上一张</button><button type="button" aria-label="下一张" @click="move(1)">下一张 →</button></nav>
    </dialog>
  </Teleport>
</template>
<style scoped>
.image-lightbox{position:fixed;inset:0;width:100%;height:100%;max-width:none;max-height:none;margin:0;border:0;padding:4.5rem 1rem 5rem;background:rgb(8 17 15 / .96);color:#fff;overflow:auto;overscroll-behavior:contain;touch-action:pan-y pinch-zoom;z-index:1000}
.image-lightbox::backdrop{background:rgb(8 17 15 / .9)}
.lightbox-figure{height:100%;max-width:90rem;margin:auto;display:flex;flex-direction:column;align-items:center;gap:1rem}
.lightbox-photo{position:relative;min-height:0;flex:1;display:flex;width:100%;align-items:center;justify-content:center}
.lightbox-photo img{display:block;max-width:100%;max-height:100%;object-fit:contain;width:auto;height:auto}
.lightbox-photo .image-failed{visibility:hidden}
.lightbox-status{position:absolute;max-width:85%;text-align:center;color:#d7e5df}
.lightbox-photo.is-zoomed{display:block;overflow:auto;touch-action:pan-x pan-y}.lightbox-photo.is-zoomed img{max-width:none;max-height:none;width:200%;height:auto}.lightbox-zoom{position:absolute;top:max(1rem,env(safe-area-inset-top));left:1rem}
.lightbox-close{position:absolute;top:max(1rem,env(safe-area-inset-top));right:1rem}
.lightbox-navigation{position:absolute;bottom:max(1rem,env(safe-area-inset-bottom));left:0;display:flex;justify-content:center;gap:2rem;width:100%}
.image-lightbox button{min-height:44px;padding:.5rem 1rem;border:1px solid #70877e;border-radius:999px;background:#1b3028;color:#fff;cursor:pointer}
.image-lightbox button:focus-visible{outline:3px solid #f4b469;outline-offset:3px}
figcaption{text-align:center;overflow-wrap:anywhere;max-width:100%;flex-shrink:0}
</style>
