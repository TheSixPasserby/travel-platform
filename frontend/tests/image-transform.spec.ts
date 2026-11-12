import { describe, expect, it } from 'vitest'
import { useImageTransform } from '@/composables/useImageTransform'

describe('自然尺寸图片变换', () => {
  it('fits without cropping or upscaling and reports the true natural-size ratio', () => {
    const t = useImageTransform()
    t.setViewport(800, 600); t.setImage(1600, 900)
    expect(t.scale.value).toBe(.5)
    expect(t.percentage.value).toBe('50%')
    expect(t.x.value).toBe(0); expect(t.y.value).toBe(0)
    t.setImage(200, 100)
    expect(t.scale.value).toBe(1)
  })
  it('zooms around the pointer and clamps pan to the rotated bounds', () => {
    const t = useImageTransform()
    t.setViewport(800, 600); t.setImage(1600, 1200)
    t.zoomTo(1, 100, 50)
    expect(t.x.value).toBe(-100); expect(t.y.value).toBe(-50)
    t.panTo(10000, -10000)
    expect(t.x.value).toBe(400); expect(t.y.value).toBe(-300)
    t.zoomTo(.1)
    expect(t.scale.value).toBe(.5)
    expect(t.x.value).toBe(0); expect(t.y.value).toBe(0)
  })
  it('steps through fit, 100%, 150%, 200%, 300%, 400% without wrapping or closing', () => {
    const t = useImageTransform()
    t.setViewport(800, 600); t.setImage(1600, 1200)
    const values: number[] = []
    for (let i=0;i<6;i++) { t.step(1); values.push(t.scale.value) }
    expect(values).toEqual([1, 1.5, 2, 3, 4, 4])
    t.step(-1); expect(t.scale.value).toBe(3)
    t.fit(); expect(t.scale.value).toBe(.5)
  })
  it('rotates to a fresh contain ratio and resets on a new image', () => {
    const t = useImageTransform()
    t.setViewport(800, 600); t.setImage(1600, 900)
    t.zoomTo(2); t.panTo(200, 300); t.rotate(1)
    expect(t.rotation.value).toBe(90)
    expect(t.scale.value).toBe(.375)
    expect(t.x.value).toBe(0); expect(t.y.value).toBe(0)
    t.rotate(-1); expect(t.scale.value).toBe(.5)
    t.setImage(1000, 1000)
    expect(t.rotation.value).toBe(0); expect(t.scale.value).toBe(.6)
  })
  it('refits on resize in fit mode and constrains a zoomed image after resize', () => {
    const t = useImageTransform()
    t.setViewport(800, 600); t.setImage(1600, 1200)
    t.setViewport(400, 600); expect(t.scale.value).toBe(.25)
    t.zoomTo(1); t.panTo(600, 0)
    t.setViewport(1400, 600)
    expect(t.scale.value).toBe(1); expect(t.x.value).toBe(100)
    t.reset(); expect(t.percentage.value).toBe('—')
  })
})
