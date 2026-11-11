<script setup lang="ts">
import { computed,onBeforeUnmount,ref,useId,watch } from 'vue'
import { RouterLink,useRoute } from 'vue-router'
import { createComment,deleteComment,fetchComments } from '@/api/interactions'
import { ApiError } from '@/api/client'
import UserAvatar from './UserAvatar.vue'
import UserIdentity from './UserIdentity.vue'
import { useAuthStore } from '@/stores/auth'
import type { PageResult } from '@/types/api'
import type { GuideComment } from '@/types/interaction'
import { commentLength,normalizeComment } from '@/utils/comment'

const props=defineProps<{ guideId:string; guideAuthorId?:string }>()
const emit=defineEmits<{ 'count-changed':[value:number] }>()
const auth=useAuthStore(),route=useRoute(),inputId=useId()
const page=ref(1),loading=ref(true),loadError=ref(''),writeError=ref(''),notice=ref(''),text=ref(''),pending=ref(false),confirmId=ref<string|null>(null)
const result=ref<PageResult<GuideComment>>({ records:[],total:0,current:1,size:10,pages:0 })
let listVersion=0,contextVersion=0,draftOwner:string|null=null
const loginLocation=computed(()=>({ name:'login',query:{ redirect:route.fullPath } }))
const length=computed(()=>{try{return commentLength(text.value)}catch{return [...text.value].length}})
const owns=(comment:GuideComment)=>auth.isAuthenticated&&auth.user?.id===comment.author.id
const message=(error:unknown)=>error instanceof ApiError?error.message:'操作未能完成，请稍后重试'
async function load():Promise<void>{
  const request=++listVersion;loading.value=true;loadError.value=''
  try{const data=await fetchComments(props.guideId,{ page:page.value,size:10 });if(request===listVersion){result.value=data;emit('count-changed',data.total)}}
  catch(e){if(request===listVersion)loadError.value=message(e)}
  finally{if(request===listVersion)loading.value=false}
}
async function submit():Promise<void>{
  if(pending.value||!auth.isAuthenticated)return
  writeError.value='';notice.value=''
  let content:string
  try{content=normalizeComment(text.value)}catch(e){writeError.value=e instanceof Error?e.message:'请检查评论内容';return}
  const context=contextVersion;pending.value=true
  try{
    await createComment(props.guideId,{ content });if(context!==contextVersion)return
    text.value='';page.value=1;notice.value='评论已发布';await load()
  }catch(e){if(context===contextVersion)writeError.value=message(e)}
  finally{if(context===contextVersion)pending.value=false}
}
async function remove(comment:GuideComment):Promise<void>{
  if(pending.value||confirmId.value!==comment.id||!owns(comment))return
  const context=contextVersion;pending.value=true;writeError.value='';notice.value=''
  try{await deleteComment(comment.id);if(context!==contextVersion)return;confirmId.value=null;notice.value='评论已删除';await load()}
  catch(e){if(context===contextVersion)writeError.value=message(e)}
  finally{if(context===contextVersion)pending.value=false}
}
function turn(delta:number):void{page.value+=delta;void load()}
function firstPage():void{page.value=1;void load()}
watch([()=>props.guideId,()=>auth.user?.id,()=>auth.token],([id,user],[previousId])=>{
  contextVersion++;pending.value=false;confirmId.value=null;writeError.value='';notice.value=''
  if(previousId!==id){text.value='';page.value=1}
  if(user&&draftOwner&&user!==draftOwner)text.value=''
  if(user)draftOwner=user
  if(!auth.isAuthenticated&&text.value)writeError.value='登录状态已失效，草稿仍在这里，可复制后重新登录'
  void load()
},{ immediate:true })
onBeforeUnmount(()=>{contextVersion++;listVersion++})
</script>
<template>
  <section class="surface-card min-w-0 p-6 sm:p-9" aria-label="攻略评论">
    <div class="flex flex-wrap items-center justify-between gap-3"><h2 class="font-serif text-2xl text-pine-deep">旅人的留言</h2><span class="text-sm text-ink-soft">{{ result.total }} 条评论</span></div>
    <form v-if="auth.isAuthenticated||text" data-test="comment-form" class="mt-6" @submit.prevent="submit">
      <label :for="inputId" class="field-label">写下你的旅行建议</label>
      <textarea :id="inputId" v-model="text" class="field-input min-h-28" rows="4" :disabled="pending" :readonly="!auth.isAuthenticated" placeholder="分享一条有用的经验，或留下一句想说的话。" />
      <div class="mt-2 flex flex-wrap items-center justify-between gap-3"><span class="text-xs text-ink-soft">{{ length }} / 2000</span><button v-if="auth.isAuthenticated" type="submit" class="btn-primary" :disabled="pending">{{ pending?'正在处理…':'发表评论' }}</button><RouterLink v-else :to="loginLocation" class="btn-ghost">登录后继续</RouterLink></div>
    </form>
    <p v-else class="mt-5 text-sm text-ink-soft"><RouterLink :to="loginLocation" class="text-pine underline underline-offset-4">登录后评论</RouterLink>，和走过这里的人聊聊。</p>
    <p v-if="writeError" role="alert" class="mt-4 rounded-xl bg-ember/10 p-3 text-sm text-ember">{{ writeError }}</p>
    <p v-if="notice" role="status" class="mt-4 text-sm text-pine">{{ notice }}</p>
    <div v-if="loading" role="status" class="mt-6 py-6 text-center text-sm text-ink-soft">正在读取旅人的留言…</div>
    <div v-else-if="loadError" role="alert" class="mt-6 rounded-xl border border-ember/20 p-4"><p class="text-sm text-ink">{{ loadError }}</p><button type="button" data-test="retry-comments" class="btn-ghost mt-3" @click="load">重新加载</button></div>
    <div v-else-if="!result.records.length" class="mt-7 rounded-2xl bg-pine-soft/20 p-7 text-center"><p class="text-sm text-ink-soft">{{ result.total>0?'这一页暂时没有评论':'还没有评论，留下第一条旅行建议吧。' }}</p><button v-if="result.total>0" type="button" class="btn-ghost mt-4" @click="firstPage">回到第一页</button></div>
    <ol v-else class="mt-7 divide-y divide-pine/10"><li v-for="comment in result.records" :key="comment.id" data-test="comment-row" class="py-5 first:pt-0"><div class="flex items-start gap-3"><UserAvatar :src="comment.author.avatarUrl" :name="comment.author.nickname?.trim() || comment.author.username || '旅行者'" /><div class="min-w-0 flex-1"><div class="flex min-w-0 items-start gap-2"><UserIdentity class="flex-1" :user="comment.author" :show-avatar="false" linked /><span v-if="guideAuthorId === comment.author.id" data-test="guide-author-badge" class="shrink-0 rounded-md bg-pine-soft px-2 py-0.5 text-xs font-medium text-pine">作者</span></div><div class="mt-1 flex flex-wrap gap-x-3 gap-y-1 text-xs text-ink-soft"><time :datetime="comment.createdAt">{{ comment.createdAt.slice(0,16).replace('T',' ') }}</time></div><p class="mt-3 whitespace-pre-wrap break-words text-sm leading-relaxed text-ink [overflow-wrap:anywhere]">{{ comment.content }}</p><button v-if="owns(comment)" type="button" :data-test="'delete-comment-'+comment.id" class="mt-3 -ml-2 min-h-11 px-2 text-xs text-ember underline underline-offset-4" :disabled="pending" @click="confirmId=confirmId===comment.id?null:comment.id">删除</button><div v-if="confirmId===comment.id&&owns(comment)" class="mt-3 rounded-xl border border-ember/20 p-3"><p class="text-sm text-ink">确认删除这条评论？</p><div class="mt-3 flex gap-3"><button type="button" :data-test="'confirm-delete-comment-'+comment.id" class="btn-ghost text-ember" :disabled="pending" @click="remove(comment)">确认删除</button><button type="button" class="btn-ghost" :disabled="pending" @click="confirmId=null">保留评论</button></div></div></div></div></li></ol>
    <nav v-if="result.pages>1" aria-label="评论分页" class="mt-5 flex items-center justify-center gap-3"><button type="button" class="btn-ghost" :disabled="loading||pending||page<=1" @click="turn(-1)">上一页</button><span class="text-sm text-ink-soft">{{ result.current }} / {{ result.pages }}</span><button type="button" data-test="comments-next" class="btn-ghost" :disabled="loading||pending||page>=result.pages" @click="turn(1)">下一页</button></nav>
  </section>
</template>
