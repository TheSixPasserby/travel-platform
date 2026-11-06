vi.mock('@/api/guides', () => ({ fetchTags:vi.fn().mockResolvedValue([{id:'2',name:'Nature'}]) }))
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import DestinationDetailView from '@/views/DestinationDetailView.vue'
import { fetchDestination, fetchDestinationGuides, fetchDestinations } from '@/api/destinations'
import * as itineraries from '@/api/itineraries'
import { useAuthStore } from '@/stores/auth'
import type { Destination } from '@/types/destination'
import type { ItineraryDetail, ItinerarySummary } from '@/types/itinerary'
import { ApiError } from '@/api/client'

vi.mock('@/api/destinations', () => ({
  fetchDestinations: vi.fn(), fetchDestination: vi.fn(), fetchDestinationGuides: vi.fn()
}))
vi.mock('@/api/itineraries', () => ({
  fetchItineraries: vi.fn(), fetchItinerary: vi.fn(), createItineraryItem: vi.fn()
}))

const SPOT_ID = '9007199254741001'

function spot(overrides: Partial<Destination> = {}): Destination {
  return {
    id: SPOT_ID, name: '西湖', destinationType: 'SCENIC_SPOT', parentId: '9007199254740993', country: '中国',
    province: '浙江', city: '杭州', description: '湖光山色', coverImageUrl: null, recommendedSeasons: '春',
    status: 'ACTIVE', sortOrder: 0, address: '浙江省杭州市西湖区龙井路 1 号', longitude: '120.150000', latitude: '30.250000',
    recommendedDurationMinutes: 120, recommendedDays: 1, openingHours: '08:00-17:30', ticketInfo: '免费开放',
    officialUrl: null, sourceUrl: 'https://example.com/xihu', verifiedAt: '2026-10-30T10:00:00.000+08:00', images: [],
    createdAt: '2026-10-01T00:00:00.000+08:00', updatedAt: '2026-10-01T00:00:00.000+08:00',
    ...overrides
  } as Destination
}

const summary: ItinerarySummary = {
  id: '8007199254740993', title: '杭州三日', destination: { id: '9007199254740993', name: '杭州', status: 'ACTIVE' },
  startDate: '2026-11-10', endDate: '2026-11-12', status: 'ACTIVE', dayCount: 2, itemCount: 0,
  createdAt: '2026-11-01T00:00:00.000+08:00', updatedAt: '2026-11-01T00:00:00.000+08:00'
}
const detail: ItineraryDetail = {
  ...summary, notes: null,
  days: [
    { id: '9007199254742001', itineraryId: summary.id, dayNo: 1, travelDate: '2026-11-10', title: '湖边', notes: null, createdAt: summary.createdAt, updatedAt: summary.updatedAt, items: [] },
    { id: '9007199254742002', itineraryId: summary.id, dayNo: 2, travelDate: '2026-11-11', title: null, notes: null, createdAt: summary.createdAt, updatedAt: summary.updatedAt, items: [] }
  ]
}

async function render(path = `/destinations/${SPOT_ID}`, loggedIn = true) {
  const pinia = createPinia(); setActivePinia(pinia)
  const auth = useAuthStore()
  if (loggedIn) {
    auth.token = 'test-token'
    auth.user = { id: '1', username: 'luna', nickname: '山间旅人', avatarUrl: null, bio: null, role: 'USER', status: 'ACTIVE', createdAt: summary.createdAt }
  }
  const stub = { template: '<div />' }
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/', name: 'home', component: stub },
    { path: '/destinations', name: 'destinations', component: stub },
    { path: '/destinations/:id', name: 'destination-detail', component: DestinationDetailView },
    { path: '/guides', name: 'guides', component: stub },
    { path: '/guides/:id', name: 'guide-detail', component: stub },
    { path: '/itineraries/:id', name: 'itinerary-detail', component: stub },
    { path: '/login', name: 'login', component: stub }
  ] })
  await router.push(path); await router.isReady()
  const wrapper = mount(RouterView, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return { wrapper, router }
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(fetchDestination).mockResolvedValue(spot())
  vi.mocked(fetchDestinations).mockResolvedValue({ records: [], total: 0, current: 1, size: 6, pages: 0 })
  vi.mocked(fetchDestinationGuides).mockResolvedValue({ records: [], total: 0, current: 1, size: 6, pages: 0 })
  vi.mocked(itineraries.fetchItineraries).mockResolvedValue({ records: [summary], total: 1, current: 1, size: 50, pages: 1 })
  vi.mocked(itineraries.fetchItinerary).mockResolvedValue(detail)
  vi.mocked(itineraries.createItineraryItem).mockResolvedValue({
    id: '9007199254743001', itineraryDayId: detail.days[0]!.id, destination: { id: SPOT_ID, name: '西湖', status: 'ACTIVE' },
    placeName: '西湖', addressText: '浙江省杭州市西湖区龙井路 1 号', startTime: null, endTime: null, notes: null, sortOrder: 0,
    createdAt: summary.createdAt, updatedAt: summary.updatedAt
  })
})

describe('目的地详情加入本人行程', () => {
  it('切换账号后丢弃前一账号迟到的私人行程列表', async () => {
    let resolve!: (value: Awaited<ReturnType<typeof itineraries.fetchItineraries>>) => void
    vi.mocked(itineraries.fetchItineraries).mockReturnValue(new Promise(done => { resolve = done }) as ReturnType<typeof itineraries.fetchItineraries>)
    const { wrapper } = await render()
    await wrapper.get('[data-test="itinerary-open"]').trigger('click')
    const auth=useAuthStore();auth.token='different-session';auth.user={...auth.user!,id:'2'}
    await flushPromises()
    resolve({records:[summary],total:1,current:1,size:50,pages:1});await flushPromises()
    expect(wrapper.text()).not.toContain(summary.title)
    wrapper.unmount()
  })
  it('离开页面后迟到的401不把新页面导航到登录', async () => {
    let reject!: (reason: unknown) => void
    vi.mocked(itineraries.fetchItineraries).mockReturnValue(new Promise((_done,fail) => {reject=fail}))
    const { wrapper,router }=await render()
    await wrapper.get('[data-test="itinerary-open"]').trigger('click')
    await router.push('/');await flushPromises()
    reject(new ApiError('expired',{status:401}));await flushPromises()
    expect(router.currentRoute.value.path).toBe('/')
    wrapper.unmount()
  })
  it('未登录时只提供登录入口，不请求行程数据', async () => {
    const { wrapper } = await render(`/destinations/${SPOT_ID}`, false)
    const entry = wrapper.get('[data-test="itinerary-entry"]')
    expect(entry.text()).toContain('登录')
    expect(wrapper.get('[data-test="itinerary-login"]').attributes('href')).toContain('/login')
    expect(itineraries.fetchItineraries).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('登录用户主动打开后只加载进行中的本人行程', async () => {
    const { wrapper } = await render()
    expect(itineraries.fetchItineraries).not.toHaveBeenCalled()
    await wrapper.get('[data-test="itinerary-open"]').trigger('click')
    await flushPromises()
    expect(itineraries.fetchItineraries).toHaveBeenCalledWith(expect.objectContaining({ page: 1, status: 'ACTIVE' }))
    expect(wrapper.get('[data-test="itinerary-select"]').text()).toContain('杭州三日')
    wrapper.unmount()
  })

  it('选择行程与当天后由用户确认，才把真实景点写入行程项目', async () => {
    const { wrapper } = await render()
    await wrapper.get('[data-test="itinerary-open"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-test="itinerary-select"]').setValue(summary.id)
    await flushPromises()
    expect(itineraries.fetchItinerary).toHaveBeenCalledWith(summary.id)
    await wrapper.get('[data-test="itinerary-day"]').setValue(detail.days[1]!.id)
    expect(itineraries.createItineraryItem).not.toHaveBeenCalled()
    await wrapper.get('[data-test="itinerary-confirm"]').trigger('click')
    await flushPromises()
    expect(itineraries.createItineraryItem).toHaveBeenCalledWith(detail.days[1]!.id, expect.objectContaining({
      destinationId: SPOT_ID, placeName: '西湖', addressText: '浙江省杭州市西湖区龙井路 1 号'
    }))
    expect(wrapper.get('[data-test="itinerary-notice"]').text()).toContain('已加入')
    wrapper.unmount()
  })

  it('停用目的地不提供加入行程入口', async () => {
    vi.mocked(fetchDestination).mockResolvedValue(spot({ status: 'INACTIVE' }))
    const { wrapper } = await render()
    expect(wrapper.find('[data-test="itinerary-entry"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('没有可用行程时给出明确提示且不写入任何项目', async () => {
    vi.mocked(itineraries.fetchItineraries).mockResolvedValue({ records: [], total: 0, current: 1, size: 50, pages: 0 })
    const { wrapper } = await render()
    await wrapper.get('[data-test="itinerary-open"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('还没有进行中的行程')
    expect(wrapper.find('[data-test="itinerary-confirm"]').exists()).toBe(false)
    wrapper.unmount()
  })
})
