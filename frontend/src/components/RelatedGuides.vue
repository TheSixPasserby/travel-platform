<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { fetchRelatedGuides } from '@/api/guides'
import GuideCard from './GuideCard.vue'
import type { GuideSummary } from '@/types/guide'
const props=defineProps<{guideId:string}>()
const records=ref<GuideSummary[]>([]),loading=ref(false),error=ref('')
let version=0
async function load(){const request=++version;loading.value=true;error.value='';records.value=[]
 try{const data=await fetchRelatedGuides(props.guideId);if(request===version)records.value=data}
 catch{if(request===version)error.value='相关手记暂时无法加载'}finally{if(request===version)loading.value=false}
}
watch(()=>props.guideId,()=>void load(),{immediate:true});onBeforeUnmount(()=>version++)
</script>
<template>
 <section class="mt-12 border-t border-pine/15 pt-8" aria-label="你可能也喜欢">
  <div class="mb-6 flex flex-wrap items-end justify-between gap-3"><h2 class="font-serif text-2xl text-pine-deep">你可能也喜欢</h2><p class="text-xs text-ink-soft">同一目的地或旅行主题的公开手记</p></div>
  <p v-if="loading" role="status" class="py-6 text-sm text-ink-soft">正在寻找相关的旅程…</p>
  <div v-else-if="error" role="alert"><p class="text-sm text-ink-soft">{{ error }}</p><button type="button" class="btn-ghost mt-3" @click="load">重试</button></div>
  <div v-else-if="records.length" class="related-grid"><GuideCard v-for="guide in records" :key="guide.id" :guide="guide" /></div>
  <p v-else class="py-6 text-sm text-ink-soft">还没有相关的手记，<RouterLink to="/" class="text-pine underline">去发现更多旅程</RouterLink>。</p>
 </section>
</template>
<style scoped>.related-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(min(100%,240px),1fr));align-items:start;gap:1.5rem}</style>
