<script setup lang="ts">
import SafeImage from './SafeImage.vue'
defineProps<{ title: string; cover?: string | null; description?: string | null; meta?: string }>()
defineEmits<{ open: [] }>()
</script>
<template>
  <button class="card list-card" @tap="$emit('open')">
    <SafeImage :src="cover" :alt="title" />
    <text class="subtitle">{{ title }}</text>
    <text v-if="description" class="description muted">{{ description }}</text>
    <text v-if="meta" class="meta muted">{{ meta }}</text>
  </button>
</template>
<style scoped>
.list-card { width: 100%; text-align: left; color: #25352c; background: #fffdf8; border: 1px solid #dce3d8; border-radius: 18px; white-space: normal; }
.subtitle { margin-top: 14px; }
.description { display: block; overflow: hidden; display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; }
.meta { display: block; margin-top: 10px; font-size: 12px; }
</style>
