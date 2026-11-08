<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getApiBase, resetApiBase, saveApiBase } from '../../platform/url'

const address = ref(''), error = ref(''), current = ref<string | null>(null)
onShow(() => {
  current.value = getApiBase()
  address.value = current.value === '/api/v1' ? '' : current.value || ''
  error.value = ''
})
function save(): void {
  error.value = ''
  try {
    saveApiBase(address.value)
    uni.switchTab({ url: '/pages/guides/index' })
  } catch (failure) {
    error.value = failure instanceof Error ? failure.message : '地址没有保存，请检查后重试'
  }
}
function useWebsite(): void {
  resetApiBase()
  uni.switchTab({ url: '/pages/guides/index' })
}
</script>

<template>
  <view class="screen">
    <text class="title">连接你的旅行平台</text>
    <text class="muted">填写后端服务器地址，连接后即可浏览攻略和登录账号。你可以随时在“我的”中修改。</text>
    <view class="card">
      <text class="label">服务器地址</text>
      <input v-model="address" type="text" :maxlength="512" :adjust-position="true" placeholder="https://你的服务器/api/v1" aria-label="服务器地址" confirm-type="done" @confirm="save" />
      <text class="muted">可填写完整接口地址，或填写服务器根地址，由应用补全 /api/v1。</text>
      <text v-if="error" class="error body-text" role="alert">{{ error }}</text>
      <button @tap="save">保存并进入</button>
      <!-- #ifdef H5 -->
      <button class="secondary" @tap="useWebsite">使用当前网站接口</button>
      <!-- #endif -->
    </view>
    <view class="card">
      <text class="muted body-text">修改服务器地址会退出原账号并清空旧服务器的页面内容。请确认这是你信任的平台；公网地址建议使用 HTTPS，局域网测试可使用 HTTP。</text>
      <text v-if="current === '/api/v1'" class="muted body-text">当前使用本网站的同源接口。</text>
      <text v-else-if="current" class="muted body-text">当前地址：{{ current }}</text>
      <text v-else class="muted body-text">首次使用，请先填写服务器地址。</text>
    </view>
  </view>
</template>
