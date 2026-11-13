import { isManagedImageSource } from './managedImages'

export const CARD_IMAGE_SIZES = '(max-width: 767px) calc((100vw - 40px) / 2), (max-width: 1199px) 30vw, 320px'

export function cardImageSource(original: string | null): string | undefined {
  if (!original) return undefined
  return isManagedImageSource(original) ? `${original}?width=640` : original
}
export function cardImageSrcset(original: string | null): string | undefined {
  if (!original || !isManagedImageSource(original)) return undefined
  return [320, 640, 960].map(width => `${original}?width=${width} ${width}w`).join(', ')
}
