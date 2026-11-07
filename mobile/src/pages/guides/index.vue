<script setup lang="ts">
import { ref } from 'vue'
import { onPullDownRefresh } from '@dcloudio/uni-app'
import { fetchGuides } from '../../services/api'
import { useResource } from '../../composables/resource'
import { openDetail } from '../../platform/navigation'
import ContentCard from '../../components/ContentCard.vue'
import ResourceState from '../../components/ResourceState.vue'
import PageNavigation from '../../components/PageNavigation.vue'
const page = ref(1), keyword = ref('')
const { data, loading, error, load } = useResource(() => fetchGuides({ page: page.value, size: 10, keyword: keyword.value }))
function changePage(value: number): void { page.value = value; void load() }
function search(): void { changePage(1) }
onPullDownRefresh(async () => { await load(); uni.stopPullDownRefresh() })
</script>
<template>
  <view class="screen">
    <text class="title">旅行攻略</text>
    <input v-model="keyword" maxlength="100" placeholder="搜索标题或摘要" confirm-type="search" @confirm="search" />
    <button class="secondary" :disabled="loading" @tap="search">查找攻略</button>
    <ResourceState :loading="loading" :error="error" :empty="!!data && !data.records.length" @retry="load" />
    <ContentCard v-for="guide in data?.records" :key="guide.id" :title="guide.title" :cover="guide.coverImageUrl" :description="guide.summary" :meta="`${guide.author.nickname} · ${guide.favoriteCount} 收藏`" @open="openDetail('guide', guide.id)" />
    <PageNavigation v-if="data" :current="data.current" :pages="data.pages" :total="data.total" :disabled="loading" @change="changePage" />
  </view>
</template>
