<script setup lang="ts">
import { computed,onBeforeUnmount,ref,watch } from 'vue'
import { useRoute,useRouter } from 'vue-router'
import { fetchNotifications,markAllNotificationsRead,markNotificationRead } from '@/api/notifications'
import { ApiError } from '@/api/client'
import UserIdentity from '@/components/UserIdentity.vue'
import { useAuthStore } from '@/stores/auth'
import type { PageResult } from '@/types/api'
import type { AppNotification, NotificationType } from '@/types/notification'

const PAGE_SIZE=10
const TYPE_LABELS:Readonly<Record<NotificationType,string>>={ COMMENT:'评论了你',REPLY:'回复了你',MENTION:'在评论中提到了你' }

const route=useRoute(),router=useRouter(),auth=useAuthStore()
const loading=ref(true),error=ref(''),actionError=ref('')
const markingAll=ref(false),openingId=ref('')
const page=ref(1),unreadOnly=ref(false)
const result=ref<PageResult<AppNotification>>({ records:[],total:0,current:1,size:PAGE_SIZE,pages:0 })
let version=0

const hasUnread=computed<boolean>(()=>result.value.records.some((item)=>!item.readAt))

function typeLabel(type:NotificationType):string{return TYPE_LABELS[type]??'有新动态'}
function formatTime(value:string):string{return value.slice(0,16).replace('T',' ')}

async function load():Promise<void>{
  const request=++version
  loading.value=true;error.value=''
  try{
    const data=await fetchNotifications({ page:page.value,size:PAGE_SIZE,unreadOnly:unreadOnly.value })
    if(request===version)result.value=data
  }catch(caught){
    if(request!==version)return
    if(caught instanceof ApiError&&caught.status===401){
      void router.replace({ name:'login',query:{ redirect:route.fullPath } })
      return
    }
    error.value=caught instanceof ApiError?caught.message:'通知暂时无法加载'
  }finally{
    if(request===version)loading.value=false
  }
}

/** 页码与筛选都以路由查询为准，刷新后状态可复现。 */
async function go(nextPage:number,nextUnreadOnly:boolean=unreadOnly.value):Promise<void>{
  const query:Record<string,string>={}
  if(nextPage>1)query.page=String(nextPage)
  if(nextUnreadOnly)query.unreadOnly='1'
  const target=router.resolve({ path:route.path,query })
  if(target.fullPath===route.fullPath){
    page.value=nextPage;unreadOnly.value=nextUnreadOnly
    await load()
    return
  }
  await router.push(target)
}

function setUnreadOnly(value:boolean):void{
  if(value===unreadOnly.value)return
  void go(1,value)
}

function turn(delta:number):void{void go(Math.max(1,result.value.current+delta))}

/** 契约直接给出 Web 链接，优先使用；缺失时才按 guideId/commentId 拼装。 */
function guideLocation(item:AppNotification){
  if(typeof item.href==='string'&&item.href.trim())return item.href
  return { path:'/guides/'+item.guideId,query:{ comments:'1',commentId:item.commentId } }
}

async function openNotification(item:AppNotification):Promise<void>{
  if(openingId.value||markingAll.value)return
  const context=version
  actionError.value=''
  if(item.readAt){
    await router.push(guideLocation(item))
    return
  }
  openingId.value=item.id
  try{
    await markNotificationRead(item.id)
    if(context!==version)return
    item.readAt=new Date().toISOString()
    await router.push(guideLocation(item))
  }catch(caught){
    if(context!==version)return
    if(caught instanceof ApiError&&caught.status===404){
      actionError.value='这条通知已失效，已为你刷新列表'
      await load()
    }else{
      actionError.value=caught instanceof ApiError?caught.message:'通知暂时无法打开，请稍后再试'
    }
  }finally{
    if(context===version)openingId.value=''
  }
}

async function markAll():Promise<void>{
  if(markingAll.value||loading.value||!hasUnread.value)return
  const context=version
  markingAll.value=true;actionError.value=''
  try{
    await markAllNotificationsRead()
    if(context!==version)return
    const stamp=new Date().toISOString()
    result.value={ ...result.value,records:result.value.records.map((item)=>item.readAt?item:{ ...item,readAt:stamp }) }
    await load()
  }catch(caught){
    if(context===version)actionError.value=caught instanceof ApiError?caught.message:'暂时无法全部标为已读，请稍后再试'
  }finally{
    markingAll.value=false
  }
}

watch([()=>route.fullPath,()=>auth.user?.id,()=>auth.token],()=>{
  version++
  result.value={records:[],total:0,current:1,size:PAGE_SIZE,pages:0}
  actionError.value='';openingId.value='';markingAll.value=false
  if(!auth.isAuthenticated){loading.value=false;return}
  if(route.name!=='notifications')return
  const requested=Number(route.query.page)
  page.value=Number.isSafeInteger(requested)&&requested>0?requested:1
  unreadOnly.value=route.query.unreadOnly==='1'||route.query.unreadOnly==='true'
  void load()
},{ immediate:true })

onBeforeUnmount(()=>{ version++ })
</script>
<template>
  <section class="app-container py-10 lg:py-14">
    <div class="mb-8 flex flex-wrap items-end justify-between gap-5">
      <div>
        <h1 class="mt-3 font-serif text-4xl text-pine-deep">通知</h1>
        <p class="mt-3 text-sm text-ink-soft">旅友的评论、回复与提及，都会在这里等你。</p>
      </div>
      <button type="button" class="btn-ghost" data-test="mark-all-read" :disabled="loading||markingAll||!hasUnread" @click="markAll">{{ markingAll?'正在标记…':'全部标为已读' }}</button>
    </div>

    <div class="mb-6 flex flex-wrap items-center justify-between gap-4">
      <div class="inline-flex rounded-full bg-pine-soft/60 p-1" role="group" aria-label="通知筛选">
        <button type="button" class="filter-chip" :class="{ 'filter-chip-active':!unreadOnly }" data-test="notifications-filter-all" :aria-pressed="!unreadOnly" :disabled="loading" @click="setUnreadOnly(false)">全部</button>
        <button type="button" class="filter-chip" :class="{ 'filter-chip-active':unreadOnly }" data-test="notifications-filter-unread" :aria-pressed="unreadOnly" :disabled="loading" @click="setUnreadOnly(true)">仅未读</button>
      </div>
      <p class="text-xs text-ink-soft">共 {{ result.total }} 条通知</p>
    </div>

    <p v-if="actionError" role="alert" data-test="notification-error" class="mb-4 rounded-xl bg-ember/10 px-4 py-3 text-sm text-ember">{{ actionError }}</p>

    <div v-if="loading" role="status" class="surface-card p-10 text-center text-ink-soft">正在收取旅友的消息…</div>
    <div v-else-if="error" role="alert" data-test="notifications-load-error" class="surface-card p-7">
      <p>{{ error }}</p>
      <button type="button" data-test="notifications-retry" class="btn-ghost mt-4" @click="load">重新加载</button>
    </div>
    <div v-else-if="!result.records.length" class="surface-card p-12 text-center">
      <h2 class="font-serif text-2xl text-pine-deep">{{ unreadOnly?'暂时没有未读通知':'还没有通知' }}</h2>
      <p class="mt-3 text-sm text-ink-soft">{{ unreadOnly?'都读完啦，去攻略里继续逛逛吧。':'等旅友在你的攻略下留言，这里就会热闹起来。' }}</p>
      <RouterLink to="/guides" class="btn-ghost mt-5">去逛攻略</RouterLink>
    </div>
    <ul v-else class="space-y-3">
      <li v-for="item in result.records" :key="item.id" class="surface-card overflow-hidden" :class="item.readAt?'':'unread-card'">
        <div class="notification-row" data-test="notification-row" role="button" tabindex="0" :aria-busy="openingId===item.id" @click="openNotification(item)" @keydown.enter.prevent="openNotification(item)" @keydown.space.prevent="openNotification(item)">
          <div class="flex flex-wrap items-center gap-x-3 gap-y-2">
            <UserIdentity :user="item.actor" size="sm" />
            <span class="text-sm text-ink-soft">{{ typeLabel(item.type) }}</span>
            <span v-if="!item.readAt" class="unread-badge" data-test="notification-unread">未读</span>
            <time class="ml-auto text-xs text-ink-soft" :datetime="item.createdAt">{{ formatTime(item.createdAt) }}</time>
          </div>
          <p class="mt-2 line-clamp-2 text-sm text-ink">{{ item.content }}</p>
          <p class="mt-2 text-xs text-pine">{{ openingId===item.id?'正在打开…':'查看这条评论' }}</p>
        </div>
      </li>
    </ul>

    <nav v-if="result.pages>1" aria-label="通知分页" class="mt-8 flex items-center justify-center gap-3">
      <button type="button" class="btn-ghost" data-test="notifications-prev" :disabled="loading||result.current<=1" @click="turn(-1)">上一页</button>
      <span class="text-sm text-ink-soft">{{ result.current }} / {{ result.pages }}</span>
      <button type="button" class="btn-ghost" data-test="notifications-next" :disabled="loading||result.current>=result.pages" @click="turn(1)">下一页</button>
    </nav>
  </section>
</template>
<style scoped>
.filter-chip{min-height:44px;padding:.4rem 1.1rem;border-radius:9999px;font-size:.875rem;color:var(--color-ink-soft);transition:background-color .18s ease,color .18s ease}
.filter-chip:hover:not(:disabled){color:var(--color-pine-deep)}
.filter-chip-active{background:var(--color-surface);color:var(--color-pine-deep);font-weight:600;box-shadow:0 1px 3px rgb(0 0 0 / .08)}
.filter-chip:disabled{cursor:not-allowed;opacity:.6}
.notification-row{min-height:44px;padding:1rem 1.25rem;cursor:pointer;overflow-wrap:anywhere;transition:background-color .18s ease}
.notification-row:hover{background:var(--ui-accent-soft)}
.notification-row:focus-visible{outline:2px solid var(--color-pine);outline-offset:-2px}
.unread-card{border-left:3px solid var(--color-ember)}
.unread-badge{display:inline-flex;align-items:center;border-radius:9999px;background:var(--color-ember);padding:.1rem .5rem;font-size:.7rem;font-weight:600;color:#fff}
</style>
