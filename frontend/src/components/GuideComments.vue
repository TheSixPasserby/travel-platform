<script setup lang="ts">
import { computed,nextTick,onBeforeUnmount,ref,useId,watch } from 'vue'
import { RouterLink,useRoute } from 'vue-router'
import { createComment,deleteComment,fetchCommentLocation,fetchComments,fetchMentionSuggestions,fetchReplies } from '@/api/interactions'
import { ApiError } from '@/api/client'
import UserAvatar from './UserAvatar.vue'
import UserIdentity from './UserIdentity.vue'
import { useAuthStore } from '@/stores/auth'
import type { PageResult } from '@/types/api'
import type { PublicProfile } from '@/types/guide'
import type { CommentCreatePayload,GuideComment } from '@/types/interaction'
import { commentLength,normalizeComment } from '@/utils/comment'

/**
 * panel：沿用详情页原有评论卡片；drawer：嵌进右侧评论抽屉，列表内部滚动、
 * 输入区固定在底部。两种模式共用同一套分页/发布/删除/草稿/账号切换逻辑。
 *
 * M17：两级回复（根评论 + 服务端分页的回复）、回复目标选择、@ 提及选择、
 * 通知 commentId 定位。所有新增字段对旧夹具保持可选。
 */
const props=withDefaults(defineProps<{ guideId:string; guideAuthorId?:string; layout?:'panel'|'drawer'; locationCommentId?:string }>(),{ layout:'panel', locationCommentId:undefined })
const emit=defineEmits<{ 'count-changed':[value:number] }>()
const auth=useAuthStore(),route=useRoute(),inputId=useId(),mentionInputId=useId()
const page=ref(1),loading=ref(true),loadError=ref(''),writeError=ref(''),notice=ref(''),text=ref(''),pending=ref(false),confirmId=ref<string|null>(null)
const result=ref<PageResult<GuideComment>>({ records:[],total:0,current:1,size:10,pages:0 })
const textarea=ref<HTMLTextAreaElement|null>(null)
let listVersion=0,contextVersion=0,draftOwner:string|null=null

/* --------------------------- 两级回复：按根评论缓存 --------------------------- */
interface ReplyView{ open:boolean;loading:boolean;error:string;page:number;result:PageResult<GuideComment> }
const emptyPage=():PageResult<GuideComment>=>({ records:[],total:0,current:1,size:10,pages:0 })
const emptyReplyView=():ReplyView=>({ open:false,loading:false,error:'',page:1,result:emptyPage() })
const replies=ref<Record<string,ReplyView>>({})
const replyVersions:Record<string,number>={}
/** 渲染期读取，不创建状态。 */
const EMPTY_VIEW:ReplyView=emptyReplyView()
function replyView(id:string):ReplyView{ return replies.value[id]??EMPTY_VIEW }
function ensureReply(id:string):ReplyView{
  // 必须通过 reactive 代理取回并修改，直接改原始对象不会触发模板更新。
  if(!replies.value[id])replies.value[id]=emptyReplyView()
  return replies.value[id]!
}
function replyTotal(comment:GuideComment):number{ return Math.max(Number(comment.replyCount)||0,replyView(comment.id).result.total||0) }
function showReplyToggle(comment:GuideComment):boolean{ return replyTotal(comment)>0||replyView(comment.id).open }
function replyCountLabel(comment:GuideComment):string{ return '查看 '+replyTotal(comment)+' 条回复' }
async function loadReplies(rootId:string,pageNumber=1):Promise<void>{
  if(typeof fetchReplies!=='function')return
  const view=ensureReply(rootId),ctx=contextVersion
  const version=(replyVersions[rootId]=(replyVersions[rootId]||0)+1)
  view.open=true;view.loading=true;view.error=''
  try{
    const data=await fetchReplies(rootId,{ page:pageNumber,size:10 })
    if(ctx!==contextVersion||version!==replyVersions[rootId])return
    view.result={ records:Array.isArray(data?.records)?data.records:[],total:Number(data?.total)||0,current:Number(data?.current)||pageNumber,size:Number(data?.size)||10,pages:Number(data?.pages)||0 }
    view.page=pageNumber;view.loading=false
  }catch(e){
    if(ctx!==contextVersion||version!==replyVersions[rootId])return
    view.error=message(e);view.loading=false
  }
}
function toggleReplies(root:GuideComment):void{
  const view=ensureReply(root.id)
  if(view.open){ view.open=false;return }
  view.open=true
  if(!view.loading&&!view.result.records.length)void loadReplies(root.id,1)
}

/* ------------------------------- 回复目标选择 -------------------------------- */
const replyTarget=ref<GuideComment|null>(null)
function startReply(target:GuideComment):void{
  replyTarget.value=target;writeError.value='';notice.value=''
  void nextTick(()=>{ try{ textarea.value?.focus({ preventScroll:true }) }catch{ /* ignore */ } })
}
function cancelReply():void{ replyTarget.value=null }

/* --------------------------------- @ 提及 ---------------------------------- */
const mentions=ref<PublicProfile[]>([])
const mentionOpen=ref(false),mentionKeyword=ref(''),mentionLoading=ref(false),mentionError=ref(''),suggestions=ref<PublicProfile[]>([])
let mentionVersion=0
async function searchMentions():Promise<void>{
  const keyword=mentionKeyword.value.trim()
  const ctx=contextVersion,version=++mentionVersion
  if(!mentionOpen.value||!keyword||typeof fetchMentionSuggestions!=='function'){
    suggestions.value=[];mentionLoading.value=false;mentionError.value='';return
  }
  mentionLoading.value=true;mentionError.value=''
  try{
    const data=await fetchMentionSuggestions(keyword)
    if(ctx!==contextVersion||version!==mentionVersion)return
    suggestions.value=Array.isArray(data)?data.filter(item=>!!item&&!!item.id&&!!item.username):[]
  }catch(e){
    if(ctx===contextVersion&&version===mentionVersion){ mentionError.value=message(e);suggestions.value=[] }
  }finally{
    if(version===mentionVersion)mentionLoading.value=false
  }
}
watch(mentionKeyword,()=>{ void searchMentions() })
function toggleMentions():void{
  mentionOpen.value=!mentionOpen.value
  if(mentionOpen.value)void searchMentions()
  else suggestions.value=[]
}
function addMention(user:PublicProfile):void{
  if(!user||!user.username||pending.value)return
  const token='@'+user.username
  if(!mentions.value.some(item=>item.id===user.id)){
    if(mentions.value.length>=5){mentionError.value='每条评论最多提及五人';return}
    mentions.value=[...mentions.value,user]
  }
  const current=text.value
  if(!current.includes(token)){
    const element=textarea.value
    let start=typeof element?.selectionStart==='number'?element.selectionStart:current.length
    let end=typeof element?.selectionEnd==='number'?element.selectionEnd:current.length
    if(start<0)start=current.length;if(end<0)end=start
    if(start>current.length)start=current.length
    if(end<start)end=start
    if(end>current.length)end=current.length
    const before=current.slice(0,start),after=current.slice(end)
    const prefix=before&&!/\s$/.test(before)?' ':''
    const suffix=after&&!/^\s/.test(after)?' ':''
    text.value=before+prefix+token+suffix+after
    void nextTick(()=>{
      const el=textarea.value;if(!el)return
      const position=start+token.length
      try{ el.focus({ preventScroll:true });el.setSelectionRange(position,position) }catch{ /* ignore */ }
    })
  }
  mentionOpen.value=false;mentionKeyword.value='';suggestions.value=[]
}
function removeMention(user:PublicProfile):void{
  mentions.value=mentions.value.filter(item=>item.id!==user.id)
  const token='@'+user.username
  if(token)text.value=text.value.split(token).join('').replace(/[ \t]{2,}/g,' ')
}

/* -------------------------- 通知 commentId 定位 --------------------------- */
const locatedCommentId=ref<string|null>(null),locationError=ref('')
let locationVersion=0
function scrollToLocated():void{
  const id=locatedCommentId.value
  if(!id||typeof document==='undefined')return
  const element=document.getElementById('comment-'+id)
  if(!element||typeof element.scrollIntoView!=='function')return
  try{ element.scrollIntoView({ block:'center' }) }catch{ /* ignore */ }
}
async function locate():Promise<void>{
  const id=props.locationCommentId
  if(!id||typeof fetchCommentLocation!=='function'){ locatedCommentId.value=null;return }
  const ctx=contextVersion,version=++locationVersion
  locationError.value=''
  try{
    const location=await fetchCommentLocation(id)
    if(ctx!==contextVersion||version!==locationVersion)return
    if(!location||location.guideId!==props.guideId)return
    locatedCommentId.value=id
    page.value=Math.max(1,Number(location.rootPage)||1)
    await load()
    if(ctx!==contextVersion||version!==locationVersion)return
    const root=result.value.records.find(item=>item.id===location.rootId)
    if(root)await loadReplies(root.id,Math.max(1,Number(location.replyPage)||1))
    if(ctx!==contextVersion||version!==locationVersion)return
    await nextTick();scrollToLocated()
  }catch(e){
    if(ctx!==contextVersion||version!==locationVersion)return
    locationError.value=message(e)
    page.value=1;await load()
  }
}

/* --------------------------------- 列表读写 -------------------------------- */
const loginLocation=computed(()=>({ name:'login',query:{ redirect:route.fullPath } }))
const length=computed(()=>{try{return commentLength(text.value)}catch{return [...text.value].length}})
const owns=(comment:GuideComment)=>auth.isAuthenticated&&auth.user?.id===comment.author.id
function message(error:unknown):string{ return error instanceof ApiError?error.message:'操作未能完成，请稍后重试' }
/** 文章评论数统计根评论，回复数在各线程独立显示。 */
function publishCount(data:PageResult<GuideComment>):void{
  emit('count-changed',Number(data.total)||0)
}
async function load():Promise<void>{
  const request=++listVersion;loading.value=true;loadError.value=''
  try{ const data=await fetchComments(props.guideId,{ page:page.value,size:10 });if(request===listVersion){ result.value=data;publishCount(data) } }
  catch(e){ if(request===listVersion)loadError.value=message(e) }
  finally{ if(request===listVersion)loading.value=false }
}
async function submit():Promise<void>{
  if(pending.value||!auth.isAuthenticated)return
  writeError.value='';notice.value=''
  let content:string
  try{ content=normalizeComment(text.value) }catch(e){ writeError.value=e instanceof Error?e.message:'请检查评论内容';return }
  const payload:CommentCreatePayload={ content }
  const target=replyTarget.value
  if(target)payload.replyToId=target.id
  const selected=mentions.value.filter(user=>user.username&&content.includes('@'+user.username))
  if(selected.length)payload.mentionUserIds=selected.map(user=>user.id)
  const context=contextVersion,pendingParent=target?(target.rootId||target.id):null
  pending.value=true
  try{
    await createComment(props.guideId,payload)
    if(context!==contextVersion)return
    text.value='';mentions.value=[];mentionOpen.value=false;mentionKeyword.value='';suggestions.value=[];replyTarget.value=null
    notice.value=pendingParent?'回复已发布':'评论已发布'
    if(pendingParent){
      const replyPage=replies.value[pendingParent]?.page||1
      await load()
      await loadReplies(pendingParent,replyPage)
    }
    else{ page.value=1;await load() }
  }catch(e){
    if(context===contextVersion)writeError.value=message(e)
  }finally{
    if(context===contextVersion)pending.value=false
  }
}
async function remove(comment:GuideComment):Promise<void>{
  if(pending.value||confirmId.value!==comment.id||!owns(comment))return
  const context=contextVersion,pendingRoot=comment.rootId||null
  pending.value=true;writeError.value='';notice.value=''
  try{
    await deleteComment(comment.id)
    if(context!==contextVersion)return
    confirmId.value=null;notice.value=pendingRoot?'回复已删除':'评论已删除'
    if(pendingRoot){
      const root=result.value.records.find(item=>item.id===pendingRoot)
      if(root&&typeof root.replyCount==='number')root.replyCount=Math.max(0,root.replyCount-1)
      await loadReplies(pendingRoot,replyView(pendingRoot).page||1)
    }else{
      await load()
    }
  }catch(e){
    if(context===contextVersion)writeError.value=message(e)
  }finally{
    if(context===contextVersion)pending.value=false
  }
}
function turn(delta:number):void{ page.value+=delta;void load() }
function firstPage():void{ page.value=1;void load() }
watch([()=>props.guideId,()=>auth.user?.id,()=>auth.token,()=>props.locationCommentId],([id,user],[previousId])=>{
  contextVersion++;listVersion++;locationVersion++
  pending.value=false;confirmId.value=null;writeError.value='';notice.value=''
  replyTarget.value=null;mentions.value=[];mentionOpen.value=false;mentionKeyword.value='';suggestions.value=[];mentionError.value=''
  replies.value={};locatedCommentId.value=null;locationError.value=''
  if(previousId!==id){ text.value='';page.value=1 }
  if(user&&draftOwner&&user!==draftOwner)text.value=''
  if(user)draftOwner=user
  if(!auth.isAuthenticated&&text.value)writeError.value='登录状态已失效，草稿仍在这里，可复制后重新登录'
  if(props.locationCommentId)void locate()
  else{ page.value=1;void load() }
},{ immediate:true })
onBeforeUnmount(()=>{ contextVersion++;listVersion++;locationVersion++;mentionVersion++ })
</script>
<template>
  <section :class="layout==='drawer' ? 'comments comments--drawer' : 'surface-card min-w-0 p-6 sm:p-9'" aria-label="攻略评论" :data-layout="layout">
    <div v-if="layout==='panel'" class="flex flex-wrap items-center justify-between gap-3"><h2 class="font-serif text-2xl text-pine-deep">旅人的留言</h2><span class="text-sm text-ink-soft">{{ result.total }} 条评论</span></div>
    <div class="comments__stream">
      <div v-if="loading" role="status" class="mt-6 py-6 text-center text-sm text-ink-soft">正在读取旅人的留言…</div>
      <div v-else-if="loadError" role="alert" class="mt-6 rounded-xl border border-ember/20 p-4"><p class="text-sm text-ink">{{ loadError }}</p><button type="button" data-test="retry-comments" class="btn-ghost mt-3" @click="load">重新加载</button></div>
      <div v-else-if="!result.records.length" class="mt-7 rounded-2xl bg-pine-soft/20 p-7 text-center"><p class="text-sm text-ink-soft">{{ result.total>0?'这一页暂时没有评论':'还没有评论，留下第一条旅行建议吧。' }}</p><button v-if="result.total>0" type="button" class="btn-ghost mt-4" @click="firstPage">回到第一页</button></div>
      <template v-else>
        <ol class="mt-7 divide-y divide-pine/10">
          <li v-for="comment in result.records" :id="'comment-'+comment.id" :key="comment.id" data-test="comment-row" class="py-5 first:pt-0">
            <div class="flex items-start gap-3">
              <component :is="comment.author.username ? RouterLink : 'span'" :to="comment.author.username ? '/users/'+encodeURIComponent(comment.author.username) : undefined" :aria-label="'查看'+(comment.author.nickname||'旅行者')+'的主页'"><UserAvatar :src="comment.author.avatarUrl" :name="comment.author.nickname?.trim() || comment.author.username || '旅行者'" /></component>
              <div class="min-w-0 flex-1">
                <div class="flex min-w-0 items-start gap-2"><UserIdentity class="flex-1" :user="comment.author" :show-avatar="false" linked /><span v-if="guideAuthorId === comment.author.id" data-test="guide-author-badge" class="shrink-0 rounded-md bg-pine-soft px-2 py-0.5 text-xs font-medium text-pine">作者</span></div>
                <div class="mt-1 flex flex-wrap gap-x-3 gap-y-1 text-xs text-ink-soft"><time :datetime="comment.createdAt">{{ comment.createdAt.slice(0,16).replace('T',' ') }}</time></div>
                <p class="mt-3 whitespace-pre-wrap break-words text-sm leading-relaxed text-ink [overflow-wrap:anywhere]">{{ comment.content }}</p>
                <p v-if="locatedCommentId===comment.id" data-test="located-comment" class="mt-2 inline-flex rounded-md bg-pine-soft px-2 py-0.5 text-xs font-medium text-pine">已定位到这条评论</p>
                <div class="mt-2 flex flex-wrap items-center gap-1">
                  <button v-if="auth.isAuthenticated" type="button" :data-test="'reply-comment-'+comment.id" class="min-h-11 px-2 text-xs text-pine underline underline-offset-4" :disabled="pending" @click="startReply(comment)">回复</button>
                  <button v-if="showReplyToggle(comment)" type="button" :data-test="'toggle-replies-'+comment.id" :aria-expanded="replyView(comment.id).open" class="min-h-11 px-2 text-xs text-pine underline underline-offset-4" @click="toggleReplies(comment)">{{ replyView(comment.id).open ? '收起回复' : replyCountLabel(comment) }}</button>
                  <button v-if="owns(comment)" type="button" :data-test="'delete-comment-'+comment.id" class="min-h-11 px-2 text-xs text-ember underline underline-offset-4" :disabled="pending" @click="confirmId=confirmId===comment.id?null:comment.id">删除</button>
                </div>
                <div v-if="confirmId===comment.id&&owns(comment)" class="mt-3 rounded-xl border border-ember/20 p-3"><p class="text-sm text-ink">确认删除这条评论？</p><div class="mt-3 flex gap-3"><button type="button" :data-test="'confirm-delete-comment-'+comment.id" class="btn-ghost text-ember" :disabled="pending" @click="remove(comment)">确认删除</button><button type="button" class="btn-ghost" :disabled="pending" @click="confirmId=null">保留评论</button></div></div>
                <div v-if="replyView(comment.id).open" :data-test="'reply-list-'+comment.id" class="mt-3 rounded-xl bg-pine-soft/15 p-3 sm:p-4">
                  <p v-if="replyView(comment.id).error" role="alert" class="text-sm text-ember">{{ replyView(comment.id).error }}</p>
                  <button v-if="replyView(comment.id).error" type="button" :data-test="'retry-replies-'+comment.id" class="btn-ghost mt-2 min-h-11" @click="loadReplies(comment.id,1)">重新加载</button>
                  <p v-else-if="replyView(comment.id).loading&&!replyView(comment.id).result.records.length" role="status" class="text-sm text-ink-soft">正在读取回复…</p>
                  <template v-else>
                    <p v-if="!replyView(comment.id).result.records.length" class="text-sm text-ink-soft">还没有回复。</p>
                    <ol v-else class="divide-y divide-pine/10">
                      <li v-for="reply in replyView(comment.id).result.records" :id="'comment-'+reply.id" :key="reply.id" data-test="reply-row" class="py-3 first:pt-0">
                        <div class="flex items-start gap-3">
                          <component :is="reply.author.username ? RouterLink : 'span'" :to="reply.author.username ? '/users/'+encodeURIComponent(reply.author.username) : undefined" :aria-label="'查看'+(reply.author.nickname||'旅行者')+'的主页'"><UserAvatar :src="reply.author.avatarUrl" :name="reply.author.nickname?.trim() || reply.author.username || '旅行者'" /></component>
                          <div class="min-w-0 flex-1">
                            <div class="flex min-w-0 flex-wrap items-center gap-2"><UserIdentity class="min-w-0" :user="reply.author" :show-avatar="false" linked /><span v-if="reply.replyToAuthor" class="text-xs text-ink-soft">回复 @{{ reply.replyToAuthor.nickname || reply.replyToAuthor.username }}</span></div>
                            <div class="mt-1 flex flex-wrap gap-x-3 gap-y-1 text-xs text-ink-soft"><time :datetime="reply.createdAt">{{ reply.createdAt.slice(0,16).replace('T',' ') }}</time></div>
                            <p class="mt-2 whitespace-pre-wrap break-words text-sm leading-relaxed text-ink [overflow-wrap:anywhere]">{{ reply.content }}</p>
                            <p v-if="locatedCommentId===reply.id" data-test="located-comment" class="mt-2 inline-flex rounded-md bg-pine px-2 py-0.5 text-xs font-medium text-cream">已定位到这条回复</p>
                            <div class="mt-2 flex flex-wrap items-center gap-1">
                              <button v-if="auth.isAuthenticated" type="button" :data-test="'reply-comment-'+reply.id" class="min-h-11 px-2 text-xs text-pine underline underline-offset-4" :disabled="pending" @click="startReply(reply)">回复</button>
                              <button v-if="owns(reply)" type="button" :data-test="'delete-comment-'+reply.id" class="min-h-11 px-2 text-xs text-ember underline underline-offset-4" :disabled="pending" @click="confirmId=confirmId===reply.id?null:reply.id">删除</button>
                            </div>
                            <div v-if="confirmId===reply.id&&owns(reply)" class="mt-3 rounded-xl border border-ember/20 p-3"><p class="text-sm text-ink">确认删除这条回复？</p><div class="mt-3 flex gap-3"><button type="button" :data-test="'confirm-delete-comment-'+reply.id" class="btn-ghost text-ember" :disabled="pending" @click="remove(reply)">确认删除</button><button type="button" class="btn-ghost" :disabled="pending" @click="confirmId=null">保留回复</button></div></div>
                          </div>
                        </div>
                      </li>
                    </ol>
                    <nav v-if="replyView(comment.id).result.pages>1" aria-label="回复分页" class="mt-2 flex items-center justify-center gap-3">
                      <button type="button" :data-test="'replies-prev-'+comment.id" class="btn-ghost min-h-11 px-2 text-xs" :disabled="replyView(comment.id).loading||replyView(comment.id).page<=1" @click="loadReplies(comment.id,replyView(comment.id).page-1)">上一页</button>
                      <span class="text-xs text-ink-soft">{{ replyView(comment.id).page }} / {{ replyView(comment.id).result.pages }}</span>
                      <button type="button" :data-test="'replies-next-'+comment.id" class="btn-ghost min-h-11 px-2 text-xs" :disabled="replyView(comment.id).loading||replyView(comment.id).page>=replyView(comment.id).result.pages" @click="loadReplies(comment.id,replyView(comment.id).page+1)">下一页</button>
                    </nav>
                  </template>
                </div>
              </div>
            </div>
          </li>
        </ol>
        <nav v-if="result.pages>1" aria-label="评论分页" class="mt-5 flex items-center justify-center gap-3"><button type="button" class="btn-ghost" :disabled="loading||pending||page<=1" @click="turn(-1)">上一页</button><span class="text-sm text-ink-soft">{{ result.current }} / {{ result.pages }}</span><button type="button" data-test="comments-next" class="btn-ghost" :disabled="loading||pending||page>=result.pages" @click="turn(1)">下一页</button></nav>
      </template>
    </div>
    <div class="comments__feedback">
      <p v-if="locationError" role="status" class="mt-4 rounded-xl bg-pine-soft/30 p-3 text-sm text-ink">{{ locationError }}</p>
      <p v-if="writeError" role="alert" class="mt-4 rounded-xl bg-ember/10 p-3 text-sm text-ember">{{ writeError }}</p>
      <p v-if="notice" role="status" class="mt-4 text-sm text-[var(--ui-success)]">{{ notice }}</p>
    </div>
    <div class="comments__compose">
      <form v-if="auth.isAuthenticated||text" data-test="comment-form" class="mt-6" @submit.prevent="submit">
        <div v-if="replyTarget" data-test="reply-target" class="mt-4 flex flex-wrap items-center justify-between gap-2 rounded-xl bg-pine-soft/30 px-3 py-2"><p class="text-sm text-ink">正在回复 <span class="font-medium">@{{ replyTarget.author.nickname || replyTarget.author.username }}</span></p><button type="button" data-test="cancel-reply" class="btn-ghost min-h-11 px-2 text-xs" :disabled="pending" @click="cancelReply">取消回复</button></div>
        <label :for="inputId" class="field-label">写下你的旅行建议</label>
        <textarea :id="inputId" ref="textarea" v-model="text" class="field-input min-h-28" rows="4" :disabled="pending" :readonly="!auth.isAuthenticated" placeholder="分享一条有用的经验，或留下一句想说的话。" />
        <div class="mt-3 flex flex-wrap items-center gap-2">
          <button v-if="auth.isAuthenticated" type="button" data-test="mention-toggle" class="btn-ghost min-h-11 px-3 text-sm" :aria-expanded="mentionOpen" :disabled="pending" @click="toggleMentions">@ 提及旅伴</button>
          <ul v-if="mentions.length" class="flex flex-wrap gap-2">
            <li v-for="user in mentions" :key="user.id" :data-test="'mention-chip-'+user.id" class="inline-flex items-center gap-1 rounded-full bg-pine-soft px-3 py-1 text-xs text-pine-deep"><span>@{{ user.nickname || user.username }}</span><button type="button" :data-test="'remove-mention-'+user.id" :aria-label="'移除提及 '+(user.nickname || user.username)" class="inline-flex min-h-11 min-w-8 items-center justify-center text-base leading-none" :disabled="pending" @click="removeMention(user)">×</button></li>
          </ul>
        </div>
        <div v-if="mentionOpen" class="mt-3 rounded-xl border border-pine/15 p-3">
          <label :for="mentionInputId" class="field-label">搜索要提及的旅伴</label>
          <input :id="mentionInputId" v-model="mentionKeyword" data-test="mention-search" type="search" autocomplete="off" class="field-input min-h-11" placeholder="输入用户名或昵称" :disabled="pending" />
          <p v-if="mentionError" role="alert" class="mt-2 text-sm text-ember">{{ mentionError }}</p>
          <p v-else-if="mentionLoading" class="mt-2 text-sm text-ink-soft">正在搜索…</p>
          <p v-else-if="mentionKeyword.trim()&&!suggestions.length" class="mt-2 text-sm text-ink-soft">没有匹配的旅伴；只输入 @ 不会自动建立提及关系。</p>
          <ul v-if="suggestions.length" class="mt-2 divide-y divide-pine/10">
            <li v-for="user in suggestions" :key="user.id"><button type="button" :data-test="'mention-option-'+user.id" class="flex min-h-11 w-full items-center gap-2 py-2 text-left" :disabled="pending" @click="addMention(user)"><UserAvatar :src="user.avatarUrl" :name="user.nickname?.trim() || user.username || '旅伴'" /><span class="min-w-0"><span class="block truncate text-sm text-ink">{{ user.nickname || user.username }}</span><span class="block truncate text-xs text-ink-soft">@{{ user.username }}</span></span></button></li>
          </ul>
        </div>
        <div class="mt-2 flex flex-wrap items-center justify-between gap-3"><span class="text-xs text-ink-soft">{{ length }} / 2000</span><button v-if="auth.isAuthenticated" type="submit" class="btn-primary" :disabled="pending">{{ pending?'正在处理…':'发表评论' }}</button><RouterLink v-else :to="loginLocation" class="btn-ghost">登录后继续</RouterLink></div>
      </form>
      <p v-else class="mt-5 text-sm text-ink-soft"><RouterLink :to="loginLocation" class="text-pine underline underline-offset-4">登录后评论</RouterLink>，和走过这里的人聊聊。</p>
    </div>
  </section>
</template>
<style scoped>
section[data-layout='panel'] {
  display: flex;
  flex-direction: column;
}

section[data-layout='panel'] .comments__compose {
  order: 1;
}

section[data-layout='panel'] .comments__feedback {
  order: 2;
}

section[data-layout='panel'] .comments__stream {
  order: 3;
}

.comments--drawer {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  padding: 0 1.1rem;
  background: var(--color-surface);
}

.comments--drawer .comments__stream {
  order: 1;
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  -webkit-overflow-scrolling: touch;
  padding-bottom: 0.5rem;
}

.comments--drawer .comments__feedback {
  order: 2;
  flex: none;
}

.comments--drawer .comments__compose {
  order: 3;
  flex: none;
  margin-top: 0.35rem;
  padding: 0.85rem 0 calc(0.9rem + env(safe-area-inset-bottom));
  border-top: 1px solid color-mix(in srgb, var(--color-pine) 14%, transparent);
  background: var(--color-surface);
}

.comments--drawer .comments__compose .field-input {
  min-height: 4.5rem;
  background: color-mix(in srgb, var(--color-cream) 72%, var(--color-surface));
}

.comments--drawer .comments__compose > .mt-5 {
  margin-top: 0;
}

/* 回复列表缩进与触控尺寸：所有操作按钮保持至少 44px 高。 */
[data-test='reply-row'] {
  padding-left: 0.15rem;
}

.comments__stream button,
.comments__compose button {
  touch-action: manipulation;
}

@media (max-width: 767px) {
  .comments--drawer {
    padding: 0 0.9rem;
  }
}
</style>
