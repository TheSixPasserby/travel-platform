import { beforeEach,expect,it,vi } from 'vitest'
import { flushPromises,mount } from '@vue/test-utils'
import { createMemoryHistory,createRouter,RouterView } from 'vue-router'
import { createPinia,setActivePinia } from 'pinia'
import AdminModerationView from '@/views/AdminModerationView.vue'
import * as api from '@/api/administration'
import { ApiError } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import type { AdminComment,GuideSummary } from '@/types/administration'
const time='2026-11-04T10:00:00.000+08:00'
const author={id:'9007199254740994',nickname:'溪谷旅人',avatarUrl:null,bio:null}
const guide:GuideSummary={id:'9007199254740993',title:'山谷里的清晨',summary:'沿溪慢行',coverImageUrl:null,author,destination:null,travelDays:null,budgetPerPerson:null,season:null,tags:[],status:'PUBLISHED',publishedAt:time,createdAt:time,updatedAt:time,likeCount:0,favoriteCount:0,commentCount:1}
const comment:AdminComment={id:'9007199254740995',guideId:guide.id,guideTitle:guide.title,author,content:'<script>window.x=1</script>\n出发要早一点。',status:'NORMAL',createdAt:time,updatedAt:time}
const page=<T,>(records:T[],extra={})=>({records,total:records.length,current:1,size:10,pages:records.length?1:0,...extra})
vi.mock('@/api/administration',()=>({fetchAdminGuides:vi.fn(),patchGuideStatus:vi.fn(),fetchAdminComments:vi.fn(),patchCommentStatus:vi.fn()}))
async function render(path='/admin/guides'){
  const pinia=createPinia();setActivePinia(pinia);const auth=useAuthStore();auth.token='test-token';auth.user={id:'1',username:'admin',nickname:'管理旅人',avatarUrl:null,bio:null,role:'ADMIN',status:'ACTIVE',createdAt:time}
  const stub={template:'<div />'},router=createRouter({history:createMemoryHistory(),routes:[{path:'/admin',name:'admin-home',component:stub},{path:'/admin/guides',name:'admin-guides',component:AdminModerationView,props:{resource:'guides'},meta:{requiresAuth:true,requiresAdmin:true}},{path:'/admin/comments',name:'admin-comments',component:AdminModerationView,props:{resource:'comments'},meta:{requiresAuth:true,requiresAdmin:true}},{path:'/admin/users',name:'admin-users',component:stub},{path:'/admin/ai',name:'admin-ai-settings',component:stub},{path:'/admin/destinations',name:'admin-destinations',component:stub},{path:'/guides',name:'guides',component:stub},{path:'/login',name:'login',component:stub}]})
  await router.push(path);await router.isReady();const wrapper=mount(RouterView,{global:{plugins:[pinia,router]}});await flushPromises();return {wrapper,router,auth}
}
beforeEach(()=>{
  vi.resetAllMocks();vi.mocked(api.fetchAdminGuides).mockResolvedValue(page([guide]));vi.mocked(api.fetchAdminComments).mockResolvedValue(page([comment]));vi.mocked(api.patchGuideStatus).mockResolvedValue({...guide,status:'HIDDEN'});vi.mocked(api.patchCommentStatus).mockResolvedValue({...comment,status:'HIDDEN'})
})
it('攻略使用真实服务端页码和状态搜索，筛选复位第一页',async()=>{
  vi.mocked(api.fetchAdminGuides).mockResolvedValueOnce(page([guide],{total:21,current:2,pages:3}));const {wrapper}=await render('/admin/guides?page=2&status=PUBLISHED')
  expect(api.fetchAdminGuides).toHaveBeenCalledWith(expect.objectContaining({page:2,size:10,status:'PUBLISHED'}))
  await wrapper.get('[data-test=moderation-keyword]').setValue('山谷');await wrapper.get('[data-test=moderation-status]').setValue('HIDDEN');await wrapper.get('[data-test=moderation-search]').trigger('submit');await flushPromises();expect(api.fetchAdminGuides).toHaveBeenLastCalledWith(expect.objectContaining({page:1,keyword:'山谷',status:'HIDDEN'}));wrapper.unmount()
})
it('草稿只能作者管理，后台不提供发布或隐藏动作',async()=>{
  vi.mocked(api.fetchAdminGuides).mockResolvedValueOnce(page([{...guide,status:'DRAFT',publishedAt:null}]));const {wrapper}=await render();expect(wrapper.get('[data-test="moderate-'+guide.id+'"]').attributes('disabled')).toBeDefined();await wrapper.get('[data-test="moderate-'+guide.id+'"]').trigger('click');expect(api.patchGuideStatus).not.toHaveBeenCalled();expect(wrapper.find('[role=alertdialog]').exists()).toBe(false);wrapper.unmount()
})
it('确认不可被另一行或筛选覆盖，只发送原目标字符串ID',async()=>{
  const other={...guide,id:'9007199254740996',title:'另一篇攻略'};vi.mocked(api.fetchAdminGuides).mockResolvedValueOnce(page([guide,other]));const {wrapper}=await render()
  await wrapper.get('[data-test="moderate-'+guide.id+'"]').trigger('click');expect(wrapper.get('[role=alertdialog]').text()).toContain(guide.title);expect(wrapper.get('[data-test="moderate-'+other.id+'"]').attributes('disabled')).toBeDefined();expect(wrapper.get('[data-test=moderation-keyword]').attributes('disabled')).toBeDefined()
  await wrapper.get('[data-test="moderate-'+other.id+'"]').trigger('click');await wrapper.get('[data-test=confirm-moderation]').trigger('click');await flushPromises();expect(api.patchGuideStatus).toHaveBeenCalledWith(guide.id,'HIDDEN');wrapper.unmount()
})
it('待响应不乐观改状态，失败保留确认可重试并防重',async()=>{
  let reject!:(error:Error)=>void;vi.mocked(api.patchGuideStatus).mockReturnValueOnce(new Promise((_,fail)=>{reject=fail}));const {wrapper}=await render();await wrapper.get('[data-test="moderate-'+guide.id+'"]').trigger('click');await wrapper.get('[data-test=confirm-moderation]').trigger('click');await wrapper.get('[data-test=confirm-moderation]').trigger('click');expect(api.patchGuideStatus).toHaveBeenCalledTimes(1);expect(wrapper.get('[data-test=moderation-row]').text()).toContain('已发布')
  reject(new ApiError('暂时无法治理',{status:503}));await flushPromises();expect(wrapper.get('[role=alert]').text()).toContain('暂时无法治理');expect(wrapper.find('[role=alertdialog]').exists()).toBe(true)
  vi.mocked(api.fetchAdminGuides).mockResolvedValueOnce(page([{...guide,status:'HIDDEN'}]));await wrapper.get('[data-test=confirm-moderation]').trigger('click');await flushPromises();expect(wrapper.get('[data-test=moderation-row]').text()).toContain('已隐藏');wrapper.unmount()
})
it('评论以纯文本显示，隐藏恢复调用评论状态接口',async()=>{
  const first=await render('/admin/comments');expect(first.wrapper.text()).toContain(comment.content);expect(first.wrapper.find('script,img[src=x]').exists()).toBe(false);await first.wrapper.get('[data-test="moderate-'+comment.id+'"]').trigger('click');await first.wrapper.get('[data-test=confirm-moderation]').trigger('click');await flushPromises();expect(api.patchCommentStatus).toHaveBeenCalledWith(comment.id,'HIDDEN');expect(api.patchGuideStatus).not.toHaveBeenCalled();first.wrapper.unmount()
  vi.mocked(api.fetchAdminComments).mockResolvedValueOnce(page([{...comment,status:'HIDDEN'}]));const second=await render('/admin/comments');await second.wrapper.get('[data-test="moderate-'+comment.id+'"]').trigger('click');await second.wrapper.get('[data-test=confirm-moderation]').trigger('click');await flushPromises();expect(api.patchCommentStatus).toHaveBeenLastCalledWith(comment.id,'NORMAL');second.wrapper.unmount()
})
it('加载错误可重试，越界空页有显式第一页入口',async()=>{
  vi.mocked(api.fetchAdminGuides).mockRejectedValueOnce(new ApiError('列表暂不可用',{status:503})).mockResolvedValueOnce(page([],{total:2,current:999,pages:1}));const {wrapper}=await render('/admin/guides?page=999');await wrapper.get('[data-test=retry-moderation]').trigger('click');await flushPromises();await wrapper.get('[data-test=moderation-first]').trigger('click');await flushPromises();expect(api.fetchAdminGuides).toHaveBeenLastCalledWith(expect.objectContaining({page:1}));wrapper.unmount()
})
it('非法评论状态URL重试不能静默扩大查询',async()=>{
  const {wrapper}=await render('/admin/comments?status=DRAFT');expect(api.fetchAdminComments).not.toHaveBeenCalled();expect(wrapper.find('[role=alert]').exists()).toBe(true);await wrapper.get('[data-test=retry-moderation]').trigger('click');await flushPromises();expect(api.fetchAdminComments).not.toHaveBeenCalled();wrapper.unmount()
})
it('资源切换后迟到攻略列表不能覆盖评论',async()=>{
  let finish!:(value:ReturnType<typeof page<GuideSummary>>)=>void;vi.mocked(api.fetchAdminGuides).mockReturnValueOnce(new Promise(resolve=>{finish=resolve}));const {wrapper,router}=await render();await router.push('/admin/comments');await flushPromises();finish(page([{...guide,title:'过期的攻略'}]));await flushPromises();expect(wrapper.get('h1').text()).toContain('评论');expect(wrapper.text()).toContain(comment.content);expect(wrapper.text()).not.toContain('过期的攻略');wrapper.unmount()
})
it('资源切换后旧写成功不刷新或覆盖新资源',async()=>{
  let finish!:(value:GuideSummary)=>void;vi.mocked(api.patchGuideStatus).mockReturnValueOnce(new Promise(resolve=>{finish=resolve}));const {wrapper,router}=await render();await wrapper.get('[data-test="moderate-'+guide.id+'"]').trigger('click');await wrapper.get('[data-test=confirm-moderation]').trigger('click');await router.push('/admin/comments');await flushPromises();finish({...guide,status:'HIDDEN'});await flushPromises();expect(wrapper.text()).toContain(comment.content);expect(api.fetchAdminGuides).toHaveBeenCalledTimes(1);expect(api.patchCommentStatus).not.toHaveBeenCalled();wrapper.unmount()
})
it('401保留原路径，卸载后的401不能导航公共页',async()=>{
  vi.mocked(api.fetchAdminGuides).mockRejectedValueOnce(new ApiError('会话失效',{status:401}));const first=await render('/admin/guides?status=PUBLISHED');expect(first.router.currentRoute.value.name).toBe('login');expect(first.router.currentRoute.value.query.redirect).toBe('/admin/guides?status=PUBLISHED');first.wrapper.unmount()
  let reject!:(error:Error)=>void;vi.mocked(api.fetchAdminGuides).mockReturnValueOnce(new Promise((_,fail)=>{reject=fail}));const next=await render();await next.router.push('/guides');await flushPromises();reject(new ApiError('失效',{status:401}));await flushPromises();expect(next.router.currentRoute.value.name).toBe('guides');next.wrapper.unmount()
})
it('账号变化后403必须隐藏前一管理员的列表',async()=>{
  const {wrapper,auth}=await render();vi.mocked(api.fetchAdminGuides).mockRejectedValueOnce(new ApiError('无权访问该资源',{status:403}));auth.user={id:'2',username:'reader',nickname:'旅人',avatarUrl:null,bio:null,role:'USER',status:'ACTIVE',createdAt:time};auth.token='reader-token';await flushPromises();expect(wrapper.get('[role=alert]').text()).toContain('无权访问');expect(wrapper.find('[data-test=moderation-row]').exists()).toBe(false);wrapper.unmount()
})
it('治理后刷新只使用URL已应用的条件，未提交筛选不生效',async()=>{
  const {wrapper,router}=await render('/admin/guides?keyword=山谷&status=PUBLISHED');await wrapper.get('[data-test=moderation-keyword]').setValue('尚未提交');await wrapper.get('[data-test=moderation-status]').setValue('HIDDEN')
  await wrapper.get('[data-test="moderate-'+guide.id+'"]').trigger('click');await wrapper.get('[data-test=confirm-moderation]').trigger('click');await flushPromises()
  expect(api.fetchAdminGuides).toHaveBeenLastCalledWith(expect.objectContaining({keyword:'山谷',status:'PUBLISHED'}));expect(router.currentRoute.value.query.status).toBe('PUBLISHED');wrapper.unmount()
})
