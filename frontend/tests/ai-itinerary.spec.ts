import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import AiItineraryView from '@/views/AiItineraryView.vue'
import { fetchAiCapabilities, previewAiDay, previewAiItinerary } from '@/api/ai'
import { fetchDestinations } from '@/api/destinations'
import { importItinerary } from '@/api/itineraries'
import { ApiError } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import type { AiCandidate, AiCapabilities, AiDay, AiItem, AiPreview } from '@/types/ai'
import type { Destination, DestinationQuery } from '@/types/destination'
import type { ItineraryDetail } from '@/types/itinerary'

vi.mock('@/api/ai', () => ({
  fetchAiCapabilities: vi.fn(),
  previewAiItinerary: vi.fn(),
  previewAiDay: vi.fn(),
}))
vi.mock('@/api/destinations', () => ({ fetchDestinations: vi.fn() }))
vi.mock('@/api/itineraries', () => ({ importItinerary: vi.fn() }))

const CITY_ID = '9007199254740993'
const CITY2_ID = '9007199254740994'
const SPOT_XIHU = '9007199254741001'
const SPOT_LINGYIN = '9007199254741002'
const SPOT_HEFANG = '9007199254741003'
const D1 = '2026-11-10'
const D2 = '2026-11-11'
const D3 = '2026-11-12'
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

const availableCaps: AiCapabilities = {
  enabled: true,
  available: true,
  reason: null,
  maxDays: 14,
  provider: 'deepseek',
  providerLabel: 'DeepSeek',
}

function dest(overrides: Partial<Destination> = {}): Destination {
  return {
    id: CITY_ID,
    name: '杭州',
    destinationType: 'CITY',
    parentId: null,
    country: '中国',
    province: '浙江',
    city: '杭州',
    description: null,
    coverImageUrl: null,
    recommendedSeasons: null,
    status: 'ACTIVE',
    sortOrder: 0,
    address: null,
    longitude: null,
    latitude: null,
    recommendedDurationMinutes: null,
    recommendedDays: null,
    openingHours: null,
    ticketInfo: null,
    officialUrl: null,
    sourceUrl: null,
    verifiedAt: null,
    images: [],
    createdAt: '2026-10-01T00:00:00.000+08:00',
    updatedAt: '2026-10-01T00:00:00.000+08:00',
    ...overrides,
  }
}
const cityOne = () => dest()
const cityTwo = () => dest({ id: CITY2_ID, name: '苏州' })
const spotDest = (id: string, name: string, address: string | null = `杭州市${name}路`) =>
  dest({ id, name, destinationType: 'SCENIC_SPOT', parentId: CITY_ID, address })
const pageOf = (records: Destination[], current = 1, pages = 1) => ({
  records,
  total: pages * 20,
  current,
  size: 20,
  pages,
})

function mockCandidates(pages: Record<number, Destination[]>): void {
  const pagesCount = Math.max(...Object.keys(pages).map(Number), 1)
  const total = Object.values(pages).reduce((sum, items) => sum + items.length, 0)
  vi.mocked(fetchDestinations).mockImplementation(async (query: DestinationQuery = {}) => {
    if (query.destinationType === 'CITY') return pageOf([cityOne(), cityTwo()], 1, 1)
    const current = query.page ?? 1
    return { records: pages[current] ?? [], total, current, size: 20, pages: pagesCount }
  })
}

const candidate = (
  id: string,
  name: string,
  address: string | null = `候选地址 ${name}`,
  extra: Partial<AiCandidate> = {},
): AiCandidate => ({
  id,
  name,
  address,
  coverImageUrl: null,
  recommendedDurationMinutes: 120,
  openingHours: '08:00-17:30',
  ticketInfo: '免费开放',
  sourceUrl: 'https://example.com/source',
  verifiedAt: '2026-10-30T10:00:00.000+08:00',
  ...extra,
})
const aiItem = (overrides: Partial<AiItem> = {}): AiItem => ({
  kind: 'SCENIC_SPOT',
  destinationId: SPOT_XIHU,
  placeName: '西湖风景区',
  addressText: 'AI 编造的地址',
  startTime: '09:00',
  endTime: '11:00',
  notes: '沿湖慢走',
  estimatedCost: '0',
  ...overrides,
})
const aiDay = (dayNo: number, travelDate: string, title: string, items: AiItem[]): AiDay => ({
  dayNo,
  travelDate,
  title,
  items,
})
const aiPreview = (overrides: Partial<AiPreview> = {}): AiPreview => ({
  title: '杭州三日慢行',
  summary: '沿湖慢走，留白看云',
  city: { id: CITY_ID, name: '杭州', status: 'ACTIVE' },
  startDate: D1,
  endDate: D3,
  travelers: 2,
  budgetAmount: '3000',
  budgetBasis: 'PER_PERSON',
  currency: 'CNY',
  days: [
    aiDay(1, D1, '湖边', [aiItem()]),
    aiDay(2, D2, '山间', [
      aiItem({
        destinationId: SPOT_LINGYIN,
        placeName: '灵隐寺',
        addressText: 'AI 编造的地址',
        estimatedCost: '75',
      }),
    ]),
    aiDay(3, D3, '街巷', [
      aiItem({
        kind: 'FREE_TIME',
        destinationId: null,
        placeName: '自由活动',
        addressText: null,
        estimatedCost: '0',
      }),
    ]),
  ],
  budgetEstimate: { currency: 'CNY', perPerson: '1500', total: '3000', notes: '按公开票价估算' },
  warnings: ['想去的河坊街没有排进行程'],
  candidates: [
    candidate(SPOT_XIHU, '西湖', '杭州市西湖区龙井路 1 号'),
    candidate(SPOT_LINGYIN, '灵隐寺', '杭州市西湖区灵隐路 1 号'),
    candidate(SPOT_HEFANG, '河坊街', '杭州市上城区河坊街'),
  ],
  sourceGuides: [],
  ...overrides,
})
const detailOf = (id = '7101'): ItineraryDetail => ({
  id,
  title: '杭州三日慢行',
  destination: { id: CITY_ID, name: '杭州', status: 'ACTIVE' },
  startDate: D1,
  endDate: D3,
  status: 'ACTIVE',
  dayCount: 3,
  itemCount: 3,
  notes: null,
  days: [],
  createdAt: '2026-11-01T00:00:00.000+08:00',
  updatedAt: '2026-11-01T00:00:00.000+08:00',
})

async function render(options: { caps?: AiCapabilities | null } = {}) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAuthStore()
  auth.token = 'token-luna'
  auth.user = {
    id: '1',
    username: 'luna',
    nickname: '山间旅人',
    avatarUrl: null,
    bio: null,
    role: 'USER',
    status: 'ACTIVE',
    createdAt: '2026-10-01T00:00:00.000+08:00',
  }
  const stub = { template: '<div />' }
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/itineraries/ai', name: 'ai-itinerary', component: AiItineraryView },
      { path: '/my/itineraries', name: 'my-itineraries', component: stub },
      { path: '/itineraries/:id', name: 'itinerary-detail', component: stub },
      { path: '/login', name: 'login', component: stub },
      { path: '/', name: 'home', component: stub },
    ],
  })
  await router.push('/itineraries/ai')
  await router.isReady()
  if (options.caps !== null)
    vi.mocked(fetchAiCapabilities).mockResolvedValue(options.caps ?? availableCaps)
  const wrapper = mount(AiItineraryView, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return { wrapper, router, auth }
}
type Wrapper = Awaited<ReturnType<typeof render>>['wrapper']
const field = (wrapper: Wrapper, name: string) => wrapper.get(`[data-test="${name}"]`)

async function pickCity(wrapper: Wrapper, id = CITY_ID): Promise<void> {
  await field(wrapper, 'ai-city').setValue(id)
  await flushPromises()
}
async function fillForm(
  wrapper: Wrapper,
  options: { travelers?: string; start?: string; end?: string; budget?: string } = {},
): Promise<void> {
  await pickCity(wrapper)
  await field(wrapper, 'ai-start-date').setValue(options.start ?? D1)
  await field(wrapper, 'ai-end-date').setValue(options.end ?? D3)
  await field(wrapper, 'ai-travelers').setValue(options.travelers ?? '2')
  await field(wrapper, 'ai-budget-amount').setValue(options.budget ?? '3000')
}
async function generate(wrapper: Wrapper): Promise<void> {
  await field(wrapper, 'ai-submit').trigger('submit')
  await flushPromises()
}
async function openSave(wrapper: Wrapper): Promise<void> {
  await field(wrapper, 'ai-save').trigger('click')
  await flushPromises()
}
async function confirmSave(wrapper: Wrapper): Promise<void> {
  await field(wrapper, 'ai-confirm-save').trigger('click')
  await flushPromises()
}
const text = (wrapper: Wrapper, name: string) => field(wrapper, name).text()
const inputValue = (wrapper: Wrapper, name: string) =>
  (field(wrapper, name).element as HTMLInputElement).value

describe('主代理集成边界', () => {
  it('其他日期在单日请求过程中被编辑时丢弃迟到结果', async () => {
    let resolve!: (value: { day: AiDay; warnings: string[] }) => void
    vi.mocked(previewAiDay).mockReturnValue(
      new Promise((done) => {
        resolve = done
      }),
    )
    const { wrapper } = await render()
    await fillForm(wrapper)
    await generate(wrapper)
    await field(wrapper, `ai-day-${D2}-regen`).trigger('click')
    await field(wrapper, `ai-day-${D1}-title`).setValue('修改了另一日')
    resolve({
      day: aiDay(2, D2, '过期结果', [aiItem({ destinationId: SPOT_HEFANG })]),
      warnings: [],
    })
    await flushPromises()
    expect(inputValue(wrapper, `ai-day-${D2}-title`)).toBe('山间')
    wrapper.unmount()
  })
  it('预算需求变化会使在途单日响应失效', async () => {
    let resolve!: (value: { day: AiDay; warnings: string[] }) => void
    vi.mocked(previewAiDay).mockReturnValue(
      new Promise((done) => {
        resolve = done
      }),
    )
    const { wrapper } = await render()
    await fillForm(wrapper)
    await generate(wrapper)
    await field(wrapper, `ai-day-${D2}-regen`).trigger('click')
    await field(wrapper, 'ai-budget-amount').setValue('100')
    resolve({
      day: aiDay(2, D2, '旧预算结果', [aiItem({ destinationId: SPOT_HEFANG })]),
      warnings: [],
    })
    await flushPromises()
    expect(inputValue(wrapper, `ai-day-${D2}-title`)).toBe('山间')
    wrapper.unmount()
  })
  it('切换账号后迟到的保存结果不导航到前一账号的行程', async () => {
    let resolve!: (value: ItineraryDetail) => void
    vi.mocked(importItinerary).mockReturnValue(
      new Promise((done) => {
        resolve = done
      }),
    )
    const { wrapper, router, auth } = await render()
    await fillForm(wrapper)
    await generate(wrapper)
    await openSave(wrapper)
    await field(wrapper, 'ai-confirm-save').trigger('click')
    auth.token = 'new-account'
    auth.user = { ...auth.user!, id: '2' }
    await flushPromises()
    resolve(detailOf('old-account-root'))
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/itineraries/ai')
    wrapper.unmount()
  })
  it('保存网络失败后编辑确认面板中的版本时刷新幂等键', async () => {
    vi.mocked(importItinerary)
      .mockRejectedValueOnce(new ApiError('网络状态不确定', { isNetworkError: true }))
      .mockResolvedValue(detailOf())
    const { wrapper } = await render()
    await fillForm(wrapper)
    await generate(wrapper)
    await openSave(wrapper)
    await confirmSave(wrapper)
    const firstKey = vi.mocked(importItinerary).mock.calls[0]![0].clientRequestId
    await field(wrapper, 'ai-root-title').setValue('新的保存版本')
    await confirmSave(wrapper)
    expect(vi.mocked(importItinerary).mock.calls[1]![0].clientRequestId).not.toBe(firstKey)
    wrapper.unmount()
  })
  it('打开确认面板后输入超长备注仍会在请求前阻止保存', async () => {
    const { wrapper } = await render()
    await fillForm(wrapper)
    await generate(wrapper)
    await openSave(wrapper)
    await field(wrapper, 'ai-root-notes').setValue('长'.repeat(2001))
    await confirmSave(wrapper)
    expect(importItinerary).not.toHaveBeenCalled()
    expect(text(wrapper, 'ai-save-error')).toContain('2000')
    wrapper.unmount()
  })
  it('最终正文保留用户总述、预算金额与自由活动的费用估算', async () => {
    const { wrapper } = await render()
    await fillForm(wrapper)
    await generate(wrapper)
    await field(wrapper, 'ai-root-summary').setValue('用户编辑的总述')
    await field(wrapper, `ai-day-${D3}-item-0-cost`).setValue('55')
    await openSave(wrapper)
    await confirmSave(wrapper)
    const body = vi.mocked(importItinerary).mock.calls[0]![0]
    expect(body.notes).toContain('用户编辑的总述')
    expect(body.notes).toContain('3000')
    expect(body.notes).toContain('1500')
    expect(body.days[2]!.items[0]!.notes).toContain('55')
    wrapper.unmount()
  })
  it('编辑添加项目时保持每天八项的限制', async () => {
    const { wrapper } = await render()
    await fillForm(wrapper)
    await generate(wrapper)
    for (let n = 0; n < 8; n++) await field(wrapper, `ai-day-${D1}-add-free`).trigger('click')
    expect(
      wrapper
        .findAll(`[data-test^="ai-day-${D1}-item-"]`)
        .filter((item) => /^ai-day-.*-item-\d+$/.test(item.attributes('data-test') ?? '')),
    ).toHaveLength(8)
    wrapper.unmount()
  })
  it('移除必须去的景点后需补回项目或调整需求才能保存', async () => {
    const { wrapper } = await render()
    await fillForm(wrapper)
    await field(wrapper, `ai-must-${SPOT_LINGYIN}`).setValue(true)
    await generate(wrapper)
    await field(wrapper, `ai-day-${D2}-item-0-remove`).trigger('click')
    await field(wrapper, `ai-day-${D2}-add-free`).trigger('click')
    await openSave(wrapper)
    expect(importItinerary).not.toHaveBeenCalled()
    expect(text(wrapper, 'ai-save-error')).toContain('必须去')
    wrapper.unmount()
  })
})

beforeEach(() => {
  vi.resetAllMocks()
  mockCandidates({
    1: [spotDest(SPOT_XIHU, '西湖'), spotDest(SPOT_LINGYIN, '灵隐寺')],
    2: [spotDest(SPOT_HEFANG, '河坊街')],
  })
  vi.mocked(previewAiItinerary).mockResolvedValue(aiPreview())
  vi.mocked(previewAiDay).mockResolvedValue({
    day: aiDay(2, D2, '山间新安排', [aiItem({ destinationId: SPOT_HEFANG, placeName: '河坊街' })]),
    warnings: [],
  })
  vi.mocked(importItinerary).mockResolvedValue(detailOf())
})

describe('AI 行程页：能力与候选', () => {
  it('AI 关闭时显示原因和手动入口，不渲染需求表单', async () => {
    const { wrapper } = await render({
      caps: {
        ...availableCaps,
        enabled: false,
        available: false,
        reason: '管理员尚未配置 AI 服务',
      },
    })
    expect(text(wrapper, 'ai-unavailable')).toContain('管理员尚未配置 AI 服务')
    expect(field(wrapper, 'ai-manual-link').attributes('href')).toBe('/my/itineraries')
    expect(wrapper.find('[data-test="ai-form"]').exists()).toBe(false)
    expect(previewAiItinerary).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('能力加载失败时给出重试而不是判定 AI 关闭', async () => {
    vi.mocked(fetchAiCapabilities)
      .mockRejectedValueOnce(new ApiError('AI 配置读取失败', { status: 503, code: 50310 }))
      .mockResolvedValue(availableCaps)
    const { wrapper } = await render()
    expect(field(wrapper, 'ai-caps-error').text()).toContain('AI 配置读取失败')
    expect(wrapper.find('[data-test="ai-unavailable"]').exists()).toBe(false)
    await field(wrapper, 'ai-caps-retry').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-test="ai-form"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('可用时展示当前 providerLabel 且页面不出现密钥或 URL', async () => {
    const { wrapper } = await render()
    expect(text(wrapper, 'ai-provider-label')).toContain('DeepSeek')
    expect(wrapper.text()).not.toMatch(/sk-[A-Za-z0-9]|https?:\/\//)
    wrapper.unmount()
  })

  it('候选只来自当前城市的真实景点并支持服务端分页', async () => {
    const { wrapper } = await render()
    await pickCity(wrapper)
    expect(fetchDestinations).toHaveBeenCalledWith(
      expect.objectContaining({ destinationType: 'SCENIC_SPOT', parentId: CITY_ID, page: 1 }),
    )
    expect(wrapper.text()).toContain('西湖')
    await field(wrapper, 'ai-candidate-next').trigger('click')
    await flushPromises()
    expect(fetchDestinations).toHaveBeenCalledWith(
      expect.objectContaining({ page: 2, parentId: CITY_ID }),
    )
    expect(wrapper.text()).toContain('河坊街')
    wrapper.unmount()
  })

  it('表单校验拦住非法天数与人数，不请求生成', async () => {
    const { wrapper } = await render()
    await fillForm(wrapper, { travelers: '21' })
    await generate(wrapper)
    expect(wrapper.get('[data-test="ai-form-error"]').text()).toContain('1–20')
    await field(wrapper, 'ai-travelers').setValue('2')
    await field(wrapper, 'ai-end-date').setValue('2026-11-24')
    await generate(wrapper)
    expect(text(wrapper, 'ai-form-error')).toContain('14')
    expect(previewAiItinerary).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('想去/必须去/避免三类偏好按需求提交，避免与想去冲突时拒绝', async () => {
    const { wrapper } = await render()
    await fillForm(wrapper)
    await field(wrapper, 'ai-interest-FOOD').setValue(true)
    await field(wrapper, 'ai-interest-NATURE').setValue(true)
    await field(wrapper, 'ai-budget-basis').setValue('TOTAL')
    await field(wrapper, 'ai-pace').setValue('RELAXED')
    await field(wrapper, 'ai-transport').setValue('WALK')
    await field(wrapper, `ai-pref-${SPOT_XIHU}`).setValue(true)
    await field(wrapper, `ai-must-${SPOT_LINGYIN}`).setValue(true)
    await field(wrapper, `ai-avoid-${SPOT_XIHU}`).setValue(true)
    await generate(wrapper)
    expect(text(wrapper, 'ai-form-error')).toContain('避免')
    expect(previewAiItinerary).not.toHaveBeenCalled()
    await field(wrapper, `ai-avoid-${SPOT_XIHU}`).setValue(false)
    await generate(wrapper)
    expect(previewAiItinerary).toHaveBeenCalledTimes(1)
    expect(previewAiItinerary).toHaveBeenCalledWith(
      expect.objectContaining({
        cityId: CITY_ID,
        startDate: D1,
        endDate: D3,
        travelers: 2,
        budgetAmount: '3000',
        budgetBasis: 'TOTAL',
        currency: 'CNY',
        interests: ['FOOD', 'NATURE'],
        pace: 'RELAXED',
        transport: 'WALK',
        preferredIds: [SPOT_XIHU],
        mustVisitIds: [SPOT_LINGYIN],
        avoidIds: [],
      }),
    )
    wrapper.unmount()
  })

  it('每类偏好最多 20 个，超出后不能再勾选', async () => {
    const many = Array.from({ length: 21 }, (_, index) =>
      spotDest(String(9100 + index), `景点${index + 1}`),
    )
    mockCandidates({ 1: many })
    const { wrapper } = await render()
    await fillForm(wrapper)
    for (let index = 0; index < 20; index += 1)
      await field(wrapper, `ai-pref-${many[index]!.id}`).setValue(true)
    expect(field(wrapper, `ai-pref-${many[20]!.id}`).attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('20 / 20')
    wrapper.unmount()
  })
})

describe('AI 行程页：预览与编辑', () => {
  it('生成期间显示加载状态并禁用保存入口', async () => {
    let resolve!: (value: AiPreview) => void
    vi.mocked(previewAiItinerary).mockReturnValue(
      new Promise<AiPreview>((done) => {
        resolve = done
      }) as ReturnType<typeof previewAiItinerary>,
    )
    const { wrapper } = await render()
    await fillForm(wrapper)
    await field(wrapper, 'ai-submit').trigger('submit')
    expect(wrapper.text()).toContain('正在生成结构化预览')
    expect(wrapper.find('[data-test="ai-preview"]').exists()).toBe(false)
    expect(field(wrapper, 'ai-submit').text()).toContain('正在生成')
    resolve(aiPreview())
    await flushPromises()
    expect(wrapper.find('[data-test="ai-preview"]').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('正在生成结构化预览')
    wrapper.unmount()
  })

  it('单日重生成会过滤掉其他天已用过的景点，避免整程重复', async () => {
    const { wrapper } = await render()
    await fillForm(wrapper)
    await generate(wrapper)
    vi.mocked(previewAiDay).mockResolvedValue({
      day: aiDay(2, D2, '第二天', [
        aiItem({ destinationId: SPOT_XIHU, placeName: '西湖', estimatedCost: '0' }),
        aiItem({ destinationId: SPOT_HEFANG, placeName: '河坊街', estimatedCost: '0' }),
      ]),
      warnings: [],
    })
    await field(wrapper, `ai-day-${D2}-regen`).trigger('click')
    await flushPromises()
    expect(text(wrapper, `ai-day-${D2}-item-0-name`)).toBe('河坊街')
    expect(wrapper.find(`[data-test="ai-day-${D2}-item-1"]`).exists()).toBe(false)
    expect(text(wrapper, `ai-day-${D1}-item-0-name`)).toBe('西湖')
    wrapper.unmount()
  })

  it('生成后只展示预览，不自动保存也不渲染模型 HTML', async () => {
    const { wrapper } = await render()
    await fillForm(wrapper)
    await generate(wrapper)
    expect(wrapper.find('[data-test="ai-preview"]').exists()).toBe(true)
    expect(importItinerary).not.toHaveBeenCalled()
    expect(importItinerary).not.toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('西湖')
    expect(wrapper.text()).toContain('灵隐寺')
    expect(wrapper.text()).toContain('估算')
    expect(text(wrapper, 'ai-budget-estimate')).toContain('1500')
    expect(text(wrapper, 'ai-warnings')).toContain('想去的河坊街没有排进行程')
    wrapper.unmount()
  })

  it('模型文本按纯文本渲染，不生成 HTML 节点', async () => {
    vi.mocked(previewAiItinerary).mockResolvedValue(
      aiPreview({
        title: '<img src=x onerror="alert(1)">',
        summary: '<script>alert(2)</script> 慢行',
      }),
    )
    const { wrapper } = await render()
    await fillForm(wrapper)
    await generate(wrapper)
    expect(wrapper.find('[data-test="ai-preview"] img').exists()).toBe(false)
    expect(wrapper.find('[data-test="ai-preview"] script').exists()).toBe(false)
    expect(inputValue(wrapper, 'ai-root-title')).toContain('<img src=x')
    wrapper.unmount()
  })

  it('预览中的景点名称与地址一律取真实候选，并去掉整程重复景点', async () => {
    vi.mocked(previewAiItinerary).mockResolvedValue(
      aiPreview({
        days: [
          aiDay(1, D1, '湖边', [
            aiItem({
              destinationId: SPOT_XIHU,
              placeName: '西湖风景区',
              addressText: 'AI 编造的地址',
            }),
            aiItem({ destinationId: SPOT_XIHU, placeName: '西湖（重复）', addressText: null }),
          ]),
          aiDay(2, D2, '山间', [
            aiItem({
              destinationId: SPOT_LINGYIN,
              placeName: '灵隐寺',
              addressText: 'AI 编造的地址',
            }),
          ]),
        ],
      }),
    )
    const { wrapper } = await render()
    await fillForm(wrapper, { end: D2 })
    await generate(wrapper)
    expect(text(wrapper, `ai-day-${D1}-item-0-name`)).toBe('西湖')
    expect(text(wrapper, `ai-day-${D1}-item-0-address`)).toBe('杭州市西湖区龙井路 1 号')
    expect(wrapper.find(`[data-test="ai-day-${D1}-item-1"]`).exists()).toBe(false)
    expect(text(wrapper, `ai-day-${D2}-item-0-name`)).toBe('灵隐寺')
    expect(text(wrapper, `ai-day-${D2}-item-0-address`)).toBe('杭州市西湖区灵隐路 1 号')
    wrapper.unmount()
  })

  it('预览没有可用逐日安排时给出空态而不是空白页', async () => {
    vi.mocked(previewAiItinerary).mockResolvedValue(aiPreview({ days: [] }))
    const { wrapper } = await render()
    await fillForm(wrapper)
    await generate(wrapper)
    expect(wrapper.find('[data-test="ai-preview-empty"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="ai-preview-empty"]').text()).toContain('没有具体安排')
    wrapper.unmount()
  })

  it('换城市或换日期后旧预览明确作废', async () => {
    const { wrapper } = await render()
    await fillForm(wrapper)
    await generate(wrapper)
    expect(wrapper.find('[data-test="ai-preview"]').exists()).toBe(true)
    await pickCity(wrapper, CITY2_ID)
    expect(wrapper.find('[data-test="ai-preview"]').exists()).toBe(false)
    expect(text(wrapper, 'ai-stale')).toContain('作废')
    await generate(wrapper)
    expect(wrapper.find('[data-test="ai-preview"]').exists()).toBe(true)
    await field(wrapper, 'ai-end-date').setValue('2026-11-13')
    expect(wrapper.find('[data-test="ai-preview"]').exists()).toBe(false)
    expect(text(wrapper, 'ai-stale')).toContain('作废')
    wrapper.unmount()
  })

  it('添加候选与自由活动、上下移动并移除，名称地址取自候选', async () => {
    const { wrapper } = await render()
    await fillForm(wrapper)
    await generate(wrapper)
    expect(wrapper.find(`[data-test="ai-day-${D1}-add-${SPOT_HEFANG}"]`).exists()).toBe(true)
    await field(wrapper, `ai-day-${D1}-add-${SPOT_HEFANG}`).trigger('click')
    await field(wrapper, `ai-day-${D1}-add-free`).trigger('click')
    expect(text(wrapper, `ai-day-${D1}-item-1-name`)).toBe('河坊街')
    expect(text(wrapper, `ai-day-${D1}-item-2-name`)).toBe('自由活动')
    expect(field(wrapper, `ai-day-${D1}-item-1-address`).text()).toBe('杭州市上城区河坊街')
    await field(wrapper, `ai-day-${D1}-item-1-up`).trigger('click')
    expect(text(wrapper, `ai-day-${D1}-item-0-name`)).toBe('河坊街')
    expect(wrapper.find(`[data-test="ai-day-${D1}-add-${SPOT_HEFANG}"]`).exists()).toBe(false)
    await field(wrapper, `ai-day-${D1}-item-2-remove`).trigger('click')
    expect(wrapper.find(`[data-test="ai-day-${D1}-item-2"]`).exists()).toBe(false)
    wrapper.unmount()
  })

  it('编辑后点保存先显示最终备注与估算摘要，确认后才创建整程', async () => {
    const { wrapper, router } = await render()
    await fillForm(wrapper)
    await generate(wrapper)
    await field(wrapper, 'ai-root-title').setValue('杭州慢行（我改的）')
    await field(wrapper, `ai-day-${D1}-title`).setValue('湖边清晨')
    await field(wrapper, `ai-day-${D2}-item-0-start`).setValue('08:30')
    await field(wrapper, `ai-day-${D2}-item-0-cost`).setValue('99')
    await field(wrapper, `ai-day-${D2}-item-0-notes`).setValue('排队早一点')
    await field(wrapper, 'ai-root-notes').setValue('带父母，节奏慢一些')
    expect(importItinerary).not.toHaveBeenCalled()
    await openSave(wrapper)
    expect(wrapper.find('[data-test="ai-save-panel"]').exists()).toBe(true)
    const summary = text(wrapper, 'ai-save-panel')
    expect(summary).toContain('CNY')
    expect(summary).toContain('2 人')
    expect(summary).toContain('每人预算')
    expect(summary).toContain('估算')
    expect(summary).toContain('不确定性')
    expect(text(wrapper, 'ai-save-notes')).toContain('带父母，节奏慢一些')
    expect(summary).toContain('排队早一点')
    expect(importItinerary).not.toHaveBeenCalled()
    await confirmSave(wrapper)
    expect(importItinerary).toHaveBeenCalledTimes(1)
    const body = vi.mocked(importItinerary).mock.calls[0]![0]
    expect(body.clientRequestId).toMatch(UUID)
    expect(body.title).toBe('杭州慢行（我改的）')
    expect(body.destinationId).toBe(CITY_ID)
    expect(body.startDate).toBe(D1)
    expect(body.endDate).toBe(D3)
    expect(body.notes).toContain('带父母，节奏慢一些')
    expect(body.notes).toContain('CNY')
    expect(body.notes).toContain('2 人')
    expect(body.notes).toContain('每人预算')
    expect(body.notes).toContain('估算')
    expect(body.notes).toContain('不确定性')
    expect(body.days[0]!.title).toBe('湖边清晨')
    const first = body.days[0]!.items[0]!
    expect(first.kind).toBe('SCENIC_SPOT')
    expect(first.destinationId).toBe(SPOT_XIHU)
    expect(first.placeName).toBe('西湖')
    expect(first.addressText).toBe('杭州市西湖区龙井路 1 号')
    const edited = body.days[1]!.items[0]!
    expect(edited.startTime).toBe('08:30')
    expect(edited.notes).toContain('排队早一点')
    expect(edited.notes).toContain('99')
    expect(edited.notes).toContain('估算')
    const free = body.days[2]!.items[0]!
    expect(free.kind).toBe('FREE_TIME')
    expect(free.destinationId).toBeNull()
    expect(free.placeName).toBe('自由活动')
    expect(router.currentRoute.value.path).toBe('/itineraries/7101')
    wrapper.unmount()
  })

  it('单项备注加上估算说明超过 2000 码点时也拒绝保存', async () => {
    const { wrapper } = await render()
    await fillForm(wrapper)
    await generate(wrapper)
    await field(wrapper, `ai-day-${D2}-item-0-notes`).setValue('细'.repeat(1990))
    await openSave(wrapper)
    expect(wrapper.get('[data-test="ai-save-error"]').text()).toContain('2000')
    expect(wrapper.find('[data-test="ai-save-panel"]').exists()).toBe(false)
    expect(importItinerary).not.toHaveBeenCalled()
    expect(inputValue(wrapper, 'ai-root-notes')).toBe('')
    wrapper.unmount()
  })

  it('备注超过 2000 码点时不截断、不保存，要求先编辑', async () => {
    const { wrapper } = await render()
    await fillForm(wrapper)
    await generate(wrapper)
    const longNotes = '好'.repeat(2001)
    await field(wrapper, 'ai-root-notes').setValue(longNotes)
    await openSave(wrapper)
    expect(field(wrapper, 'ai-save-error').text()).toContain('2000')
    expect(wrapper.find('[data-test="ai-save-panel"]').exists()).toBe(false)
    expect(importItinerary).not.toHaveBeenCalled()
    expect(inputValue(wrapper, 'ai-root-notes').length).toBe(2001)
    await field(wrapper, 'ai-root-notes').setValue('')
    await openSave(wrapper)
    expect(wrapper.find('[data-test="ai-save-panel"]').exists()).toBe(true)
    expect(text(wrapper, 'ai-save-notes').length).toBeLessThanOrEqual(2000)
    wrapper.unmount()
  })
})

describe('AI 行程页：单日重生成与迟到响应', () => {
  const deferred = <T>() => {
    let resolve!: (value: T) => void
    let reject!: (reason: unknown) => void
    const promise = new Promise<T>((done, fail) => {
      resolve = done
      reject = fail
    })
    return { promise, resolve, reject }
  }
  const openPreview = async (wrapper: Wrapper): Promise<void> => {
    await fillForm(wrapper)
    await generate(wrapper)
  }
  const regen = async (wrapper: Wrapper, date: string): Promise<void> => {
    await field(wrapper, `ai-day-${date}-regen`).trigger('click')
    await flushPromises()
  }

  it('单日重生成只替换目标日，其他天用户编辑保持不动', async () => {
    const { wrapper } = await render()
    await openPreview(wrapper)
    await field(wrapper, `ai-day-${D1}-title`).setValue('我改的第一天')
    await field(wrapper, `ai-day-${D3}-title`).setValue('我改的第三天')
    vi.mocked(previewAiDay).mockResolvedValue({
      day: aiDay(2, D2, '只有第二天变了', [
        aiItem({
          destinationId: SPOT_HEFANG,
          placeName: '河坊街',
          startTime: '14:00',
          endTime: '16:00',
        }),
        aiItem({
          destinationId: null,
          kind: 'FREE_TIME',
          placeName: '自由活动',
          addressText: null,
        }),
      ]),
      warnings: ['第二天安排已更新'],
    })
    await regen(wrapper, D2)
    expect(previewAiDay).toHaveBeenCalledTimes(1)
    const body = vi.mocked(previewAiDay).mock.calls[0]![0]
    expect(body.targetDate).toBe(D2)
    expect(body.usedDestinationIds).toEqual([SPOT_XIHU])
    expect(body.requirements.cityId).toBe(CITY_ID)
    expect(inputValue(wrapper, `ai-day-${D1}-title`)).toBe('我改的第一天')
    expect(inputValue(wrapper, `ai-day-${D3}-title`)).toBe('我改的第三天')
    expect(inputValue(wrapper, `ai-day-${D2}-title`)).toBe('只有第二天变了')
    expect(text(wrapper, `ai-day-${D2}-item-0-name`)).toBe('河坊街')
    expect(text(wrapper, 'ai-warnings')).toContain('第二天安排已更新')
    wrapper.unmount()
  })

  it('需求版本变化后的迟到预览被拒绝，不覆盖新预览', async () => {
    const { wrapper } = await render()
    const slow = deferred<AiPreview>()
    vi.mocked(previewAiItinerary).mockReturnValueOnce(slow.promise)
    await openPreview(wrapper)
    await field(wrapper, 'ai-end-date').setValue('2026-11-14')
    expect(text(wrapper, 'ai-stale')).toContain('作废')
    vi.mocked(previewAiItinerary).mockResolvedValueOnce(
      aiPreview({ title: '新的预览', summary: '新需求生成' }),
    )
    await generate(wrapper)
    expect(inputValue(wrapper, 'ai-root-title')).toBe('新的预览')
    slow.resolve(aiPreview({ title: '迟到的旧预览', summary: '旧需求生成' }))
    await flushPromises()
    expect(inputValue(wrapper, 'ai-root-title')).toBe('新的预览')
    wrapper.unmount()
  })

  it('单日重生成期间继续编辑该日，迟到的结果被丢弃且保留编辑', async () => {
    const { wrapper } = await render()
    await openPreview(wrapper)
    const slow = deferred<{ day: AiDay; warnings: string[] }>()
    vi.mocked(previewAiDay).mockReturnValueOnce(slow.promise)
    await regen(wrapper, D2)
    await field(wrapper, `ai-day-${D2}-title`).setValue('我在等待时改的')
    slow.resolve({
      day: aiDay(2, D2, '迟到的自动安排', [
        aiItem({ destinationId: SPOT_HEFANG, placeName: '河坊街' }),
      ]),
      warnings: [],
    })
    await flushPromises()
    expect(inputValue(wrapper, `ai-day-${D2}-title`)).toBe('我在等待时改的')
    expect(text(wrapper, `ai-day-${D2}-item-0-name`)).toBe('灵隐寺')
    wrapper.unmount()
  })

  it('单日重生成失败时保留全部已有编辑并给出错误', async () => {
    const { wrapper } = await render()
    await openPreview(wrapper)
    await field(wrapper, `ai-day-${D1}-title`).setValue('保留的第一天')
    await field(wrapper, 'ai-root-summary').setValue('保留的总述')
    vi.mocked(previewAiDay).mockRejectedValue(
      new ApiError('上游模型超时', { status: 504, code: 50410 }),
    )
    await regen(wrapper, D1)
    expect(text(wrapper, 'ai-day-error')).toContain('上游模型超时')
    expect(inputValue(wrapper, `ai-day-${D1}-title`)).toBe('保留的第一天')
    expect(inputValue(wrapper, 'ai-root-summary')).toBe('保留的总述')
    expect(wrapper.find('[data-test="ai-preview"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('整程重新生成失败时保留已有预览与全部用户编辑', async () => {
    const { wrapper } = await render()
    await fillForm(wrapper)
    await generate(wrapper)
    await field(wrapper, 'ai-root-title').setValue('不能丢的标题')
    await field(wrapper, `ai-day-${D1}-title`).setValue('不能丢的第一天')
    await field(wrapper, `ai-day-${D2}-item-0-cost`).setValue('88')
    vi.mocked(previewAiItinerary).mockRejectedValue(
      new ApiError('上游模型返回失败', { status: 502, code: 50210 }),
    )
    await generate(wrapper)
    expect(wrapper.get('[data-test="ai-preview-error"]').text()).toContain('上游模型返回失败')
    expect(wrapper.find('[data-test="ai-preview"]').exists()).toBe(true)
    expect(inputValue(wrapper, 'ai-root-title')).toBe('不能丢的标题')
    expect(inputValue(wrapper, `ai-day-${D1}-title`)).toBe('不能丢的第一天')
    expect(inputValue(wrapper, `ai-day-${D2}-item-0-cost`)).toBe('88')
    wrapper.unmount()
  })

  it('整程生成失败时保留表单输入并给出错误', async () => {
    const { wrapper } = await render()
    await fillForm(wrapper)
    vi.mocked(previewAiItinerary).mockRejectedValue(
      new ApiError('AI 服务暂时不可用', { status: 503 }),
    )
    await generate(wrapper)
    expect(text(wrapper, 'ai-preview-error')).toContain('AI 服务暂时不可用')
    expect(inputValue(wrapper, 'ai-start-date')).toBe(D1)
    expect(inputValue(wrapper, 'ai-end-date')).toBe(D3)
    expect(inputValue(wrapper, 'ai-travelers')).toBe('2')
    wrapper.unmount()
  })
})

describe('AI 行程页：保存与账号', () => {
  const prepare = async (wrapper: Wrapper): Promise<void> => {
    await fillForm(wrapper)
    await generate(wrapper)
  }

  it('保存网络不确定时重试沿用同一 clientRequestId', async () => {
    const { wrapper } = await render()
    await prepare(wrapper)
    vi.mocked(importItinerary).mockRejectedValueOnce(
      new ApiError('网络连接失败，请检查网络后重试', { isNetworkError: true }),
    )
    await openSave(wrapper)
    await confirmSave(wrapper)
    expect(text(wrapper, 'ai-save-error')).toContain('网络')
    expect(wrapper.find('[data-test="ai-save-panel"]').exists()).toBe(true)
    const firstKey = vi.mocked(importItinerary).mock.calls[0]![0].clientRequestId
    vi.mocked(importItinerary).mockResolvedValueOnce(detailOf())
    await confirmSave(wrapper)
    expect(importItinerary).toHaveBeenCalledTimes(2)
    expect(vi.mocked(importItinerary).mock.calls[1]![0].clientRequestId).toBe(firstKey)
    expect(wrapper.find('[data-test="ai-preview"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('保存成功后再编辑需要新的 clientRequestId', async () => {
    const { wrapper } = await render()
    await prepare(wrapper)
    await openSave(wrapper)
    await confirmSave(wrapper)
    expect(importItinerary).toHaveBeenCalledTimes(1)
    expect(wrapper.find('[data-test="ai-preview"]').exists()).toBe(true)
    await field(wrapper, `ai-day-${D2}-item-0-notes`).setValue('再改一次')
    await openSave(wrapper)
    await confirmSave(wrapper)
    expect(importItinerary).toHaveBeenCalledTimes(2)
    expect(vi.mocked(importItinerary).mock.calls[1]![0].clientRequestId).not.toBe(
      vi.mocked(importItinerary).mock.calls[0]![0].clientRequestId,
    )
    wrapper.unmount()
  })

  it('保存进行中重复点击只发一次请求', async () => {
    const { wrapper } = await render()
    await prepare(wrapper)
    let resolve!: (value: ItineraryDetail) => void
    vi.mocked(importItinerary).mockReturnValue(
      new Promise<ItineraryDetail>((done) => {
        resolve = done
      }) as ReturnType<typeof importItinerary>,
    )
    await openSave(wrapper)
    await field(wrapper, 'ai-confirm-save').trigger('click')
    expect(field(wrapper, 'ai-confirm-save').attributes('disabled')).toBeDefined()
    await field(wrapper, 'ai-confirm-save').trigger('click')
    expect(importItinerary).toHaveBeenCalledTimes(1)
    resolve(detailOf())
    await flushPromises()
    wrapper.unmount()
  })

  it('保存 401 跳转登录', async () => {
    const { wrapper, router } = await render()
    await prepare(wrapper)
    vi.mocked(importItinerary).mockRejectedValue(
      new ApiError('登录状态已失效，请重新登录', { status: 401 }),
    )
    await openSave(wrapper)
    await confirmSave(wrapper)
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('login')
    wrapper.unmount()
  })

  it('切换账号后清空私人预览与编辑', async () => {
    const { wrapper, auth } = await render()
    await prepare(wrapper)
    await field(wrapper, 'ai-root-title').setValue('我的私人计划')
    expect(inputValue(wrapper, 'ai-root-title')).toBe('我的私人计划')
    auth.token = 'token-other'
    auth.user = { ...auth.user!, id: '2', username: 'other' }
    await flushPromises()
    expect(wrapper.find('[data-test="ai-preview"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('我的私人计划')
    wrapper.unmount()
  })

  it('390px 窄屏布局使用流式宽度与响应式断点，无固定像素宽度', async () => {
    const { wrapper } = await render()
    await prepare(wrapper)
    const page = field(wrapper, 'ai-page').html()
    expect(page).not.toMatch(/w-\[\d{3,}px\]/)
    expect(page).not.toMatch(/min-width:\s*\d{3,}px/)
    expect(wrapper.get('[data-test="ai-page"]').classes().join(' ')).toMatch(/px-4|px-5|px-6/)
    expect(wrapper.html()).toMatch(/sm:/)
    expect(wrapper.html()).toMatch(/break-words/)
    wrapper.unmount()
  })
})
