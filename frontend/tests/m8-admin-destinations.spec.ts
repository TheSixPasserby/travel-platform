import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import AdminDestinationsView from '@/views/AdminDestinationsView.vue'
import {
  changeDestinationStatus, createDestination, fetchAdminDestinations, fetchDestination, fetchDestinations, updateDestination
} from '@/api/destinations'
import type { Destination, DestinationImage } from '@/types/destination'

vi.mock('@/api/destinations', () => ({
  fetchAdminDestinations: vi.fn(), fetchDestinations: vi.fn(), fetchDestination: vi.fn(),
  createDestination: vi.fn(), updateDestination: vi.fn(), changeDestinationStatus: vi.fn()
}))

const CITY_ID = '9007199254740993'

const image: DestinationImage = {
  id: '501', url: '/uploads/2026/11/xihu.png', altText: '西湖晨雾', sortOrder: 0,
  sourceUrl: 'https://example.com/xihu', author: '市文旅局', license: 'CC BY 4.0',
  licenseUrl: 'https://creativecommons.org/licenses/by/4.0/', retrievedAt: '2026-10-30T10:00:00.000+08:00'
}

function spot(overrides: Partial<Destination> = {}): Destination {
  return {
    id: CITY_ID, name: '西湖', destinationType: 'SCENIC_SPOT', parentId: '9007199254740990',
    country: '中国', province: '浙江', city: '杭州', description: '湖畔慢行', coverImageUrl: null,
    recommendedSeasons: '春秋', status: 'ACTIVE', sortOrder: 0,
    address: '浙江省杭州市西湖区龙井路 1 号', longitude: '120.150000', latitude: '30.250000',
    recommendedDurationMinutes: 120, recommendedDays: 1, openingHours: '08:00-17:30',
    ticketInfo: '免费开放', officialUrl: 'https://www.hangzhou.gov.cn/', sourceUrl: 'https://www.hangzhou.gov.cn/xihu',
    verifiedAt: '2026-10-30T10:00:00.000+08:00', images: [image],
    createdAt: '2026-11-03T00:00:00.000+08:00', updatedAt: '2026-11-03T00:00:00.000+08:00',
    ...overrides
  }
}

const pageOf = (records: Destination[], current = 1, pages = 1, size = 10) =>
  ({ records, total: records.length + (pages - 1) * size, current, size, pages })

async function render() {
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/admin/destinations', name: 'admin-destinations', component: AdminDestinationsView },
    { path: '/admin', name: 'admin-home', component: { template: '<div />' } },
    { path: '/admin/users', name: 'admin-users', component: { template: '<div />' } },
    { path: '/admin/guides', name: 'admin-guides', component: { template: '<div />' } },
    { path: '/admin/comments', name: 'admin-comments', component: { template: '<div />' } },
    { path: '/destinations', name: 'destinations', component: { template: '<div />' } },
    { path: '/destinations/:id', name: 'destination-detail', component: { template: '<div />' } },
    { path: '/login', name: 'login', component: { template: '<div />' } }
  ] })
  await router.push('/admin/destinations'); await router.isReady()
  const wrapper = mount(AdminDestinationsView, { global: { plugins: [router], stubs: { ImageUpload: true } } })
  await flushPromises()
  return { wrapper, router }
}

async function openEditor(wrapper: Awaited<ReturnType<typeof render>>['wrapper']) {
  await wrapper.get(`[data-test="edit-${CITY_ID}"]`).trigger('click')
  await flushPromises()
}

const field = (wrapper: Awaited<ReturnType<typeof render>>['wrapper'], name: string) =>
  wrapper.get(`[data-test="${name}"]`)

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(fetchAdminDestinations).mockResolvedValue(pageOf([spot()]))
  vi.mocked(fetchDestinations).mockResolvedValue(pageOf([]))
  vi.mocked(fetchDestination).mockResolvedValue({ ...spot(), id: '9007199254740990', name: '杭州', destinationType: 'CITY', parentId: null })
  vi.mocked(createDestination).mockResolvedValue(spot())
  vi.mocked(updateDestination).mockResolvedValue(spot())
  vi.mocked(changeDestinationStatus).mockResolvedValue(spot())
})

describe('后台目的地 M8 资料字段', () => {
  it('OTHER保留通用父级候选，景点的城市约束单独生效', async () => {
    vi.mocked(fetchAdminDestinations).mockResolvedValue(pageOf([spot({destinationType:'OTHER',parentId:null})]))
    const {wrapper}=await render()
    await openEditor(wrapper)
    expect(fetchDestinations).toHaveBeenLastCalledWith(expect.objectContaining({destinationType:undefined}))
    wrapper.unmount()
  })
  it('编辑时回显地址、坐标、时长、开放票务与核验来源', async () => {
    const { wrapper } = await render()
    await openEditor(wrapper)
    expect((field(wrapper, 'destination-address').element as HTMLInputElement).value).toBe('浙江省杭州市西湖区龙井路 1 号')
    expect((field(wrapper, 'destination-longitude').element as HTMLInputElement).value).toBe('120.150000')
    expect((field(wrapper, 'destination-latitude').element as HTMLInputElement).value).toBe('30.250000')
    expect((field(wrapper, 'destination-duration').element as HTMLInputElement).value).toBe('120')
    expect((field(wrapper, 'destination-days').element as HTMLInputElement).value).toBe('1')
    expect((field(wrapper, 'destination-opening').element as HTMLInputElement).value).toBe('08:00-17:30')
    expect((field(wrapper, 'destination-ticket').element as HTMLInputElement).value).toBe('免费开放')
    expect((field(wrapper, 'destination-official-url').element as HTMLInputElement).value).toBe('https://www.hangzhou.gov.cn/')
    expect((field(wrapper, 'destination-source-url').element as HTMLInputElement).value).toBe('https://www.hangzhou.gov.cn/xihu')
    expect((field(wrapper, 'destination-verified-at').element as HTMLInputElement).value).toBe('2026-10-30T10:00')
    wrapper.unmount()
  })

  it('保存时按契约提交全部新字段，空值发送 null', async () => {
    const { wrapper } = await render()
    await openEditor(wrapper)
    await field(wrapper, 'destination-address').setValue(' 杭州市西湖区 ')
    await field(wrapper, 'destination-duration').setValue('90')
    await field(wrapper, 'destination-days').setValue('')
    await field(wrapper, 'destination-ticket').setValue('')
    await field(wrapper, 'destination-verified-at').setValue('2026-11-05T08:30')
    await wrapper.get('[data-test="destination-form"]').trigger('submit')
    await flushPromises()
    expect(updateDestination).toHaveBeenCalledWith(CITY_ID, expect.objectContaining({
      address: '杭州市西湖区',
      longitude: '120.150000',
      latitude: '30.250000',
      recommendedDurationMinutes: 90,
      recommendedDays: null,
      openingHours: '08:00-17:30',
      ticketInfo: null,
      officialUrl: 'https://www.hangzhou.gov.cn/',
      sourceUrl: 'https://www.hangzhou.gov.cn/xihu',
      verifiedAt: '2026-11-05T08:30:00.000+08:00'
    }))
    const payload = vi.mocked(updateDestination).mock.calls[0]![1] as Record<string, unknown>
    expect(payload).not.toHaveProperty('id')
    expect(payload).not.toHaveProperty('sortOrder ' as string)
    wrapper.unmount()
  })

  it('经纬度必须成对填写，单边提交被阻止', async () => {
    const { wrapper } = await render()
    await openEditor(wrapper)
    await field(wrapper, 'destination-latitude').setValue('')
    await wrapper.get('[data-test="destination-form"]').trigger('submit')
    await flushPromises()
    expect(updateDestination).not.toHaveBeenCalled()
    expect(wrapper.get('[data-test="destination-save-error"]').text()).toContain('经纬度')
    wrapper.unmount()
  })

  it('经纬度与推荐时长不合法时阻止提交', async () => {
    const { wrapper } = await render()
    await openEditor(wrapper)
    await field(wrapper, 'destination-longitude').setValue('120.1234567')
    await field(wrapper, 'destination-duration').setValue('0')
    await wrapper.get('[data-test="destination-form"]').trigger('submit')
    await flushPromises()
    expect(updateDestination).not.toHaveBeenCalled()
    expect(wrapper.get('[data-test="destination-save-error"]').text()).toContain('小数')
    wrapper.unmount()
  })
})

describe('后台目的地图库管理', () => {
  it('回显已有受管图片的作者与许可信息', async () => {
    const { wrapper } = await render()
    await openEditor(wrapper)
    const item = wrapper.get('[data-test="gallery-item-0"]')
    const val = (selector: string) => (item.get(selector).element as HTMLInputElement).value
    expect(val('[data-test="gallery-alt-0"]')).toBe('西湖晨雾')
    expect(val('[data-test="gallery-author-0"]')).toBe('市文旅局')
    expect(val('[data-test="gallery-license-0"]')).toBe('CC BY 4.0')
    expect(val('[data-test="gallery-license-url-0"]')).toBe('https://creativecommons.org/licenses/by/4.0/')
    wrapper.unmount()
  })

  it('可调整顺序并删除图片，保存时按新顺序提交且不发送内部字段', async () => {
    const second = { ...image, id: '502', url: '/uploads/2026/11/lingyin.png', altText: '灵隐寺', sortOrder: 1 }
    vi.mocked(fetchAdminDestinations).mockResolvedValue(pageOf([spot({ images: [image, second] })]))
    const { wrapper } = await render()
    await openEditor(wrapper)
    await wrapper.get('[data-test="gallery-down-0"]').trigger('click')
    await flushPromises()
    expect((wrapper.get('[data-test="gallery-alt-0"]').element as HTMLInputElement).value).toBe('灵隐寺')
    await wrapper.get('[data-test="gallery-remove-0"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-test="gallery-item-1"]').exists()).toBe(false)
    await wrapper.get('[data-test="destination-form"]').trigger('submit')
    await flushPromises()
    const payload = vi.mocked(updateDestination).mock.calls[0]![1] as { images: Record<string, unknown>[] }
    expect(payload.images).toHaveLength(1)
    expect(payload.images[0]).toMatchObject({
      url: '/uploads/2026/11/xihu.png', altText: '西湖晨雾', author: '市文旅局', license: 'CC BY 4.0'
    })
    expect(Object.keys(payload.images[0]!).sort()).toEqual(
      ['altText', 'author', 'license', 'licenseUrl', 'retrievedAt', 'sourceUrl', 'url'].sort()
    )
    wrapper.unmount()
  })

  it('新上传图片缺少元数据时阻止保存并说明原因', async () => {
    const { wrapper } = await render()
    await openEditor(wrapper)
    const uploads = wrapper.findAllComponents({ name: 'ImageUpload' })
    uploads[1]!.vm.$emit('update:modelValue', '/uploads/2026/11/new.png')
    await flushPromises()
    expect(wrapper.find('[data-test="gallery-item-1"]').exists()).toBe(true)
    await wrapper.get('[data-test="destination-form"]').trigger('submit')
    await flushPromises()
    expect(updateDestination).not.toHaveBeenCalled()
    expect(wrapper.get('[data-test="destination-save-error"]').text()).toContain('图库')
    wrapper.unmount()
  })

  it('补齐元数据后可保存，许可字段完全来自人工填写', async () => {
    const { wrapper } = await render()
    await openEditor(wrapper)
    wrapper.findAllComponents({ name: 'ImageUpload' })[1]!.vm.$emit('update:modelValue', '/uploads/2026/11/new.png')
    await flushPromises()
    await wrapper.get('[data-test="gallery-alt-1"]').setValue('新照片')
    await wrapper.get('[data-test="gallery-source-1"]').setValue('https://example.com/photo')
    await wrapper.get('[data-test="gallery-author-1"]').setValue('摄影师丙')
    await wrapper.get('[data-test="gallery-license-1"]').setValue('CC0 1.0')
    await wrapper.get('[data-test="gallery-license-url-1"]').setValue('https://creativecommons.org/publicdomain/zero/1.0/')
    await wrapper.get('[data-test="gallery-retrieved-1"]').setValue('2026-11-06')
    await wrapper.get('[data-test="destination-form"]').trigger('submit')
    await flushPromises()
    const payload = vi.mocked(updateDestination).mock.calls[0]![1] as { images: Record<string, unknown>[] }
    expect(payload.images[1]).toEqual({
      url: '/uploads/2026/11/new.png', altText: '新照片', sourceUrl: 'https://example.com/photo',
      author: '摄影师丙', license: 'CC0 1.0',
      licenseUrl: 'https://creativecommons.org/publicdomain/zero/1.0/',
      retrievedAt: '2026-11-06T00:00:00.000+08:00'
    })
    wrapper.unmount()
  })
})

describe('后台目的地父级选择', () => {
  it('父级候选只请求城市类型', async () => {
    const { wrapper } = await render()
    await openEditor(wrapper)
    expect(fetchDestinations).toHaveBeenCalledWith(
      expect.objectContaining({ destinationType: 'CITY', page: 1 })
    )
    wrapper.unmount()
  })

  it('父级候选支持服务端分页翻页', async () => {
    const cityA = { ...spot(), id: '9007199254740991', name: '杭州', destinationType: 'CITY' as const, parentId: null }
    const cityB = { ...spot(), id: '9007199254740992', name: '苏州', destinationType: 'CITY' as const, parentId: null }
    vi.mocked(fetchDestinations).mockResolvedValue(pageOf([cityA], 1, 2, 10))
    const { wrapper } = await render()
    await openEditor(wrapper)
    vi.mocked(fetchDestinations).mockResolvedValue(pageOf([cityB], 2, 2, 10))
    await wrapper.get('[data-test="parent-next-page"]').trigger('click')
    await flushPromises()
    expect(fetchDestinations).toHaveBeenLastCalledWith(expect.objectContaining({ destinationType: 'CITY', page: 2 }))
    expect(wrapper.get('[data-test="destination-parent"]').text()).toContain('苏州')
    wrapper.unmount()
  })

  it('类型切到城市时清空上级选择并隐藏父级控件', async () => {
    const { wrapper } = await render()
    await openEditor(wrapper)
    await wrapper.get('#edit-type').setValue('CITY')
    await flushPromises()
    expect(wrapper.find('[data-test="destination-parent"]').exists()).toBe(false)
    await wrapper.get('[data-test="destination-form"]').trigger('submit')
    await flushPromises()
    expect(updateDestination).toHaveBeenLastCalledWith(CITY_ID, expect.objectContaining({ parentId: null }))
    wrapper.unmount()
  })

  it('景点类型下展示父级控件并允许选择城市', async () => {
    const cityA = { ...spot(), id: '9007199254740991', name: '杭州', destinationType: 'CITY' as const, parentId: null }
    vi.mocked(fetchDestinations).mockResolvedValue(pageOf([cityA]))
    const { wrapper } = await render()
    await openEditor(wrapper)
    expect(field(wrapper, 'destination-parent').text()).toContain('杭州')
    await wrapper.get('[data-test="destination-parent"]').setValue(cityA.id)
    await wrapper.get('[data-test="destination-form"]').trigger('submit')
    await flushPromises()
    expect(updateDestination).toHaveBeenLastCalledWith(CITY_ID, expect.objectContaining({ parentId: cityA.id }))
    wrapper.unmount()
  })
})
