<script setup lang="ts">
import TravelArt from '@/components/TravelArt.vue'
</script>

<template>
  <section class="app-container flex flex-col items-center gap-6 py-20 text-center">
    <div class="w-full max-w-sm">
      <TravelArt variant="trail" class="aspect-square w-full" />
    </div>
    <div>
      <h1 class="font-serif text-3xl text-pine-deep">这条小径还没有被记录</h1>
      <p class="mt-3 text-sm text-ink-soft">你访问的页面不存在，或者已经搬到别处了。</p>
    </div>
    <RouterLink to="/" class="btn-primary">回到首页</RouterLink>
  </section>
</template>
