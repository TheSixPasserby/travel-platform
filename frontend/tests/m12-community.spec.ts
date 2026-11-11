import { clearFeedCache } from '@/composables/useInfiniteFeed'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import CreatorView from '@/views/CreatorView.vue'
import UserIdentity from '@/components/UserIdentity.vue'
vi.mock('@/api/creators',()=>({fetchCreator:vi.fn(),fetchCreatorFeed:vi.fn()}))
import { fetchCreator,fetchCreatorFeed } from '@/api/creators'
const wrappers:VueWrapper[]=[]
afterEach(()=>{wrappers.splice(0).forEach(w=>w.unmount());vi.resetAllMocks();clearFeedCache()})
const profile=(username='walker')=>({id:'12',username,nickname:'山野旅人',avatarUrl:null,bio:'沿途记录',publishedGuideCount:1,likeCount:0,favoriteCount:0})
async function render(path='/users/walker'){
 const pinia=createPinia();setActivePinia(pinia)
 const router=createRouter({history:createMemoryHistory(),routes:[{path:'/users/:username',component:CreatorView},{path:'/me',component:{template:'<p>我的资料</p>'}},{path:'/guides/:id',name:'guide-detail',component:{template:'<p>攻略</p>'}}]})
 await router.push(path);await router.isReady()
 const w=mount(CreatorView,{global:{plugins:[pinia,router],stubs:{GuideCard:{template:'<article data-test="public-note">公开手记</article>'}}}});wrappers.push(w);await flushPromises();return {w,router}
}
describe('公开创作者页',()=>{
 it('显示安全资料与真实统计，公开空流保留明确结束状态',async()=>{
  vi.mocked(fetchCreator).mockResolvedValue(profile())
  vi.mocked(fetchCreatorFeed).mockResolvedValue({records:[],nextCursor:null,hasMore:false})
  const {w}=await render('/users/walker?page=2')
  expect(w.text()).toContain('山野旅人');expect(w.text()).toContain('1 篇公开手记');expect(w.text()).not.toContain('我的收藏')
  expect(w.text()).not.toContain('上一页');expect(w.text()).not.toContain('下一页')
  expect(fetchCreatorFeed).toHaveBeenCalledWith('walker',expect.objectContaining({size:12}),expect.anything())
 })
 it('切换作者后迟到响应不能覆盖新作者',async()=>{
  let finish!:(value:ReturnType<typeof profile>)=>void
  vi.mocked(fetchCreator).mockImplementation(username=>username==='walker'?new Promise(resolve=>finish=resolve):Promise.resolve(profile(username)))
  vi.mocked(fetchCreatorFeed).mockResolvedValue({records:[],nextCursor:null,hasMore:false})
  const {w,router}=await render();await router.push('/users/newwalker');await flushPromises();finish({...profile(),nickname:'迟到的旧作者'});await flushPromises()
  expect(w.text()).toContain('@newwalker');expect(w.text()).not.toContain('迟到的旧作者')
 })
 it('请求失败可重试，删除或不可用作者显示后端错误',async()=>{
  vi.mocked(fetchCreator).mockRejectedValueOnce(new Error('offline')).mockResolvedValue(profile())
  vi.mocked(fetchCreatorFeed).mockResolvedValue({records:[],nextCursor:null,hasMore:false})
  const {w}=await render();expect(w.get('[role="alert"]').text()).toContain('暂时无法加载')
  await w.get('[data-test="creator-retry"]').trigger('click');await flushPromises();expect(w.text()).toContain('@walker')
 })
})
it('身份组件支持独立公开主页链接且注销占位无空链接',()=>{
 const w=mount(UserIdentity,{props:{user:profile(),linked:true},global:{stubs:{RouterLink:{props:['to'],template:'<a :href="to"><slot /></a>'}}}});wrappers.push(w)
 expect(w.get('a').attributes('href')).toBe('/users/walker')
 const deleted=mount(UserIdentity,{props:{user:{nickname:'已注销的旅行者'},linked:true}});wrappers.push(deleted);expect(deleted.find('a').exists()).toBe(false)
})
