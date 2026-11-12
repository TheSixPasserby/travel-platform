import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ImageViewerToolbar from '@/components/ImageViewerToolbar.vue'

const LABELS = [
  '向左旋转90度',
  '向右旋转90度',
  '缩小图片',
  '放大图片',
  '适应屏幕',
  '实际尺寸',
  '下载原图'
] as const

const EVENTS = [
  'rotate-left',
  'rotate-right',
  'zoom-out',
  'zoom-in',
  'fit',
  'actual',
  'download'
] as const

function mountToolbar(props: Record<string, unknown> = {}) {
  return mount(ImageViewerToolbar, {
    props: {
      loaded: true,
      percentage: '67%',
      zoomInDisabled: false,
      zoomOutDisabled: false,
      downloading: false,
      ...props
    }
  })
}

function button(wrapper: ReturnType<typeof mountToolbar>, label: string) {
  return wrapper.get(`button[aria-label="${label}"]`)
}

describe('图片工具栏', () => {
  it('渲染 toolbar 语义与当前缩放比例', () => {
    const wrapper = mountToolbar({ percentage: '67%' })
    const toolbar = wrapper.get('[role="toolbar"]')
    expect(toolbar.attributes('aria-label')).toBe('图片工具栏')
    const output = wrapper.get('output[aria-label="当前缩放比例"]')
    expect(output.text()).toBe('67%')
    wrapper.unmount()
  })

  it('七个按钮按顺序带精确 aria-label', () => {
    const wrapper = mountToolbar()
    const labels = wrapper
      .findAll('button')
      .map((item) => item.attributes('aria-label'))
    expect(labels).toEqual([...LABELS])
    wrapper.unmount()
  })

  it('点击各按钮触发对应无参事件', async () => {
    const wrapper = mountToolbar()
    for (let i = 0; i < LABELS.length; i += 1) {
      await button(wrapper, LABELS[i]!).trigger('click')
      const emitted = wrapper.emitted(EVENTS[i]!)
      expect(emitted, EVENTS[i]).toBeTruthy()
      expect(emitted).toHaveLength(1)
      expect(emitted![0]).toEqual([])
    }
    wrapper.unmount()
  })

  it('loaded=false 时所有按钮禁用', () => {
    const wrapper = mountToolbar({ loaded: false })
    for (const label of LABELS) {
      expect(button(wrapper, label).attributes('disabled')).toBeDefined()
    }
    wrapper.unmount()
  })

  it('缩放边界额外禁用对应按钮', () => {
    const wrapper = mountToolbar({ zoomInDisabled: true, zoomOutDisabled: true })
    expect(button(wrapper, '放大图片').attributes('disabled')).toBeDefined()
    expect(button(wrapper, '缩小图片').attributes('disabled')).toBeDefined()
    expect(button(wrapper, '向左旋转90度').attributes('disabled')).toBeUndefined()
    wrapper.unmount()
  })

  it('下载中禁用下载按钮并显示下载中', async () => {
    const wrapper = mountToolbar({ downloading: true })
    const download = button(wrapper, '下载原图')
    expect(download.attributes('disabled')).toBeDefined()
    expect(download.text()).toContain('下载中')
    await download.trigger('click')
    expect(wrapper.emitted('download')).toBeUndefined()
    wrapper.unmount()
  })

  it('适应屏幕与下载按钮带线性 SVG 图标与文字', () => {
    const wrapper = mountToolbar()
    for (const label of ['适应屏幕', '下载原图']) {
      const el = button(wrapper, label)
      const svg = el.get('svg')
      expect(svg.attributes('aria-hidden')).toBe('true')
      expect(svg.attributes('fill')).toBe('none')
      expect(el.text().trim().length).toBeGreaterThan(0)
    }
    expect(button(wrapper, '实际尺寸').text()).toContain('1:1')
    wrapper.unmount()
  })

})
