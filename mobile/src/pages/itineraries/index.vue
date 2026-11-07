<script setup lang="ts">
import { ref } from 'vue'
import { onPullDownRefresh } from '@dcloudio/uni-app'
import { fetchItineraries } from '../../services/api'
import { useResource } from '../../composables/resource'
import { openDetail } from '../../platform/navigation'
import ResourceState from '../../components/ResourceState.vue'
import PageNavigation from '../../components/PageNavigation.vue'
const page = ref(1)
const { data, loading, error, needsLogin, load } = useResource(() => fetchItineraries(page.value), true)
function changePage(value: number): void { page.value = value; void load() }
onPullDownRefresh(async () => { await load(); uni.stopPullDownRefresh() })
</script>
<template>
  <view class="screen">
    <text class="title">我的私人行程</text>
    <ResourceState :loading="loading" :error="error" :needs-login="needsLogin" :empty="!!data && !data.total" @retry="load" />
    <view v-for="itinerary in data?.records" :key="itinerary.id" class="card">
      <text class="subtitle">{{ itinerary.title }}</text>
      <text class="muted">{{ itinerary.startDate }} 至 {{ itinerary.endDate }} · {{ itinerary.dayCount }} 天 · {{ itinerary.itemCount }} 个项目</text>
      <button class="secondary" @tap="openDetail('itinerary', itinerary.id)">查看每日安排</button>
    </view>
    <PageNavigation v-if="data" :current="data.current" :pages="data.pages" :total="data.total" :disabled="loading" @change="changePage" />
  </view>
</template>
