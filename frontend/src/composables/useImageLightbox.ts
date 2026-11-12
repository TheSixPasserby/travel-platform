import { inject, onScopeDispose, shallowRef, watch } from 'vue'
import { routerKey } from 'vue-router'
import { isManagedImageSource } from '@/utils/markdown'
export interface PreviewImage { src: string; alt: string }
export interface ImagePreview {
  images: PreviewImage[]
  index: number
  trigger: HTMLElement | null
  sourceRect?: { left: number; top: number; width: number; height: number }
}
export function useImageLightbox() {
  const preview = shallowRef<ImagePreview | null>(null)
  const router = inject(routerKey, null)
  function open(images: PreviewImage[], src: string, trigger: EventTarget | null, position?: number) {
    const safe = images.filter(image => isManagedImageSource(image.src))
    const selectedIndex = position ?? images.findIndex(image => image.src === src)
    const selected = images[selectedIndex]
    if (!Number.isInteger(selectedIndex) || selected?.src !== src || !isManagedImageSource(src)) return
    const index = images.slice(0, selectedIndex).filter(image => isManagedImageSource(image.src)).length
    const element = trigger instanceof HTMLElement ? trigger : null
    const thumbnail = element instanceof HTMLImageElement ? element : element?.querySelector('img')
    const rect = thumbnail?.getBoundingClientRect()
    const visible = rect && [rect.left, rect.top, rect.width, rect.height].every(Number.isFinite)
      && rect.width > 0 && rect.height > 0 && rect.right > 0 && rect.bottom > 0
      && rect.left < window.innerWidth && rect.top < window.innerHeight
    preview.value = {
      images: safe, index, trigger: element,
      sourceRect: visible ? { left: rect.left, top: rect.top, width: rect.width, height: rect.height } : undefined
    }
  }
  function close() { preview.value = null }
  if (router) watch(() => router.currentRoute.value.fullPath, close)
  onScopeDispose(close)
  return { preview, open, close }
}
