<script setup lang="ts">
import GuideFeed from '@/components/GuideFeed.vue'
import CommunitySidebar from '@/components/CommunitySidebar.vue'
</script>
<template>
  <div class="community-shell">
    <CommunitySidebar />
    <section class="community-main">
      <header class="community-heading"><h1>旅行攻略</h1><p>寻找目的地、旅行主题与实用路线。</p></header>
      <GuideFeed />
    </section>
  </div>
</template>
