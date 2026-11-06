vi.mock('@/api/guides', () => ({ fetchTags:vi.fn().mockResolvedValue([{id:'2',name:'Nature'}]) }))
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import DestinationsView from '@/views/DestinationsView.vue'
import DestinationDetailView from '@/views/DestinationDetailView.vue'
import { fetchDestination, fetchDestinationGuides, fetchDestinations } from '@/api/destinations'
import { fetchTags } from '@/api/guides'
import { ApiError } from '@/api/client'
import type { Destination } from '@/types/destination'
import type { GuideSummary } from '@/types/guide'

vi.mock('@/api/destinations', () => ({
  fetchDestinations: vi.fn(),
  fetchDestination: vi.fn(),
  fetchDestinationGuides: vi.fn()
}))

const CITY_ID = '9007199254740993'
const SPOT_ID = '9007199254740994'
const OTHER_SPOT_ID = '9007199254740995'

function base(index: number): Partial<Destination> {
  return {
    address: null, longitude: null, latitude: null, recommendedDurationMinutes: null, recommendedDays: null,
    openingHours: null, ticketInfo: null, officialUrl: null, sourceUrl: null, verifiedAt: null, images: [],
    country: '中国', province: '浙江', city: '杭州', description: '沿着湖畔慢慢走。', coverImageUrl: null,
    recommendedSeasons: '春秋', status: 'ACTIVE', sortOrder: 0,
    createdAt: '2026-11-03T00:00:00.000+08:00', updatedAt: '2026-11-03T00:00:00.000+08:00',
    id: (9007199254740993n + BigInt(index)).toString()
  }
}

function city(overrides: Partial<Destination> = {}): Destination {
  return {
    ...base(0), name: '杭州', destinationType: 'CITY', parentId: null,
    recommendedDays: 3, address: '浙江省杭州市西湖区', officialUrl: 'https://www.hangzhou.gov.cn/',
    sourceUrl: 'https://www.hangzhou.gov.cn/about', verifiedAt: '2026-11-01T09:30:00.000+08:00',
    ...overrides
  } as Destination
}

function spot(overrides: Partial<Destination> = {}): Destination {
  return {
    ...base(1), name: '西湖', destinationType: 'SCENIC_SPOT', parentId: CITY_ID,
    address: '浙江省杭州市西湖区龙井路 1 号',
    recommendedDurationMinutes: 120,
    openingHours: '08:00-17:30',
    ticketInfo: '免费开放，游船另计',
    sourceUrl: 'https://www.hangzhou.gov.cn/xihu',
    verifiedAt: '2026-10-30T10:00:00.000+08:00',
    images: [{
      id: '91', url: '/uploads/2026/11/xihu.png', altText: '西湖晨雾', sortOrder: 0,
      sourceUrl: 'https://www.hangzhou.gov.cn/xihu-photo', author: '市文旅局', license: 'CC BY 4.0',
      licenseUrl: 'https://creativecommons.org/licenses/by/4.0/', retrievedAt: '2026-10-30T10:00:00.000+08:00'
    }],
    ...overrides
  } as Destination
}

function guide(index: number): GuideSummary {
  return {
    id: (8007199254740993n + BigInt(index)).toString(),
    title: `西湖慢行 ${index}`, summary: '沿湖走一圈。', coverImageUrl: null,
    author: { id: '7', nickname: '旅人', avatarUrl: null, bio: null },
    destination: { id: SPOT_ID, name: '西湖', status: 'ACTIVE' },
    travelDays: 2, budgetPerPerson: '800.00', season: '秋季', tags: [], status: 'PUBLISHED',
    publishedAt: '2026-11-02T00:00:00.000+08:00',
    createdAt: '2026-11-01T00:00:00.000+08:00', updatedAt: '2026-11-02T00:00:00.000+08:00',
    likeCount: 3, favoriteCount: 1, commentCount: 0
  }
}

function pageOf<T>(records: T[], current = 1, pages = 1, size = 9) {
  return { records, total: records.length + (pages - 1) * size, current, size, pages }
}

async function render(path: string) {
  const pinia = createPinia(); setActivePinia(pinia)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/destinations', name: 'destinations', component: DestinationsView },
      { path: '/destinations/:id', name: 'destination-detail', component: DestinationDetailView },
      { path: '/guides', name: 'guides', component: { template: '<div />' } },
      { path: '/guides/:id', name: 'guide-detail', component: { template: '<div />' } },
      { path: '/login', name: 'login', component: { template: '<div />' } },
      { path: '/itineraries/:id', name: 'itinerary-detail', component: { template: '<div />' } }
    ]
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(path === '/destinations' ? DestinationsView : DestinationDetailView, {
    global: { plugins: [pinia, router], stubs: { DestinationGallery: false } }
  })
  await flushPromises()
  return { wrapper, router }
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(fetchTags).mockResolvedValue([{id:"2",name:"Nature"}])
  vi.mocked(fetchDestinations).mockResolvedValue(pageOf([city()]))
  vi.mocked(fetchDestination).mockResolvedValue(city())
  vi.mocked(fetchDestinationGuides).mockResolvedValue(pageOf([guide(0)]))
})

describe('城市列表与城市详情', () => {
  it('城市卡片显示季节和建议天数，相关攻略按主题发起服务端筛选', async () => {
    const list=await render('/destinations')
    expect(list.wrapper.get('[data-test="destination-card"]').text()).toContain('3 天')
    expect(list.wrapper.get('[data-test="destination-card"]').text()).toContain('春秋')
    list.wrapper.unmount()
    const detail=await render(`/destinations/${CITY_ID}`)
    await detail.wrapper.get('[data-test="guides-theme"]').setValue('2')
    await flushPromises()
    expect(fetchDestinationGuides).toHaveBeenLastCalledWith(CITY_ID,expect.objectContaining({page:1,tagId:'2'}))
    detail.wrapper.unmount()
  })
  it('目的地列表默认只请求城市类型', async () => {
    const { wrapper } = await render('/destinations')
    expect(fetchDestinations).toHaveBeenCalledWith(expect.objectContaining({ destinationType: 'CITY', page: 1 }))
    wrapper.unmount()
  })

  it('城市详情分页列出下属景点，并携带城市作为父级筛选', async () => {
    vi.mocked(fetchDestinations).mockResolvedValue(pageOf([spot(), { ...spot({ id: OTHER_SPOT_ID, name: '灵隐寺' }) }], 1, 2, 2))
    const { wrapper } = await render(`/destinations/${CITY_ID}`)
    const spots = wrapper.get('[data-test="city-spots"]')
    expect(spots.findAll('[data-test="destination-card"]')).toHaveLength(2)
    expect(fetchDestinations).toHaveBeenCalledWith(
      expect.objectContaining({ destinationType: 'SCENIC_SPOT', parentId: CITY_ID, page: 1, size: expect.any(Number) })
    )
    vi.mocked(fetchDestinations).mockResolvedValue(pageOf([{ ...spot({ id: OTHER_SPOT_ID, name: '灵隐寺' }) }], 2, 2, 2))
    await wrapper.get('[data-test="spots-next-page"]').trigger('click')
    await flushPromises()
    expect(fetchDestinations).toHaveBeenLastCalledWith(expect.objectContaining({ parentId: CITY_ID, page: 2 }))
    expect(wrapper.get('[data-test="spots-page-status"]').text()).toContain('2 / 2')
    wrapper.unmount()
  })

  it('城市详情展示相关公开攻略并支持翻页', async () => {
    vi.mocked(fetchDestinationGuides).mockResolvedValue(pageOf([guide(0)], 1, 2, 6))
    const { wrapper } = await render(`/destinations/${CITY_ID}`)
    const section = wrapper.get('[data-test="destination-guides"]')
    expect(section.findAll('[data-test="guide-card"]')).toHaveLength(1)
    expect(fetchDestinationGuides).toHaveBeenCalledWith(CITY_ID, expect.objectContaining({ page: 1 }))
    vi.mocked(fetchDestinationGuides).mockResolvedValue(pageOf([guide(1)], 2, 2, 6))
    await wrapper.get('[data-test="guides-next-page"]').trigger('click')
    await flushPromises()
    expect(fetchDestinationGuides).toHaveBeenLastCalledWith(CITY_ID, expect.objectContaining({ page: 2 }))
    wrapper.unmount()
  })

  it('城市详情展示推荐天数，未填写的资料明确标注未核实', async () => {
    vi.mocked(fetchDestination).mockResolvedValue(city({ recommendedDays: null, address: null }))
    const { wrapper } = await render(`/destinations/${CITY_ID}`)
    const text = wrapper.text()
    expect(text).toContain('未核实')
    expect(wrapper.find('[data-test="fact-recommended-days"]').exists()).toBe(true)
    wrapper.unmount()
  })
})

describe('景点详情资料与图库', () => {
  it('展示地址、推荐时长、开放时间、票务与来源核验日期', async () => {
    vi.mocked(fetchDestination).mockResolvedValue(spot())
    vi.mocked(fetchDestinations).mockResolvedValue(pageOf([]))
    vi.mocked(fetchDestinationGuides).mockResolvedValue(pageOf([]))
    const { wrapper } = await render(`/destinations/${SPOT_ID}`)
    expect(wrapper.get('[data-test="fact-address"]').text()).toContain('龙井路 1 号')
    expect(wrapper.get('[data-test="fact-duration"]').text()).toContain('120')
    expect(wrapper.get('[data-test="fact-opening"]').text()).toContain('08:00-17:30')
    expect(wrapper.get('[data-test="fact-ticket"]').text()).toContain('免费开放')
    expect(wrapper.get('[data-test="fact-verified"]').text()).toContain('2026-10-30')
    expect(wrapper.get('[data-test="fact-source"] a').attributes('href')).toBe('https://www.hangzhou.gov.cn/xihu')
    wrapper.unmount()
  })

  it('资料为空时显示未核实，不用 0 或空串伪造坐标与价格', async () => {
    vi.mocked(fetchDestination).mockResolvedValue(spot({
      address: null, recommendedDurationMinutes: null, openingHours: null, ticketInfo: null,
      sourceUrl: null, verifiedAt: null, longitude: null, latitude: null
    }))
    vi.mocked(fetchDestinations).mockResolvedValue(pageOf([]))
    vi.mocked(fetchDestinationGuides).mockResolvedValue(pageOf([]))
    const { wrapper } = await render(`/destinations/${SPOT_ID}`)
    const text = wrapper.text()
    expect(text).toContain('未核实')
    expect(text).not.toContain('0.000000')
    expect(wrapper.get('[data-test="fact-ticket"]').text()).not.toContain('¥')
    wrapper.unmount()
  })

  it('景点详情渲染受管图库并标注作者与许可', async () => {
    vi.mocked(fetchDestination).mockResolvedValue(spot())
    vi.mocked(fetchDestinations).mockResolvedValue(pageOf([]))
    vi.mocked(fetchDestinationGuides).mockResolvedValue(pageOf([]))
    const { wrapper } = await render(`/destinations/${SPOT_ID}`)
    const gallery = wrapper.get('[data-test="destination-gallery"]')
    expect(gallery.findAll('[data-test="gallery-image"]')).toHaveLength(1)
    expect(gallery.text()).toContain('市文旅局')
    expect(gallery.text()).toContain('CC BY 4.0')
    wrapper.unmount()
  })

  it('景点详情列出自身相关攻略，父城市链接可导航', async () => {
    vi.mocked(fetchDestination).mockImplementation(async (id: string) =>
      id === CITY_ID ? city() : spot()
    )
    vi.mocked(fetchDestinations).mockResolvedValue(pageOf([]))
    const { wrapper } = await render(`/destinations/${SPOT_ID}`)
    expect(wrapper.get('[data-test="destination-guides"]').findAll('[data-test="guide-card"]').length).toBeGreaterThan(0)
    const parentLink = wrapper.findAll('a').find(link => link.attributes('href') === `/destinations/${CITY_ID}`)
    expect(parentLink).toBeTruthy()
    wrapper.unmount()
  })

  it('景点详情不再请求下属景点列表', async () => {
    vi.mocked(fetchDestination).mockResolvedValue(spot())
    vi.mocked(fetchDestinations).mockResolvedValue(pageOf([]))
    vi.mocked(fetchDestinationGuides).mockResolvedValue(pageOf([]))
    const { wrapper } = await render(`/destinations/${SPOT_ID}`)
    expect(fetchDestinations).not.toHaveBeenCalledWith(expect.objectContaining({ destinationType: 'SCENIC_SPOT' }))
    expect(wrapper.find('[data-test="city-spots"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('相关攻略加载失败不阻塞主体资料', async () => {
    vi.mocked(fetchDestination).mockResolvedValue(spot())
    vi.mocked(fetchDestinations).mockResolvedValue(pageOf([]))
    vi.mocked(fetchDestinationGuides).mockRejectedValue(new ApiError('攻略暂时不可用', { status: 503 }))
    const { wrapper } = await render(`/destinations/${SPOT_ID}`)
    expect(wrapper.text()).toContain('西湖')
    expect(wrapper.get('[data-test="guides-error"]').text()).toContain('攻略暂时不可用')
    wrapper.unmount()
  })
})
