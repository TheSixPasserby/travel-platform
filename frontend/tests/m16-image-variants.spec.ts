import { describe, expect, it } from 'vitest'
import { cardImageSource, cardImageSrcset } from '@/utils/imageVariants'

describe('managed card image variants', () => {
  it('uses fixed widths and keeps the original URL intact for viewing', () => {
    const original = '/uploads/2026/11/0123456789abcdef0123456789abcdef.jpg'
    expect(cardImageSource(original)).toBe(original + '?width=640')
    expect(cardImageSrcset(original)).toBe(`${original}?width=320 320w, ${original}?width=640 640w, ${original}?width=960 960w`)
    expect(original).toBe('/uploads/2026/11/0123456789abcdef0123456789abcdef.jpg')
  })
  it('avoids sending arbitrary URLs or query strings to the variant endpoint', () => {
    for (const source of ['https://example.com/image.jpg', '/uploads/../private.jpg', '/uploads/photo.jpg?path=private']) {
      expect(cardImageSource(source)).toBe(source)
      expect(cardImageSrcset(source)).toBeUndefined()
    }
    expect(cardImageSource(null)).toBeUndefined()
  })
})
