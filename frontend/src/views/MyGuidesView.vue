<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute,useRouter } from 'vue-router'
import { fetchMyGuides } from '@/api/guides'
import { ApiError } from '@/api/client'
import type { PageResult } from '@/types/api'
import { guideStatusLabels,type GuideStatus,type GuideSummary } from '@/types/guide'
const route=useRoute(),router=useRouter()
const page=ref(1),keyword=ref(''),status=ref<GuideStatus|''>(''),loading=ref(true),error=ref('')
const result=ref<PageResult<GuideSummary>>({ records:[],total:0,current:1,size:10,pages:0 })
let version=0
async function load():Promise<void>{
  const request=++version;loading.value=true;error.value=''
  try{const data=await fetchMyGuides({ page:page.value,size:10,keyword:keyword.value.trim()||undefined,status:status.value||undefined });if(request===version)result.value=data}
  catch(e){if(e instanceof ApiError&&e.status===401)void router.replace({ name:'login',query:{ redirect:route.fullPath } });else if(request===version)error.value=e instanceof ApiError?e.message:'我的攻略暂时无法加载'}
  finally{if(request===version)loading.value=false}
}
function search():void{page.value=1;void load()}
function turn(delta:number):void{page.value+=delta;void load()}
onMounted(()=>{void load()})
</script>
<template>
  <section class="app-container py-10 lg:py-14">
    <div class="mb-8 flex flex-wrap items-end justify-between gap-5"><div><p class="text-xs tracking-[0.3em] text-ember">MY TRAVEL NOTES</p><h1 class="mt-3 font-serif text-4xl text-pine-deep">我的攻略</h1><p class="mt-3 text-sm text-ink-soft">从一个草稿开始，把走过的路慢慢写完整。</p></div><RouterLink :to="{ name:'guide-new' }" class="btn-primary">写篇攻略</RouterLink></div>
    <form class="surface-card mb-6 grid gap-4 p-5 sm:grid-cols-[1fr_10rem_auto]" @submit.prevent="search"><div><label for="my-guide-search" class="field-label">搜索我的标题或摘要</label><input id="my-guide-search" v-model="keyword" class="field-input" type="search" /></div><div><label for="my-guide-state" class="field-label">状态</label><select id="my-guide-state" v-model="status" data-test="my-guide-status" class="field-input" @change="search"><option value="">全部状态</option><option v-for="(label,value) in guideStatusLabels" :key="value" :value="value">{{ label }}</option></select></div><button type="submit" class="btn-ghost self-end" :disabled="loading">查找</button></form>
    <div v-if="loading" role="status" class="surface-card p-10 text-center text-ink-soft">正在整理你的手记…</div>
    <div v-else-if="error" role="alert" class="surface-card p-7"><p>{{ error }}</p><button class="btn-ghost mt-4" @click="load">重新加载</button></div>
    <div v-else-if="!result.records.length" class="surface-card p-12 text-center"><h2 class="font-serif text-2xl text-pine-deep">这里还没有手记</h2><p class="mt-3 text-sm text-ink-soft">换个筛选条件，或写下你的下一程。</p><RouterLink :to="{ name:'guide-new' }" class="btn-ghost mt-5">开始记录</RouterLink></div>
    <div v-else class="space-y-4"><article v-for="guide in result.records" :key="guide.id" class="surface-card flex flex-wrap items-center justify-between gap-5 p-6"><div class="min-w-0 flex-1"><span class="rounded-full bg-pine-soft/50 px-3 py-1 text-xs text-pine">{{ guideStatusLabels[guide.status] }}</span><h2 class="mt-3 break-words font-serif text-2xl text-pine-deep"><RouterLink :to="{ name:'guide-edit',params:{ id:guide.id } }">{{ guide.title }}</RouterLink></h2><p v-if="guide.summary" class="mt-2 line-clamp-2 text-sm text-ink-soft">{{ guide.summary }}</p><p class="mt-3 text-xs text-ink-soft">更新于 {{ guide.updatedAt.slice(0,10) }}</p></div><div class="flex flex-wrap gap-3"><RouterLink v-if="guide.status==='PUBLISHED'" :to="{ name:'guide-detail',params:{ id:guide.id } }" class="btn-ghost">公开预览</RouterLink><RouterLink :to="{ name:'guide-edit',params:{ id:guide.id } }" class="btn-ghost">{{ guide.status==='HIDDEN'?'查看与管理':'继续编辑' }}</RouterLink></div></article></div>
    <nav v-if="result.pages>1" aria-label="我的攻略分页" class="mt-7 flex items-center justify-center gap-3"><button class="btn-ghost" :disabled="loading||page<=1" @click="turn(-1)">上一页</button><span class="text-sm text-ink-soft">{{ result.current }} / {{ result.pages }}</span><button class="btn-ghost" :disabled="loading||page>=result.pages" @click="turn(1)">下一页</button></nav>
  </section>
</template>
