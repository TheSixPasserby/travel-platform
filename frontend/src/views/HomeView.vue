<script setup lang="ts">
import GuideFeed from '@/components/GuideFeed.vue'
</script>
<template>
 <section class="app-container py-8 lg:py-10">
  <header class="flex flex-wrap items-end justify-between gap-5"><div><h1 class="font-serif text-3xl leading-snug text-pine-deep sm:text-4xl">从一篇手记，走向下一程</h1><p class="mt-3 text-sm leading-7 text-ink-soft">在山水间，发现旅人的路线、风景和亲身经验。</p></div><RouterLink :to="{name:'guide-new'}" class="btn-ghost">写篇旅行手记</RouterLink></header>
  <GuideFeed discovery />
 </section>
</template>
