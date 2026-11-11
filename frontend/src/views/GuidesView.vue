<script setup lang="ts">
import GuideFeed from '@/components/GuideFeed.vue'
</script>
<template>
 <section class="app-container py-8 lg:py-10">
  <header class="flex flex-wrap items-end justify-between gap-5"><div><h1 class="font-serif text-3xl leading-snug text-pine-deep sm:text-4xl">在别人的脚步里，找到下一程</h1><p class="mt-3 text-sm leading-7 text-ink-soft">真实的路线、偶遇的风景，还有值得慢下来的地方。</p></div><RouterLink :to="{name:'guide-new'}" class="btn-primary">写篇攻略</RouterLink></header>
  <GuideFeed />
 </section>
</template>
