<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { fetchDestination, fetchDestinations } from '../../services/api'
import { useResource } from '../../composables/resource'
import { openDetail, previewImages } from '../../platform/navigation'
import SafeImage from '../../components/SafeImage.vue'
import ResourceState from '../../components/ResourceState.vue'
import ContentCard from '../../components/ContentCard.vue'
import PageNavigation from '../../components/PageNavigation.vue'
const id = ref(''), page = ref(1)
onLoad(options => { id.value = options?.id ?? '' })
const { data, loading, error, load } = useResource(async () => {
  const destination = await fetchDestination(id.value)
  const spots = destination.destinationType === 'CITY' ? await fetchDestinations({ parentId: id.value, destinationType: 'SCENIC_SPOT', page: page.value, size: 10 }) : null
  return { destination, spots }
})
function changePage(value: number): void { page.value = value; void load() }
</script>
<template>
  <view class="screen">
    <ResourceState :loading="loading" :error="error" @retry="load" />
    <template v-if="data">
      <SafeImage :src="data.destination.coverImageUrl" :alt="data.destination.name" />
      <text class="title">{{ data.destination.name }}</text>
      <button v-if="data.destination.parentId" class="secondary" @tap="openDetail('destination', data.destination.parentId)">返回所属城市</button>
      <view class="card">
        <text class="body-text">{{ data.destination.description || '介绍待补充' }}</text>
        <view class="muted">建议季节 · {{ data.destination.recommendedSeasons || '待核实' }}</view>
        <view class="muted">建议游玩 · {{ data.destination.recommendedDays ? data.destination.recommendedDays + ' 天' : data.destination.recommendedDurationMinutes ? data.destination.recommendedDurationMinutes + ' 分钟' : '待核实' }}</view>
        <view>地址 · {{ data.destination.address || '待核实' }}</view>
        <view>开放信息 · {{ data.destination.openingHours || '待核实' }}</view>
        <view>票务信息 · {{ data.destination.ticketInfo || '待核实' }}</view>
        <view class="muted">核验日期 · {{ data.destination.verifiedAt?.slice(0, 10) || '待核实' }}，出行前请复核</view>
        <text v-if="data.destination.sourceUrl" class="muted body-text" selectable>资料来源 · {{ data.destination.sourceUrl }}</text>
      </view>
      <view v-for="image in data.destination.images" :key="image.id" class="card" @tap="previewImages(data.destination.images.map(item => item.url), image.url)">
        <SafeImage :src="image.url" :alt="image.altText" />
        <view class="muted">{{ image.altText }}</view>
        <view class="muted">作者 · {{ image.author }}</view>
        <view class="muted">许可 · {{ image.license }}</view>
        <text class="muted body-text" selectable>{{ image.licenseUrl }}</text>
        <text class="muted body-text" selectable>来源 · {{ image.sourceUrl }}</text>
      </view>
      <template v-if="data.spots">
        <text class="subtitle">城市里的景点</text><text v-if="!data.spots.total" class="muted">暂未收录景点</text>
        <ContentCard v-for="spot in data.spots.records" :key="spot.id" :title="spot.name" :cover="spot.coverImageUrl" :description="spot.description" @open="openDetail('destination', spot.id)" />
        <PageNavigation :current="data.spots.current" :pages="data.spots.pages" :total="data.spots.total" :disabled="loading" @change="changePage" />
      </template>
    </template>
  </view>
</template>
