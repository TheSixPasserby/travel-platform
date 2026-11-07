<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { onLoad, onHide } from '@dcloudio/uni-app'
import { fetchGuide, fetchInteraction, setFavorite } from '../../services/api'
import { sessionVersion } from '../../services/session'
import { useResource } from '../../composables/resource'
import { openLogin, previewImages } from '../../platform/navigation'
import { errorMessage } from '../../platform/errors'
import SafeImage from '../../components/SafeImage.vue'
import SafeContent from '../../components/SafeContent.vue'
import ResourceState from '../../components/ResourceState.vue'
const id = ref('')
onLoad(options => { id.value = options?.id ?? '' })
const { data: guide, loading, error, load } = useResource(() => fetchGuide(id.value))
const { data: interaction, loading: interactionLoading, error: interactionError, needsLogin, load: loadInteraction } = useResource(() => fetchInteraction(id.value), true)
const busy = ref(false), actionError = ref('')
let actionVersion = 0
function clearAction(): void { actionVersion++; busy.value = false; actionError.value = '' }
onHide(clearAction)
watch(sessionVersion, clearAction)
const extraImages = computed(() => guide.value?.images.filter(image => !guide.value?.content.includes(image.url)) ?? [])
async function toggleFavorite(): Promise<void> {
  if (needsLogin.value) { openLogin(); return }
  if (busy.value || !interaction.value) return
  const version = ++actionVersion
  busy.value = true
  actionError.value = ''
  try {
    const next = await setFavorite(id.value, !interaction.value.favorited)
    if (version === actionVersion) {
      interaction.value = next
      if (guide.value) guide.value.favoriteCount = next.favoriteCount
    }
  } catch (failure) { if (version === actionVersion) actionError.value = errorMessage(failure) }
  finally { if (version === actionVersion) busy.value = false }
}
</script>
<template>
  <view class="screen">
    <ResourceState :loading="loading" :error="error" @retry="load" />
    <template v-if="guide">
      <SafeImage :src="guide.coverImageUrl" :alt="guide.title" />
      <text class="title">{{ guide.title }}</text>
      <text class="muted">{{ guide.author.nickname }} · {{ guide.travelDays || '待定' }} 天 · {{ guide.favoriteCount }} 收藏</text>
      <view class="card" v-if="guide.summary"><text class="body-text">{{ guide.summary }}</text></view>
      <view v-if="interactionError" class="card"><text class="error">{{ interactionError }}</text><button class="secondary" @tap="loadInteraction">重新读取收藏状态</button></view>
      <button v-if="needsLogin" @tap="openLogin">登录后收藏</button>
      <button v-else :disabled="busy || interactionLoading || !interaction" @tap="toggleFavorite">{{ busy ? '正在更新…' : interaction?.favorited ? '取消收藏' : '收藏这篇攻略' }}</button>
      <text v-if="actionError" class="error" role="alert">{{ actionError }}</text>
      <view class="card"><SafeContent :content="guide.content" /></view>
      <view v-for="image in extraImages" :key="image.url" class="card" @tap="previewImages(guide.images.map(item => item.url), image.url)"><SafeImage :src="image.url" :alt="image.altText || guide.title" /></view>
      <text class="muted">正文使用安全文本排版与受管图片。复杂 Markdown 格式保留为可读文本。</text>
    </template>
  </view>
</template>
