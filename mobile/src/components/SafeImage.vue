<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { imageUrl } from '../platform/url'
const props = defineProps<{ src?: string | null; alt: string }>()
const failed = ref(false)
const url = computed(() => imageUrl(props.src))
watch(() => props.src, () => { failed.value = false })
</script>
<template>
  <image v-if="url && !failed" :src="url" :alt="alt" mode="aspectFill" class="photo" @error="failed = true" />
  <view v-else class="photo placeholder"><text>{{ alt }} · 图片暂不可用</text></view>
</template>
<style scoped>
.photo { display: block; width: 100%; height: 210px; border-radius: 12px; background: #e1e9dc; }
.placeholder { display: flex; align-items: center; justify-content: center; padding: 16px; font-size: 13px; color: #55645b; }
</style>
