<script setup lang="ts">
import { onBeforeUnmount,ref,watch } from 'vue'
import { useRoute,useRouter } from 'vue-router'
import { createItinerary,fetchItineraries } from '@/api/itineraries'
import { ApiError } from '@/api/client'
import ItineraryForm from '@/components/ItineraryForm.vue'
import type { PageResult } from '@/types/api'
import { itineraryStatusLabels,type ItineraryInput,type ItineraryStatus,type ItinerarySummary } from '@/types/itinerary'
const route=useRoute(),router=useRouter()
const keyword=ref(''),status=ref<ItineraryStatus|''>(''),page=ref(1),loading=ref(true),error=ref(''),creating=ref(false),showForm=ref(false),writeError=ref('')
const result=ref<PageResult<ItinerarySummary>>({records:[],total:0,current:1,size:10,pages:0})
let version=0,context=0
const message=(e:unknown)=>e instanceof ApiError?e.message:'行程暂时无法加载，请稍后重试'
function expired(redirect:string):void{if(route.name!=='login')void router.replace({name:'login',query:{redirect}})}
async function load():Promise<void>{
  const request=++version,redirect=route.fullPath;loading.value=true;error.value=''
  const raw=route.query.status
  if(raw!==undefined&&raw!=='ACTIVE'&&raw!=='ARCHIVED'&&raw!==''){status.value='';error.value='行程状态筛选不正确';loading.value=false;return}
  try{const data=await fetchItineraries({page:page.value,size:10,keyword:keyword.value.trim()||undefined,status:status.value||undefined});if(request===version)result.value=data}
  catch(e){if(request!==version)return;if(e instanceof ApiError&&e.status===401)expired(redirect);else error.value=message(e)}
  finally{if(request===version)loading.value=false}
}
async function search(reset=true):Promise<void>{
  if(creating.value)return;if(reset)page.value=1
  const query={page:String(page.value),keyword:keyword.value.trim()||undefined,status:status.value||undefined}
  if(router.resolve({name:'my-itineraries',query}).fullPath===route.fullPath)await load();else await router.replace({name:'my-itineraries',query})
}
async function save(body:ItineraryInput):Promise<void>{
  if(creating.value)return;const request=context,redirect=route.fullPath;creating.value=true;writeError.value=''
  try{const created=await createItinerary(body);if(request===context)await router.push({name:'itinerary-detail',params:{id:created.id}})}
  catch(e){if(request!==context)return;if(e instanceof ApiError&&e.status===401)expired(redirect);else writeError.value=message(e)}
  finally{if(request===context)creating.value=false}
}
function turn(delta:number):void{page.value+=delta;void search(false)}
watch(()=>route.fullPath,()=>{
  context++;creating.value=false;const requested=Number(route.query.page);page.value=Number.isSafeInteger(requested)&&requested>0?requested:1
  keyword.value=typeof route.query.keyword==='string'?route.query.keyword:''
  const raw=route.query.status
  status.value=raw==='ACTIVE'||raw==='ARCHIVED'?raw:'';void load()
},{immediate:true})
onBeforeUnmount(()=>{context++;version++})
</script>
<template>
  <section class="app-container py-10 lg:py-14">
    <header class="mb-8 flex flex-wrap items-end justify-between gap-5"><div><p class="text-xs tracking-[0.3em] text-ember">MAKE ROOM FOR THE JOURNEY</p><h1 class="mt-3 font-serif text-4xl text-pine-deep">我的行程</h1><p class="mt-3 text-sm text-ink-soft">从一个想去的地方，慢慢安排到每一天。计划仅自己可见。</p></div><button type="button" data-test="new-itinerary" class="btn-primary" :disabled="creating" @click="showForm=true;writeError=''">新建行程</button></header>
    <section v-if="showForm" class="surface-card mb-8 p-6 sm:p-8"><h2 class="mb-6 font-serif text-2xl text-pine-deep">为下一程起个名字</h2><p v-if="writeError" role="alert" class="mb-5 rounded-xl bg-ember/10 p-3 text-sm text-ember">{{writeError}}</p><ItineraryForm :initial="null" :busy="creating" @save="save" @cancel="showForm=false;writeError=''" /></section>
    <form data-test="itinerary-search" class="surface-card mb-6 grid gap-4 p-5 sm:grid-cols-[minmax(0,1fr)_12rem_auto]" @submit.prevent="search()"><div class="min-w-0"><label for="itinerary-search" class="field-label">搜索行程名称</label><input id="itinerary-search" v-model="keyword" data-test="itinerary-keyword" type="search" class="field-input" :disabled="creating" /></div><div><label for="itinerary-status" class="field-label">计划状态</label><select id="itinerary-status" v-model="status" data-test="itinerary-status" class="field-input" :disabled="creating"><option value="">全部状态</option><option value="ACTIVE">进行中的计划</option><option value="ARCHIVED">已归档</option></select></div><button type="submit" class="btn-ghost self-end" :disabled="loading||creating">查找行程</button></form>
    <div v-if="loading" role="status" class="surface-card p-12 text-center text-ink-soft">正在展开你的旅行计划…</div>
    <div v-else-if="error" role="alert" class="surface-card p-7"><p>{{error}}</p><button data-test="retry-itineraries" class="btn-ghost mt-4" @click="load">重新加载</button></div>
    <div v-else-if="!result.records.length" class="surface-card p-12 text-center"><h2 class="font-serif text-2xl text-pine-deep">{{result.total>0?'这一页暂时没有行程':'下一段旅程，从这里开始'}}</h2><p class="mt-3 text-sm text-ink-soft">{{result.total>0?'返回第一页继续查看你的计划。':'换个筛选条件，或新建一份属于自己的出行计划。'}}</p><button v-if="result.total>0" data-test="itineraries-first" class="btn-ghost mt-5" @click="search()">回到第一页</button><button v-else class="btn-ghost mt-5" @click="showForm=true">新建第一份行程</button></div>
    <div v-else class="grid gap-5 md:grid-cols-2"><RouterLink v-for="itinerary in result.records" :key="itinerary.id" data-test="itinerary-card" :to="{name:'itinerary-detail',params:{id:itinerary.id}}" class="surface-card min-w-0 p-6 transition hover:-translate-y-1 hover:border-pine/40"><div class="flex flex-wrap gap-3 text-xs text-pine"><span class="rounded-full bg-pine-soft/50 px-3 py-1">{{itineraryStatusLabels[itinerary.status]}}</span><span v-if="itinerary.destination" class="py-1">{{itinerary.destination.name}}{{itinerary.destination.status==='INACTIVE'?'（历史目的地）':''}}</span></div><h2 class="mt-4 break-words font-serif text-2xl text-pine-deep">{{itinerary.title}}</h2><p class="mt-3 text-sm text-ink-soft">{{itinerary.startDate}} → {{itinerary.endDate}}</p><div class="mt-6 flex flex-wrap justify-between gap-3 border-t border-pine/10 pt-4 text-xs text-ink-soft"><span>{{itinerary.dayCount}} 天已安排 · {{itinerary.itemCount}} 个项目</span><span class="text-pine">展开时间线 ↗</span></div></RouterLink></div>
    <nav v-if="result.pages>1" aria-label="行程分页" class="mt-8 flex items-center justify-center gap-3"><button class="btn-ghost" :disabled="loading||creating||page<=1" @click="turn(-1)">上一页</button><span class="text-sm text-ink-soft">{{result.current}} / {{result.pages}}</span><button class="btn-ghost" :disabled="loading||creating||page>=result.pages" @click="turn(1)">下一页</button></nav>
  </section>
</template>
