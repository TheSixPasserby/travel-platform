import { beforeEach,expect,it,vi } from 'vitest'
import { flushPromises,mount } from '@vue/test-utils'
import { createPinia,setActivePinia } from 'pinia'
import { createMemoryHistory,createRouter } from 'vue-router'
import GuideComments from '@/components/GuideComments.vue'
import { fetchComments,createComment,deleteComment } from '@/api/interactions'
import { ApiError } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import type { GuideComment } from '@/types/interaction'
vi.mock('@/api/interactions',()=>({ fetchComments:vi.fn(),createComment:vi.fn(),deleteComment:vi.fn() }))
const id='9007199254740993'
const comment:GuideComment={ id:'9007199254740994',guideId:id,author:{ id:'1',nickname:'旅人',avatarUrl:null,bio:null },content:'<img src=x onerror=alert(1)>',createdAt:'2026-11-03T10:00:00.000+08:00',updatedAt:'2026-11-03T10:00:00.000+08:00' }
const page=(records:GuideComment[],extra={})=>({ records,total:records.length,current:1,size:10,pages:records.length?1:0,...extra })
async function render(authenticated=false){
  const pinia=createPinia();setActivePinia(pinia);const auth=useAuthStore()
  if(authenticated){auth.token='test-token';auth.user={ id:'1',username:'luna',nickname:'旅人',avatarUrl:null,bio:null,role:'USER',status:'ACTIVE',createdAt:comment.createdAt }}
  const router=createRouter({ history:createMemoryHistory(),routes:[{ path:'/guides/:id',name:'guide-detail',component:{ template:'<div />' } },{ path:'/login',name:'login',component:{ template:'<div />' } }] })
  await router.push('/guides/'+id);await router.isReady()
  const wrapper=mount(GuideComments,{ props:{ guideId:id },global:{ plugins:[pinia,router] } });await flushPromises();return { wrapper,auth,router }
}
beforeEach(()=>{vi.resetAllMocks();vi.mocked(fetchComments).mockResolvedValue(page([comment]));vi.mocked(createComment).mockResolvedValue(comment);vi.mocked(deleteComment).mockResolvedValue(null)})
it('展示用户名、属地和按真实ID判断的作者标记',async()=>{
  vi.mocked(fetchComments).mockResolvedValueOnce(page([
    {...comment,author:{...comment.author,username:'forest'},ipRegion:'四川'},
    {...comment,id:'22',author:{...comment.author,id:'2',username:'cloud'},ipRegion:null}
  ]))
  const {wrapper}=await render();await wrapper.setProps({guideAuthorId:'1'})
  const rows=wrapper.findAll('[data-test="comment-row"]')
  expect(rows[0].text()).toContain('@forest');expect(rows[0].text()).not.toContain('IP 属地')
  expect(rows[0].find('[data-test="guide-author-badge"]').exists()).toBe(true)
  expect(rows[1].text()).not.toContain('IP 属地');expect(rows[1].find('[data-test="guide-author-badge"]').exists()).toBe(false)
  wrapper.unmount()
})
it('显示规范化后的码点长度，超限不发送且保留输入',async()=>{
  const { wrapper }=await render(true);const text=' '+ '🌿'.repeat(2001)+'\u00A0'
  await wrapper.get('textarea').setValue(text);expect(wrapper.text()).toContain('2001 / 2000')
  await wrapper.get('[data-test="comment-form"]').trigger('submit');await flushPromises()
  expect(createComment).not.toHaveBeenCalled();expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe(text);wrapper.unmount()
})
it('用户切换时不把前一用户未提交文字带给新用户',async()=>{
  const { wrapper,auth }=await render(true);await wrapper.get('textarea').setValue('前一用户的文字')
  auth.user={ ...auth.user!,id:'2',username:'other' };auth.token='other-token';await flushPromises()
  expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('');wrapper.unmount()
})
it('匿名安全阅读纯文本与真实服务端分页',async()=>{
  vi.mocked(fetchComments).mockResolvedValueOnce(page([comment],{ total:11,pages:2 }))
  const { wrapper }=await render()
  expect(wrapper.text()).toContain(comment.content);expect(wrapper.find('[onerror],script').exists()).toBe(false)
  expect(wrapper.findAll('img')).toHaveLength(0)
  vi.mocked(fetchComments).mockResolvedValueOnce(page([{ ...comment,id:'9007199254740995',content:'第二页' }],{ total:11,current:2,pages:2 }))
  await wrapper.get('[data-test="comments-next"]').trigger('click');await flushPromises()
  expect(fetchComments).toHaveBeenLastCalledWith(id,{ page:2,size:10 });expect(wrapper.text()).toContain('第二页')
  wrapper.unmount()
})
it('评论成功后才清空输入，规范化并刷新第一页计数',async()=>{
  vi.mocked(fetchComments).mockResolvedValueOnce(page([])).mockResolvedValueOnce(page([comment]))
  const { wrapper }=await render(true)
  await wrapper.get('textarea').setValue(' \u00A0建议\r\n很好\uFEFF ')
  await wrapper.get('[data-test="comment-form"]').trigger('submit');await flushPromises()
  expect(createComment).toHaveBeenCalledWith(id,{ content:'建议\n很好' })
  expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('')
  expect(wrapper.emitted('count-changed')?.at(-1)).toEqual([1]);wrapper.unmount()
})
it('重复提交被pending拦截，失败保留原输入并可重试',async()=>{
  let fail!:(error:Error)=>void
  vi.mocked(createComment).mockReturnValueOnce(new Promise((_resolve,reject)=>{fail=reject}))
  const { wrapper,auth }=await render(true);await wrapper.get('textarea').setValue('不能丢失的文字')
  await wrapper.get('[data-test="comment-form"]').trigger('submit');await wrapper.get('[data-test="comment-form"]').trigger('submit')
  expect(createComment).toHaveBeenCalledTimes(1)
  fail(new ApiError('评论暂时无法发布',{ status:503 }));await flushPromises()
  expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('不能丢失的文字');expect(auth.isAuthenticated).toBe(true)
  expect(wrapper.text()).toContain('评论暂时无法发布')
  await wrapper.get('[data-test="comment-form"]').trigger('submit');await flushPromises();expect(createComment).toHaveBeenCalledTimes(2)
  wrapper.unmount()
})
it('401清理会话后公共页面不跳走，草稿仍可复制',async()=>{
  const { wrapper,auth,router }=await render(true)
  vi.mocked(createComment).mockImplementationOnce(async()=>{auth.clearSession();throw new ApiError('登录状态已失效',{ status:401 })})
  await wrapper.get('textarea').setValue('仍在这里的草稿');await wrapper.get('[data-test="comment-form"]').trigger('submit');await flushPromises()
  expect(router.currentRoute.value.name).toBe('guide-detail');expect(auth.user).toBeNull()
  expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('仍在这里的草稿')
  expect(wrapper.text()).toContain('登录');wrapper.unmount()
})
it('只展示本人的删除入口，明确确认后才删除',async()=>{
  const other={ ...comment,id:'9007199254740995',author:{ ...comment.author,id:'2' },content:'另一位旅人' }
  vi.mocked(fetchComments).mockResolvedValueOnce(page([comment,other])).mockResolvedValueOnce(page([other]))
  const { wrapper }=await render(true)
  expect(wrapper.find('[data-test="delete-comment-'+other.id+'"]').exists()).toBe(false)
  await wrapper.get('[data-test="delete-comment-'+comment.id+'"]').trigger('click');expect(deleteComment).not.toHaveBeenCalled()
  await wrapper.get('[data-test="confirm-delete-comment-'+comment.id+'"]').trigger('click');await flushPromises()
  expect(deleteComment).toHaveBeenCalledWith(comment.id);expect(wrapper.emitted('count-changed')?.at(-1)).toEqual([1]);wrapper.unmount()
})
it('列表故障可重试，空状态不制造示例评论',async()=>{
  vi.mocked(fetchComments).mockRejectedValueOnce(new ApiError('评论暂时无法读取',{ status:503 })).mockResolvedValueOnce(page([]))
  const { wrapper }=await render();expect(wrapper.get('[role="alert"]').text()).toContain('评论暂时无法读取')
  await wrapper.get('[data-test="retry-comments"]').trigger('click');await flushPromises()
  expect(wrapper.text()).toContain('还没有评论');expect(wrapper.find('[data-test="comment-row"]').exists()).toBe(false);wrapper.unmount()
})
it('迟到的旧攻略评论不覆盖新列表或新计数',async()=>{
  let finish!:(value:ReturnType<typeof page>)=>void
  vi.mocked(fetchComments).mockReturnValueOnce(new Promise(resolve=>{finish=resolve})).mockResolvedValueOnce(page([{ ...comment,guideId:'9007199254740999',content:'新页' }]))
  const { wrapper }=await render();await wrapper.setProps({ guideId:'9007199254740999' });await flushPromises()
  finish(page([{ ...comment,content:'旧页' }],{ total:99 }));await flushPromises()
  expect(wrapper.text()).toContain('新页');expect(wrapper.text()).not.toContain('旧页');expect(wrapper.emitted('count-changed')?.at(-1)).toEqual([1]);wrapper.unmount()
})
