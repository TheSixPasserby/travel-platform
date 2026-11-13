import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import AddToTripDrawer from '@/components/AddToTripDrawer.vue'
import { ApiError } from '@/api/client'
import { fetchItineraries, fetchItinerary, importGuidePlaces } from '@/api/itineraries'
import { useAuthStore } from '@/stores/auth'
import type { GuideDestination, GuideDetail } from '@/types/guide'
import type {
  GuideTripImportInput,
  ItineraryDay,
  ItineraryDetail,
  ItineraryItem,
  ItinerarySummary
} from '@/types/itinerary'

vi.mock('@/api/itineraries', () => ({
  fetchItineraries: vi.fn(),
  fetchItinerary: vi.fn(),
  importGuidePlaces: vi.fn()
}))

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const GUIDE_ID = '9007199254742001'
const CITY_ID = '9007199254742002'
const SPOT_WEST_LAKE = '9007199254742101'
const SPOT_LINGYIN = '9007199254742102'
const SPOT_RETIRED = '9007199254742103'
const ITIN_ID = '9007199254742201'
const OTHER_ITIN_ID = '9007199254742202'
const DAY_ID = '9007199254742301'
const DAY2_ID = '9007199254742302'
const USER = { id: '1', username: 'traveler', nickname: '旅人', avatarUrl: null, bio: null, role: 'USER' as const, status: 'ACTIVE' as const, createdAt: '2026-11-01T00:00:00.000+08:00' }

const spot = (id: string, name: string, status: 'ACTIVE' | 'INACTIVE' = 'ACTIVE'): GuideDestination => ({ id, name, status })
const guide = (overrides: Partial<GuideDetail> = {}): GuideDetail => ({
  id: GUIDE_ID,
  title: '西湖两日慢行',
  summary: null,
  coverImageUrl: null,
  author: { id: '2', nickname: '山间旅人', avatarUrl: null, bio: null },
  destination: { id: CITY_ID, name: '杭州', status: 'ACTIVE' },
  travelDays: 2,
  budgetPerPerson: null,
  season: null,
  tags: [],
  status: 'PUBLISHED',
  publishedAt: '2026-11-01T00:00:00.000+08:00',
  createdAt: '2026-11-01T00:00:00.000+08:00',
  updatedAt: '2026-11-01T00:00:00.000+08:00',
  likeCount: 0,
  favoriteCount: 0,
  commentCount: 0,
  content: '正文里写了很多景点，但组件只认 scenicSpots，不解析正文。',
  images: [],
  scenicSpots: [spot(SPOT_WEST_LAKE, '西湖'), spot(SPOT_LINGYIN, '灵隐寺'), spot(SPOT_RETIRED, '已停业的旧景点', 'INACTIVE')],
  ...overrides
})

const summary = (id: string, title: string): ItinerarySummary => ({
  id, title, destination: { id: CITY_ID, name: '杭州', status: 'ACTIVE' },
  startDate: '2026-11-10', endDate: '2026-11-12', status: 'ACTIVE',
  dayCount: 2, itemCount: 0, createdAt: '2026-11-01T00:00:00.000+08:00', updatedAt: '2026-11-01T00:00:00.000+08:00'
})
const item = (id: string, destination: { id: string; name: string } | null, placeName: string): ItineraryItem => ({
  id, itineraryDayId: DAY_ID, destination: destination ? { ...destination, status: 'ACTIVE' } : null,
  placeName, addressText: null, startTime: null, endTime: null, notes: null, sortOrder: 0,
  createdAt: '2026-11-01T00:00:00.000+08:00', updatedAt: '2026-11-01T00:00:00.000+08:00'
})
const day = (id: string, dayNo: number, travelDate: string, items: ItineraryItem[] = []): ItineraryDay => ({
  id, itineraryId: ITIN_ID, dayNo, travelDate, title: null, notes: null,
  createdAt: '2026-11-01T00:00:00.000+08:00', updatedAt: '2026-11-01T00:00:00.000+08:00', items
})
const detail = (id: string, days: ItineraryDay[]): ItineraryDetail => ({
  ...summary(id, '杭州两日慢行'), notes: null, days
})
const pageOf = (records: ItinerarySummary[], current = 1, pages = 1) => ({
  records, total: pages * records.length, current, size: 50, pages
})

let mounted: VueWrapper[] = []
type RenderOptions = {
  props?: Record<string, unknown>
  authenticated?: boolean
  guide?: GuideDetail
}
async function render(options: RenderOptions = {}) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAuthStore()
  if (options.authenticated !== false) {
    auth.token = 'session-a'
    auth.user = { ...USER }
  }
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/login', name: 'login', component: { template: '<div />' } },
      { path: '/guides/:id', name: 'guide-detail', component: { template: '<div />' } },
      { path: '/me/itineraries', name: 'my-itineraries', component: { template: '<div />' } },
      { path: '/itineraries/:id', name: 'itinerary-detail', component: { template: '<div />' } }
    ]
  })
  await router.push('/')
  await router.isReady()
  const wrapper = mount(AddToTripDrawer, {
    props: { open: true, guide: options.guide ?? guide(), initialSelectedIds: [], ...(options.props ?? {}) },
    global: { plugins: [pinia, router] },
    attachTo: document.body
  })
  mounted.push(wrapper)
  await flushPromises()
  return { wrapper, router, auth }
}

async function chooseTrip(wrapper: VueWrapper, itineraryId = ITIN_ID, dayId = DAY_ID): Promise<void> {
  await wrapper.get('[data-test=trip-itinerary-select]').setValue(itineraryId)
  await flushPromises()
  await wrapper.get('[data-test=trip-day-select]').setValue(dayId)
  await flushPromises()
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(fetchItineraries).mockResolvedValue(pageOf([summary(ITIN_ID, '杭州两日慢行')]))
  vi.mocked(fetchItinerary).mockResolvedValue(detail(ITIN_ID, [day(DAY_ID, 1, '2026-11-10'), day(DAY2_ID, 2, '2026-11-11')]))
  vi.mocked(importGuidePlaces).mockResolvedValue({ itineraryId: ITIN_ID, dayId: DAY_ID, added: [SPOT_LINGYIN], alreadyPresent: [] })
})

afterEach(() => {
  mounted.splice(0).forEach((wrapper) => wrapper.unmount())
  document.body.style.overflow = ''
  document.body.innerHTML = ''
})

describe('M16 加入行程抽屉 · 只读与取消', () => {
  it('打开时读取本人 ACTIVE 行程，取消不发起任何写入', async () => {
    const { wrapper } = await render()
    expect(fetchItineraries).toHaveBeenCalledWith({ page: 1, size: 50, status: 'ACTIVE' })
    await chooseTrip(wrapper)
    await wrapper.get('[data-test=trip-cancel]').trigger('click')
    expect(importGuidePlaces).not.toHaveBeenCalled()
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('未登录时不读取行程，只提示登录并保留手动入口', async () => {
    const { wrapper } = await render({ authenticated: false })
    expect(fetchItineraries).not.toHaveBeenCalled()
    expect(wrapper.get('[data-test=trip-login-needed]').text()).toContain('请先登录')
    expect(wrapper.get('[data-test=trip-login-link]').attributes('href')).toContain('/login')
    expect(wrapper.get('[data-test=trip-confirm]').attributes('disabled')).toBeDefined()
    expect(importGuidePlaces).not.toHaveBeenCalled()
  })

  it('城市失效或没有关联景点时说明原因并给出去手动安排的链接', async () => {
    const retired = await render({ guide: guide({ destination: { id: CITY_ID, name: '杭州', status: 'INACTIVE' } }) })
    expect(retired.wrapper.get('[data-test=trip-city-invalid]').text()).toContain('城市已失效')
    expect(retired.wrapper.get('[data-test=trip-manual-link]').attributes('href')).toBe('/me/itineraries')
    expect(retired.wrapper.find('[data-test=trip-confirm]').attributes('disabled')).toBeDefined()

    const empty = await render({ guide: guide({ scenicSpots: [] }) })
    expect(empty.wrapper.get('[data-test=trip-no-spots]').text()).toContain('还没有关联景点')
    expect(empty.wrapper.get('[data-test=trip-manual-link]').attributes('href')).toBe('/me/itineraries')
    expect(empty.wrapper.get('[data-test=trip-confirm]').attributes('disabled')).toBeDefined()
  })
})

describe('M16 加入行程抽屉 · 真实 payload 与去重', () => {
  it('默认勾选全部 ACTIVE 景点，失效资源禁用且不进入 payload', async () => {
    const { wrapper } = await render()
    const boxes = wrapper.findAll('input[type=checkbox]')
    expect(boxes).toHaveLength(3)
    expect((wrapper.get('[data-test=spot-' + SPOT_WEST_LAKE + ']').element as HTMLInputElement).checked).toBe(true)
    expect((wrapper.get('[data-test=spot-' + SPOT_RETIRED + ']').element as HTMLInputElement).disabled).toBe(true)
    expect((wrapper.get('[data-test=spot-' + SPOT_RETIRED + ']').element as HTMLInputElement).checked).toBe(false)
    await chooseTrip(wrapper)
    await wrapper.get('[data-test=trip-confirm]').trigger('click')
    await flushPromises()
    const body = vi.mocked(importGuidePlaces).mock.calls[0]![0] as GuideTripImportInput
    expect(body.clientRequestId).toMatch(UUID)
    expect(body.guideId).toBe(GUIDE_ID)
    expect(body.destinationIds).toEqual([SPOT_WEST_LAKE, SPOT_LINGYIN])
    expect(body.itineraryId).toBe(ITIN_ID)
    expect(body.dayId).toBe(DAY_ID)
    expect(body.newItinerary).toBeNull()
  })

  it('预览当天已存在的 destination.id，结果显示真实新增与跳过数量并停止重复提交', async () => {
    vi.mocked(fetchItinerary).mockResolvedValue(
      detail(ITIN_ID, [
        day(DAY_ID, 1, '2026-11-10', [item('item-1', { id: SPOT_WEST_LAKE, name: '西湖' }, '西湖')]),
        day(DAY2_ID, 2, '2026-11-11')
      ])
    )
    vi.mocked(importGuidePlaces).mockResolvedValue({
      itineraryId: ITIN_ID, dayId: DAY_ID, added: [SPOT_LINGYIN], alreadyPresent: [SPOT_WEST_LAKE]
    })
    const { wrapper } = await render()
    await chooseTrip(wrapper)
    expect(wrapper.get('[data-test=trip-new-count]').text()).toBe('1')
    expect(wrapper.get('[data-test=trip-duplicates]').text()).toContain('西湖')
    await wrapper.get('[data-test=trip-confirm]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-test=trip-added-count]').text()).toBe('1')
    expect(wrapper.get('[data-test=trip-skipped-count]').text()).toBe('1')
    expect(wrapper.get('[data-test=trip-result-link]').attributes('href')).toBe('/itineraries/' + ITIN_ID)
    expect(wrapper.get('[data-test=trip-confirm]').attributes('disabled')).toBeDefined()
    await wrapper.get('[data-test=trip-confirm]').trigger('click')
    await flushPromises()
    expect(importGuidePlaces).toHaveBeenCalledTimes(1)
  })

  it('失败保留选择并用同一 UUID 重试，输入改变后换新 UUID', async () => {
    vi.mocked(importGuidePlaces)
      .mockRejectedValueOnce(new ApiError('网络连接失败，请检查网络后重试', { isNetworkError: true }))
      .mockResolvedValueOnce({ itineraryId: ITIN_ID, dayId: DAY_ID, added: [SPOT_WEST_LAKE, SPOT_LINGYIN], alreadyPresent: [] })
      .mockResolvedValueOnce({ itineraryId: ITIN_ID, dayId: DAY_ID, added: [SPOT_WEST_LAKE], alreadyPresent: [] })
    const { wrapper } = await render()
    await chooseTrip(wrapper)
    await wrapper.get('[data-test=trip-confirm]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-test=trip-error]').text()).toContain('网络连接失败')
    expect((wrapper.get('[data-test=spot-' + SPOT_LINGYIN + ']').element as HTMLInputElement).checked).toBe(true)
    expect(wrapper.get('[data-test=trip-itinerary-select]').element).toHaveProperty('value', ITIN_ID)

    await wrapper.get('[data-test=trip-confirm]').trigger('click')
    await flushPromises()
    const first = vi.mocked(importGuidePlaces).mock.calls[0]![0].clientRequestId
    const second = vi.mocked(importGuidePlaces).mock.calls[1]![0].clientRequestId
    expect(second).toBe(first)
    expect(wrapper.find('[data-test=trip-result]').exists()).toBe(true)

    await wrapper.get('[data-test=spot-' + SPOT_LINGYIN + ']').setValue(false)
    await wrapper.get('[data-test=trip-confirm]').trigger('click')
    await flushPromises()
    const third = vi.mocked(importGuidePlaces).mock.calls[2]![0].clientRequestId
    expect(third).not.toBe(first)
    expect(vi.mocked(importGuidePlaces).mock.calls[2]![0].destinationIds).toEqual([SPOT_WEST_LAKE])
  })
})

describe('M16 加入行程抽屉 · 行程选择与新建', () => {
  it('超过前 50 条时可加载更多并选择第二页行程', async () => {
    vi.mocked(fetchItineraries).mockImplementation(async (query = {}) =>
      query.page === 2
        ? pageOf([summary(OTHER_ITIN_ID, '苏州两日')], 2, 2)
        : pageOf([summary(ITIN_ID, '杭州两日慢行')], 1, 2)
    )
    const { wrapper } = await render()
    expect(wrapper.findAll('[data-test=trip-itinerary-select] option')).toHaveLength(2)
    await wrapper.get('[data-test=trip-load-more]').trigger('click')
    await flushPromises()
    expect(fetchItineraries).toHaveBeenLastCalledWith({ page: 2, size: 50, status: 'ACTIVE' })
    expect(wrapper.findAll('[data-test=trip-itinerary-select] option')).toHaveLength(3)
    await wrapper.get('[data-test=trip-itinerary-select]').setValue(OTHER_ITIN_ID)
    await flushPromises()
    expect(fetchItinerary).toHaveBeenLastCalledWith(OTHER_ITIN_ID)
  })

  it('行程没有日期时提示去行程详情手动添加，不提交', async () => {
    vi.mocked(fetchItinerary).mockResolvedValue(detail(ITIN_ID, []))
    const { wrapper } = await render()
    await wrapper.get('[data-test=trip-itinerary-select]').setValue(ITIN_ID)
    await flushPromises()
    expect(wrapper.get('[data-test=trip-no-days]').text()).toContain('还没有日期')
    expect(wrapper.get('[data-test=trip-no-days]').text()).toContain('行程详情')
    expect(wrapper.get('[data-test=trip-confirm]').attributes('disabled')).toBeDefined()
  })

  it('新建模式提交完整 newItinerary 且不带 existing 目标', async () => {
    const { wrapper } = await render()
    await wrapper.get('[data-test=trip-mode-new]').trigger('click')
    await wrapper.get('[data-test=trip-title]').setValue('西湖三日')
    await wrapper.get('[data-test=trip-start]').setValue('2026-12-01')
    await wrapper.get('[data-test=trip-end]').setValue('2026-12-03')
    await wrapper.get('[data-test=trip-travel-date]').setValue('2026-12-02')
    expect(wrapper.text()).toContain('旅行日期（景点加入哪一天）')
    await wrapper.get('[data-test=trip-confirm]').trigger('click')
    await flushPromises()
    const body = vi.mocked(importGuidePlaces).mock.calls[0]![0]
    expect(body.itineraryId).toBeNull()
    expect(body.dayId).toBeNull()
    expect(body.newItinerary).toEqual({ title: '西湖三日', startDate: '2026-12-01', endDate: '2026-12-03', travelDate: '2026-12-02' })
    expect(body.destinationIds).toEqual([SPOT_WEST_LAKE, SPOT_LINGYIN])
  })

  it('新建模式字段不完整时禁用确认按钮', async () => {
    const { wrapper } = await render()
    await wrapper.get('[data-test=trip-mode-new]').trigger('click')
    await wrapper.get('[data-test=trip-title]').setValue('西湖三日')
    await wrapper.get('[data-test=trip-start]').setValue('2026-12-03')
    await wrapper.get('[data-test=trip-end]').setValue('2026-12-01')
    await wrapper.get('[data-test=trip-travel-date]').setValue('2026-12-02')
    expect(wrapper.get('[data-test=trip-confirm]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-test=trip-hint]').text()).toContain('结束日期不能早于开始日期')
    await wrapper.get('[data-test=trip-confirm]').trigger('click')
    await flushPromises()
    expect(importGuidePlaces).not.toHaveBeenCalled()
  })
})

describe('M16 加入行程抽屉 · 抽屉行为与迟到响应', () => {
  it('锁定背景滚动、Esc 关闭，关闭后恢复触发器焦点', async () => {
    const trigger = document.createElement('button')
    trigger.textContent = '打开抽屉'
    document.body.appendChild(trigger)
    trigger.focus()
    const { wrapper } = await render()
    expect(document.body.style.overflow).toBe('hidden')
    expect(document.activeElement?.getAttribute('data-test')).toBe('trip-close')
    await wrapper.setProps({ open: false })
    await flushPromises()
    expect(document.body.style.overflow).toBe('')
    expect(document.activeElement).toBe(trigger)
  })

  it('提交 pending 时禁止重复提交与关闭，当前选择看起来不被清空', async () => {
    let finish!: (value: { itineraryId: string; dayId: string; added: string[]; alreadyPresent: string[] }) => void
    vi.mocked(importGuidePlaces).mockReturnValue(new Promise((resolve) => { finish = resolve }) as never)
    const { wrapper } = await render()
    await chooseTrip(wrapper)
    await wrapper.get('[data-test=trip-confirm]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-test=trip-confirm]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-test=trip-close]').attributes('disabled')).toBeDefined()
    await wrapper.get('[data-test=trip-confirm]').trigger('click')
    await wrapper.get('[data-test=trip-close]').trigger('click')
    await wrapper.get('[data-test=trip-scrim]').trigger('click')
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await flushPromises()
    expect(importGuidePlaces).toHaveBeenCalledTimes(1)
    expect(wrapper.emitted('close')).toBeUndefined()
    expect((wrapper.get('[data-test=spot-' + SPOT_WEST_LAKE + ']').element as HTMLInputElement).checked).toBe(true)
    finish({ itineraryId: ITIN_ID, dayId: DAY_ID, added: [SPOT_WEST_LAKE, SPOT_LINGYIN], alreadyPresent: [] })
    await flushPromises()
    expect(wrapper.find('[data-test=trip-result]').exists()).toBe(true)
  })

  it('切换攻略后，上一份攻略的迟到提交结果被丢弃', async () => {
    let finish!: (value: { itineraryId: string; dayId: string; added: string[]; alreadyPresent: string[] }) => void
    vi.mocked(importGuidePlaces).mockReturnValue(new Promise((resolve) => { finish = resolve }) as never)
    const { wrapper } = await render()
    await chooseTrip(wrapper)
    await wrapper.get('[data-test=trip-confirm]').trigger('click')
    await flushPromises()
    await wrapper.setProps({ guide: guide({ id: '9007199254742999', title: '另一篇攻略' }) })
    await flushPromises()
    finish({ itineraryId: ITIN_ID, dayId: DAY_ID, added: [SPOT_WEST_LAKE], alreadyPresent: [] })
    await flushPromises()
    expect(wrapper.find('[data-test=trip-result]').exists()).toBe(false)
  })

  it('账号切换后清空上一个账号的提交结果并重新读取行程', async () => {
    const { wrapper, auth } = await render()
    await chooseTrip(wrapper)
    await wrapper.get('[data-test=trip-confirm]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-test=trip-result]').exists()).toBe(true)
    vi.mocked(fetchItineraries).mockClear()
    vi.mocked(fetchItinerary).mockClear()
    auth.user = { ...USER, id: '2', username: 'another' }
    auth.token = 'session-b'
    await flushPromises()
    expect(wrapper.find('[data-test=trip-result]').exists()).toBe(false)
    expect(fetchItineraries).toHaveBeenCalledWith({ page: 1, size: 50, status: 'ACTIVE' })
    expect(fetchItinerary).not.toHaveBeenCalled()
  })

  it('服务端 401 保留选择并引导重新登录', async () => {
    vi.mocked(importGuidePlaces).mockRejectedValueOnce(new ApiError('登录状态已失效，请重新登录', { status: 401 }))
    const { wrapper } = await render()
    await chooseTrip(wrapper)
    await wrapper.get('[data-test=trip-confirm]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-test=trip-error]').text()).toContain('登录状态已失效')
    expect(wrapper.get('[data-test=trip-error]').text()).toContain('保留')
    expect(wrapper.get('[data-test=trip-login-link]').attributes('href')).toContain('/login')
    expect((wrapper.get('[data-test=spot-' + SPOT_LINGYIN + ']').element as HTMLInputElement).checked).toBe(true)
    expect(wrapper.find('[data-test=trip-result]').exists()).toBe(false)
  })

  it('同账号令牌更换使旧提交失效并解除等待状态', async () => {
    let finish!: (value: { itineraryId: string; dayId: string; added: string[]; alreadyPresent: string[] }) => void
    vi.mocked(importGuidePlaces).mockReturnValueOnce(new Promise(resolve => { finish = resolve }))
    const { wrapper, auth } = await render(); await chooseTrip(wrapper)
    await wrapper.get('[data-test=trip-confirm]').trigger('click'); await flushPromises()
    auth.token = 'replacement-session'; await flushPromises()
    expect(wrapper.get('[data-test=trip-close]').attributes('disabled')).toBeUndefined()
    finish({ itineraryId: ITIN_ID, dayId: DAY_ID, added: [SPOT_WEST_LAKE], alreadyPresent: [] }); await flushPromises()
    expect(wrapper.find('[data-test=trip-result]').exists()).toBe(false)
  })

  it('快速更换目标时迟到的旧行程不会覆盖新行程日期', async () => {
    vi.mocked(fetchItineraries).mockResolvedValueOnce(pageOf([summary(ITIN_ID, '第一份行程'), summary(OTHER_ITIN_ID, '第二份行程')]))
    let finish!: (value: ItineraryDetail) => void
    vi.mocked(fetchItinerary).mockReturnValueOnce(new Promise(resolve => { finish = resolve })).mockResolvedValueOnce(detail(OTHER_ITIN_ID, [day(DAY2_ID, 2, '2026-11-11')]))
    const { wrapper } = await render()
    await wrapper.get('[data-test=trip-itinerary-select]').setValue(ITIN_ID)
    await wrapper.get('[data-test=trip-itinerary-select]').setValue(OTHER_ITIN_ID); await flushPromises()
    finish(detail(ITIN_ID, [day(DAY_ID, 1, '2026-11-10')])); await flushPromises()
    expect((wrapper.get('[data-test=trip-day-select]').element as HTMLSelectElement).value).toBe(DAY2_ID)
  })

  it('交给 AI 只发出已选景点 ID 并请求关闭，不直接写接口', async () => {
    const { wrapper } = await render({ props: { initialSelectedIds: [SPOT_LINGYIN] } })
    expect((wrapper.get('[data-test=spot-' + SPOT_WEST_LAKE + ']').element as HTMLInputElement).checked).toBe(false)
    expect((wrapper.get('[data-test=spot-' + SPOT_LINGYIN + ']').element as HTMLInputElement).checked).toBe(true)
    await wrapper.get('[data-test=trip-plan-ai]').trigger('click')
    expect(wrapper.emitted('plan-ai')?.[0]).toEqual([[SPOT_LINGYIN]])
    expect(wrapper.emitted('close')).toHaveLength(1)
    expect(importGuidePlaces).not.toHaveBeenCalled()
  })
})
