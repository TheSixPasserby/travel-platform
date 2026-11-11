import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import DestinationGallery from '@/components/DestinationGallery.vue'
import type { DestinationImage } from '@/types/destination'

const images: DestinationImage[] = [
  {
    id: '2', url: '/uploads/2026/11/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb.png', altText: '湖岸清晨', sortOrder: 1,
    sourceUrl: 'https://example.com/source-b', author: '摄影者乙', license: 'CC BY 4.0',
    licenseUrl: 'https://creativecommons.org/licenses/by/4.0/', retrievedAt: '2026-11-01T10:00:00.000+08:00'
  },
  {
    id: '1', url: '/uploads/2026/11/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.png', altText: '山间步道', sortOrder: 0,
    sourceUrl: 'https://example.com/source-a', author: '摄影者甲', license: 'CC0 1.0',
    licenseUrl: 'https://creativecommons.org/publicdomain/zero/1.0/', retrievedAt: '2026-10-30T10:00:00.000+08:00'
  }
]

describe('目的地图库', () => {
  it('按服务端 sortOrder 展示且 alt 使用真实说明', () => {
    const wrapper = mount(DestinationGallery, { props: { images } })
    const figures = wrapper.findAll('[data-test="gallery-image"]')
    expect(figures).toHaveLength(2)
    expect(figures[0]!.get('img').attributes('alt')).toBe('山间步道')
    expect(figures[0]!.get('img').attributes('src')).toBe('/uploads/2026/11/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.png')
    expect(figures[1]!.get('img').attributes('alt')).toBe('湖岸清晨')
    wrapper.unmount()
  })

  it('逐图展示作者、原始来源与许可链接', () => {
    const wrapper = mount(DestinationGallery, { props: { images } })
    const first = wrapper.findAll('[data-test="gallery-image"]')[0]!
    expect(first.text()).toContain('摄影者甲')
    const links = first.findAll('a').map(link => link.attributes('href'))
    expect(links).toContain('https://creativecommons.org/publicdomain/zero/1.0/')
    expect(links).toContain('https://example.com/source-a')
    expect(first.text()).toContain('2026-10-30')
    wrapper.unmount()
  })

  it('图片加载失败显示可见占位，仍保留说明与许可信息', async () => {
    const wrapper = mount(DestinationGallery, { props: { images } })
    await wrapper.findAll('[data-test="gallery-image"] img')[0]!.trigger('error')
    const first = wrapper.findAll('[data-test="gallery-image"]')[0]!
    expect(first.find('[data-test="gallery-placeholder"]').text()).toContain('图片暂时无法显示')
    expect(first.text()).toContain('山间步道')
    expect(first.text()).toContain('摄影者甲')
    wrapper.unmount()
  })

  it('没有受管图片时不渲染图库区域', () => {
    const wrapper = mount(DestinationGallery, { props: { images: [] } })
    expect(wrapper.find('[data-test="destination-gallery"]').exists()).toBe(false)
    wrapper.unmount()
  })
})
