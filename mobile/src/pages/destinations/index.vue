<script setup lang="ts">
import { ref } from 'vue'
import { onPullDownRefresh } from '@dcloudio/uni-app'
import type { DestinationType } from '../../../../frontend/src/types/destination'
import { fetchDestinations } from '../../services/api'
import { useResource } from '../../composables/resource'
import { openDetail } from '../../platform/navigation'
import ContentCard from '../../components/ContentCard.vue'
import ResourceState from '../../components/ResourceState.vue'
import PageNavigation from '../../components/PageNavigation.vue'
const page = ref(1), keyword = ref(''), type = ref<DestinationType>('CITY')
const { data, loading, error, load } = useResource(() => fetchDestinations({ page: page.value, size: 10, keyword: keyword.value, destinationType: type.value }))
function changePage(value: number): void { page.value = value; void load() }
function choose(value: DestinationType): void { type.value = value; changePage(1) }
onPullDownRefresh(async () => { await load(); uni.stopPullDownRefresh() })
</script>
<template>
  <view class="screen">
    <text class="title">探索目的地</text>
    <view class="row"><button :class="{ secondary: type !== 'CITY' }" @tap="choose('CITY')">城市</button><button :class="{ secondary: type !== 'SCENIC_SPOT' }" @tap="choose('SCENIC_SPOT')">景点</button></view>
    <input v-model="keyword" maxlength="100" placeholder="搜索目的地" confirm-type="search" @confirm="changePage(1)" />
    <button class="secondary" :disabled="loading" @tap="changePage(1)">查找目的地</button>
    <ResourceState :loading="loading" :error="error" :empty="!!data && !data.records.length" @retry="load" />
    <ContentCard v-for="destination in data?.records" :key="destination.id" :title="destination.name" :cover="destination.coverImageUrl" :description="destination.description" :meta="destination.city || destination.province || destination.country" @open="openDetail('destination', destination.id)" />
    <PageNavigation v-if="data" :current="data.current" :pages="data.pages" :total="data.total" :disabled="loading" @change="changePage" />
  </view>
</template>
