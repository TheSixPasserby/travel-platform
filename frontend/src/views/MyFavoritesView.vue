<script setup lang="ts">
import { onBeforeUnmount,ref,watch } from 'vue'
import { useRoute,useRouter } from 'vue-router'
import { fetchFavorites } from '@/api/interactions'
import { fetchGuide } from '@/api/guides'
import AddToTripDrawer from '@/components/AddToTripDrawer.vue'
import { useTripIntentStore } from '@/stores/tripIntent'
import { ApiError } from '@/api/client'
import GuideCard from '@/components/GuideCard.vue'
import type { PageResult } from '@/types/api'
import type { GuideSummary, GuideDetail } from '@/types/guide'
const route=useRoute(),router=useRouter()
const tripIntent=useTripIntentStore()
const tripGuide=ref<GuideDetail|null>(null),tripOpen=ref(false),tripLoading=ref(''),tripError=ref('')
let tripVersion=0
async function openTrip(id:string):Promise<void>{
  const request=++tripVersion;tripLoading.value=id;tripError.value=''
  try{const data=await fetchGuide(id);if(request===tripVersion){tripGuide.value=data;tripOpen.value=true}}
  catch(caught){if(request===tripVersion)tripError.value=caught instanceof ApiError?caught.message:'攻略地点暂时无法读取，请重试'}
  finally{if(request===tripVersion)tripLoading.value=''}
}
function planFromFavorite(ids:string[]):void{
  if(tripGuide.value&&tripIntent.select(tripGuide.value,ids)){tripOpen.value=false;void router.push('/itineraries/ai')}
}
const keyword=ref(''),page=ref(1),loading=ref(true),error=ref('')
const result=ref<PageResult<GuideSummary>>({ records:[],total:0,current:1,size:9,pages:0 })
let version=0
async function load():Promise<void>{
  const request=++version;loading.value=true;error.value=''
  try{const data=await fetchFavorites({ page:page.value,size:9,keyword:keyword.value.trim()||undefined });if(request===version)result.value=data}
  catch(e){if(request!==version)return;if(e instanceof ApiError&&e.status===401)void router.replace({ name:'login',query:{ redirect:route.fullPath } });else error.value=e instanceof ApiError?e.message:'收藏暂时无法加载'}
  finally{if(request===version)loading.value=false}
}
async function search(reset=true):Promise<void>{
  if(reset)page.value=1
  const query={ page:String(page.value),keyword:keyword.value.trim()||undefined }
  if(router.resolve({ path:'/me/favorites',query }).fullPath===route.fullPath)await load()
  else await router.replace({ path:'/me/favorites',query })
}
function turn(delta:number):void{page.value+=delta;void search(false)}
watch(()=>route.fullPath,()=>{
  const requested=Number(route.query.page);page.value=Number.isSafeInteger(requested)&&requested>0?requested:1
  keyword.value=typeof route.query.keyword==='string'?route.query.keyword:'';void load()
},{ immediate:true })
onBeforeUnmount(()=>{version++;tripVersion++})
</script>
<template>
  <section class="app-container py-10 lg:py-14">
    <div class="mb-8 flex flex-wrap items-end justify-between gap-5"><div><p class="text-xs tracking-[0.3em] text-ember">SAVED FOR THE NEXT JOURNEY</p><h1 class="mt-3 font-serif text-4xl text-pine-deep">我的收藏</h1><p class="mt-3 text-sm text-ink-soft">把值得重走的路线，留给下一次出发。</p></div><RouterLink :to="{ name:'guides' }" class="btn-ghost">探索更多攻略</RouterLink></div>
    <form class="surface-card mb-7 flex flex-wrap items-end gap-4 p-5" @submit.prevent="search()"><div class="min-w-0 flex-1"><label for="favorite-search" class="field-label">搜索收藏的标题或摘要</label><input id="favorite-search" v-model="keyword" data-test="favorite-keyword" class="field-input" type="search" /></div><button type="submit" class="btn-primary" :disabled="loading">查找收藏</button></form>
    <div v-if="loading" role="status" class="surface-card p-10 text-center text-ink-soft">正在整理你珍藏的路线…</div>
    <div v-else-if="error" role="alert" class="surface-card p-7"><p>{{ error }}</p><button data-test="retry-favorites" class="btn-ghost mt-4" @click="load">重新加载</button></div>
    <div v-else-if="!result.records.length" class="surface-card p-12 text-center"><h2 class="font-serif text-2xl text-pine-deep">{{ result.total>0?'这一页暂时没有收藏':'还没有收藏的攻略' }}</h2><template v-if="result.total>0"><button class="btn-ghost mt-5" @click="search()">回到第一页</button></template><template v-else><p class="mt-3 text-sm text-ink-soft">换个关键词，或在喜欢的公开攻略上按下收藏。</p><RouterLink :to="{ name:'guides' }" class="btn-ghost mt-5">翻开旅行手记</RouterLink></template></div>
    <div v-else class="grid gap-6 md:grid-cols-2 xl:grid-cols-3"><div v-for="guide in result.records" :key="guide.id" class="min-w-0"><GuideCard :guide="guide" /><button type="button" class="btn-ghost mt-3 w-full" :disabled="Boolean(tripLoading)" @click="openTrip(guide.id)">{{ tripLoading===guide.id?'正在读取地点…':'加入我的行程' }}</button></div></div>
    <p v-if="tripError" role="alert" class="mt-4 text-ember">{{ tripError }}</p>
    <AddToTripDrawer :open="tripOpen" :guide="tripGuide" @close="tripOpen=false" @plan-ai="planFromFavorite" />
    <nav v-if="result.pages>1" aria-label="收藏分页" class="mt-8 flex items-center justify-center gap-3"><button class="btn-ghost" :disabled="loading||page<=1" @click="turn(-1)">上一页</button><span class="text-sm text-ink-soft">{{ result.current }} / {{ result.pages }}</span><button class="btn-ghost" :disabled="loading||page>=result.pages" @click="turn(1)">下一页</button></nav>
  </section>
</template>
