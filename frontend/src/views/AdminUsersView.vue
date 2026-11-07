<script setup lang="ts">
import { computed,onBeforeUnmount,onMounted,ref,watch } from 'vue'
import { useRoute,useRouter } from 'vue-router'
import AdminNavigation from '@/components/AdminNavigation.vue'
import UserAvatar from '@/components/UserAvatar.vue'
import { fetchAdminUsers,patchUserStatus } from '@/api/administration'
import { ApiError } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import type { AdminRole,AdminUser,AdminUserQuery,AdminUserStatus } from '@/types/administration'

interface Filters { page:number;keyword:string;role:''|AdminRole;status:''|AdminUserStatus }
interface ConfirmTarget { id:string;username:string;nickname:string;targetStatus:AdminUserStatus }

const PAGE_SIZE=10
const ROLE_LABELS:Record<AdminRole,string>={ADMIN:'管理员',USER:'普通用户'}
const STATUS_LABELS:Record<AdminUserStatus,string>={ACTIVE:'正常',DISABLED:'已禁用'}
const INVALID_QUERY_MESSAGE='链接中的筛选条件无效，请从后台导航重新进入用户管理'

const route=useRoute(),router=useRouter(),auth=useAuthStore()
const keyword=ref(''),role=ref<''|AdminRole>(''),status=ref<''|AdminUserStatus>('')
const records=ref<AdminUser[]>([]),total=ref(0),current=ref(1),pages=ref(0)
const loading=ref(false),listError=ref(''),queryError=ref(''),pending=ref(false),actionError=ref('')
const dialog=ref<ConfirmTarget|null>(null)
let active=false,context=0,loadSeq=0

const busy=computed(()=>pending.value||dialog.value!==null)
const beyondLast=computed(()=>records.value.length===0&&total.value>0)
const totalPages=computed(()=>Math.max(pages.value,1))
const actionLabel=(row:AdminUser)=>row.status==='ACTIVE'?'禁用账号':'恢复正常'
const isSelf=(row:AdminUser)=>row.id===auth.user?.id

function positiveSafeInt(raw:unknown):number|null{
  if(typeof raw!=='string'||!/^\d+$/.test(raw))return null
  const value=Number(raw)
  return Number.isSafeInteger(value)&&value>=1?value:null
}
function pickEnum<T extends string>(raw:unknown,allowed:readonly T[]):T|''|null{
  if(raw===undefined||raw==='')return ''
  return typeof raw==='string'&&(allowed as readonly string[]).includes(raw)?(raw as T):null
}
/** 解析地址栏查询；非法角色/状态返回 null，调用方必须拒绝发请求，不能静默丢掉筛选。 */
function readQuery():Filters|null{
  const nextRole=pickEnum(route.query.role,['USER','ADMIN'] as const)
  const nextStatus=pickEnum(route.query.status,['ACTIVE','DISABLED'] as const)
  const rawKeyword=route.query.keyword
  if(nextRole===null||nextStatus===null)return null
  if(rawKeyword!==undefined&&typeof rawKeyword!=='string')return null
  return {page:positiveSafeInt(route.query.page)??1,keyword:(rawKeyword??'').trim(),role:nextRole,status:nextStatus}
}

async function request(filters:Filters):Promise<void>{
  const ctx=context,seq=++loadSeq,redirect=route.fullPath
  const params:AdminUserQuery={page:filters.page,size:PAGE_SIZE}
  if(filters.keyword)params.keyword=filters.keyword
  if(filters.role)params.role=filters.role
  if(filters.status)params.status=filters.status
  loading.value=true
  listError.value=''
  try{
    const result=await fetchAdminUsers(params)
    if(!active||ctx!==context||seq!==loadSeq)return
    records.value=result.records
    total.value=result.total
    current.value=result.current
    pages.value=result.pages
  }catch(error){
    if(!active||ctx!==context||seq!==loadSeq)return
    const failure=error instanceof ApiError?error:new ApiError('用户列表加载失败，请稍后再试')
    if(failure.status===401){
      records.value=[]
      total.value=0;current.value=1;pages.value=0
      // 401 但页面已离开：不把用户从别处拽回登录页。
      if(route.name!=='login')void router.replace({name:'login',query:{redirect}}).catch(()=>undefined)
      return
    }
    if(failure.status===403){
      records.value=[]
      total.value=0
      current.value=1
      pages.value=0
    }
    listError.value=failure.message
  }finally{
    if(ctx===context&&seq===loadSeq)loading.value=false
  }
}

async function loadFromQuery():Promise<void>{
  const parsed=readQuery()
  if(!parsed){
    loading.value=false
    loadSeq+=1
    records.value=[]
    total.value=0
    current.value=1
    pages.value=0
    queryError.value=INVALID_QUERY_MESSAGE
    return
  }
  queryError.value=''
  keyword.value=parsed.keyword
  role.value=parsed.role
  status.value=parsed.status
  await request(parsed)
}

function navigate(page:number):void{
  const location={name:'admin-users',query:{page:String(page),keyword:keyword.value.trim()||undefined,role:role.value||undefined,status:status.value||undefined}}
  if(router.resolve(location).fullPath===route.fullPath)void loadFromQuery()
  else void router.replace(location)
}
function submitSearch():void{
  if(busy.value)return
  navigate(1)
}
function goPage(page:number):void{
  if(queryError.value||busy.value)return
  navigate(page)
}
function retry():void{
  if(loading.value||busy.value)return
  void loadFromQuery()
}

function openConfirm(row:AdminUser):void{
  if(busy.value||isSelf(row))return
  actionError.value=''
  dialog.value={id:row.id,username:row.username,nickname:row.nickname,targetStatus:row.status==='ACTIVE'?'DISABLED':'ACTIVE'}
}
function closeDialog():void{
  if(pending.value)return
  dialog.value=null
  actionError.value=''
}

async function confirmStatus():Promise<void>{
  const target=dialog.value
  if(!target||pending.value)return
  const ctx=context,redirect=route.fullPath
  pending.value=true
  actionError.value=''
  try{
    await patchUserStatus(target.id,target.targetStatus)
    if(!active||ctx!==context)return
    dialog.value=null
    await loadFromQuery()
  }catch(error){
    if(!active||ctx!==context)return
    const failure=error instanceof ApiError?error:new ApiError('更新失败，请稍后重试')
    if(failure.status===401){
      records.value=[]
      dialog.value=null
      if(route.name!=='login')void router.replace({name:'login',query:{redirect}})
    }else if(failure.status===403){
      records.value=[];total.value=0;pages.value=0;dialog.value=null;listError.value=failure.message
    }else actionError.value=failure.message
  }finally{
    if(ctx===context)pending.value=false
  }
}

watch([()=>route.fullPath,()=>auth.user?.id,()=>auth.token],()=>{
  if(!active)return
  context+=1
  pending.value=false
  dialog.value=null
  records.value=[]
  actionError.value=''
  if(route.name!=='admin-users')return
  void loadFromQuery()
})
onMounted(()=>{active=true;context+=1;void loadFromQuery()})
onBeforeUnmount(()=>{active=false;context+=1})
</script>

<template>
  <section class="mx-auto max-w-5xl px-4 py-10">
    <AdminNavigation />
    <header class="mb-6">
      <p class="text-sm font-semibold tracking-[0.18em] text-ember">后台管理</p>
      <h1 class="mt-1 font-serif text-3xl text-pine-deep">用户管理</h1>
      <p class="mt-2 text-sm text-pine/70">查看平台注册用户，必要时调整账号的可用状态。</p>
    </header>

    <form data-test="admin-user-search" class="surface-card mb-5 grid gap-3 p-4 sm:grid-cols-2 lg:grid-cols-4" @submit.prevent="submitSearch">
      <label class="text-sm text-pine/80">关键词
        <input v-model="keyword" data-test="admin-user-keyword" :disabled="busy" type="search" placeholder="昵称或用户名" class="mt-1 w-full rounded-xl border border-pine/20 bg-cream px-3 py-2 text-pine-deep outline-none focus:border-pine" />
      </label>
      <label class="text-sm text-pine/80">角色
        <select v-model="role" data-test="admin-user-role" :disabled="busy" class="mt-1 w-full rounded-xl border border-pine/20 bg-cream px-3 py-2 text-pine-deep outline-none focus:border-pine">
          <option value="">全部角色</option>
          <option value="ADMIN">管理员</option>
          <option value="USER">普通用户</option>
        </select>
      </label>
      <label class="text-sm text-pine/80">状态
        <select v-model="status" data-test="admin-user-status" :disabled="busy" class="mt-1 w-full rounded-xl border border-pine/20 bg-cream px-3 py-2 text-pine-deep outline-none focus:border-pine">
          <option value="">全部状态</option>
          <option value="ACTIVE">正常</option>
          <option value="DISABLED">已禁用</option>
        </select>
      </label>
      <button type="submit" class="btn-primary mt-1 self-end" :disabled="busy">搜索</button>
    </form>

    <div v-if="queryError||listError" role="alert" class="surface-card mb-5 flex flex-col gap-3 border-l-4 border-ember p-4 text-sm text-pine-deep sm:flex-row sm:items-center sm:justify-between">
      <span>{{ queryError||listError }}</span>
      <button data-test="retry-admin-users" class="btn-ghost shrink-0" :disabled="loading" @click="retry">{{ loading?'重试中…':'重试' }}</button>
    </div>

    <p v-if="loading&&!records.length" class="surface-card p-6 text-sm text-pine/70">正在加载用户…</p>

    <ul v-else-if="!listError&&!queryError&&records.length" class="surface-card divide-y divide-pine/10">
      <li v-for="row in records" :key="row.id" data-test="admin-user-row" class="flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:justify-between">
        <div class="flex min-w-0 items-center gap-3">
          <UserAvatar :src="row.avatarUrl" :name="row.nickname" size="md" />
          <div class="min-w-0">
            <p class="truncate font-medium text-pine-deep">{{ row.nickname }}</p>
            <p class="truncate text-sm text-pine/70">{{ row.username }} · {{ ROLE_LABELS[row.role] }}</p>
          </div>
        </div>
        <div class="flex w-full flex-col gap-2 sm:w-auto sm:flex-row sm:items-center">
          <span class="self-start rounded-full px-3 py-1 text-xs font-semibold" :class="row.status==='ACTIVE'?'bg-pine-soft/70 text-pine-deep':'bg-ember/10 text-ember'">{{ STATUS_LABELS[row.status] }}</span>
          <button :data-test="`change-user-${row.id}`" class="btn-ghost w-full sm:w-auto" :disabled="busy||isSelf(row)" @click="openConfirm(row)">{{ isSelf(row)?'当前账号':actionLabel(row) }}</button>
        </div>
      </li>
    </ul>

    <div v-else-if="!listError&&!queryError&&beyondLast" class="surface-card p-6 text-sm text-pine/70">
      <p>当前页没有数据：共 {{ total }} 位用户，共 {{ totalPages }} 页。</p>
      <button data-test="admin-users-first" class="btn-ghost mt-3 w-full sm:w-auto" :disabled="loading" @click="goPage(1)">回到第一页</button>
    </div>
    <p v-else-if="!loading&&!listError&&!queryError" class="surface-card p-6 text-sm text-pine/70">暂无符合条件的用户。</p>

    <div v-if="!listError&&!queryError&&records.length" class="mt-4 flex flex-col items-center justify-between gap-3 text-sm text-pine/70 sm:flex-row">
      <p>第 {{ current }} / {{ totalPages }} 页 · 共 {{ total }} 位用户</p>
      <div class="flex w-full gap-2 sm:w-auto">
        <button class="btn-ghost flex-1 sm:flex-none" :disabled="loading||busy||current<=1" @click="goPage(current-1)">上一页</button>
        <button class="btn-ghost flex-1 sm:flex-none" :disabled="loading||busy||current>=pages" @click="goPage(current+1)">下一页</button>
      </div>
    </div>

    <div v-if="dialog" class="fixed inset-0 z-20 flex items-end justify-center overflow-y-auto bg-scrim/60 p-4 sm:items-center" @click.self="closeDialog">
      <div role="alertdialog" aria-modal="true" class="surface-card w-full max-w-md p-5">
        <h2 class="font-serif text-xl text-pine-deep">{{ dialog.targetStatus==='DISABLED'?'确认禁用账号':'确认恢复账号' }}</h2>
        <p class="mt-2 text-sm text-pine/70">目标账号：{{ dialog.nickname }}（{{ dialog.username }}）。{{ dialog.targetStatus==='DISABLED'?'禁用后该用户将无法登录。':'恢复后该用户可以重新登录。' }}</p>
        <p v-if="actionError" role="alert" class="mt-3 text-sm text-ember">{{ actionError }}</p>
        <div class="mt-5 flex flex-col gap-2 sm:flex-row sm:justify-end">
          <button data-test="cancel-user-status" class="btn-ghost w-full sm:w-auto" :disabled="pending" @click="closeDialog">取消</button>
          <button data-test="confirm-user-status" class="btn-primary w-full sm:w-auto" :disabled="pending" @click="confirmStatus">{{ pending?'处理中…':'确认' }}</button>
        </div>
      </div>
    </div>
  </section>
</template>
