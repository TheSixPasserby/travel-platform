import { shallowRef } from 'vue'
import { isManagedImageSource } from '@/utils/markdown'
export interface PreviewImage { src: string; alt: string }
export interface ImagePreview { images: PreviewImage[]; index: number; trigger: HTMLElement | null }
export function useImageLightbox() {
  const preview = shallowRef<ImagePreview | null>(null)
  function open(images: PreviewImage[], src: string, trigger: EventTarget | null, position?: number) {
    const safe = images.filter(image => isManagedImageSource(image.src))
    const selected = position === undefined ? images.find(image => image.src === src) : images[position]
    const index = selected?.src === src ? safe.indexOf(selected) : -1
    if (index < 0) return
    preview.value = { images: safe, index, trigger: trigger instanceof HTMLElement ? trigger : null }
  }
  function close() { preview.value = null }
  return { preview, open, close }
}
