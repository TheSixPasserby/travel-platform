import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import HomeView from '@/views/HomeView.vue'
import GuidesView from '@/views/GuidesView.vue'
import GuideCard from '@/components/GuideCard.vue'
import { fetchGuides,fetchTags } from '@/api/guides'
import { fetchDestinations } from '@/api/destinations'
import type { GuideSummary } from '@/types/guide'
vi.mock('@/api/guides',()=>({fetchGuides:vi.fn(),fetchTags:vi.fn()}))
vi.mock('@/api/destinations',()=>({fetchDestinations:vi.fn(),fetchDestination:vi.fn()}))
const note={id:'4',title:'成都两日慢行',summary:'沿街走走',coverImageUrl:null,author:{id:'12',nickname:'林间旅人',username:'walker',avatarUrl:null,bio:null},destination:{id:'1',name:'成都',status:'ACTIVE'},tags:[],travelDays:null,budgetPerPerson:null,season:null,likeCount:2,favoriteCount:1,commentCount:0,status:'PUBLISHED',publishedAt:'2026-11-01',createdAt:'2026-11-01',updatedAt:'2026-11-01'} as GuideSummary
const data=(records=[note])=>({records,total:records.length,current:1,size:12,pages:records.length?1:0})
const wrappers:VueWrapper[]=[]
afterEach(()=>{wrappers.splice(0).forEach(w=>w.unmount());vi.resetAllMocks()})
async function render(path='/'){
 vi.mocked(fetchGuides).mockResolvedValue(data());vi.mocked(fetchTags).mockResolvedValue([{id:'7',name:'慢旅行'}]);vi.mocked(fetchDestinations).mockResolvedValue({records:[{id:'1',name:'成都'}] as never[],total:1,current:1,size:8,pages:1})
 const router=createRouter({history:createMemoryHistory(),routes:[{path:'/',component:HomeView},{path:'/guides',component:GuidesView},{path:'/guides/new',name:'guide-new',component:{template:'<p>编辑</p>'}},{path:'/guides/:id',name:'guide-detail',component:{template:'<p>攻略</p>'}},{path:'/users/:username',component:{template:'<p>作者</p>'}}]})
 await router.push(path);await router.isReady();const w=mount(path.startsWith('/guides')?GuidesView:HomeView,{global:{plugins:[router]}});wrappers.push(w);await flushPromises();return {w,router}
}
describe('真实内容发现流',()=>{
 it('首页从API显示内容、作者与真实主题城市，推荐排序可解释',async()=>{
  const {w}=await render();expect(fetchGuides).toHaveBeenCalledWith(expect.objectContaining({sort:'popular',size:12}));expect(w.text()).toContain('成都两日慢行');expect(w.text()).toContain('慢旅行');expect(w.text()).toContain('点赞与发布时间');expect(w.text()).not.toContain('这三件事，先做好')
  expect(w.get('[data-test="guide-card"]').attributes('href')).toBe('/guides/4');expect(w.find('a[href="/users/walker"]').exists()).toBe(true)
 })
 it('切换最新/热门更新查询，主题过滤复位分页',async()=>{
  const {w,router}=await render('/?page=2');await w.get('[data-test="order-latest"]').trigger('click');await flushPromises()
  expect(fetchGuides).toHaveBeenLastCalledWith(expect.objectContaining({sort:'latest',page:1}));expect(router.currentRoute.value.query.sort).toBe('latest')
  await w.get('[data-test="topic-7"]').trigger('click');await flushPromises();expect(fetchGuides).toHaveBeenLastCalledWith(expect.objectContaining({tagId:'7',page:1}))
 })
 it('竞态只接受当前筛选结果，错误可重试且空流不造卡片',async()=>{
  const {w}=await render();let finish!:(value:ReturnType<typeof data>)=>void
  vi.mocked(fetchGuides).mockReturnValueOnce(new Promise(resolve=>finish=resolve)).mockResolvedValueOnce(data([{...note,title:'当前热门'}]))
  await w.get('[data-test="order-latest"]').trigger('click');await flushPromises();await w.get('[data-test="order-popular"]').trigger('click');await flushPromises();finish(data([{...note,title:'迟到旧文'}]));await flushPromises();expect(w.text()).toContain('当前热门');expect(w.text()).not.toContain('迟到旧文')
  vi.mocked(fetchGuides).mockRejectedValueOnce(new Error('network'));await w.get('[data-test="order-latest"]').trigger('click');await flushPromises();expect(w.get('[role="alert"]').text()).toContain('无法加载')
  vi.mocked(fetchGuides).mockResolvedValue(data([]));await w.get('[data-test="retry-guides"]').trigger('click');await flushPromises();expect(w.find('[data-test="guide-card"]').exists()).toBe(false)
 })
 it('列表刷新恢复关键词/地点/标签/排序/页码，提交复位页码',async()=>{
  const {w,router}=await render('/guides?keyword=茶馆&destinationId=1&tagId=7&sort=popular&page=3')
  expect(fetchGuides).toHaveBeenCalledWith(expect.objectContaining({keyword:'茶馆',destinationId:'1',tagId:'7',sort:'popular',page:3}))
  await w.get('[data-test="guide-keyword"]').setValue('山谷');await w.get('[data-test="guide-search"]').trigger('submit');await flushPromises();expect(router.currentRoute.value.query.page).toBe('1');expect(fetchGuides).toHaveBeenLastCalledWith(expect.objectContaining({keyword:'山谷',page:1}))
 })
 it('卡片缺图有占位，身份链接独立且不嵌套，缺失旅行数据自然隐藏',async()=>{
  const {router}=await render();const w=mount(GuideCard,{props:{guide:note},global:{plugins:[router]}});wrappers.push(w)
  expect(w.find('a a').exists()).toBe(false);expect(w.text()).not.toContain('未知');expect(w.find('[data-test="card-placeholder"]').exists()).toBe(true)
 })
})
