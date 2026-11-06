<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import TravelArt from './TravelArt.vue'
import { destinationTypeLabels, type Destination } from '@/types/destination'

const props = defineProps<{ destination: Destination }>()
const imageFailed = ref(false)
const location = computed(() => [...new Set([props.destination.country, props.destination.province, props.destination.city].filter(Boolean))].join(' · '))
watch(() => props.destination.coverImageUrl, () => { imageFailed.value = false })
</script>

<template>
  <RouterLink :to="{ name: 'destination-detail', params: { id: props.destination.id } }"
    :aria-label="props.destination.name" data-test="destination-card"
    class="surface-card group block overflow-hidden transition-transform duration-200 hover:-translate-y-1 motion-reduce:transform-none">
    <div class="relative aspect-[4/3] overflow-hidden bg-pine-soft/30">
      <img v-if="props.destination.coverImageUrl && !imageFailed" :src="props.destination.coverImageUrl"
        :alt="props.destination.name" loading="lazy" class="h-full w-full object-cover" @error="imageFailed = true" />
      <TravelArt v-else variant="hero" class="h-full w-full" aria-hidden="true" />
      <span class="absolute left-4 top-4 rounded-full bg-cream/95 px-3 py-1 text-xs font-medium text-pine-deep">
        {{ destinationTypeLabels[props.destination.destinationType] }}
      </span>
    </div>
    <div class="p-5">
      <p class="text-xs tracking-wide text-ink-soft">{{ location }}</p>
      <h2 class="mt-2 break-words font-serif text-2xl text-pine-deep">{{ props.destination.name }}</h2>
      <p class="mt-2 line-clamp-2 text-sm leading-relaxed text-ink-soft">{{ props.destination.description || '留一点时间，去认识这个地方。' }}</p>
      <p class="mt-3 text-xs text-ink-soft">{{ props.destination.recommendedSeasons || '季节待核实' }}<span v-if="props.destination.recommendedDays"> · 建议 {{ props.destination.recommendedDays }} 天</span><span v-else-if="props.destination.recommendedDurationMinutes"> · 建议 {{ props.destination.recommendedDurationMinutes }} 分钟</span></p>
      <span class="mt-5 inline-flex items-center gap-2 text-sm font-medium text-pine">看看这里 <span aria-hidden="true">↗</span></span>
    </div>
  </RouterLink>
</template>
