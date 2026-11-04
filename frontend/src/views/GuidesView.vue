<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute,useRouter } from 'vue-router'
import { fetchGuides,fetchTags } from '@/api/guides'
import { ApiError } from '@/api/client'
import DestinationPicker from '@/components/DestinationPicker.vue'
import GuideCard from '@/components/GuideCard.vue'
import type { PageResult } from '@/types/api'
import type { GuideSort,GuideSummary,Tag } from '@/types/guide'

const route=useRoute(),router=useRouter()
const keyword=ref(''),destinationId=ref<string|null>(null),tagId=ref(''),sort=ref<GuideSort>('latest'),page=ref(1)
const tags=ref<Tag[]>([]),tagsError=ref(''),loading=ref(false),error=ref('')
const result=ref<PageResult<GuideSummary>>({ records:[],total:0,current:1,size:9,pages:0 })
let version=0
function syncQuery():void{
  keyword.value=typeof route.query.keyword==='string'?route.query.keyword:''
  destinationId.value=typeof route.query.destinationId==='string'?route.query.destinationId:null
  tagId.value=typeof route.query.tagId==='string'?route.query.tagId:''
  sort.value=route.query.sort==='popular'?'popular':'latest'
  const requested=Number(route.query.page);page.value=Number.isSafeInteger(requested)&&requested>0?requested:1
}
async function load():Promise<void>{
  const request=++version;loading.value=true;error.value=''
  try{
    const data=await fetchGuides({ page:page.value,size:9,keyword:keyword.value.trim()||undefined,destinationId:destinationId.value||undefined,tagId:tagId.value||undefined,sort:sort.value })
    if(request===version)result.value=data
  }catch(e){if(request===version)error.value=e instanceof ApiError?e.message:'攻略暂时无法加载，请稍后重试'}
  finally{if(request===version)loading.value=false}
}
async function search(reset=true):Promise<void>{
  if(reset)page.value=1
  const query={ page:String(page.value),keyword:keyword.value.trim()||undefined,destinationId:destinationId.value||undefined,tagId:tagId.value||undefined,sort:sort.value==='latest'?undefined:sort.value }
  if(router.resolve({ path:'/guides',query }).fullPath===route.fullPath)await load()
  else await router.replace({ path:'/guides',query })
}
function turn(delta:number):void{page.value+=delta;void search(false)}
async function loadTags():Promise<void>{try{tags.value=await fetchTags();tagsError.value=''}catch{tagsError.value='标签暂时无法加载，可继续搜索'}}
watch(()=>route.fullPath,()=>{syncQuery();void load()},{ immediate:true })
onMounted(()=>{void loadTags()})
</script>
<template>
  <section class="app-container py-10 lg:py-14">
    <div class="mb-9 flex flex-wrap items-end justify-between gap-5"><div><p class="text-xs tracking-[0.3em] text-ember">STORIES FROM THE ROAD</p><h1 class="mt-3 font-serif text-4xl text-pine-deep">在别人的脚步里，找到下一程</h1><p class="mt-4 text-sm leading-relaxed text-ink-soft">真实的路线、偶遇的风景，还有值得慢下来的地方。</p></div><RouterLink :to="{ name:'guide-new' }" class="btn-primary">写篇攻略 <span aria-hidden="true">↗</span></RouterLink></div>
    <form data-test="guide-search" class="surface-card mb-8 grid items-end gap-4 p-5 lg:grid-cols-[1.2fr_1fr_9rem_8rem_auto]" @submit.prevent="search()">
      <div><label for="guide-search-keyword" class="field-label">搜索标题或摘要</label><input id="guide-search-keyword" v-model="keyword" data-test="guide-keyword" class="field-input" type="search" placeholder="想去哪里，想看什么" /></div>
      <DestinationPicker v-model="destinationId" label="目的地筛选" :allow-inactive-selection="true" />
      <div><label for="guide-search-tag" class="field-label">旅行主题</label><select id="guide-search-tag" v-model="tagId" class="field-input" @change="search()"><option value="">全部主题</option><option v-for="tag in tags" :key="tag.id" :value="tag.id">{{ tag.name }}</option></select><p v-if="tagsError" class="mt-1 text-xs text-ember">{{ tagsError }} <button type="button" class="underline" @click="loadTags">重试</button></p></div>
      <div><label for="guide-search-sort" class="field-label">排序</label><select id="guide-search-sort" v-model="sort" class="field-input" @change="search()"><option value="latest">最新发布</option><option value="popular">最多点赞</option></select></div>
      <button type="submit" class="btn-primary" :disabled="loading">{{ loading?'寻找中…':'寻找攻略' }}</button>
    </form>
    <div v-if="loading" role="status" class="surface-card p-10 text-center text-ink-soft">正在翻开路上的故事…</div>
    <div v-else-if="error" role="alert" class="surface-card p-8"><p>{{ error }}</p><button class="btn-ghost mt-4" data-test="retry-guides" @click="load">重新加载</button></div>
    <div v-else-if="!result.records.length" class="surface-card p-12 text-center">
      <h2 class="font-serif text-2xl text-pine-deep">{{ result.total>0?'这一页暂时没有攻略':'还没有找到攻略' }}</h2>
      <template v-if="result.total>0"><p class="mt-3 text-sm text-ink-soft">已有{{ result.total }}篇攻略符合条件，回到第一页继续浏览。</p><button type="button" class="btn-ghost mt-5" @click="search()">回到第一页</button></template>
      <template v-else><p class="mt-3 text-sm text-ink-soft">换个关键词，或记录你自己的第一篇手记。</p><RouterLink :to="{ name:'guide-new' }" class="btn-ghost mt-5">开始记录</RouterLink></template>
    </div>
    <template v-else><p class="mb-5 text-sm text-ink-soft">找到 {{ result.total }} 篇旅行攻略</p><div class="grid gap-6 md:grid-cols-2 xl:grid-cols-3"><GuideCard v-for="guide in result.records" :key="guide.id" :guide="guide" /></div></template>
    <nav v-if="result.pages>1" aria-label="攻略分页" class="mt-8 flex items-center justify-center gap-3"><button class="btn-ghost" :disabled="loading||page<=1" @click="turn(-1)">上一页</button><span class="text-sm text-ink-soft">{{ result.current }} / {{ result.pages }}</span><button class="btn-ghost" :disabled="loading||page>=result.pages" @click="turn(1)">下一页</button></nav>
  </section>
</template>
