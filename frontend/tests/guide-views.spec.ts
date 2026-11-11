import { clearFeedCache } from '@/composables/useInfiniteFeed'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import GuidesView from '@/views/GuidesView.vue'
import GuideDetailView from '@/views/GuideDetailView.vue'
import MyGuidesView from '@/views/MyGuidesView.vue'
import GuideEditorView from '@/views/GuideEditorView.vue'
import ImageUpload from '@/components/ImageUpload.vue'
import GuideActions from '@/components/GuideActions.vue'
import GuideComments from '@/components/GuideComments.vue'
import * as api from '@/api/guides'
import * as interactions from '@/api/interactions'
import { fetchDestinations, fetchDestination } from '@/api/destinations'
import { ApiError } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import type { GuideDetail, GuideSummary } from '@/types/guide'

vi.mock('@/api/guides', () => ({ fetchGuideFeed:vi.fn(),fetchRelatedGuides:vi.fn(),fetchGuides:vi.fn(),fetchGuide:vi.fn(),fetchOwnGuide:vi.fn(),fetchMyGuides:vi.fn(),fetchTags:vi.fn(),createGuide:vi.fn(),updateGuide:vi.fn(),deleteGuide:vi.fn(),publishGuide:vi.fn(),unpublishGuide:vi.fn() }))
vi.mock('@/api/destinations', () => ({ fetchDestinations:vi.fn(),fetchDestination:vi.fn() }))
vi.mock('@/api/interactions',()=>({ fetchInteraction:vi.fn(),fetchComments:vi.fn(),setGuideLike:vi.fn(),setGuideFavorite:vi.fn(),createComment:vi.fn(),deleteComment:vi.fn() }))
const guide: GuideDetail = { id:'9007199254740993',title:'山谷里的清晨',summary:'沿溪慢行',coverImageUrl:null,
  author:{ id:'1',nickname:'山间旅人',avatarUrl:null,bio:'喜欢山野' },destination:null,travelDays:2,budgetPerPerson:'0.10',season:'春',tags:[{ id:'1',name:'自然' }],
  status:'PUBLISHED',publishedAt:'2026-11-03T10:00:00.000+08:00',createdAt:'2026-11-03T09:00:00.000+08:00',updatedAt:'2026-11-03T10:00:00.000+08:00',
  likeCount:0,favoriteCount:0,commentCount:0,content:'# 山谷\n\n<script>alert(1)</script>',images:[],scenicSpots:[] }
const page = (records:GuideSummary[],extra={}) => ({ records,total:records.length,current:1,size:9,pages:records.length?1:0,...extra })
async function render(component: typeof GuidesView, path:string, loggedIn=false) {
  const pinia=createPinia();setActivePinia(pinia);const auth=useAuthStore()
  if(loggedIn){auth.token='test-token';auth.user={ id:'1',username:'luna',nickname:'山间旅人',avatarUrl:null,bio:null,role:'USER',status:'ACTIVE',createdAt:guide.createdAt }}
  const stub={ template:'<div />' }
  const router=createRouter({ history:createMemoryHistory(),routes:[
    { path:'/',name:'home',component:stub },{ path:'/guides',name:'guides',component:GuidesView },{ path:'/guides/new',name:'guide-new',component:GuideEditorView },
    { path:'/guides/:id',name:'guide-detail',component:GuideDetailView },{ path:'/guides/:id/edit',name:'guide-edit',component:GuideEditorView },
    { path:'/me/guides',name:'my-guides',component:MyGuidesView },{ path:'/login',name:'login',component:stub },
    { path:'/destinations/:id',name:'destination-detail',component:stub }
  ] })
  await router.push(path);await router.isReady()
  expect(router.currentRoute.value.matched.at(-1)?.components?.default).toBe(component)
  const wrapper=mount(RouterView,{ global:{ plugins:[pinia,router] } });await flushPromises();return { wrapper,router,auth }
}
beforeEach(() => {
  clearFeedCache();vi.resetAllMocks();vi.mocked(api.fetchGuideFeed).mockResolvedValue({records:[guide],nextCursor:null,hasMore:false});vi.mocked(api.fetchRelatedGuides).mockResolvedValue([]);vi.mocked(api.fetchGuides).mockResolvedValue(page([guide]));vi.mocked(api.fetchMyGuides).mockResolvedValue(page([guide]));
  vi.mocked(api.fetchGuide).mockResolvedValue(structuredClone(guide));vi.mocked(api.fetchOwnGuide).mockResolvedValue(guide);vi.mocked(api.fetchTags).mockResolvedValue(guide.tags);
  vi.mocked(interactions.fetchInteraction).mockResolvedValue({ guideId:guide.id,liked:false,favorited:false,likeCount:0,favoriteCount:0,commentCount:0 });
  vi.mocked(interactions.fetchComments).mockResolvedValue({ records:[],total:0,current:1,size:10,pages:0 });
  vi.mocked(api.createGuide).mockResolvedValue({ ...guide,status:'DRAFT',publishedAt:null });vi.mocked(api.updateGuide).mockResolvedValue(guide);
  vi.mocked(api.publishGuide).mockResolvedValue(guide);vi.mocked(api.unpublishGuide).mockResolvedValue({ ...guide,status:'DRAFT' });vi.mocked(api.deleteGuide).mockResolvedValue(null);
  vi.mocked(fetchDestinations).mockResolvedValue({ records:[],total:0,current:1,size:50,pages:0 });
  vi.mocked(fetchDestination).mockRejectedValue(new ApiError('资源不存在',{ status:404 }));
})

describe('攻略浏览与编辑', () => {
  it('主页草稿入口按URL状态读取本人草稿',async()=>{
    const {wrapper}=await render(MyGuidesView,'/me/guides?status=DRAFT',true)
    expect((wrapper.get('[data-test="my-guide-status"]').element as HTMLSelectElement).value).toBe('DRAFT')
    expect(api.fetchMyGuides).toHaveBeenLastCalledWith(expect.objectContaining({status:'DRAFT',page:1}))
    wrapper.unmount()
  })
  it('评论更新后，互动返回的旧评论计数不能回退父页',async()=>{
    const { wrapper }=await render(GuideDetailView,'/guides/'+guide.id,true)
    wrapper.getComponent(GuideComments).vm.$emit('count-changed',2);await flushPromises()
    wrapper.getComponent(GuideActions).vm.$emit('change',{ guideId:guide.id,liked:true,favorited:true,likeCount:3,favoriteCount:4,commentCount:0 });await flushPromises()
    expect(wrapper.get('.article-author').text()).toContain('3 赞 · 4 收藏 · 2 评论')
    expect(wrapper.getComponent(GuideActions).props('commentCount')).toBe(2)
    wrapper.unmount()
  })
  it('历史页码链接从首批连续流开始，公开列表没有分页控件',async()=>{
    const { wrapper }=await render(GuidesView,'/guides?page=999')
    expect(api.fetchGuideFeed).toHaveBeenCalledWith(expect.objectContaining({size:12}),expect.anything())
    expect(wrapper.find('[data-test="guide-card"]').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('上一页');expect(wrapper.text()).not.toContain('下一页')
    wrapper.unmount()
  })

  it('未保存修改时拒绝离开，并支持确认放弃',async()=>{
    const {wrapper,router}=await render(GuideEditorView,'/guides/'+guide.id+'/edit',true)
    await wrapper.get('[data-test="guide-title"]').setValue('未保存的标题')
    const confirm=vi.spyOn(window,'confirm').mockReturnValue(false)
    await router.push('/me/guides');expect(router.currentRoute.value.name).toBe('guide-edit');expect(confirm).toHaveBeenCalled()
    confirm.mockReturnValue(true);await router.push('/me/guides');expect(router.currentRoute.value.name).toBe('my-guides')
    confirm.mockRestore();wrapper.unmount()
  })
  it('上传期间阻止离开编辑页，但允许会话失效前往登录',async()=>{
    const { wrapper,router }=await render(GuideEditorView,'/guides/'+guide.id+'/edit',true)
    wrapper.findAllComponents(ImageUpload)[0]!.vm.$emit('busy',true);await flushPromises()
    await router.push('/me/guides');await flushPromises()
    expect(router.currentRoute.value.name).toBe('guide-edit')
    expect(wrapper.text()).toContain('请稍后切换页面')
    await router.push({ name:'login',query:{ redirect:'/guides/'+guide.id+'/edit' } })
    expect(router.currentRoute.value.name).toBe('login')
    wrapper.unmount()
  })
  it('独立正文上传先返回URL后解除busy仍能插图并关联图库',async()=>{
    const {wrapper}=await render(GuideEditorView,'/guides/'+guide.id+'/edit',true)
    const upload=wrapper.findAllComponents(ImageUpload)[1]!
    upload.vm.$emit('busy',true);await flushPromises()
    const url='/uploads/2026/11/0123456789abcdef0123456789abcdef.png'
    upload.vm.$emit('update:modelValue',url);upload.vm.$emit('busy',false);await flushPromises()
    expect((wrapper.get('[data-test="guide-content"]').element as HTMLTextAreaElement).value).toContain(url)
    await wrapper.get('[data-test="save-guide"]').trigger('click');await flushPromises()
    expect(api.updateGuide).toHaveBeenCalledWith(guide.id,expect.objectContaining({images:[{url,altText:'旅行影像'}]}))
    wrapper.unmount()
  })
  it('正文上传会话失效保留当前未保存文字',async()=>{
    const {wrapper,router}=await render(GuideEditorView,'/guides/'+guide.id+'/edit',true)
    await wrapper.get('[data-test="guide-title"]').setValue('必须保留的标题')
    wrapper.findAllComponents(ImageUpload)[1]!.vm.$emit('session-expired');await flushPromises()
    expect(router.currentRoute.value.name).toBe('guide-edit');expect(wrapper.text()).toContain('未保存内容已保留')
    expect((wrapper.get('[data-test="guide-title"]').element as HTMLInputElement).value).toBe('必须保留的标题');wrapper.unmount()
  })
  it('旧攻略加载结果不能覆盖后来打开的编辑对象',async()=>{
    let release!:(value:GuideDetail)=>void
    vi.mocked(api.fetchOwnGuide).mockReturnValueOnce(new Promise(resolve=>{release=resolve}))
      .mockResolvedValueOnce({ ...guide,id:'9007199254740994',title:'新的山谷' })
    const { wrapper,router }=await render(GuideEditorView,'/guides/'+guide.id+'/edit',true)
    await router.push('/guides/9007199254740994/edit');await flushPromises()
    release(guide);await flushPromises()
    expect((wrapper.get('[data-test="guide-title"]').element as HTMLInputElement).value).toBe('新的山谷')
    wrapper.unmount()
  })
  it('撤回发布不丢失当前未保存的正文',async()=>{
    const { wrapper }=await render(GuideEditorView,'/guides/'+guide.id+'/edit',true)
    await wrapper.get('[data-test="guide-content"]').setValue('仍在编辑的正文')
    const button=wrapper.findAll('button').find(item=>item.text()==='撤回为草稿')!
    await button.trigger('click');await flushPromises()
    expect((wrapper.get('[data-test="guide-content"]').element as HTMLTextAreaElement).value).toBe('仍在编辑的正文')
    expect(api.updateGuide).not.toHaveBeenCalled()
    expect(wrapper.get('[data-test="save-guide"]').text()).toBe('保存草稿')
    wrapper.unmount()
  })
  it('编辑图库图片读取失败提供可见的文字占位',async()=>{
    vi.mocked(api.fetchOwnGuide).mockResolvedValue({ ...guide,images:[{ url:'/uploads/2026/11/0123456789abcdef0123456789abcdef.png',altText:'溪谷',sortOrder:0 }] })
    const { wrapper }=await render(GuideEditorView,'/guides/'+guide.id+'/edit',true)
    await wrapper.get('img').trigger('error');await flushPromises()
    expect(wrapper.text()).toContain('图片暂时无法显示')
    wrapper.unmount()
  })

  it('列表使用服务端游标，筛选复位并保留字符串ID', async () => {
    vi.mocked(api.fetchGuides).mockResolvedValue(page([guide],{ total:19,current:2,pages:3 }))
    const { wrapper }=await render(GuidesView,'/guides?page=2')
    expect(api.fetchGuideFeed).toHaveBeenCalledWith(expect.objectContaining({size:12}),expect.anything())
    expect(wrapper.get('[data-test="guide-card"]').attributes('href')).toBe('/guides/9007199254740993')
    await wrapper.get('[data-test="guide-keyword"]').setValue('山谷')
    await wrapper.get('[data-test="guide-search"]').trigger('submit');await flushPromises()
    expect(api.fetchGuideFeed).toHaveBeenLastCalledWith(expect.objectContaining({keyword:'山谷'}),expect.anything())
    wrapper.unmount()
  })
  it('列表错误可重试，空结果不制造卡片', async () => {
    vi.mocked(api.fetchGuideFeed).mockRejectedValueOnce(new ApiError('暂时无法加载',{ status:503 }))
    const { wrapper }=await render(GuidesView,'/guides')
    expect(wrapper.get('[role="alert"]').text()).toContain('暂时无法加载')
    vi.mocked(api.fetchGuideFeed).mockResolvedValueOnce({records:[],nextCursor:null,hasMore:false})
    await wrapper.get('[data-test="retry-guides"]').trigger('click');await flushPromises()
    expect(wrapper.text()).toContain('还没有找到攻略');expect(wrapper.find('[data-test="guide-card"]').exists()).toBe(false)
    wrapper.unmount()
  })
  it('详情安全展示正文、精确金额和作者编辑入口', async () => {
    const { wrapper }=await render(GuideDetailView,'/guides/'+guide.id,true)
    expect(api.fetchGuide).toHaveBeenCalledWith(guide.id)
    expect(wrapper.find('script').exists()).toBe(false);expect(wrapper.text()).toContain('0.10')
    expect(wrapper.get('[data-test="edit-guide"]').attributes('href')).toBe('/guides/'+guide.id+'/edit')
    wrapper.unmount()
  })
  it('私有/隐藏内容的公开404显示可返回入口', async () => {
    vi.mocked(api.fetchGuide).mockRejectedValueOnce(new ApiError('请求的资源不存在',{ status:404,code:40401 }))
    const { wrapper }=await render(GuideDetailView,'/guides/'+guide.id)
    expect(wrapper.get('[role="alert"]').text()).toContain('请求的资源不存在')
    expect(wrapper.find('[data-test="edit-guide"]').exists()).toBe(false)
    wrapper.unmount()
  })
  it('我的攻略包含草稿和隐藏状态，筛选仍服务端执行', async () => {
    vi.mocked(api.fetchMyGuides).mockResolvedValue(page([{ ...guide,status:'HIDDEN' }]))
    const { wrapper }=await render(MyGuidesView,'/me/guides',true)
    expect(wrapper.text()).toContain('已隐藏')
    await wrapper.get('[data-test="my-guide-status"]').setValue('DRAFT');await flushPromises()
    expect(api.fetchMyGuides).toHaveBeenLastCalledWith(expect.objectContaining({ status:'DRAFT',page:1 }))
    wrapper.unmount()
  })
  it('新草稿提交完整白名单，预算保持字符串', async () => {
    const { wrapper }=await render(GuideEditorView,'/guides/new',true)
    await wrapper.get('[data-test="guide-title"]').setValue('新的清晨')
    await wrapper.get('[data-test="guide-budget"]').setValue('0.10')
    await wrapper.get('[data-test="save-guide"]').trigger('click');await flushPromises()
    expect(api.createGuide).toHaveBeenCalledWith(expect.objectContaining({ title:'新的清晨',content:'',budgetPerPerson:'0.10',status:'DRAFT',tagIds:[],images:[] }))
    expect(Object.keys(vi.mocked(api.createGuide).mock.calls[0]![0]).sort()).toEqual(['title','content','summary','destinationId','coverImageUrl','travelDays','budgetPerPerson','season','tagIds','images','scenicSpotIds','status'].sort())
    wrapper.unmount()
  })
  it('编辑先完整保存再发布，PUT不夹带状态和作者ID', async () => {
    vi.mocked(api.fetchOwnGuide).mockResolvedValue({ ...guide,status:'DRAFT',publishedAt:null })
    const { wrapper }=await render(GuideEditorView,'/guides/'+guide.id+'/edit',true)
    await wrapper.get('[data-test="guide-title"]').setValue('修改后的山谷')
    await wrapper.get('[data-test="publish-guide"]').trigger('click');await flushPromises()
    expect(api.updateGuide).toHaveBeenCalledWith(guide.id,expect.objectContaining({ title:'修改后的山谷',budgetPerPerson:'0.10' }))
    expect(vi.mocked(api.updateGuide).mock.calls[0]![1]).not.toHaveProperty('status')
    expect(vi.mocked(api.updateGuide).mock.calls[0]![1]).not.toHaveProperty('authorId')
    expect(vi.mocked(api.updateGuide).mock.invocationCallOrder[0]).toBeLessThan(vi.mocked(api.publishGuide).mock.invocationCallOrder[0]!)
    wrapper.unmount()
  })
  it('保存失败保持编辑内容，上传期间阻止提交', async () => {
    vi.mocked(api.updateGuide).mockRejectedValueOnce(new ApiError('保存暂时失败',{ status:503 }))
    const { wrapper }=await render(GuideEditorView,'/guides/'+guide.id+'/edit',true)
    await wrapper.get('[data-test="guide-title"]').setValue('不能丢失的文字')
    wrapper.findAllComponents(ImageUpload)[0]!.vm.$emit('busy',true);await flushPromises()
    await wrapper.get('[data-test="save-guide"]').trigger('click');expect(api.updateGuide).not.toHaveBeenCalled()
    wrapper.findAllComponents(ImageUpload)[0]!.vm.$emit('busy',false);await flushPromises()
    await wrapper.get('[data-test="save-guide"]').trigger('click');await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('保存暂时失败')
    expect((wrapper.get('[data-test="guide-title"]').element as HTMLInputElement).value).toBe('不能丢失的文字')
    wrapper.unmount()
  })
  it('HIDDEN只允许私有查看和删除，不开放作者编辑解禁', async () => {
    vi.mocked(api.fetchOwnGuide).mockResolvedValue({ ...guide,status:'HIDDEN' })
    const { wrapper }=await render(GuideEditorView,'/guides/'+guide.id+'/edit',true)
    expect(wrapper.get('[data-test="guide-title"]').attributes('disabled')).toBeDefined()
    expect(wrapper.find('[data-test="publish-guide"]').exists()).toBe(false)
    await wrapper.get('[data-test="delete-guide"]').trigger('click')
    expect(api.deleteGuide).not.toHaveBeenCalled()
    await wrapper.get('[data-test="confirm-delete-guide"]').trigger('click');await flushPromises()
    expect(api.deleteGuide).toHaveBeenCalledWith(guide.id)
    wrapper.unmount()
  })
  it('编辑读取401回到登录且保留完整回跳', async () => {
    vi.mocked(api.fetchOwnGuide).mockRejectedValueOnce(new ApiError('登录状态已失效',{ status:401 }))
    const { router,wrapper }=await render(GuideEditorView,'/guides/'+guide.id+'/edit',true)
    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/guides/'+guide.id+'/edit')
    wrapper.unmount()
  })
})
