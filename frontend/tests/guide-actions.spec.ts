import { beforeEach,expect,it,vi } from 'vitest'
import { flushPromises,mount } from '@vue/test-utils'
import { createPinia,setActivePinia } from 'pinia'
import { createMemoryHistory,createRouter } from 'vue-router'
import GuideActions from '@/components/GuideActions.vue'
import { fetchInteraction,setGuideLike,setGuideFavorite } from '@/api/interactions'
import { ApiError } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import type { GuideInteraction } from '@/types/interaction'
vi.mock('@/api/interactions',()=>({ fetchInteraction:vi.fn(),setGuideLike:vi.fn(),setGuideFavorite:vi.fn() }))
const id='9007199254740993'
const state:GuideInteraction={ guideId:id,liked:true,favorited:false,likeCount:7,favoriteCount:3,commentCount:2 }
async function render(authenticated=true){
  const pinia=createPinia();setActivePinia(pinia);const auth=useAuthStore()
  if(authenticated){auth.token='test-token';auth.user={ id:'1',username:'luna',nickname:'旅人',avatarUrl:null,bio:null,role:'USER',status:'ACTIVE',createdAt:'2026-11-03T10:00:00.000+08:00' }}
  const router=createRouter({ history:createMemoryHistory(),routes:[{ path:'/guides/:id',name:'guide-detail',component:{ template:'<div />' } },{ path:'/login',name:'login',component:{ template:'<div />' } }] })
  await router.push('/guides/'+id);await router.isReady()
  const wrapper=mount(GuideActions,{ props:{ guideId:id,likeCount:0,favoriteCount:0,commentCount:0 },global:{ plugins:[pinia,router] } });await flushPromises();return { wrapper,router,auth }
}
beforeEach(()=>{
  vi.resetAllMocks();vi.mocked(fetchInteraction).mockResolvedValue(state)
  vi.mocked(setGuideLike).mockResolvedValue({ ...state,liked:false,likeCount:6 })
  vi.mocked(setGuideFavorite).mockResolvedValue({ ...state,favorited:true,favoriteCount:4 })
})
it('匿名动作前往登录并保留当前攻略回跳',async()=>{
  const { wrapper,router }=await render(false)
  expect(fetchInteraction).not.toHaveBeenCalled()
  await wrapper.get('[data-test="like-guide"]').trigger('click');await flushPromises()
  expect(router.currentRoute.value.name).toBe('login');expect(router.currentRoute.value.query.redirect).toBe('/guides/'+id)
  expect(setGuideLike).not.toHaveBeenCalled();wrapper.unmount()
})
it('初始化使用真实选中状态和计数，ID不转Number',async()=>{
  const { wrapper }=await render()
  expect(fetchInteraction).toHaveBeenCalledWith(id)
  expect(wrapper.get('[data-test="like-guide"]').attributes('aria-pressed')).toBe('true')
  expect(wrapper.get('[data-test="favorite-guide"]').attributes('aria-pressed')).toBe('false')
  expect(wrapper.text()).toContain('7');wrapper.unmount()
})
it('待响应时串行防重，只有服务端成功才翻转状态',async()=>{
  let finish!:(value:GuideInteraction)=>void
  vi.mocked(setGuideLike).mockReturnValueOnce(new Promise(resolve=>{finish=resolve}))
  const { wrapper }=await render()
  await wrapper.get('[data-test="like-guide"]').trigger('click');await wrapper.get('[data-test="like-guide"]').trigger('click')
  expect(setGuideLike).toHaveBeenCalledTimes(1);expect(setGuideLike).toHaveBeenCalledWith(id,false)
  expect(wrapper.get('[data-test="like-guide"]').attributes('aria-pressed')).toBe('true')
  expect(wrapper.get('[data-test="favorite-guide"]').attributes('disabled')).toBeDefined()
  finish({ ...state,liked:false,likeCount:6 });await flushPromises()
  expect(wrapper.get('[data-test="like-guide"]').attributes('aria-pressed')).toBe('false')
  expect(wrapper.emitted('change')?.at(-1)?.[0]).toMatchObject({ liked:false,likeCount:6 });wrapper.unmount()
})
it('动作失败保留已知状态并可重试',async()=>{
  vi.mocked(setGuideFavorite).mockRejectedValueOnce(new ApiError('暂时无法收藏',{ status:503 }))
  const { wrapper,auth }=await render()
  await wrapper.get('[data-test="favorite-guide"]').trigger('click');await flushPromises()
  expect(wrapper.get('[role="alert"]').text()).toContain('暂时无法收藏');expect(auth.isAuthenticated).toBe(true)
  expect(wrapper.get('[data-test="favorite-guide"]').attributes('aria-pressed')).toBe('false')
  await wrapper.get('[data-test="favorite-guide"]').trigger('click');await flushPromises()
  expect(wrapper.get('[data-test="favorite-guide"]').attributes('aria-pressed')).toBe('true');wrapper.unmount()
})
it('状态初始化失败不能假设未点赞并发送相反动作',async()=>{
  vi.mocked(fetchInteraction).mockRejectedValueOnce(new ApiError('状态暂时无法加载',{ status:503 }))
  const { wrapper }=await render()
  expect(wrapper.get('[data-test="like-guide"]').attributes('disabled')).toBeDefined()
  await wrapper.get('[data-test="like-guide"]').trigger('click');expect(setGuideLike).not.toHaveBeenCalled()
  await wrapper.get('[data-test="retry-interaction"]').trigger('click');await flushPromises()
  expect(wrapper.get('[data-test="like-guide"]').attributes('aria-pressed')).toBe('true');wrapper.unmount()
})
it('迟到的旧攻略状态不覆盖当前攻略',async()=>{
  let finish!:(value:GuideInteraction)=>void
  vi.mocked(fetchInteraction).mockReturnValueOnce(new Promise(resolve=>{finish=resolve})).mockResolvedValueOnce({ ...state,guideId:'9007199254740994',liked:false })
  const { wrapper }=await render();await wrapper.setProps({ guideId:'9007199254740994' });await flushPromises()
  finish(state);await flushPromises()
  expect(wrapper.get('[data-test="like-guide"]').attributes('aria-pressed')).toBe('false')
  await wrapper.get('[data-test="like-guide"]').trigger('click');await flushPromises()
  expect(setGuideLike).toHaveBeenCalledWith('9007199254740994',true);wrapper.unmount()
})
it('用户退出后移除前一用户私有选中状态',async()=>{
  const { wrapper,auth }=await render();auth.clearSession();await flushPromises()
  expect(wrapper.get('[data-test="like-guide"]').attributes('aria-pressed')).toBeUndefined()
  wrapper.unmount()
})
