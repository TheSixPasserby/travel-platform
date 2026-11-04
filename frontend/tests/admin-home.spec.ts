import { beforeEach,expect,it,vi } from 'vitest'
import { flushPromises,mount } from '@vue/test-utils'
import { createMemoryHistory,createRouter,RouterView } from 'vue-router'
import { createPinia,setActivePinia } from 'pinia'
import AdminHomeView from '@/views/AdminHomeView.vue'
import { fetchAdminSummary } from '@/api/administration'
import { ApiError } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import type { AdminSummary } from '@/types/administration'
vi.mock('@/api/administration',()=>({fetchAdminSummary:vi.fn()}))
const summary:AdminSummary={userCount:2,guideCount:3,destinationCount:6,commentCount:0}
async function render(){
  const pinia=createPinia();setActivePinia(pinia);const auth=useAuthStore();auth.token='admin-token';auth.user={id:'1',username:'admin',nickname:'管理旅人',avatarUrl:null,bio:null,role:'ADMIN',status:'ACTIVE',createdAt:'2026-11-04T10:00:00.000+08:00'}
  const stub={template:'<div />'},router=createRouter({history:createMemoryHistory(),routes:[{path:'/admin',name:'admin-home',component:AdminHomeView,meta:{requiresAuth:true,requiresAdmin:true}},{path:'/admin/users',name:'admin-users',component:stub},{path:'/admin/guides',name:'admin-guides',component:stub},{path:'/admin/comments',name:'admin-comments',component:stub},{path:'/admin/destinations',name:'admin-destinations',component:stub},{path:'/login',name:'login',component:stub},{path:'/guides',name:'guides',component:stub}]})
  await router.push('/admin');await router.isReady();const wrapper=mount(RouterView,{global:{plugins:[pinia,router]}});await flushPromises();return {wrapper,router,auth}
}
beforeEach(()=>{vi.resetAllMocks();vi.mocked(fetchAdminSummary).mockResolvedValue(summary)})
it('等待真实响应再显示四个计数，零值也如实呈现',async()=>{
  let finish!:(value:AdminSummary)=>void;vi.mocked(fetchAdminSummary).mockReturnValueOnce(new Promise(resolve=>{finish=resolve}));const {wrapper}=await render();expect(wrapper.find('[data-test=admin-stat-userCount]').exists()).toBe(false);expect(wrapper.find('[role=status]').exists()).toBe(true)
  finish(summary);await flushPromises();expect(wrapper.get('[data-test=admin-stat-userCount]').text()).toBe('2');expect(wrapper.get('[data-test=admin-stat-commentCount]').text()).toBe('0');wrapper.unmount()
})
it('失败不制造统计数字，可重新加载',async()=>{
  vi.mocked(fetchAdminSummary).mockRejectedValueOnce(new ApiError('统计暂不可用',{status:503}));const {wrapper}=await render();expect(wrapper.get('[role=alert]').text()).toContain('统计暂不可用');expect(wrapper.find('[data-test=admin-stat-guideCount]').exists()).toBe(false);await wrapper.get('[data-test=retry-admin-summary]').trigger('click');await flushPromises();expect(wrapper.get('[data-test=admin-stat-guideCount]').text()).toBe('3');wrapper.unmount()
})
it('失效会话回登录并保留后台概览路径',async()=>{
  vi.mocked(fetchAdminSummary).mockRejectedValueOnce(new ApiError('登录状态已失效',{status:401}));const {wrapper,router}=await render();expect(router.currentRoute.value.name).toBe('login');expect(router.currentRoute.value.query.redirect).toBe('/admin');wrapper.unmount()
})
it('卸载后迟到401不导航公共页',async()=>{
  let reject!:(error:Error)=>void;vi.mocked(fetchAdminSummary).mockReturnValueOnce(new Promise((_,fail)=>{reject=fail}));const {wrapper,router}=await render();await router.push('/guides');await flushPromises();reject(new ApiError('失效',{status:401}));await flushPromises();expect(router.currentRoute.value.name).toBe('guides');wrapper.unmount()
})
it('账号切换等待权限期间清掉旧统计，403不展示旧值',async()=>{
  const {wrapper,auth}=await render();let reject!:(error:Error)=>void;vi.mocked(fetchAdminSummary).mockReturnValueOnce(new Promise((_,fail)=>{reject=fail}));auth.user={id:'2',username:'reader',nickname:'旅人',avatarUrl:null,bio:null,role:'USER',status:'ACTIVE',createdAt:'2026-11-04T10:00:00.000+08:00'};auth.token='reader-token';await flushPromises();expect(wrapper.find('[data-test=admin-stat-userCount]').exists()).toBe(false);reject(new ApiError('无权访问该资源',{status:403}));await flushPromises();expect(wrapper.get('[role=alert]').text()).toContain('无权访问');wrapper.unmount()
})
