<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { fetchDestination } from '@/api/destinations'
import { ApiError } from '@/api/client'
import TravelArt from '@/components/TravelArt.vue'
import { destinationTypeLabels, type Destination } from '@/types/destination'

const route = useRoute()
const destination = ref<Destination | null>(null)
const parent = ref<Destination | null>(null)
const loading = ref(true)
const error = ref('')
const imageFailed = ref(false)
const location = computed(() => destination.value ? [...new Set([destination.value.country, destination.value.province, destination.value.city].filter(Boolean))].join(' · ') : '')
let version = 0
async function load(): Promise<void> {
  const request = ++version
  loading.value = true; error.value = ''; destination.value = null; parent.value = null; imageFailed.value = false
  try {
    const value = await fetchDestination(String(route.params.id ?? ''))
    if (request !== version) return
    destination.value = value
    if (value.parentId) {
      const ancestor = await fetchDestination(value.parentId).catch(() => null)
      if (request === version) parent.value = ancestor
    }
  } catch (failure) {
    if (request === version) error.value = failure instanceof ApiError ? failure.message : '目的地加载失败，请稍后再试'
  } finally { if (request === version) loading.value = false }
}
watch(() => route.params.id, () => { void load() }, { immediate: true })
</script>

<template>
  <section class="app-container py-8 lg:py-12">
    <nav aria-label="位置导航" class="mb-6 flex flex-wrap items-center gap-2 text-sm text-ink-soft">
      <RouterLink to="/destinations" class="text-pine hover:underline">目的地</RouterLink>
      <template v-if="parent"><span aria-hidden="true">/</span><RouterLink :to="{ name: 'destination-detail', params: { id: parent.id } }" class="text-pine hover:underline">{{ parent.name }}</RouterLink></template>
      <template v-if="destination"><span aria-hidden="true">/</span><span>{{ destination.name }}</span></template>
    </nav>
    <div v-if="loading" role="status" class="surface-card p-12 text-center text-ink-soft">正在打开这一程的风景…</div>
    <div v-else-if="error" role="alert" class="surface-card p-10 text-center">
      <h1 class="font-serif text-2xl text-pine-deep">{{ error }}</h1>
      <div class="mt-5 flex flex-wrap justify-center gap-3"><RouterLink to="/destinations" class="btn-primary">返回目的地列表</RouterLink><button class="btn-ghost" @click="load">重新加载</button></div>
    </div>
    <article v-else-if="destination" class="surface-card overflow-hidden">
      <div class="grid lg:grid-cols-[1.05fr_1fr]">
        <div class="aspect-[4/3] overflow-hidden bg-pine-soft/30 lg:aspect-auto lg:min-h-[28rem]">
          <img v-if="destination.coverImageUrl && !imageFailed" :src="destination.coverImageUrl" :alt="destination.name"
            class="h-full w-full object-cover" @error="imageFailed = true" />
          <TravelArt v-else variant="hero" class="h-full min-h-full w-full" aria-hidden="true" />
        </div>
        <div class="p-6 sm:p-10 lg:p-12">
          <span class="rounded-full bg-pine-soft/60 px-3 py-1 text-xs text-pine-deep">{{ destinationTypeLabels[destination.destinationType] }}</span>
          <p class="mt-6 text-sm text-ink-soft">{{ location }}</p>
          <h1 class="mt-2 break-words font-serif text-4xl text-pine-deep sm:text-5xl">{{ destination.name }}</h1>
          <p v-if="destination.status === 'INACTIVE'" role="status" class="mt-5 rounded-xl border border-ember/30 bg-ember/10 p-3 text-sm text-ink">该目的地已停用，保留历史信息，暂不用于新的旅行内容。</p>
          <p class="mt-6 whitespace-pre-line leading-loose text-ink-soft">{{ destination.description || '这里的目的地介绍还在整理中。' }}</p>
          <dl class="mt-8 border-t border-pine/10 pt-5"><dt class="text-xs tracking-wide text-ink-soft">适合季节</dt><dd class="mt-2 text-sm text-pine-deep">{{ destination.recommendedSeasons || '暂无季节信息' }}</dd></dl>
          <div class="mt-8 flex flex-wrap gap-3"><RouterLink :to="{ name:'guides',query:{ destinationId:destination.id } }" class="btn-primary">看看这里的旅行攻略</RouterLink><RouterLink to="/destinations" class="btn-ghost">继续探索目的地</RouterLink></div>
        </div>
      </div>
    </article>
  </section>
</template>
