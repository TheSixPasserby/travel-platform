<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchDestinations } from '@/api/destinations'
import { ApiError } from '@/api/client'
import DestinationCard from '@/components/DestinationCard.vue'
import type { PageResult } from '@/types/api'
import { destinationTypeLabels, type Destination, type DestinationQuery, type DestinationType } from '@/types/destination'

const route = useRoute()
const router = useRouter()
const keyword = ref(typeof route.query.keyword === 'string' ? route.query.keyword : '')
const allowedTypes = Object.keys(destinationTypeLabels) as DestinationType[]
const initialType = typeof route.query.type === 'string' ? route.query.type : ''
const type = ref<DestinationType | ''>(allowedTypes.includes(initialType as DestinationType) ? initialType as DestinationType : '')
const initialPage = Number(route.query.page ?? 1)
const page = ref(Number.isSafeInteger(initialPage) && initialPage > 0 ? initialPage : 1)
const result = ref<PageResult<Destination>>({ records: [], total: 0, current: page.value, size: 9, pages: 0 })
const loading = ref(false)
const error = ref('')
let version = 0

async function load(): Promise<void> {
  const request = ++version
  loading.value = true
  error.value = ''
  const params: DestinationQuery = { page: page.value, size: 9, keyword: keyword.value.trim() || undefined, destinationType: type.value || undefined }
  try {
    await router.replace({ query: { page: String(page.value), keyword: params.keyword, type: type.value || undefined } })
    const data = await fetchDestinations(params)
    if (request === version) { result.value = data; page.value = data.current }
  } catch (failure) {
    if (request === version) error.value = failure instanceof ApiError ? failure.message : '目的地加载失败，请稍后再试'
  } finally { if (request === version) loading.value = false }
}
function search(): void { page.value = 1; void load() }
function reset(): void { keyword.value = ''; type.value = ''; search() }
function turn(delta: number): void { page.value += delta; void load() }
onMounted(() => { void load() })
</script>

<template>
  <section class="app-container py-10 lg:py-16">
    <div class="flex flex-wrap items-end justify-between gap-6">
      <div>
        <p class="text-xs tracking-[0.3em] text-ember">FIND YOUR NEXT STOP</p>
        <h1 class="mt-3 font-serif text-4xl text-pine-deep sm:text-5xl">下一程，去哪里</h1>
        <p class="mt-4 max-w-xl leading-relaxed text-ink-soft">从一座城、一片山水开始，把心里的远方慢慢走近。</p>
      </div>
      <p v-if="!loading && !error" class="text-sm text-ink-soft">找到 {{ result.total }} 个目的地</p>
    </div>

    <form class="surface-card my-8 grid gap-4 p-4 sm:grid-cols-[minmax(0,1fr)_12rem_auto] sm:p-5" @submit.prevent="search">
      <div><label for="destination-keyword" class="field-label">搜索目的地</label>
        <input id="destination-keyword" v-model="keyword" data-test="keyword" class="field-input" type="search" placeholder="输入城市或景点名称" /></div>
      <div><label for="destination-type" class="field-label">目的地类型</label>
        <select id="destination-type" v-model="type" data-test="destination-type" class="field-input" @change="search">
          <option value="">全部类型</option><option v-for="(label, value) in destinationTypeLabels" :key="value" :value="value">{{ label }}</option>
        </select></div>
      <button class="btn-primary self-end" type="submit" :disabled="loading">{{ loading ? '寻找中…' : '寻找目的地' }}</button>
    </form>

    <div v-if="loading" role="status" class="surface-card p-10 text-center text-ink-soft">正在寻找山水之间的好去处…</div>
    <div v-else-if="error" role="alert" class="surface-card p-8 text-center">
      <p class="text-ink">{{ error }}</p><button data-test="retry-destinations" class="btn-ghost mt-4" @click="load">重新加载</button>
    </div>
    <div v-else-if="result.records.length === 0" class="surface-card p-10 text-center">
      <h2 class="font-serif text-2xl text-pine-deep">还没有找到目的地</h2>
      <p class="mt-3 text-sm text-ink-soft">试试换个关键词或类型，给旅程留一点新的可能。</p>
      <button class="btn-ghost mt-5" @click="reset">清空筛选</button>
    </div>
    <div v-else class="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
      <DestinationCard v-for="destination in result.records" :key="destination.id" :destination="destination" />
    </div>

    <nav v-if="!error && result.pages > 1" aria-label="目的地分页" class="mt-10 flex items-center justify-center gap-4">
      <button class="btn-ghost" :disabled="loading || page <= 1" @click="turn(-1)">上一页</button>
      <span data-test="page-status" class="text-sm tabular-nums text-ink-soft">{{ result.current }} / {{ result.pages }}</span>
      <button class="btn-ghost" data-test="next-page" :disabled="loading || page >= result.pages" @click="turn(1)">下一页</button>
    </nav>
  </section>
</template>
