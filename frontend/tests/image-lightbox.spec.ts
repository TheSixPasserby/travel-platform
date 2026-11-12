import { afterEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import ContentImage from '@/components/ContentImage.vue'
import SafeMarkdown from '@/components/SafeMarkdown.vue'
import ImageLightbox from '@/components/ImageLightbox.vue'
import * as downloads from '@/utils/imageDownload'
enableAutoUnmount(afterEach)
function pointer(element: Element, type: string, x: number, y: number, id = 1, pointerType = 'touch') {
  const event = new MouseEvent(type, { bubbles: true, cancelable: true, clientX: x, clientY: y, button: 0 })
  Object.defineProperties(event, { pointerId: { value: id }, pointerType: { value: pointerType } })
  element.dispatchEvent(event)
}
async function loadPhoto() {
  const stage = document.querySelector<HTMLElement>('.lightbox-photo')!
  vi.spyOn(stage, 'getBoundingClientRect').mockReturnValue({ left: 0, top: 0, width: 800, height: 600, right: 800, bottom: 600, x: 0, y: 0, toJSON() {} })
  const image = stage.querySelector('img')!
  Object.defineProperties(image, { naturalWidth: { value: 1600, configurable: true }, naturalHeight: { value: 1200, configurable: true } })
  image.dispatchEvent(new Event('load'))
  await flushPromises()
  return { stage, image }
}
function button(label: string) { return document.querySelector<HTMLButtonElement>(`[aria-label="${label}"]`)! }
const urls = Array.from(
  { length: 5 },
  (_, i) => `/uploads/2026/11/${String(i + 1).padStart(32, '0')}.jpg`
)
afterEach(() => {
  document.body.innerHTML = ''
  document.body.style.overflow = ''
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
  vi.useRealTimers()
})
async function open() {
  const wrapper = mount(SafeMarkdown, {
    props: {
      content: urls.map((url, i) => `![照片${i + 1}](${url})`).join('\n\n')
    },
    attachTo: document.body
  })
  await flushPromises()
  const image = wrapper.findAll('img')[2]!
  image.element.focus()
  await image.trigger('click')
  await flushPromises()
  return wrapper
}
describe('内容图片灯箱', () => {
  it('详情图库的重复URL按位置打开第二处说明', async () => {
    const wrapper = mount(ContentImage, {
      props: {
        src: urls[0]!,
        alt: '第二处',
        position: 1,
        images: [
          { src: urls[0]!, alt: '第一处' },
          { src: urls[0]!, alt: '第二处' }
        ]
      },
      attachTo: document.body
    })
    await wrapper.get('button').trigger('click')
    await flushPromises()
    const dialog = document.querySelector('[role="dialog"]')!
    expect(dialog.textContent).toContain('2 / 2')
    expect(dialog.textContent).toContain('第二处')
    wrapper.unmount()
  })

  it('可放大和平移，切换图片恢复适应窗口；缩放中滑动不误翻页', async () => {
    const wrapper = await open(),
      dialog = document.querySelector('[role="dialog"]')!
    const { image } = await loadPhoto()
    const zoom = button('放大图片')
    zoom.click()
    await flushPromises()
    expect(dialog.querySelector('.is-zoomed')).not.toBeNull()
    pointer(image, 'pointerdown', 220, 100)
    pointer(image, 'pointermove', 40, 100)
    pointer(image, 'pointerup', 40, 100)
    await flushPromises()
    expect(dialog.textContent).toContain('3 / 5')
    document.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true })
    )
    await flushPromises()
    expect(dialog.textContent).toContain('4 / 5')
    expect(dialog.querySelector('.is-zoomed')).toBeNull()
    wrapper.unmount()
  })

  it('重复URL按实际点击位置定位，保留该处说明', async () => {
    const wrapper = mount(SafeMarkdown, {
      props: { content: `![第一处](${urls[0]})\n\n![第二处](${urls[0]})` },
      attachTo: document.body
    })
    await flushPromises()
    await wrapper.findAll('img')[1]!.trigger('click')
    await flushPromises()
    const dialog = document.querySelector('[role="dialog"]')!
    expect(dialog.textContent).toContain('2 / 2')
    expect(dialog.textContent).toContain('第二处')
    wrapper.unmount()
  })
  it('正文五图按点击索引分组、键盘切换、Esc关闭并归还焦点/滚动', async () => {
    const wrapper = await open()
    const dialog = document.querySelector('[role="dialog"]')!
    expect(dialog).not.toBeNull()
    expect(dialog.textContent).toContain('3 / 5')
    expect(document.body.style.overflow).toBe('hidden')
    document.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true })
    )
    await flushPromises()
    expect(dialog.textContent).toContain('4 / 5')
    document.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })
    )
    await flushPromises()
    expect(document.querySelector('[role="dialog"]')).toBeNull()
    expect(document.activeElement).toBe(wrapper.findAll('img')[2]!.element)
    expect(document.body.style.overflow).toBe('')
    wrapper.unmount()
  })
  it('图片失败后提示、触摸翻页、组件卸载清理背景锁', async () => {
    const wrapper = await open()
    const img = document.querySelector('[role="dialog"] img')!
    img.dispatchEvent(new Event('error'))
    await flushPromises()
    expect(document.body.textContent).toContain('图片加载失败')
    const dialog = document.querySelector('[role="dialog"]')!
    const stage = dialog.querySelector('.lightbox-photo')!
    pointer(stage, 'pointerdown', 220, 100)
    pointer(stage, 'pointermove', 40, 100)
    pointer(stage, 'pointerup', 40, 100)
    await flushPromises()
    expect(dialog.textContent).toContain('4 / 5')
    wrapper.unmount()
    expect(document.body.style.overflow).toBe('')
  })
  it('非法图片和恶意HTML不创建远程图片或灯箱入口', async () => {
    const wrapper = mount(SafeMarkdown, {
      props: {
        content:
          '![远程](https://example.org/a.jpg)\n\n<img src=x onerror=alert(1)><script>alert(1)</script>'
      },
      attachTo: document.body
    })
    await flushPromises()
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.find('script').exists()).toBe(false)
    expect(document.querySelector('[role="dialog"]')).toBeNull()
    wrapper.unmount()
  })
  it('单图不显示翻页，Tab焦点循环，关闭按钮可用', async () => {
    const wrapper = mount(SafeMarkdown, {
      props: { content: `![独照](${urls[0]})` },
      attachTo: document.body
    })
    await flushPromises()
    await wrapper.get('img').trigger('keydown', { key: 'Enter' })
    await flushPromises()
    const dialog = document.querySelector('[role="dialog"]')!
    expect(dialog).not.toBeNull()
    expect(dialog.querySelector('[aria-label="下一张"]')).toBeNull()
    const close = dialog.querySelector<HTMLButtonElement>(
      '[aria-label="关闭图片预览"]'
    )!
    close.focus()
    document.dispatchEvent(
      new KeyboardEvent('keydown', {
        key: 'Tab',
        bubbles: true,
        cancelable: true
      })
    )
    expect(dialog.contains(document.activeElement)).toBe(true)
    close.click()
    await flushPromises()
    expect(document.querySelector('[role="dialog"]')).toBeNull()
    wrapper.unmount()
  })
})

describe('专业查看器交互', () => {
  it('animates from the thumbnail rectangle for 260ms and cancels on interaction/unmount', async () => {
    vi.stubGlobal('matchMedia', () => ({ matches: true }))
    const wrapper = mount(ImageLightbox, { props: { preview: { images: [{ src: urls[0]!, alt: '山水' }], index: 0, trigger: null,
      sourceRect: { left: 10, top: 20, width: 160, height: 120 } } }, attachTo: document.body })
    const cancel = vi.fn(), animate = vi.fn((_frames: unknown, _options: unknown) => ({ cancel, onfinish: null }))
    Object.defineProperty(document.querySelector('.lightbox-motion'), 'animate', { value: animate })
    vi.spyOn(document.querySelector('.lightbox-photo img')!, 'getBoundingClientRect').mockReturnValue(new DOMRect(0, 0, 800, 600))
    const { stage } = await loadPhoto()
    expect(animate).toHaveBeenCalledWith([
      { transform: 'translate(-310px, -220px) scale(0.2, 0.2)', opacity: .85 },
      { transform: 'none', opacity: 1 }
    ], expect.objectContaining({ duration: 260 }))
    stage.dispatchEvent(new WheelEvent('wheel', { bubbles: true, deltaY: -10 }))
    expect(cancel).toHaveBeenCalledOnce()
    wrapper.unmount()
  })
  it('falls back to a small fade/scale without a rectangle and skips motion when reduced', async () => {
    vi.stubGlobal('matchMedia', () => ({ matches: true }))
    const wrapper = await open(), animate = vi.fn((_frames: unknown, _options: unknown) => ({ cancel: vi.fn(), onfinish: null }))
    Object.defineProperty(document.querySelector('.lightbox-motion'), 'animate', { value: animate })
    vi.spyOn(document.querySelector('.lightbox-photo img')!, 'getBoundingClientRect').mockReturnValue(new DOMRect(0, 0, 800, 600))
    await loadPhoto()
    expect(animate.mock.calls[0]?.[0]).toEqual([{ transform: 'scale(0.98)', opacity: 0 }, { transform: 'none', opacity: 1 }])
    vi.stubGlobal('matchMedia', () => ({ matches: false }))
    await loadPhoto()
    expect(animate).toHaveBeenCalledOnce()
    wrapper.unmount()
  })
  it('shows a fixed download failure and aborts pending work when switching images', async () => {
    const wrapper = await open(); await loadPhoto()
    const download = vi.spyOn(downloads, 'downloadManagedImage').mockRejectedValueOnce(new Error('private server details'))
    button('下载原图').click(); await flushPromises()
    expect(document.querySelector('[role="alert"]')?.textContent).toBe('原图下载失败，请稍后重试。')
    expect(document.body.textContent).not.toContain('private server details')
    let signal: AbortSignal | undefined
    download.mockImplementationOnce(async (_src, input) => { signal = input; await new Promise<void>((_resolve, reject) => input?.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')))) })
    button('下载原图').click(); await flushPromises()
    button('下一张').click(); await flushPromises()
    expect(signal?.aborted).toBe(true)
    expect(document.querySelector('[role="alert"]')).toBeNull()
    wrapper.unmount()
  })
  it('uses natural dimensions for percentage, repeated clicks, rotation and 1:1', async () => {
    const wrapper = await open(), { image } = await loadPhoto()
    const percentage = () => document.querySelector('[aria-label="当前缩放比例"]')?.textContent
    expect(percentage()).toBe('50%')
    for (const value of ['100%', '150%', '200%', '300%', '400%', '400%']) {
      image.dispatchEvent(new MouseEvent('click', { bubbles: true, clientX: 400, clientY: 300 }))
      await flushPromises(); expect(percentage()).toBe(value)
    }
    button('向右旋转90度').click(); await flushPromises()
    expect(percentage()).toBe('37.5%')
    expect(image.style.transform).toContain('rotate(90deg)')
    button('实际尺寸').click(); await flushPromises(); expect(percentage()).toBe('100%')
    button('适应屏幕').click(); await flushPromises(); expect(percentage()).toBe('37.5%')
    wrapper.unmount()
  })
  it('distinguishes drag from click, clamps bounds and scales with wheel', async () => {
    const wrapper = await open(), { image, stage } = await loadPhoto()
    button('实际尺寸').click(); await flushPromises()
    pointer(image, 'pointerdown', 400, 300, 1, 'mouse')
    pointer(image, 'pointermove', 2400, 2300, 1, 'mouse')
    await flushPromises(); expect(stage.classList.contains('is-dragging')).toBe(true)
    pointer(image, 'pointerup', 2400, 2300, 1, 'mouse')
    image.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flushPromises()
    expect(image.style.transform).toContain('translate(400px, 300px)')
    expect(document.querySelector('[aria-label="当前缩放比例"]')?.textContent).toBe('100%')
    stage.dispatchEvent(new WheelEvent('wheel', { bubbles: true, cancelable: true, deltaY: -100, clientX: 400, clientY: 300 }))
    await flushPromises()
    expect(Number(document.querySelector('[aria-label="当前缩放比例"]')?.textContent?.replace('%', ''))).toBeGreaterThan(100)
    wrapper.unmount()
  })
  it('pinches and pans without switching, then allows a fit-mode swipe', async () => {
    const wrapper = await open(), { image, stage } = await loadPhoto()
    pointer(image, 'pointerdown', 300, 300, 1)
    pointer(image, 'pointerdown', 500, 300, 2)
    pointer(image, 'pointermove', 200, 300, 1)
    pointer(image, 'pointermove', 600, 300, 2)
    pointer(image, 'pointerup', 200, 300, 1)
    pointer(image, 'pointerup', 600, 300, 2)
    image.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flushPromises()
    expect(document.querySelector('[aria-label="当前缩放比例"]')?.textContent).toBe('100%')
    expect(document.querySelector('[role="dialog"]')?.textContent).toContain('3 / 5')
    button('适应屏幕').click(); await flushPromises()
    pointer(stage, 'pointerdown', 600, 300); pointer(stage, 'pointermove', 200, 300); pointer(stage, 'pointerup', 200, 300)
    await flushPromises()
    expect(document.querySelector('[role="dialog"]')?.textContent).toContain('4 / 5')
    wrapper.unmount()
  })
  it('does not close on an image click, closes on empty canvas and disables failed-image tools', async () => {
    const wrapper = await open(), { image, stage } = await loadPhoto()
    image.click(); await flushPromises()
    expect(document.querySelector('[role="dialog"]')).not.toBeNull()
    image.dispatchEvent(new Event('error')); await flushPromises()
    expect(button('下载原图').disabled).toBe(true)
    stage.click(); await flushPromises()
    expect(document.querySelector('[role="dialog"]')).toBeNull()
    wrapper.unmount()
  })
})

it('正常动效下灯箱淡出完成后恢复焦点和背景滚动', async () => {
  vi.useFakeTimers()
  vi.stubGlobal('matchMedia', () => ({ matches: true }))
  const wrapper = await open()
  document.dispatchEvent(
    new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })
  )
  await flushPromises()
  expect(document.querySelector('.image-lightbox.is-closing')).not.toBeNull()
  expect(document.body.style.overflow).toBe('hidden')
  await vi.advanceTimersByTimeAsync(200)
  await flushPromises()
  expect(document.querySelector('.image-lightbox')).toBeNull()
  expect(document.body.style.overflow).toBe('')
  expect(document.activeElement).toBe(wrapper.findAll('img')[2]!.element)
  wrapper.unmount()
})
it('减少动态效果时灯箱立即关闭', async () => {
  vi.stubGlobal('matchMedia', () => ({ matches: false }))
  const wrapper = await open()
  document.dispatchEvent(
    new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })
  )
  await flushPromises()
  expect(document.querySelector('.image-lightbox')).toBeNull()
  wrapper.unmount()
})
