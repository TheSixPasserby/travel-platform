<script setup lang="ts">
import { ref } from 'vue'
import { onPullDownRefresh } from '@dcloudio/uni-app'
import { fetchFavorites } from '../../services/api'
import { useResource } from '../../composables/resource'
import { openDetail } from '../../platform/navigation'
import ContentCard from '../../components/ContentCard.vue'
import ResourceState from '../../components/ResourceState.vue'
import PageNavigation from '../../components/PageNavigation.vue'
const page = ref(1)
const { data, loading, error, needsLogin, load } = useResource(() => fetchFavorites(page.value), true)
function changePage(value: number): void { page.value = value; void load() }
onPullDownRefresh(async () => { await load(); uni.stopPullDownRefresh() })
</script>
<template>
  <view class="screen">
    <text class="title">我的收藏</text>
    <ResourceState :loading="loading" :error="error" :needs-login="needsLogin" :empty="!!data && !data.total" @retry="load" />
    <ContentCard v-for="guide in data?.records" :key="guide.id" :title="guide.title" :cover="guide.coverImageUrl" :description="guide.summary" @open="openDetail('guide', guide.id)" />
    <PageNavigation v-if="data" :current="data.current" :pages="data.pages" :total="data.total" :disabled="loading" @change="changePage" />
  </view>
</template>
