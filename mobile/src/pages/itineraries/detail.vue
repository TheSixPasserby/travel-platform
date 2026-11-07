<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { fetchItinerary } from '../../services/api'
import { useResource } from '../../composables/resource'
import { openDetail } from '../../platform/navigation'
import ResourceState from '../../components/ResourceState.vue'
const id = ref('')
onLoad(options => { id.value = options?.id ?? '' })
const { data: itinerary, loading, error, needsLogin, load } = useResource(() => fetchItinerary(id.value), true)
</script>
<template>
  <view class="screen">
    <ResourceState :loading="loading" :error="error" :needs-login="needsLogin" @retry="load" />
    <template v-if="itinerary">
      <text class="title">{{ itinerary.title }}</text>
      <text class="muted">{{ itinerary.startDate }} 至 {{ itinerary.endDate }} · {{ itinerary.status === 'ACTIVE' ? '进行中' : '已归档' }}</text>
      <view v-if="itinerary.notes" class="card"><text class="body-text">{{ itinerary.notes }}</text></view>
      <view v-if="!itinerary.days.length" class="card muted">还没有每日安排，可在 Web 编辑。</view>
      <view v-for="day in itinerary.days" :key="day.id" class="card">
        <text class="subtitle">第 {{ day.dayNo }} 天 · {{ day.travelDate }}</text>
        <text v-if="day.title" class="body-text">{{ day.title }}</text>
        <text v-if="day.notes" class="muted body-text">{{ day.notes }}</text>
        <view v-for="item in day.items" :key="item.id" class="timeline-item">
          <text class="label">{{ item.startTime?.slice(0, 5) || '时间待定' }} {{ item.endTime ? '— ' + item.endTime.slice(0, 5) : '' }}</text>
          <text class="subtitle">{{ item.placeName }}</text>
          <text v-if="item.addressText" class="muted body-text">{{ item.addressText }}</text>
          <text v-if="item.notes" class="body-text">{{ item.notes }}</text>
          <button v-if="item.destination?.status === 'ACTIVE'" class="secondary" @tap="openDetail('destination', item.destination.id)">查看目的地</button>
        </view>
      </view>
    </template>
  </view>
</template>
<style scoped>
.timeline-item { margin: 16px 0; padding: 4px 0 4px 14px; border-left: 3px solid #c87545; }
</style>
