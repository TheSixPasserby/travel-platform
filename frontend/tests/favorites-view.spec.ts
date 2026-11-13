import { beforeEach,expect,it,vi } from 'vitest'
import { flushPromises,mount } from '@vue/test-utils'
import { createMemoryHistory,createRouter } from 'vue-router'
import { createPinia } from 'pinia'
import MyFavoritesView from '@/views/MyFavoritesView.vue'
import { fetchFavorites } from '@/api/interactions'
import { ApiError } from '@/api/client'
import type { GuideSummary } from '@/types/guide'
vi.mock('@/api/interactions',()=>({ fetchFavorites:vi.fn() }))
const guide:GuideSummary={ id:'9007199254740993',title:'收藏的山谷',summary:'清晨出发',coverImageUrl:null,author:{ id:'1',nickname:'旅人',avatarUrl:null,bio:null },destination:null,travelDays:null,budgetPerPerson:null,season:null,tags:[],status:'PUBLISHED',publishedAt:'2026-11-03T10:00:00.000+08:00',createdAt:'2026-11-03T10:00:00.000+08:00',updatedAt:'2026-11-03T10:00:00.000+08:00',likeCount:1,favoriteCount:1,commentCount:0 }
const page=(records:GuideSummary[],extra={})=>({ records,total:records.length,current:1,size:9,pages:records.length?1:0,...extra })
async function render(path='/me/favorites'){
  const router=createRouter({ history:createMemoryHistory(),routes:[{ path:'/me/favorites',name:'my-favorites',component:MyFavoritesView },{ path:'/guides',name:'guides',component:{ template:'<div />' } },{ path:'/guides/:id',name:'guide-detail',component:{ template:'<div />' } },{ path:'/login',name:'login',component:{ template:'<div />' } }] })
  await router.push(path);await router.isReady();const wrapper=mount(MyFavoritesView,{ global:{ plugins:[createPinia(),router] } });await flushPromises();return { wrapper,router }
}
beforeEach(()=>{vi.resetAllMocks();vi.mocked(fetchFavorites).mockResolvedValue(page([guide]))})
it('收藏列表使用服务端页码与字符串ID',async()=>{
  vi.mocked(fetchFavorites).mockResolvedValueOnce(page([guide],{ total:19,current:2,pages:3 }))
  const { wrapper }=await render('/me/favorites?page=2')
  expect(fetchFavorites).toHaveBeenCalledWith(expect.objectContaining({ page:2,size:9 }))
  expect(wrapper.get('[data-test="guide-card"]').attributes('href')).toBe('/guides/'+guide.id)
  await wrapper.get('[data-test="favorite-keyword"]').setValue('山谷');await wrapper.get('form').trigger('submit');await flushPromises()
  expect(fetchFavorites).toHaveBeenLastCalledWith(expect.objectContaining({ page:1,keyword:'山谷' }));wrapper.unmount()
})
it('空收藏不显示假卡片',async()=>{
  vi.mocked(fetchFavorites).mockResolvedValueOnce(page([]));const { wrapper }=await render()
  expect(wrapper.text()).toContain('还没有收藏的攻略');expect(wrapper.find('[data-test="guide-card"]').exists()).toBe(false);wrapper.unmount()
})
it('临时错误可重试',async()=>{
  vi.mocked(fetchFavorites).mockRejectedValueOnce(new ApiError('收藏暂时无法加载',{ status:503 }))
  const { wrapper }=await render();expect(wrapper.get('[role="alert"]').text()).toContain('收藏暂时无法加载')
  await wrapper.get('[data-test="retry-favorites"]').trigger('click');await flushPromises()
  expect(wrapper.find('[data-test="guide-card"]').exists()).toBe(true);wrapper.unmount()
})
it('失效会话回登录且保留收藏回跳',async()=>{
  vi.mocked(fetchFavorites).mockRejectedValueOnce(new ApiError('登录状态已失效',{ status:401 }))
  const { wrapper,router }=await render();expect(router.currentRoute.value.name).toBe('login');expect(router.currentRoute.value.query.redirect).toBe('/me/favorites');wrapper.unmount()
})
it('离开收藏页后迟到的401不能把公开攻略送回登录',async()=>{
  let reject!:(error:Error)=>void
  vi.mocked(fetchFavorites).mockReturnValueOnce(new Promise((_,fail)=>{reject=fail}))
  const { wrapper,router }=await render()
  wrapper.unmount();await router.push('/guides/'+guide.id)
  reject(new ApiError('登录状态已失效',{ status:401 }));await flushPromises()
  expect(router.currentRoute.value.name).toBe('guide-detail')
})
