<script setup lang="ts">
defineProps<{ current: number; pages: number; total: number; disabled?: boolean }>()
defineEmits<{ change: [page: number] }>()
</script>
<template>
  <view class="pager">
    <button class="secondary" :disabled="disabled || current <= 1" @tap="$emit('change', current - 1)">上一页</button>
    <text class="muted">{{ current }} / {{ Math.max(1, pages) }} · 共 {{ total }}</text>
    <button class="secondary" :disabled="disabled || current >= pages" @tap="$emit('change', current + 1)">下一页</button>
  </view>
</template>
