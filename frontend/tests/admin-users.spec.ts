import { beforeEach,expect,it,vi } from 'vitest'
import { flushPromises,mount } from '@vue/test-utils'
import { createMemoryHistory,createRouter,RouterView } from 'vue-router'
import { createPinia,setActivePinia } from 'pinia'
import AdminUsersView from '@/views/AdminUsersView.vue'
import { fetchAdminUsers,patchUserStatus } from '@/api/administration'
import { ApiError } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import type { AdminUser } from '@/types/administration'
vi.mock('@/api/administration',()=>({fetchAdminUsers:vi.fn(),patchUserStatus:vi.fn()}))
const time='2026-11-04T10:00:00.000+08:00',self='9007199254740993'
const own:AdminUser={id:self,username:'admin',nickname:'管理旅人',avatarUrl:null,role:'ADMIN',status:'ACTIVE',createdAt:time,updatedAt:time}
const target:AdminUser={id:'9007199254740994',username:'luna',nickname:'溪谷旅人',avatarUrl:null,role:'USER',status:'ACTIVE',createdAt:time,updatedAt:time}
const page=(records:AdminUser[],extra={})=>({records,total:records.length,current:1,size:10,pages:records.length?1:0,...extra})
async function render(path='/admin/users'){
  const pinia=createPinia();setActivePinia(pinia);const auth=useAuthStore();auth.token='test-token';auth.user={id:self,username:'admin',nickname:'管理旅人',avatarUrl:null,bio:null,role:'ADMIN',status:'ACTIVE',createdAt:time}
  const stub={template:'<div />'},router=createRouter({history:createMemoryHistory(),routes:[{path:'/admin',name:'admin-home',component:stub},{path:'/admin/users',name:'admin-users',component:AdminUsersView,meta:{requiresAuth:true,requiresAdmin:true}},{path:'/admin/guides',name:'admin-guides',component:stub},{path:'/admin/comments',name:'admin-comments',component:stub},{path:'/admin/destinations',name:'admin-destinations',component:stub},{path:'/login',name:'login',component:stub},{path:'/guides',name:'guides',component:stub}]})
  await router.push(path);await router.isReady();const wrapper=mount(RouterView,{global:{plugins:[pinia,router]}});await flushPromises();return {wrapper,router,auth}
}
beforeEach(()=>{vi.resetAllMocks();vi.mocked(fetchAdminUsers).mockResolvedValue(page([own,target]));vi.mocked(patchUserStatus).mockResolvedValue({...target,status:'DISABLED'})})
it('用户列表使用服务端页码并按角色状态搜索复位',async()=>{
  vi.mocked(fetchAdminUsers).mockResolvedValueOnce(page([own,target],{total:21,current:2,pages:3}));const {wrapper}=await render('/admin/users?page=2')
  expect(fetchAdminUsers).toHaveBeenCalledWith(expect.objectContaining({page:2,size:10}));expect(wrapper.findAll('[data-test=admin-user-row]')).toHaveLength(2)
  await wrapper.get('[data-test=admin-user-keyword]').setValue('luna');await wrapper.get('[data-test=admin-user-role]').setValue('USER');await wrapper.get('[data-test=admin-user-status]').setValue('ACTIVE');await wrapper.get('[data-test=admin-user-search]').trigger('submit');await flushPromises()
  expect(fetchAdminUsers).toHaveBeenLastCalledWith(expect.objectContaining({page:1,keyword:'luna',role:'USER',status:'ACTIVE'}));wrapper.unmount()
})
it('当前账号不可禁用，其他账号必须确认后才发送字符串ID',async()=>{
  const {wrapper}=await render();expect(wrapper.get('[data-test="change-user-'+self+'"]').attributes('disabled')).toBeDefined()
  await wrapper.get('[data-test="change-user-'+target.id+'"]').trigger('click');expect(patchUserStatus).not.toHaveBeenCalled();expect(wrapper.get('[role=alertdialog]').text()).toContain('luna')
  await wrapper.get('[data-test=confirm-user-status]').trigger('click');await flushPromises();expect(patchUserStatus).toHaveBeenCalledWith(target.id,'DISABLED');wrapper.unmount()
})
it('确认存在时禁用其他行和筛选，不能替换目标',async()=>{
  const another={...target,id:'9007199254740995',username:'mori'};vi.mocked(fetchAdminUsers).mockResolvedValueOnce(page([own,target,another]));const {wrapper}=await render()
  await wrapper.get('[data-test="change-user-'+target.id+'"]').trigger('click');expect(wrapper.get('[data-test="change-user-'+another.id+'"]').attributes('disabled')).toBeDefined();expect(wrapper.get('[data-test=admin-user-keyword]').attributes('disabled')).toBeDefined()
  await wrapper.get('[data-test="change-user-'+another.id+'"]').trigger('click');await wrapper.get('[data-test=confirm-user-status]').trigger('click');await flushPromises();expect(patchUserStatus).toHaveBeenCalledWith(target.id,'DISABLED');wrapper.unmount()
})
it('等待结果时防重且不乐观改状态，失败保留确认后重试',async()=>{
  let reject!:(error:Error)=>void;vi.mocked(patchUserStatus).mockReturnValueOnce(new Promise((_,fail)=>{reject=fail}));const {wrapper}=await render()
  await wrapper.get('[data-test="change-user-'+target.id+'"]').trigger('click');await wrapper.get('[data-test=confirm-user-status]').trigger('click');await wrapper.get('[data-test=confirm-user-status]').trigger('click');expect(patchUserStatus).toHaveBeenCalledTimes(1)
  expect(wrapper.findAll('[data-test=admin-user-row]')[1]!.text()).toContain('正常');reject(new ApiError('暂时不能更新',{status:503}));await flushPromises();expect(wrapper.get('[role=alert]').text()).toContain('暂时不能更新');expect(wrapper.find('[role=alertdialog]').exists()).toBe(true)
  vi.mocked(fetchAdminUsers).mockResolvedValueOnce(page([own,{...target,status:'DISABLED'}]));await wrapper.get('[data-test=confirm-user-status]').trigger('click');await flushPromises();expect(wrapper.findAll('[data-test=admin-user-row]')[1]!.text()).toContain('已禁用');wrapper.unmount()
})
it('加载失败可重试，服务端空页可显式回第一页',async()=>{
  vi.mocked(fetchAdminUsers).mockRejectedValueOnce(new ApiError('用户列表暂不可用',{status:503})).mockResolvedValueOnce(page([],{total:2,current:999,pages:1}));const {wrapper}=await render('/admin/users?page=999')
  expect(wrapper.get('[role=alert]').text()).toContain('用户列表暂不可用');await wrapper.get('[data-test=retry-admin-users]').trigger('click');await flushPromises();await wrapper.get('[data-test=admin-users-first]').trigger('click');await flushPromises();expect(fetchAdminUsers).toHaveBeenLastCalledWith(expect.objectContaining({page:1}));wrapper.unmount()
})
it('禁止非法角色/状态URL通过重试静默扩大查询',async()=>{
  const {wrapper}=await render('/admin/users?role=ROOT');expect(fetchAdminUsers).not.toHaveBeenCalled();expect(wrapper.find('[role=alert]').exists()).toBe(true);await wrapper.get('[data-test=retry-admin-users]').trigger('click');await flushPromises();expect(fetchAdminUsers).not.toHaveBeenCalled();wrapper.unmount()
})
it('状态写入401也保留原管理页回跳',async()=>{
  const {wrapper,router}=await render('/admin/users?page=2');vi.mocked(patchUserStatus).mockRejectedValueOnce(new ApiError('登录状态已失效',{status:401}))
  await wrapper.get('[data-test="change-user-'+target.id+'"]').trigger('click');await wrapper.get('[data-test=confirm-user-status]').trigger('click');await flushPromises()
  expect(router.currentRoute.value.name).toBe('login');expect(router.currentRoute.value.query.redirect).toBe('/admin/users?page=2');wrapper.unmount()
})
it('状态写入403清除前一权限下的列表与确认',async()=>{
  const {wrapper}=await render();vi.mocked(patchUserStatus).mockRejectedValueOnce(new ApiError('无权访问该资源',{status:403}))
  await wrapper.get('[data-test="change-user-'+target.id+'"]').trigger('click');await wrapper.get('[data-test=confirm-user-status]').trigger('click');await flushPromises()
  expect(wrapper.get('[role=alert]').text()).toContain('无权访问');expect(wrapper.find('[data-test=admin-user-row]').exists()).toBe(false);expect(wrapper.find('[role=alertdialog]').exists()).toBe(false);wrapper.unmount()
})
it('搜索同步URL，加载失败重试继续使用用户刚提交的筛选',async()=>{
  const {wrapper,router}=await render('/admin/users?page=2&keyword=old');vi.mocked(fetchAdminUsers).mockRejectedValueOnce(new ApiError('暂时不可用',{status:503}))
  await wrapper.get('[data-test=admin-user-keyword]').setValue('new');await wrapper.get('[data-test=admin-user-role]').setValue('USER');await wrapper.get('[data-test=admin-user-search]').trigger('submit');await flushPromises()
  expect(router.currentRoute.value.query.keyword).toBe('new');expect(router.currentRoute.value.query.page).toBe('1')
  await wrapper.get('[data-test=retry-admin-users]').trigger('click');await flushPromises();expect(fetchAdminUsers).toHaveBeenLastCalledWith(expect.objectContaining({page:1,keyword:'new',role:'USER'}));wrapper.unmount()
})
it('账号切换等待新权限期间立即隐藏旧管理员数据',async()=>{
  const {wrapper,auth}=await render();let reject!:(error:Error)=>void;vi.mocked(fetchAdminUsers).mockReturnValueOnce(new Promise((_,fail)=>{reject=fail}))
  auth.user={id:'3',username:'reader',nickname:'旅人',avatarUrl:null,bio:null,role:'USER',status:'ACTIVE',createdAt:time};auth.token='reader-token';await flushPromises()
  expect(wrapper.find('[data-test=admin-user-row]').exists()).toBe(false);reject(new ApiError('无权访问该资源',{status:403}));await flushPromises();expect(wrapper.get('[role=alert]').text()).toContain('无权访问');wrapper.unmount()
})
it('状态更新后刷新沿用已提交URL，不暗中应用未提交搜索文字',async()=>{
  const {wrapper,router}=await render('/admin/users?keyword=luna&role=USER');await wrapper.get('[data-test=admin-user-keyword]').setValue('尚未提交')
  await wrapper.get('[data-test="change-user-'+target.id+'"]').trigger('click');await wrapper.get('[data-test=confirm-user-status]').trigger('click');await flushPromises()
  expect(fetchAdminUsers).toHaveBeenLastCalledWith(expect.objectContaining({keyword:'luna',role:'USER'}));expect(router.currentRoute.value.query.keyword).toBe('luna');wrapper.unmount()
})
it('昵称中的HTML只当文字显示',async()=>{
  vi.mocked(fetchAdminUsers).mockResolvedValueOnce(page([{...target,nickname:'<img src=x onerror=alert(1)>'}]));const {wrapper}=await render();expect(wrapper.text()).toContain('<img src=x onerror=alert(1)>');expect(wrapper.find('img[src=x],script').exists()).toBe(false);wrapper.unmount()
})
it('401保留原管理页回跳，403不显示旧列表',async()=>{
  vi.mocked(fetchAdminUsers).mockRejectedValueOnce(new ApiError('登录状态已失效',{status:401}));const first=await render('/admin/users?page=2');expect(first.router.currentRoute.value.name).toBe('login');expect(first.router.currentRoute.value.query.redirect).toBe('/admin/users?page=2');first.wrapper.unmount()
  vi.mocked(fetchAdminUsers).mockRejectedValueOnce(new ApiError('无权访问该资源',{status:403}));const second=await render();expect(second.wrapper.get('[role=alert]').text()).toContain('无权访问');expect(second.wrapper.find('[data-test=admin-user-row]').exists()).toBe(false);second.wrapper.unmount()
})
it('卸载后迟到401不能把公共攻略送回登录',async()=>{
  let reject!:(error:Error)=>void;vi.mocked(fetchAdminUsers).mockReturnValueOnce(new Promise((_,fail)=>{reject=fail}));const {wrapper,router}=await render();await router.push('/guides');await flushPromises();reject(new ApiError('失效',{status:401}));await flushPromises();expect(router.currentRoute.value.name).toBe('guides');wrapper.unmount()
})
it('账号切换重新核验权限，403时不能保留前一管理员的列表',async()=>{
  const {wrapper,auth}=await render();vi.mocked(fetchAdminUsers).mockRejectedValueOnce(new ApiError('无权访问该资源',{status:403}))
  auth.user={id:'9007199254740999',username:'other',nickname:'另一位旅行者',avatarUrl:null,bio:null,role:'USER',status:'ACTIVE',createdAt:time};auth.token='another-token';await flushPromises()
  expect(wrapper.get('[role=alert]').text()).toContain('无权访问');expect(wrapper.find('[data-test=admin-user-row]').exists()).toBe(false);wrapper.unmount()
})
