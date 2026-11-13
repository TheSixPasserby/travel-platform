import { beforeEach,expect,it,vi } from 'vitest'
import { flushPromises,mount } from '@vue/test-utils'
import { createPinia,setActivePinia } from 'pinia'
import { createMemoryHistory,createRouter } from 'vue-router'
import GuideComments from '@/components/GuideComments.vue'
import { createComment,deleteComment,fetchCommentLocation,fetchComments,fetchMentionSuggestions,fetchReplies } from '@/api/interactions'
import { ApiError } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import type { GuideComment } from '@/types/interaction'
vi.mock('@/api/interactions',()=>({ fetchComments:vi.fn(),createComment:vi.fn(),deleteComment:vi.fn(),fetchReplies:vi.fn(),fetchCommentLocation:vi.fn(),fetchMentionSuggestions:vi.fn() }))
const guideId='9007199254740993'
const author={ id:'2',username:'cloud',nickname:'云上行',avatarUrl:null,bio:null }
const root:GuideComment={ id:'root-1',guideId,rootId:null,replyToId:null,replyToAuthor:null,replyCount:3,author,content:'杭州的清晨值得早起',createdAt:'2026-11-03T10:00:00.000+08:00',updatedAt:'2026-11-03T10:00:00.000+08:00' }
const reply=(overrides:Partial<GuideComment>={}):GuideComment=>({ id:'reply-1',guideId,rootId:root.id,replyToId:root.id,replyToAuthor:author,mentions:[],author:{ id:'3',username:'fern',nickname:'蕨类',avatarUrl:null,bio:null },content:'同感，六点最好',createdAt:'2026-11-03T11:00:00.000+08:00',updatedAt:'2026-11-03T11:00:00.000+08:00',...overrides })
const page=(records:GuideComment[],extra={})=>({ records,total:records.length,current:1,size:10,pages:records.length?1:0,...extra })
async function render(authenticated=true,props:Record<string,unknown>={}){
  const pinia=createPinia();setActivePinia(pinia);const auth=useAuthStore()
  if(authenticated){auth.token='test-token';auth.user={ id:'1',username:'luna',nickname:'小鹿',avatarUrl:null,bio:null,role:'USER',status:'ACTIVE',createdAt:root.createdAt }}
  const router=createRouter({ history:createMemoryHistory(),routes:[{ path:'/guides/:id',name:'guide-detail',component:{ template:'<div />' } },{ path:'/login',name:'login',component:{ template:'<div />' } }] })
  await router.push('/guides/'+guideId);await router.isReady()
  const wrapper=mount(GuideComments,{ props:{ guideId,...props },global:{ plugins:[pinia,router] } });await flushPromises();return { wrapper,auth,router }
}
beforeEach(()=>{
  vi.resetAllMocks()
  vi.mocked(fetchComments).mockResolvedValue(page([root]))
  vi.mocked(fetchReplies).mockResolvedValue(page([reply()]))
  vi.mocked(createComment).mockResolvedValue(reply({ content:'新内容' }))
  vi.mocked(deleteComment).mockResolvedValue(null)
  vi.mocked(fetchMentionSuggestions).mockResolvedValue([])
  vi.mocked(fetchCommentLocation).mockResolvedValue({ guideId,rootId:root.id,rootPage:1,replyPage:1 })
})
it('根评论下按服务端分页加载回复并展开收起',async()=>{
  vi.mocked(fetchReplies).mockResolvedValueOnce(page([reply()],{ total:12,pages:2 })).mockResolvedValueOnce(page([reply({ id:'reply-11',content:'第二页回复' })],{ total:12,current:2,pages:2 }))
  const { wrapper }=await render()
  expect(fetchReplies).not.toHaveBeenCalled()
  await wrapper.get('[data-test="toggle-replies-'+root.id+'"]').trigger('click');await flushPromises()
  expect(fetchReplies).toHaveBeenCalledWith(root.id,{ page:1,size:10 })
  expect(wrapper.text()).toContain('同感，六点最好')
  await wrapper.get('[data-test="replies-next-'+root.id+'"]').trigger('click');await flushPromises()
  expect(fetchReplies).toHaveBeenLastCalledWith(root.id,{ page:2,size:10 })
  expect(wrapper.text()).toContain('第二页回复')
  await wrapper.get('[data-test="toggle-replies-'+root.id+'"]').trigger('click');await flushPromises()
  expect(wrapper.find('[data-test="reply-list-'+root.id+'"]').exists()).toBe(false)
  wrapper.unmount()
})
it('回复加载失败可重试，不伪造示例回复',async()=>{
  vi.mocked(fetchReplies).mockRejectedValueOnce(new ApiError('回复暂时无法读取',{ status:503 })).mockResolvedValueOnce(page([reply()]))
  const { wrapper }=await render()
  await wrapper.get('[data-test="toggle-replies-'+root.id+'"]').trigger('click');await flushPromises()
  expect(wrapper.get('[data-test="reply-list-'+root.id+'"]').text()).toContain('回复暂时无法读取')
  expect(wrapper.find('[data-test="reply-row"]').exists()).toBe(false)
  await wrapper.get('[data-test="retry-replies-'+root.id+'"]').trigger('click');await flushPromises()
  expect(wrapper.text()).toContain('同感，六点最好')
  wrapper.unmount()
})
it('本人回复可删除，他人回复不展示删除入口',async()=>{
  vi.mocked(fetchReplies).mockResolvedValueOnce(page([
    reply({ id:'reply-mine',author:{ id:'1',username:'luna',nickname:'小鹿',avatarUrl:null,bio:null } }),
    reply({ id:'reply-other' })
  ]))
  const { wrapper }=await render()
  await wrapper.get('[data-test="toggle-replies-'+root.id+'"]').trigger('click');await flushPromises()
  expect(wrapper.find('[data-test="delete-comment-reply-other"]').exists()).toBe(false)
  await wrapper.get('[data-test="delete-comment-reply-mine"]').trigger('click');expect(deleteComment).not.toHaveBeenCalled()
  await wrapper.get('[data-test="confirm-delete-comment-reply-mine"]').trigger('click');await flushPromises()
  expect(deleteComment).toHaveBeenCalledWith('reply-mine')
  wrapper.unmount()
})
it('回复具体用户时携带 replyToId 与回复对象提示',async()=>{
  const { wrapper }=await render()
  await wrapper.get('[data-test="reply-comment-'+root.id+'"]').trigger('click');await flushPromises()
  expect(wrapper.get('[data-test="reply-target"]').text()).toContain('云上行')
  await wrapper.get('textarea').setValue('想问问几点出发')
  await wrapper.get('[data-test="comment-form"]').trigger('submit');await flushPromises()
  expect(createComment).toHaveBeenCalledWith(guideId,{ content:'想问问几点出发',replyToId:root.id })
  expect(wrapper.find('[data-test="reply-target"]').exists()).toBe(false)
  wrapper.unmount()
})
it('@ 选择用户插入正文并发送 mentionUserIds，可移除 chip',async()=>{
  vi.mocked(fetchMentionSuggestions).mockResolvedValue([{ id:'3',username:'fern',nickname:'蕨类',avatarUrl:null,bio:null }])
  const { wrapper }=await render()
  await wrapper.get('[data-test="mention-toggle"]').trigger('click')
  await wrapper.get('[data-test="mention-search"]').setValue('fer');await flushPromises()
  expect(fetchMentionSuggestions).toHaveBeenLastCalledWith('fer')
  await wrapper.get('[data-test="mention-option-3"]').trigger('click');await flushPromises()
  expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toContain('@fern')
  expect(wrapper.get('[data-test="mention-chip-3"]').text()).toContain('蕨类')
  await wrapper.get('textarea').setValue((wrapper.get('textarea').element as HTMLTextAreaElement).value+' 想问下路线')
  await wrapper.get('[data-test="comment-form"]').trigger('submit');await flushPromises()
  expect(createComment).toHaveBeenCalledWith(guideId,{ content:'@fern 想问下路线',mentionUserIds:['3'] })
  wrapper.unmount()
  const second=await render(true)
  await second.wrapper.get('[data-test="mention-toggle"]').trigger('click')
  await second.wrapper.get('[data-test="mention-search"]').setValue('fer');await flushPromises()
  await second.wrapper.get('[data-test="mention-option-3"]').trigger('click');await flushPromises()
  await second.wrapper.get('[data-test="remove-mention-3"]').trigger('click');await flushPromises()
  expect((second.wrapper.get('textarea').element as HTMLTextAreaElement).value).not.toContain('@fern')
  expect(second.wrapper.find('[data-test="mention-chip-3"]').exists()).toBe(false)
  second.wrapper.unmount()
})
it('按通知 commentId 定位根评论页并展开目标回复',async()=>{
  vi.mocked(fetchCommentLocation).mockResolvedValue({ guideId,rootId:root.id,rootPage:2,replyPage:1 })
  vi.mocked(fetchComments).mockResolvedValue(page([root],{ total:11,current:2,pages:2 }))
  const { wrapper }=await render(true,{ locationCommentId:'reply-1' })
  expect(fetchCommentLocation).toHaveBeenCalledWith('reply-1')
  expect(fetchComments).toHaveBeenLastCalledWith(guideId,{ page:2,size:10 })
  expect(fetchReplies).toHaveBeenCalledWith(root.id,{ page:1,size:10 })
  expect(wrapper.find('[data-test="located-comment"]').exists()).toBe(true)
  wrapper.unmount()
})
it('定位目标失效时给出反馈且不跳走',async()=>{
  vi.mocked(fetchCommentLocation).mockRejectedValueOnce(new ApiError('这条评论已不可见',{ status:404 }))
  const { wrapper,router }=await render(true,{ locationCommentId:'gone' })
  expect(wrapper.text()).toContain('这条评论已不可见')
  expect(router.currentRoute.value.name).toBe('guide-detail')
  wrapper.unmount()
})
