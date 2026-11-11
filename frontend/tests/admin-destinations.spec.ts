import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import AdminDestinationsView from '@/views/AdminDestinationsView.vue'
import { fetchAdminDestinations, fetchDestinations, fetchDestination, createDestination, updateDestination, changeDestinationStatus } from '@/api/destinations'
import { ApiError } from '@/api/client'
import type { Destination } from '@/types/destination'

vi.mock('@/api/destinations', () => ({ fetchAdminDestinations: vi.fn(), fetchDestinations: vi.fn(), fetchDestination: vi.fn(),
  createDestination: vi.fn(), updateDestination: vi.fn(), changeDestinationStatus: vi.fn() }))
const row: Destination = { id: '9007199254740993', name: '湖畔', destinationType: 'CITY', parentId: null, country: '中国',
  address:null,longitude:null,latitude:null,recommendedDurationMinutes:null,recommendedDays:null,openingHours:null,ticketInfo:null,officialUrl:null,sourceUrl:null,verifiedAt:null,images:[],
  province: '浙江', city: null, description: '湖边散步', coverImageUrl: null, recommendedSeasons: null, status: 'ACTIVE',
  sortOrder: 0, createdAt: '2026-11-03T00:00:00.000+08:00', updatedAt: '2026-11-03T00:00:00.000+08:00' }
const page = (records: Destination[]) => ({ records, total: records.length, current: 1, size: 10, pages: records.length ? 1 : 0 })
async function render() {
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/admin/destinations', name: 'admin-destinations', component: AdminDestinationsView },
    { path: '/admin/users', name: 'admin-users', component: { template: '<div />' } },
    { path: '/admin/ai', name: 'admin-ai-settings', component: { template: '<div />' } },
    { path: '/admin', name: 'admin-home', component: { template: '<div />' } },
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
beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(fetchAdminDestinations).mockResolvedValue(page([row]))
  vi.mocked(fetchDestinations).mockResolvedValue(page([]))
  vi.mocked(fetchDestination).mockResolvedValue(row)
  vi.mocked(createDestination).mockResolvedValue(row)
  vi.mocked(updateDestination).mockResolvedValue(row)
  vi.mocked(changeDestinationStatus).mockResolvedValue({ ...row, status: 'INACTIVE' })
})

describe('管理员目的地维护', () => {
  it('窄屏列表使用流式布局，操作按钮保留触摸尺寸', async () => {
    const { wrapper } = await render()
    expect(wrapper.get('table').classes()).toContain('sm:table')
    expect(wrapper.get('table').classes()).not.toContain('min-w-[32rem]')
    expect(wrapper.get(`[data-test="edit-${row.id}"]`).classes()).toContain('btn-ghost')
    wrapper.unmount()
  })
  it('卸载后迟到401不能将公共目的地送回登录',async()=>{
    let reject!:(error:Error)=>void;vi.mocked(fetchAdminDestinations).mockReturnValueOnce(new Promise((_,fail)=>{reject=fail}))
    const {wrapper,router}=await render();wrapper.unmount();await router.push('/destinations');reject(new ApiError('会话失效',{status:401}));await flushPromises()
    expect(router.currentRoute.value.name).toBe('destinations')
  })
  it('状态切换尚未完成时不能提交旧表单状态', async () => {
    let finish!: (value: Destination) => void
    vi.mocked(changeDestinationStatus).mockReturnValueOnce(new Promise(resolve => { finish = resolve }))
    const { wrapper } = await render()
    await wrapper.get(`[data-test="edit-${row.id}"]`).trigger('click'); await flushPromises()
    await wrapper.get(`[data-test="status-${row.id}"]`).trigger('click')
    await wrapper.get('[data-test="destination-form"]').trigger('submit')
    expect(updateDestination).not.toHaveBeenCalled()
    expect(wrapper.get('[data-test="destination-save"]').attributes('disabled')).toBeDefined()
    finish({ ...row, status: 'INACTIVE' }); await flushPromises()
    await wrapper.get('[data-test="destination-form"]').trigger('submit'); await flushPromises()
    expect(updateDestination).toHaveBeenCalledWith(row.id, expect.objectContaining({ status: 'INACTIVE' }))
    wrapper.unmount()
  })

  it('上传期间不能切换编辑对象或清空当前表单', async () => {
    const other = { ...row, id: '9007199254740995', name: '山城' }
    vi.mocked(fetchAdminDestinations).mockResolvedValueOnce(page([row, other]))
    const { wrapper } = await render()
    await wrapper.get(`[data-test="edit-${row.id}"]`).trigger('click'); await flushPromises()
    wrapper.getComponent({ name: 'ImageUpload' }).vm.$emit('busy', true); await flushPromises()
    await wrapper.get(`[data-test="edit-${other.id}"]`).trigger('click')
    await wrapper.get('[data-test="admin-new"]').trigger('click'); await flushPromises()
    expect((wrapper.get('[data-test="destination-name"]').element as HTMLInputElement).value).toBe(row.name)
    wrapper.unmount()
  })

  it('列表切换状态同步当前编辑表单，避免保存时覆盖新状态', async () => {
    const { wrapper } = await render()
    await wrapper.get(`[data-test="edit-${row.id}"]`).trigger('click'); await flushPromises()
    vi.mocked(fetchAdminDestinations).mockResolvedValueOnce(page([{ ...row, status: 'INACTIVE' }]))
    await wrapper.get(`[data-test="status-${row.id}"]`).trigger('click'); await flushPromises()
    expect((wrapper.get('#edit-state').element as HTMLSelectElement).value).toBe('INACTIVE')
    wrapper.unmount()
  })

  it('迟到的上级查询不覆盖后来编辑的目的地', async () => {
    const a = { ...row, destinationType: 'SCENIC_SPOT' as const, parentId: '9007199254740990' }
    const b = { ...row, id: '9007199254740995', name: '山城', destinationType: 'SCENIC_SPOT' as const, parentId: '9007199254740991' }
    const parentA = { ...row, id: a.parentId, name: 'A上级' }
    const parentB = { ...row, id: b.parentId, name: 'B上级' }
    let release!: (value: Destination) => void
    const pending = new Promise<Destination>(resolve => { release = resolve })
    vi.mocked(fetchAdminDestinations).mockResolvedValueOnce(page([a, b]))
    vi.mocked(fetchDestination).mockReturnValueOnce(pending).mockResolvedValueOnce(parentB)
    const { wrapper } = await render()
    await wrapper.get(`[data-test="edit-${a.id}"]`).trigger('click'); await flushPromises()
    await wrapper.get(`[data-test="edit-${b.id}"]`).trigger('click'); await flushPromises()
    release(parentA); await flushPromises()
    expect((wrapper.get('[data-test="destination-parent"]').element as HTMLSelectElement).value).toBe(b.parentId)
    wrapper.unmount()
  })

  it('新增提交明确白名单，缺省表单值与后端契约一致', async () => {
    const { wrapper } = await render()
    await wrapper.get('[data-test="admin-new"]').trigger('click')
    await wrapper.get('[data-test="destination-name"]').setValue('  新城市  ')
    await wrapper.get('[data-test="destination-form"]').trigger('submit')
    await flushPromises()
    expect(createDestination).toHaveBeenCalledWith(expect.objectContaining({ name: '新城市', destinationType: 'CITY',
      country: '中国', status: 'ACTIVE', sortOrder: 0, parentId: null, coverImageUrl: null }))
    expect(Object.keys(vi.mocked(createDestination).mock.calls[0]![0]).sort()).toEqual(
      ['name','destinationType','country','status','sortOrder','parentId','coverImageUrl','province','city','description','recommendedSeasons',
        'address','longitude','latitude','recommendedDurationMinutes','recommendedDays','openingHours','ticketInfo','officialUrl','sourceUrl','verifiedAt','images'].sort())
    expect(wrapper.text()).toContain('目的地已创建')
    wrapper.unmount()
  })

  it('完整修改保留字符串ID，可空字段显式清空且不发送内部字段', async () => {
    const { wrapper } = await render()
    await wrapper.get(`[data-test="edit-${row.id}"]`).trigger('click')
    await flushPromises()
    await wrapper.get('[data-test="destination-description"]').setValue('')
    await wrapper.get('[data-test="destination-province"]').setValue('')
    await wrapper.get('[data-test="destination-form"]').trigger('submit')
    await flushPromises()
    expect(updateDestination).toHaveBeenCalledWith('9007199254740993', expect.objectContaining({ description: null, province: null, status: 'ACTIVE' }))
    expect(vi.mocked(updateDestination).mock.calls[0]![1]).not.toHaveProperty('id')
    expect(vi.mocked(updateDestination).mock.calls[0]![1]).not.toHaveProperty('isDeleted')
    wrapper.unmount()
  })

  it('列表临时故障可重试，停用后按真实结果刷新', async () => {
    vi.mocked(fetchAdminDestinations).mockRejectedValueOnce(new ApiError('暂时无法加载', { status: 503 }))
    const { wrapper } = await render()
    expect(wrapper.get('[role="alert"]').text()).toContain('暂时无法加载')
    await wrapper.get('[data-test="retry-admin-destinations"]').trigger('click'); await flushPromises()
    vi.mocked(fetchAdminDestinations).mockResolvedValueOnce(page([{ ...row, status: 'INACTIVE' }]))
    await wrapper.get(`[data-test="status-${row.id}"]`).trigger('click'); await flushPromises()
    expect(changeDestinationStatus).toHaveBeenCalledWith(row.id, 'INACTIVE')
    expect(wrapper.get(`[data-test="status-${row.id}"]`).text()).toContain('启用')
    wrapper.unmount()
  })

  it('编辑保留未改变的历史停用父引用', async () => {
    const parent = { ...row, id: '9007199254740991', name: '原上级', status: 'INACTIVE' as const }
    vi.mocked(fetchAdminDestinations).mockResolvedValueOnce(page([{ ...row, destinationType: 'SCENIC_SPOT' as const, parentId: parent.id }]))
    vi.mocked(fetchDestination).mockResolvedValueOnce(parent)
    const { wrapper } = await render()
    await wrapper.get(`[data-test="edit-${row.id}"]`).trigger('click'); await flushPromises()
    expect((wrapper.get('[data-test="destination-parent"]').element as HTMLSelectElement).value).toBe(parent.id)
    await wrapper.get('[data-test="destination-form"]').trigger('submit'); await flushPromises()
    expect(updateDestination).toHaveBeenCalledWith(row.id, expect.objectContaining({ parentId: parent.id }))
    wrapper.unmount()
  })
})
