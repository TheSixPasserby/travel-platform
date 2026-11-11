<script setup lang="ts">
import { ref } from 'vue'
import { onHide, onShow } from '@dcloudio/uni-app'
import { login } from '../../services/session'
import { errorMessage } from '../../platform/errors'
const username = ref(''), password = ref(''), busy = ref(false), error = ref('')
let visible = true
onShow(() => { visible = true })
onHide(() => { visible = false; password.value = '' })
async function submit(): Promise<void> {
  if (busy.value) return
  if (!username.value.trim() || !password.value) { error.value = '请输入用户名和密码'; return }
  busy.value = true
  error.value = ''
  try {
    await login({ username: username.value.trim(), password: password.value })
    password.value = ''
    if (!visible) return
    if (getCurrentPages().length > 1) uni.navigateBack()
    else uni.switchTab({ url: '/pages/me/index' })
  } catch (failure) { error.value = errorMessage(failure) }
  finally { busy.value = false }
}
</script>
<template>
  <view class="screen">
    <text class="title">欢迎回到山水间</text>
    <view class="card">
      <text class="label">用户名</text><input v-model="username" maxlength="64" :disabled="busy" placeholder="输入已有账号用户名" />
      <text class="label">密码</text><input v-model="password" password maxlength="128" :disabled="busy" placeholder="输入密码" confirm-type="done" @confirm="submit" />
      <text v-if="error" class="error" role="alert">{{ error }}</text>
      <button :disabled="busy" @tap="submit">{{ busy ? '正在登录…' : '登录' }}</button>
      <text class="muted">使用 Web 注册的同一个账号。密码仅用于本次登录请求。</text>
    </view>
  </view>
</template>
