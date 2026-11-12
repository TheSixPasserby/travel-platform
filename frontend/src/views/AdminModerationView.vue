<script setup lang="ts">
import { computed,onBeforeUnmount,ref,watch } from 'vue'
import { useRoute,useRouter } from 'vue-router'
import * as api from '@/api/administration'
import { ApiError } from '@/api/client'
import AdminNavigation from '@/components/AdminNavigation.vue'
import UserAvatar from '@/components/UserAvatar.vue'
import { useAuthStore } from '@/stores/auth'
import type { PageResult } from '@/types/api'
import type { AdminComment,CommentStatus,GuideSummary } from '@/types/administration'
import type { GuideStatus,PublicProfile } from '@/types/guide'
type Action=({resource:'guides';status:'PUBLISHED'|'HIDDEN'}|{resource:'comments';status:CommentStatus})&{id:string;title:string}
interface Row {id:string;title:string;text:string|null;author:PublicProfile;status:GuideStatus|CommentStatus;createdAt:string;meta:string;action:Action|null}
const props=defineProps<{resource:'guides'|'comments'}>()
const route=useRoute(),router=useRouter(),auth=useAuthStore()
const keyword=ref(''),status=ref(''),page=ref(1),loading=ref(true),loadError=ref(''),writeError=ref(''),notice=ref(''),pending=ref(false),confirmation=ref<Action|null>(null)
const result=ref<PageResult<Row>>({records:[],total:0,current:1,size:10,pages:0})
let version=0,context=0
const title=computed(()=>props.resource==='guides'?'攻略治理':'评论治理')
const labels:Record<string,string>={DRAFT:'草稿',PUBLISHED:'已发布',HIDDEN:'已隐藏',NORMAL:'正常'}
const statusClasses: Record<string, string> = { DRAFT: 'status-warning', PUBLISHED: 'status-success', HIDDEN: 'status-danger', NORMAL: 'status-success' }
const options=computed(()=>props.resource==='guides'?['DRAFT','PUBLISHED','HIDDEN']:['NORMAL','HIDDEN'])
const locked=computed(()=>pending.value||confirmation.value!==null)
const message=(error:unknown)=>error instanceof ApiError?error.message:'操作暂时没有完成，请稍后重试'
function expired(redirect:string):void{if(route.name!=='login')void router.replace({name:'login',query:{redirect}})}
function guideRow(g:GuideSummary):Row{return {id:g.id,title:g.title,text:g.summary,author:g.author,status:g.status,createdAt:g.createdAt,meta:`${g.likeCount} 赞 · ${g.favoriteCount} 收藏 · ${g.commentCount} 评论`,action:g.status==='DRAFT'?null:{resource:'guides',id:g.id,title:g.title,status:g.status==='HIDDEN'?'PUBLISHED':'HIDDEN'}}}
function commentRow(c:AdminComment):Row{return {id:c.id,title:c.guideTitle,text:c.content,author:c.author,status:c.status,createdAt:c.createdAt,meta:'评论编号 '+c.id,action:{resource:'comments',id:c.id,title:[...c.content].slice(0,120).join(''),status:c.status==='HIDDEN'?'NORMAL':'HIDDEN'}}}
async function load():Promise<void>{
  const request=++version,redirect=route.fullPath;loading.value=true;loadError.value=''
  const raw=route.query.status
  if(raw!==undefined&&(typeof raw!=='string'||(raw!==''&&!options.value.includes(raw)))){loadError.value='状态筛选不正确';result.value.records=[];loading.value=false;return}
  try{
    const requested=Number(route.query.page),appliedPage=Number.isSafeInteger(requested)&&requested>0?requested:1
    const appliedKeyword=typeof route.query.keyword==='string'?route.query.keyword.trim()||undefined:undefined
    const appliedStatus=typeof raw==='string'?raw||undefined:undefined
    const params={page:appliedPage,size:10,keyword:appliedKeyword}
    if(props.resource==='guides'){
      const data=await api.fetchAdminGuides({...params,status:appliedStatus as GuideStatus|undefined});if(request===version)result.value={...data,records:data.records.map(guideRow)}
    }else{
      const data=await api.fetchAdminComments({...params,status:appliedStatus as CommentStatus|undefined});if(request===version)result.value={...data,records:data.records.map(commentRow)}
    }
  }catch(error){if(request!==version)return;if(error instanceof ApiError&&error.status===401)expired(redirect);else{result.value.records=[];loadError.value=message(error)}}
  finally{if(request===version)loading.value=false}
}
async function search(reset=true):Promise<void>{
  if(locked.value)return;if(reset)page.value=1
  const location={name:props.resource==='guides'?'admin-guides':'admin-comments',query:{page:String(page.value),keyword:keyword.value.trim()||undefined,status:status.value||undefined}}
  if(router.resolve(location).fullPath===route.fullPath)await load();else await router.replace(location)
}
function turn(delta:number):void{if(!locked.value){page.value+=delta;void search(false)}}
function ask(row:Row):void{if(!locked.value&&row.action){confirmation.value={...row.action};writeError.value='';notice.value=''}}
async function submit():Promise<void>{
  const target=confirmation.value;if(!target||pending.value)return
  const request=context,redirect=route.fullPath;pending.value=true;writeError.value='';notice.value=''
  try{
    if(target.resource==='guides')await api.patchGuideStatus(target.id,target.status);else await api.patchCommentStatus(target.id,target.status)
    if(request!==context)return;confirmation.value=null;notice.value='展示状态已更新';await load()
  }catch(error){
    if(request!==context)return
    if(error instanceof ApiError&&error.status===401){confirmation.value=null;result.value.records=[];expired(redirect)}
    else if(error instanceof ApiError&&error.status===403){confirmation.value=null;result.value.records=[];loadError.value=message(error)}
    else writeError.value=message(error)
  }finally{if(request===context)pending.value=false}
}
function cancel():void{if(!pending.value){confirmation.value=null;writeError.value=''}}
watch([()=>props.resource,()=>route.fullPath,()=>auth.user?.id,()=>auth.token],()=>{
  context++;version++;pending.value=false;confirmation.value=null;result.value={records:[],total:0,current:1,size:10,pages:0};writeError.value='';notice.value=''
  const rawPage=Number(route.query.page);page.value=Number.isSafeInteger(rawPage)&&rawPage>0?rawPage:1
  keyword.value=typeof route.query.keyword==='string'?route.query.keyword:'';status.value=typeof route.query.status==='string'?route.query.status:'';void load()
},{immediate:true})
onBeforeUnmount(()=>{context++;version++})
</script>
<template>
  <section class="app-container py-8 lg:py-12">
    <AdminNavigation />
    <header class="mb-7"><p class="text-xs tracking-[0.3em] text-ember">CARE FOR THE COMMUNITY</p><h1 class="mt-3 font-serif text-3xl text-pine-deep">{{title}}</h1><p class="mt-3 text-sm text-ink-soft">{{resource==='guides'?'核对旅人的手记，调整公开展示状态；正文仍由作者维护。':'核对旅人的留言，隐藏或恢复公开展示。'}}</p></header>
    <form data-test="moderation-search" class="surface-card mb-6 grid gap-4 p-5 sm:grid-cols-[minmax(0,1fr)_12rem_auto]" @submit.prevent="search()"><div class="min-w-0"><label for="moderation-keyword" class="field-label">{{resource==='guides'?'搜索标题或摘要':'搜索评论内容'}}</label><input id="moderation-keyword" v-model="keyword" data-test="moderation-keyword" type="search" class="field-input" :disabled="locked" /></div><div><label for="moderation-status" class="field-label">展示状态</label><select id="moderation-status" v-model="status" data-test="moderation-status" class="field-input" :disabled="locked"><option value="">全部状态</option><option v-for="value in options" :key="value" :value="value">{{labels[value]}}</option></select></div><button type="submit" class="btn-ghost self-end" :disabled="locked||loading">查找</button></form>
    <p v-if="notice" role="status" class="mb-5 text-sm text-[var(--ui-success)]">{{notice}}</p>
    <div v-if="confirmation" role="alertdialog" aria-labelledby="moderation-confirm-title" class="surface-card mb-6 border-ember/30 p-6"><h2 id="moderation-confirm-title" class="font-serif text-xl text-pine-deep">确认{{confirmation.status==='HIDDEN'?'隐藏':'恢复显示'}}{{confirmation.resource==='guides'?'这篇攻略':'这条评论'}}？</h2><p class="mt-3 break-words text-sm text-ink [overflow-wrap:anywhere]">{{confirmation.title}}</p><p class="mt-2 text-xs text-ink-soft">编号 {{confirmation.id}}</p><p class="mt-3 text-sm text-ink-soft">{{confirmation.status==='HIDDEN'?'隐藏后不再公开展示，原内容与关联数据保留。':'恢复后仍以所属攻略的公开状态为准，不修改作者内容。'}}</p><p v-if="writeError" role="alert" class="mt-4 rounded-xl bg-ember/10 p-3 text-sm text-ember">{{writeError}}</p><div class="mt-5 flex flex-wrap gap-3"><button type="button" data-test="confirm-moderation" class="btn-primary" :disabled="pending" @click="submit">{{pending?'正在更新…':'确认'}}</button><button type="button" data-test="cancel-moderation" class="btn-ghost" :disabled="pending" @click="cancel">保留当前状态</button></div></div>
    <div v-if="loading" role="status" class="surface-card p-12 text-center text-ink-soft">正在读取管理列表…</div>
    <div v-else-if="loadError" role="alert" class="surface-card p-7"><p>{{loadError}}</p><button type="button" data-test="retry-moderation" class="btn-ghost mt-4" :disabled="locked" @click="load">重新加载</button></div>
    <div v-else-if="!result.records.length" class="surface-card p-10 text-center"><h2 class="font-serif text-xl text-pine-deep">{{result.total>0?'这一页暂时没有记录':'还没有符合条件的内容'}}</h2><p class="mt-3 text-sm text-ink-soft">换个关键词或展示状态继续查找。</p><button v-if="result.total>0" type="button" data-test="moderation-first" class="btn-ghost mt-5" :disabled="locked" @click="search()">回到第一页</button></div>
    <div v-else class="space-y-4"><article v-for="row in result.records" :key="row.id" data-test="moderation-row" class="surface-card flex min-w-0 flex-col gap-5 p-5 sm:flex-row sm:items-start sm:justify-between sm:p-6"><div class="min-w-0 flex-1"><div class="flex flex-wrap items-center gap-3"><span class="rounded-full px-3 py-1 text-xs" :class="statusClasses[row.status]">{{labels[row.status]}}</span><span class="text-xs text-ink-soft">{{row.createdAt.slice(0,16).replace('T',' ')}}</span></div><h2 class="mt-4 break-words font-serif text-xl text-pine-deep">{{row.title}}</h2><p v-if="row.text" class="mt-3 whitespace-pre-wrap break-words text-sm leading-relaxed text-ink-soft [overflow-wrap:anywhere]">{{row.text}}</p><div class="mt-4 flex min-w-0 flex-wrap items-center gap-3"><UserAvatar :src="row.author.avatarUrl" :name="row.author.nickname" /><span class="break-words text-sm text-pine-deep">{{row.author.nickname}}</span><span class="break-all text-xs text-ink-soft">{{row.meta}}</span></div></div><button type="button" :data-test="'moderate-'+row.id" class="btn-ghost self-start sm:shrink-0" :disabled="locked||!row.action" @click="ask(row)">{{!row.action?'草稿由作者管理':row.action.status==='HIDDEN'?'隐藏':'恢复显示'}}</button></article></div>
    <nav v-if="!loadError&&result.pages>1" aria-label="管理列表分页" class="mt-7 flex items-center justify-center gap-3"><button class="btn-ghost" :disabled="loading||locked||page<=1" @click="turn(-1)">上一页</button><span class="text-sm text-ink-soft">{{result.current}} / {{result.pages}}</span><button class="btn-ghost" :disabled="loading||locked||page>=result.pages" @click="turn(1)">下一页</button></nav>
  </section>
</template>
