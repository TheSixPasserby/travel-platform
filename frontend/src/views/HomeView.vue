<script setup lang="ts">
import GuideFeed from '@/components/GuideFeed.vue'
import CommunitySidebar from '@/components/CommunitySidebar.vue'
</script>
<template>
  <div class="community-shell">
    <CommunitySidebar />
    <section class="community-main community-main--discovery">
      <header class="community-heading"><h1>发现下一段旅程</h1><p>来自真实旅人的路线、风景与沿途故事。</p></header>
      <GuideFeed discovery />
    </section>
  </div>
</template>
