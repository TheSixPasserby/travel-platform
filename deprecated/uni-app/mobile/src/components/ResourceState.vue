<script setup lang="ts">
import { openLogin } from '../platform/navigation'
defineProps<{ loading: boolean; error: string; needsLogin?: boolean; empty?: boolean }>()
defineEmits<{ retry: [] }>()
</script>
<template>
  <view v-if="loading" class="card muted" role="status">正在加载…</view>
  <view v-else-if="needsLogin" class="card">
    <text>登录后查看自己的收藏和行程。</text><button @tap="openLogin">去登录</button>
  </view>
  <view v-else-if="error" class="card" role="alert">
    <text class="error">{{ error }}</text><button class="secondary" @tap="$emit('retry')">重新加载</button>
  </view>
  <view v-else-if="empty" class="card muted">这里还没有内容。</view>
</template>
