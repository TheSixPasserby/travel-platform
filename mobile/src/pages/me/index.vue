<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { session, logout, restoreSession } from '../../services/session'
import { openLogin, openPrivatePage } from '../../platform/navigation'
import { errorMessage } from '../../platform/errors'
const busy = ref(false), error = ref('')
async function restore(): Promise<void> {
  error.value = ''
  try { await restoreSession() } catch (failure) { error.value = errorMessage(failure) }
}
async function signOut(): Promise<void> {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try { await logout() } catch (failure) { error.value = errorMessage(failure) }
  finally { busy.value = false }
}
onShow(() => { void restore() })
</script>
<template>
  <view class="screen">
    <text class="title">{{ session.user ? session.user.nickname : '我的旅行手记' }}</text>
    <text v-if="session.restoring" class="muted">正在恢复登录状态…</text>
    <view v-if="error" class="card"><text class="error">{{ error }}</text><button class="secondary" @tap="restore">重试恢复会话</button></view>
    <view v-if="session.user" class="card"><text class="body-text">{{ session.user.bio || '为下一程留一点期待。' }}</text><button :disabled="busy" @tap="signOut">{{ busy ? '正在退出…' : '退出登录' }}</button></view>
    <button v-else @tap="openLogin">登录已有账号</button>
    <button class="secondary" @tap="openPrivatePage('favorites')">我的收藏</button>
    <button class="secondary" @tap="openPrivatePage('itineraries')">我的行程</button>
    <text class="muted">移动端行程目前支持只读查看。完整编辑可在 Web 使用。</text>
  </view>
</template>
