import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import GuideEditorView from '@/views/GuideEditorView.vue'
import GuideDetailView from '@/views/GuideDetailView.vue'
import ScenicSpotPicker from '@/components/ScenicSpotPicker.vue'
import * as api from '@/api/guides'
import * as interactions from '@/api/interactions'
import { fetchDestination, fetchDestinations } from '@/api/destinations'
import { useAuthStore } from '@/stores/auth'
import type { Destination } from '@/types/destination'
import type { GuideDetail } from '@/types/guide'

vi.mock('@/api/guides', () => ({
  fetchGuides: vi.fn(), fetchGuide: vi.fn(), fetchOwnGuide: vi.fn(), fetchMyGuides: vi.fn(), fetchTags: vi.fn(),
  createGuide: vi.fn(), updateGuide: vi.fn(), deleteGuide: vi.fn(), publishGuide: vi.fn(), unpublishGuide: vi.fn()
}))
vi.mock('@/api/destinations', () => ({ fetchDestinations: vi.fn(), fetchDestination: vi.fn() }))
vi.mock('@/api/interactions', () => ({
  fetchInteraction: vi.fn(), fetchComments: vi.fn(), setGuideLike: vi.fn(), setGuideFavorite: vi.fn(),
  createComment: vi.fn(), deleteComment: vi.fn()
}))

const CITY_ID = '9007199254740993'
const SPOT_A = '9007199254741001'
const SPOT_B = '9007199254741002'
const OTHER_CITY = '9007199254740994'

function spot(id: string, name: string, parentId = CITY_ID): Destination {
  return {
    id, name, destinationType: 'SCENIC_SPOT', parentId, country: '中国', province: '浙江', city: '杭州', description: null as unknown as string,
    coverImageUrl: null, recommendedSeasons: null, status: 'ACTIVE', sortOrder: 0,
    address: null, longitude: null, latitude: null, recommendedDurationMinutes: null, recommendedDays: null,
    openingHours: null, ticketInfo: null, officialUrl: null, sourceUrl: null, verifiedAt: null, images: [],
    createdAt: '2026-11-03T00:00:00.000+08:00', updatedAt: '2026-11-03T00:00:00.000+08:00'
  } as Destination
}
const pageOf = (records: Destination[], current = 1, pages = 1, size = 10) =>
  ({ records, total: records.length, current, size, pages })

function draft(overrides: Partial<GuideDetail> = {}): GuideDetail {
  return {
    id: '7007199254740993', title: '杭州三日', summary: '沿湖慢行', coverImageUrl: null,
    author: { id: '1', nickname: '山间旅人', avatarUrl: null, bio: null },
    destination: { id: CITY_ID, name: '杭州', status: 'ACTIVE' }, travelDays: 3, budgetPerPerson: null, season: '春',
    tags: [], status: 'DRAFT', publishedAt: null, createdAt: '2026-11-01T00:00:00.000+08:00',
    updatedAt: '2026-11-01T00:00:00.000+08:00', likeCount: 0, favoriteCount: 0, commentCount: 0,
    content: '# 杭州', images: [], scenicSpots: [],
    ...overrides
  } as GuideDetail
}

async function renderEditor(path = '/guides/new') {
  const pinia = createPinia(); setActivePinia(pinia)
  const auth = useAuthStore()
  auth.token = 'test-token'
  auth.user = { id: '1', username: 'luna', nickname: '山间旅人', avatarUrl: null, bio: null, role: 'USER', status: 'ACTIVE', createdAt: '2026-11-01T00:00:00.000+08:00' }
  const stub = { template: '<div />' }
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/', name: 'home', component: stub },
    { path: '/guides', name: 'guides', component: stub },
    { path: '/guides/new', name: 'guide-new', component: GuideEditorView },
    { path: '/guides/:id', name: 'guide-detail', component: GuideDetailView },
    { path: '/guides/:id/edit', name: 'guide-edit', component: GuideEditorView },
    { path: '/me/guides', name: 'my-guides', component: stub },
    { path: '/login', name: 'login', component: stub },
    { path: '/destinations/:id', name: 'destination-detail', component: stub }
  ] })
  await router.push(path); await router.isReady()
  const wrapper = mount(RouterView, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return { wrapper, router }
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(api.fetchTags).mockResolvedValue([])
  vi.mocked(api.fetchOwnGuide).mockResolvedValue(draft())
  vi.mocked(api.createGuide).mockResolvedValue(draft())
  vi.mocked(api.updateGuide).mockResolvedValue(draft())
  vi.mocked(api.publishGuide).mockResolvedValue(draft({ status: 'PUBLISHED' }))
  vi.mocked(api.fetchGuide).mockResolvedValue(draft({ scenicSpots: [{ id: SPOT_A, name: '西湖', status: 'ACTIVE' }] }))
  vi.mocked(fetchDestinations).mockResolvedValue(pageOf([spot(SPOT_A, '西湖'), spot(SPOT_B, '灵隐寺')]))
  vi.mocked(fetchDestination).mockRejectedValue(new Error('not found'))
  vi.mocked(interactions.fetchInteraction).mockResolvedValue({ guideId: '7007199254740993', liked: false, favorited: false, likeCount: 0, favoriteCount: 0, commentCount: 0 })
  vi.mocked(interactions.fetchComments).mockResolvedValue({ records: [], total: 0, current: 1, size: 10, pages: 0 })
})

describe('攻略景点多选组件', () => {
  it('只按主城市与服务端分页搜索景点候选', async () => {
    const wrapper = mount(ScenicSpotPicker, { props: { modelValue: [], cityId: CITY_ID } })
    await flushPromises()
    expect(fetchDestinations).toHaveBeenCalledWith(expect.objectContaining({ destinationType: 'SCENIC_SPOT', parentId: CITY_ID, page: 1 }))
    expect(wrapper.findAll('[data-test="scenic-candidate"]')).toHaveLength(2)
    wrapper.unmount()
  })

  it('未选择主城市时提示先选城市且不发起景点查询', async () => {
    const wrapper = mount(ScenicSpotPicker, { props: { modelValue: [], cityId: null } })
    await flushPromises()
    expect(fetchDestinations).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('先选择主城市')
    wrapper.unmount()
  })

  it('勾选与取消勾选都会同步 v-model 选择结果', async () => {
    const wrapper = mount(ScenicSpotPicker, { props: { modelValue: [], cityId: CITY_ID } })
    await flushPromises()
    await wrapper.get(`[data-test="scenic-candidate-${SPOT_A}"]`).setValue(true)
    expect(wrapper.emitted('update:modelValue')?.at(-1)?.[0]).toEqual([SPOT_A])
    await wrapper.setProps({ modelValue: [SPOT_A, SPOT_B] })
    await wrapper.get(`[data-test="scenic-candidate-${SPOT_A}"]`).setValue(false)
    expect(wrapper.emitted('update:modelValue')?.at(-1)?.[0]).toEqual([SPOT_B])
    wrapper.unmount()
  })

  it('达到 20 个上限后其余景点不可再选', async () => {
    const many = Array.from({ length: 25 }, (_, index) => spot(`spot-${index}`, `景点 ${index}`))
    vi.mocked(fetchDestinations).mockResolvedValue(pageOf(many, 1, 1, 30))
    const wrapper = mount(ScenicSpotPicker, { props: { modelValue: many.slice(0, 20).map(item => item.id), cityId: CITY_ID } })
    await flushPromises()
    expect(wrapper.text()).toContain('20 / 20')
    expect(wrapper.get('[data-test="scenic-candidate-spot-20"]').attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })

  it('可移除已选景点，关键词搜索带服务端 keyword', async () => {
    const wrapper = mount(ScenicSpotPicker, { props: { modelValue: [SPOT_A], cityId: CITY_ID } })
    await flushPromises()
    await wrapper.get('[data-test="scenic-keyword"]').setValue('  西湖 ')
    await wrapper.get('[data-test="scenic-search"]').trigger('click')
    await flushPromises()
    expect(fetchDestinations).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: '西湖', destinationType: 'SCENIC_SPOT', parentId: CITY_ID }))
    await wrapper.get(`[data-test="scenic-remove-${SPOT_A}"]`).trigger('click')
    expect(wrapper.emitted('update:modelValue')?.at(-1)?.[0]).toEqual([])
    wrapper.unmount()
  })
})

describe('攻略编辑关联真实景点', () => {
  it('主目的地选择器查询城市，并给多景点选择提供主城市', async () => {
    const {wrapper}=await renderEditor()
    expect(fetchDestinations).toHaveBeenCalledWith(expect.objectContaining({destinationType:'CITY'}))
    wrapper.unmount()
  })
  it('旧草稿回显已选景点并随保存提交 scenicSpotIds', async () => {
    vi.mocked(api.fetchOwnGuide).mockResolvedValue(draft({ scenicSpots: [{ id: SPOT_A, name: '西湖', status: 'ACTIVE' }] }))
    const { wrapper } = await renderEditor('/guides/7007199254740993/edit')
    expect(wrapper.find(`[data-test="scenic-selected-${SPOT_A}"]`).exists()).toBe(true)
    await wrapper.get('[data-test="save-guide"]').trigger('click')
    await flushPromises()
    expect(api.updateGuide).toHaveBeenCalledWith('7007199254740993', expect.objectContaining({ scenicSpotIds: [SPOT_A] }))
    wrapper.unmount()
  })

  it('选择主城市后可按真实数据多选景点并保存', async () => {
    const { wrapper } = await renderEditor('/guides/new')
    wrapper.findComponent({ name: 'DestinationPicker' }).vm.$emit('update:modelValue', CITY_ID)
    await flushPromises()
    await wrapper.get(`[data-test="scenic-candidate-${SPOT_A}"]`).setValue(true)
    await flushPromises()
    await wrapper.get('[data-test="guide-title"]').setValue('杭州三日')
    await wrapper.get('[data-test="save-guide"]').trigger('click')
    await flushPromises()
    expect(api.createGuide).toHaveBeenCalledWith(expect.objectContaining({ destinationId: CITY_ID, scenicSpotIds: [SPOT_A] }))
    wrapper.unmount()
  })

  it('更换主城市时清空旧的景点选择并提示原因', async () => {
    vi.mocked(api.fetchOwnGuide).mockResolvedValue(draft({ scenicSpots: [{ id: SPOT_A, name: '西湖', status: 'ACTIVE' }] }))
    const { wrapper } = await renderEditor('/guides/7007199254740993/edit')
    wrapper.findComponent({ name: 'DestinationPicker' }).vm.$emit('update:modelValue', OTHER_CITY)
    await flushPromises()
    expect(wrapper.find(`[data-test="scenic-selected-${SPOT_A}"]`).exists()).toBe(false)
    expect(wrapper.get('[data-test="scenic-notice"]').text()).toContain('已清空')
    await wrapper.get('[data-test="save-guide"]').trigger('click')
    await flushPromises()
    expect(api.updateGuide).toHaveBeenLastCalledWith('7007199254740993', expect.objectContaining({ scenicSpotIds: [] }))
    wrapper.unmount()
  })

  it('攻略详情展示可导航的关联景点', async () => {
    const { wrapper } = await renderEditor('/guides/7007199254740993')
    const links = wrapper.findAll('[data-test="guide-scenic-spots"] a')
    expect(links).toHaveLength(1)
    expect(links[0]!.text()).toContain('西湖')
    expect(links[0]!.attributes('href')).toBe(`/destinations/${SPOT_A}`)
    wrapper.unmount()
  })
})
