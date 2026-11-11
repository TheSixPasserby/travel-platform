<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import UserIdentity from './UserIdentity.vue'
import type { GuideSummary } from '@/types/guide'
const props=defineProps<{guide:GuideSummary}>()
const failed=ref(false),location=computed(()=>({name:'guide-detail',params:{id:props.guide.id}}))
watch(()=>props.guide.coverImageUrl,()=>failed.value=false)
</script>
<template>
 <article class="guide-note surface-card min-w-0 overflow-hidden">
  <RouterLink :to="location" data-test="guide-card" class="note-photo" :aria-label="`阅读：${guide.title}`">
   <img v-if="guide.coverImageUrl&&!failed" :src="guide.coverImageUrl" :alt="guide.title" loading="lazy" decoding="async" @error="failed=true" />
   <div v-else data-test="card-placeholder" class="note-placeholder"><svg viewBox="0 0 160 96" aria-hidden="true"><path d="M0 96 45 32 74 68 108 12 160 96Z" fill="currentColor" opacity=".3"/><path d="m0 96 72-42 88 42Z" fill="currentColor" opacity=".15"/></svg><span>翻开这篇旅行手记</span></div>
   <span v-if="guide.destination" class="note-location">{{ guide.destination.name }}</span>
  </RouterLink>
  <div class="p-4">
   <RouterLink :to="location" class="note-title"><h2 class="line-clamp-2 break-words font-serif text-xl leading-snug text-pine-deep">{{ guide.title }}</h2></RouterLink>
   <p v-if="guide.summary" class="mt-2 line-clamp-2 text-sm leading-6 text-ink-soft">{{ guide.summary }}</p>
   <div v-if="guide.tags.length||guide.travelDays||guide.budgetPerPerson!==null" class="mt-3 flex flex-wrap gap-x-3 gap-y-1 text-xs text-pine"><span v-for="tag in guide.tags.slice(0,2)" :key="tag.id">#{{ tag.name }}</span><span v-if="guide.travelDays">{{ guide.travelDays }}天</span><span v-if="guide.budgetPerPerson!==null">人均 ¥{{ guide.budgetPerPerson }}</span></div>
   <div class="mt-4 min-w-0 border-t border-pine/10 pt-3"><UserIdentity :user="guide.author" size="sm" linked /><p class="mt-3 flex items-center gap-4 text-xs text-ink-soft"><span :aria-label="`${guide.likeCount} 人点赞`">♡ {{ guide.likeCount }}</span><span>{{ guide.favoriteCount }} 收藏</span></p></div>
  </div>
 </article>
</template>
<style scoped>
.guide-note{--card-ratio:4/5;box-shadow:none;transition:border-color .15s}.guide-note:hover{border-color:var(--color-pine)}.note-photo{position:relative;display:block;aspect-ratio:var(--card-ratio);overflow:hidden;background:var(--color-pine-soft)}.note-photo img{width:100%;height:100%;display:block;object-fit:cover}.note-location{position:absolute;bottom:12px;left:12px;max-width:calc(100% - 24px);overflow:hidden;text-overflow:ellipsis;white-space:nowrap;background:var(--color-surface);border-radius:99px;padding:4px 10px;color:var(--color-pine-deep);font-size:12px}.note-placeholder{display:flex;flex-direction:column;align-items:center;justify-content:center;height:100%;padding:1rem;gap:1rem;color:var(--color-pine);font-size:13px}.note-placeholder svg{width:65%;max-width:160px}.note-title:hover{text-decoration:underline;text-underline-offset:4px}.note-photo:focus-visible,.note-title:focus-visible{outline:3px solid var(--color-pine);outline-offset:-3px}
@media(prefers-reduced-motion:reduce){.guide-note{transition:none}}
</style>
