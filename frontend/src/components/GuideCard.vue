<script setup lang="ts">
import { ref, watch } from 'vue'
import TravelArt from './TravelArt.vue'
import UserAvatar from './UserAvatar.vue'
import type { GuideSummary } from '@/types/guide'
const props=defineProps<{ guide:GuideSummary }>()
const failed=ref(false)
watch(()=>props.guide.coverImageUrl,()=>{failed.value=false})
</script>
<template>
  <RouterLink :to="{ name:'guide-detail',params:{ id:guide.id } }" data-test="guide-card" class="surface-card group block overflow-hidden transition-transform hover:-translate-y-1 motion-reduce:transform-none">
    <div class="relative aspect-[4/3] overflow-hidden bg-pine-soft/40">
      <img v-if="guide.coverImageUrl&&!failed" :src="guide.coverImageUrl" :alt="guide.title" class="h-full w-full object-cover" loading="lazy" @error="failed=true" />
      <TravelArt v-else variant="trail" class="h-full w-full" aria-hidden="true" />
      <span v-if="guide.destination" class="absolute bottom-4 left-4 rounded-full bg-cream/95 px-3 py-1 text-xs text-pine-deep">{{ guide.destination.name }}</span>
    </div>
    <div class="p-5">
      <div class="flex flex-wrap gap-2 text-xs text-pine"><span v-for="tag in guide.tags.slice(0,3)" :key="tag.id">#{{ tag.name }}</span></div>
      <h2 class="mt-2 break-words font-serif text-2xl text-pine-deep">{{ guide.title }}</h2>
      <p class="mt-3 line-clamp-2 text-sm leading-relaxed text-ink-soft">{{ guide.summary||'打开手记，看看路上的风景与故事。' }}</p>
      <div class="mt-5 flex items-center justify-between gap-3 border-t border-pine/10 pt-4 text-xs text-ink-soft">
        <span class="flex min-w-0 items-center gap-2"><UserAvatar :src="guide.author.avatarUrl" :name="guide.author.nickname" size="sm" /><span class="truncate">{{ guide.author.nickname }}</span></span>
        <span class="shrink-0">{{ guide.likeCount }} 赞 · {{ guide.favoriteCount }} 收藏</span>
      </div>
    </div>
  </RouterLink>
</template>
