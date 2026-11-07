<script setup lang="ts">
import { computed } from 'vue'
import { contentBlocks } from '../utils/content'
import { previewImages } from '../platform/navigation'
import SafeImage from './SafeImage.vue'
const props = defineProps<{ content: string }>()
const blocks = computed(() => contentBlocks(props.content))
const images = computed(() => blocks.value.flatMap(block => block.src ? [block.src] : []))
</script>
<template>
  <view class="safe-content">
    <view v-for="(block, index) in blocks" :key="index" class="block">
      <SafeImage v-if="block.kind === 'image'" :src="block.src" :alt="block.text" @tap="previewImages(images, block.src!)" />
      <text v-else :class="['body-text', block.kind]" selectable>{{ block.text }}</text>
    </view>
  </view>
</template>
<style scoped>
.block { margin: 14px 0; }
.heading { font-size: 20px; font-weight: 600; color: #0d3f2f; }
.quote { display: block; border-left: 3px solid #145c45; padding: 10px; background: #e1e9dc; }
.code { display: block; font-family: monospace; padding: 10px; background: #e1e9dc; }
</style>
