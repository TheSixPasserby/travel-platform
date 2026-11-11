<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchCreator, fetchCreatorGuides } from '@/api/creators'
import { ApiError } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import UserIdentity from '@/components/UserIdentity.vue'
import GuideCard from '@/components/GuideCard.vue'
import type { CreatorProfile } from '@/types/creator'
import type { PageResult } from '@/types/api'
import type { GuideSummary } from '@/types/guide'
const route=useRoute(),router=useRouter(),auth=useAuthStore()
const creator=ref<CreatorProfile|null>(null),result=ref<PageResult<GuideSummary>|null>(null),loading=ref(true),error=ref('')
let version=0
const page=computed(()=>{const value=Number(route.query.page);return Number.isSafeInteger(value)&&value>0?value:1})
const mine=computed(()=>creator.value?.id===auth.user?.id)
async function load(){
 const request=++version;loading.value=true;error.value='';creator.value=null;result.value=null
 const username=String(route.params.username??'')
 try{
  const [profile,notes]=await Promise.all([fetchCreator(username),fetchCreatorGuides(username,{page:page.value,size:12})])
  if(request===version){creator.value=profile;result.value=notes}
 }catch(e){if(request===version)error.value=e instanceof ApiError?e.message:'创作者资料暂时无法加载，请重试'}
 finally{if(request===version)loading.value=false}
}
function turn(value:number){void router.push({path:route.path,query:{page:String(value)}})}
watch([()=>route.params.username,page],()=>void load(),{immediate:true})
onBeforeUnmount(()=>version++)
</script>
<template>
 <section class="app-container py-8 lg:py-12">
  <RouterLink to="/" class="text-sm text-pine hover:underline">← 发现更多旅程</RouterLink>
  <div v-if="loading" role="status" class="surface-card mt-6 p-12 text-center text-ink-soft">正在打开旅人的主页…</div>
  <div v-else-if="error" role="alert" class="surface-card mt-6 p-8"><h1 class="font-serif text-2xl text-pine-deep">{{ error }}</h1><button type="button" data-test="creator-retry" class="btn-ghost mt-5" @click="load">重新加载</button></div>
  <template v-else-if="creator&&result">
   <header class="creator-header mt-6">
    <div class="min-w-0"><UserIdentity :user="creator" size="lg" /><h1 class="sr-only">{{ creator.nickname||creator.username }}的旅行主页</h1><p v-if="creator.bio" class="mt-5 max-w-2xl whitespace-pre-line break-words text-sm leading-7 text-ink-soft">{{ creator.bio }}</p><p class="mt-4 text-sm text-pine">{{ creator.publishedGuideCount }} 篇公开手记</p></div>
    <RouterLink v-if="mine" to="/me" class="btn-ghost shrink-0">编辑我的资料</RouterLink>
   </header>
   <div class="mb-6 mt-9 flex items-center justify-between gap-3"><h2 class="font-serif text-2xl text-pine-deep">走过的山水</h2><p class="text-xs text-ink-soft">按发布时间排列</p></div>
   <div v-if="result.records.length" class="creator-grid"><GuideCard v-for="guide in result.records" :key="guide.id" :guide="guide" /></div>
   <div v-else class="surface-card p-10 text-center"><p class="text-ink-soft">{{ result.total?'这一页暂时没有手记':'还没有公开的旅行手记' }}</p><button v-if="result.total" data-test="creator-first" type="button" class="btn-ghost mt-4" @click="turn(1)">回到第一页</button></div>
   <nav v-if="result.pages>1" class="mt-8 flex items-center justify-center gap-3" aria-label="创作者手记分页"><button type="button" class="btn-ghost" :disabled="page<=1" @click="turn(page-1)">上一页</button><span class="text-sm text-ink-soft">{{ result.current }} / {{ result.pages }}</span><button type="button" class="btn-ghost" :disabled="page>=result.pages" @click="turn(page+1)">下一页</button></nav>
  </template>
 </section>
</template>
<style scoped>
.creator-header{display:flex;flex-wrap:wrap;align-items:center;justify-content:space-between;gap:1.5rem;padding:2rem 0;border-bottom:1px solid color-mix(in srgb,var(--color-pine) 20%,transparent)}
.creator-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(min(100%,250px),1fr));gap:1.5rem;align-items:start}
</style>
