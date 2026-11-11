<script setup lang="ts">
import GuideCard from './GuideCard.vue'
import type { GuideSummary } from '@/types/guide'
defineProps<{guides:GuideSummary[]}>()
</script>
<template><div class="guide-grid"><GuideCard v-for="guide in guides" :key="guide.id" :guide="guide" /></div></template>
<style scoped>
.guide-grid{display:grid;grid-template-columns:1fr;gap:1.5rem;align-items:start}.guide-grid>:nth-child(3n+2){--card-ratio:1}.guide-grid>:nth-child(3n){--card-ratio:4/3}
@media(min-width:600px){.guide-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(min-width:960px){.guide-grid{grid-template-columns:repeat(3,minmax(0,1fr))}}@media(min-width:1280px){.guide-grid{grid-template-columns:repeat(4,minmax(0,1fr))}}
</style>
