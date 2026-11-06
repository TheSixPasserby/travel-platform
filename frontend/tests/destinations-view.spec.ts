vi.mock('@/api/guides', () => ({ fetchTags:vi.fn().mockResolvedValue([{id:'2',name:'Nature'}]) }))
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import DestinationsView from '@/views/DestinationsView.vue'
import DestinationDetailView from '@/views/DestinationDetailView.vue'
import { fetchDestination, fetchDestinations } from '@/api/destinations'
import { ApiError } from '@/api/client'
import type { Destination } from '@/types/destination'

vi.mock('@/api/destinations', () => ({ fetchDestinations: vi.fn(), fetchDestination: vi.fn() }))

function destination(index = 0): Destination {
  return {
    address:null,longitude:null,latitude:null,recommendedDurationMinutes:null,recommendedDays:null,openingHours:null,ticketInfo:null,officialUrl:null,sourceUrl:null,verifiedAt:null,images:[],
    id: (9007199254740993n + BigInt(index)).toString(), name: `山水目的地${index}`,
    destinationType: 'CITY', parentId: null, country: '中国', province: '浙江', city: null,
    description: '沿着湖畔慢慢走。', coverImageUrl: null, recommendedSeasons: '春秋', status: 'ACTIVE',
    sortOrder: 0, createdAt: '2026-11-03T00:00:00.000+08:00', updatedAt: '2026-11-03T00:00:00.000+08:00'
  }
}
const firstPage = () => ({ records: Array.from({ length: 9 }, (_, index) => destination(index)), total: 10, current: 1, size: 9, pages: 2 })
async function render(detail = false) {
  const pinia = createPinia(); setActivePinia(pinia)
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/destinations', name: 'destinations', component: DestinationsView },
    { path: '/destinations/:id', name: 'destination-detail', component: DestinationDetailView },
    { path: '/guides', name: 'guides', component: { template:'<div />' } },
    { path: '/login', name: 'login', component: { template:'<div />' } },
    { path: '/itineraries/:id', name: 'itinerary-detail', component: { template:'<div />' } }
  ] })
  await router.push(detail ? `/destinations/${destination().id}` : '/destinations')
  await router.isReady()
  const wrapper = mount(detail ? DestinationDetailView : DestinationsView, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return { wrapper, router }
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(fetchDestinations).mockResolvedValue(firstPage())
  vi.mocked(fetchDestination).mockResolvedValue(destination())
})

describe('目的地浏览', () => {
  it('保留大ID字符串并按服务器页码加载下一页', async () => {
    const { wrapper } = await render()
    expect(fetchDestinations).toHaveBeenCalledWith(expect.objectContaining({ page: 1, size: 9 }))
    expect(wrapper.findAll('[data-test="destination-card"]')).toHaveLength(9)
    expect(wrapper.get('[data-test="destination-card"]').attributes('href')).toContain('9007199254740993')
    vi.mocked(fetchDestinations).mockResolvedValueOnce({ records: [destination(9)], total: 10, current: 2, size: 9, pages: 2 })
    await wrapper.get('[data-test="next-page"]').trigger('click')
    await flushPromises()
    expect(fetchDestinations).toHaveBeenLastCalledWith(expect.objectContaining({ page: 2, size: 9 }))
    expect(wrapper.findAll('[data-test="destination-card"]')).toHaveLength(1)
    expect(wrapper.get('[data-test="page-status"]').text()).toContain('2 / 2')
    wrapper.unmount()
  })

  it('提交关键词与分类时重置到第一页', async () => {
    const { wrapper } = await render()
    await wrapper.get('[data-test="keyword"]').setValue('  西湖  ')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(fetchDestinations).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: '西湖', page: 1 }))
    await wrapper.get('[data-test="destination-type"]').setValue('SCENIC_SPOT')
    await flushPromises()
    expect(fetchDestinations).toHaveBeenLastCalledWith(expect.objectContaining({ destinationType: 'SCENIC_SPOT', page: 1 }))
    wrapper.unmount()
  })

  it('空列表有明确提示', async () => {
    vi.mocked(fetchDestinations).mockResolvedValueOnce({ records: [], total: 0, current: 1, size: 9, pages: 0 })
    const { wrapper } = await render()
    expect(wrapper.text()).toContain('还没有找到目的地')
    expect(wrapper.findAll('[data-test="destination-card"]')).toHaveLength(0)
    wrapper.unmount()
  })

  it('临时加载错误可重试', async () => {
    vi.mocked(fetchDestinations).mockRejectedValueOnce(new ApiError('暂时无法加载', { status: 503 }))
    const { wrapper } = await render()
    expect(wrapper.get('[role="alert"]').text()).toContain('暂时无法加载')
    await wrapper.get('[data-test="retry-destinations"]').trigger('click')
    await flushPromises()
    expect(wrapper.findAll('[data-test="destination-card"]')).toHaveLength(9)
    wrapper.unmount()
  })
})

describe('目的地详情', () => {
  it('保留停用历史详情并提示状态', async () => {
    vi.mocked(fetchDestination).mockResolvedValueOnce({ ...destination(), status: 'INACTIVE' })
    const { wrapper } = await render(true)
    expect(fetchDestination).toHaveBeenCalledWith('9007199254740993')
    expect(wrapper.text()).toContain('山水目的地0')
    expect(wrapper.text()).toContain('停用')
    expect(wrapper.findAll('a').map(link=>link.attributes('href'))).toContain('/guides?destinationId=9007199254740993')
    wrapper.unmount()
  })

  it('不存在详情有返回列表入口', async () => {
    vi.mocked(fetchDestination).mockRejectedValueOnce(new ApiError('目的地不存在', { status: 404, code: 40401 }))
    const { wrapper } = await render(true)
    expect(wrapper.get('[role="alert"]').text()).toContain('目的地不存在')
    expect(wrapper.find('a[href="/destinations"]').exists()).toBe(true)
    wrapper.unmount()
  })
})
