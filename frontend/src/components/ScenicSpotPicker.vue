<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { fetchDestinations } from '@/api/destinations'
import { ApiError } from '@/api/client'
import type { Destination } from '@/types/destination'

/**
 * 攻略关联景点的多选组件。
 * 只向服务端请求「当前主城市下的景点」（destinationType=SCENIC_SPOT + parentId），
 * 不在前端用静态数组伪造候选，也不跨城市混选。
 */
const props = defineProps<{
  modelValue: string[]
  cityId: string | null
  /** 已保存景点的名称，用于旧草稿在没有候选数据时仍能显示真实名称。 */
  labels?: { id: string; name: string; status?: string }[]
  disabled?: boolean
}>()
const emit = defineEmits<{ 'update:modelValue': [string[]] }>()

const MAX_SPOTS = 20
const PAGE_SIZE = 20

const keyword = ref('')
const page = ref(1)
const pages = ref(0)
const candidates = ref<Destination[]>([])
const loading = ref(false)
const error = ref('')
let version = 0

/** 已选景点名称：候选与已保存数据合并，保证旧草稿也能显示真实名称。 */
const nameOf = computed(() => {
  const map = new Map<string, string>()
  for (const item of props.labels ?? []) map.set(item.id, item.name)
  for (const item of candidates.value) map.set(item.id, item.name)
  return map
})
const selected = computed(() => props.modelValue ?? [])
const full = computed(() => selected.value.length >= MAX_SPOTS)

async function load(): Promise<void> {
  const city = props.cityId
  if (!city) {
    version++
    candidates.value = []
    pages.value = 0
    loading.value = false
    error.value = ''
    return
  }
  const request = ++version
  loading.value = true
  error.value = ''
  try {
    const data = await fetchDestinations({
      page: page.value,
      size: PAGE_SIZE,
      keyword: keyword.value.trim() || undefined,
      destinationType: 'SCENIC_SPOT',
      parentId: city
    })
    if (request !== version) return
    candidates.value = data.records
    pages.value = data.pages
    page.value = data.current
  } catch (reason) {
    if (request === version) error.value = reason instanceof ApiError ? reason.message : '景点候选暂时无法加载'
  } finally {
    if (request === version) loading.value = false
  }
}
function search(): void { page.value = 1; void load() }
function turn(delta: number): void { page.value += delta; void load() }
function toggle(id: string, checked: boolean): void {
  if (props.disabled) return
  if (checked) {
    if (full.value || selected.value.includes(id)) return
    emit('update:modelValue', [...selected.value, id])
    return
  }
  emit('update:modelValue', selected.value.filter(item => item !== id))
}
function remove(id: string): void {
  if (props.disabled) return
  emit('update:modelValue', selected.value.filter(item => item !== id))
}
function disabledCandidate(id: string): boolean {
  return Boolean(props.disabled) || (!selected.value.includes(id) && full.value)
}
watch(() => props.cityId, () => { keyword.value = ''; page.value = 1; candidates.value = []; void load() }, { immediate: true })
</script>

<template>
  <section data-test="scenic-spot-picker" class="rounded-2xl border border-pine/15 p-4">
    <div class="flex flex-wrap items-baseline justify-between gap-2">
      <h3 class="text-sm font-medium text-pine-deep">关联景点（最多 {{ MAX_SPOTS }} 个）</h3>
      <p class="text-xs text-ink-soft">{{ selected.length }} / {{ MAX_SPOTS }}</p>
    </div>
    <p v-if="!cityId" class="mt-2 text-xs text-ink-soft">请先选择主城市，才能挑选同城景点。</p>
    <template v-else>
      <p class="mt-2 text-xs text-ink-soft">候选只来自所选城市下的真实景点，可多选，最多 {{ MAX_SPOTS }} 个。</p>
      <div class="mt-3 flex flex-col gap-2 sm:flex-row">
        <label for="editor-scenic-keyword" class="sr-only">按名称搜索景点</label>
        <input id="editor-scenic-keyword" v-model="keyword" data-test="scenic-keyword" class="field-input min-w-0" type="search" :disabled="disabled" placeholder="搜索本城景点名称" @keydown.enter.prevent="search" />
        <button type="button" data-test="scenic-search" class="btn-ghost shrink-0" :disabled="disabled || loading" @click="search">搜索</button>
      </div>
      <p v-if="loading" role="status" class="mt-3 text-xs text-ink-soft">正在加载景点…</p>
      <p v-else-if="error" role="alert" class="mt-3 text-xs text-ember">{{ error }} <button type="button" class="underline" @click="load">重试</button></p>
      <ul v-else-if="candidates.length" class="mt-3 max-h-64 space-y-2 overflow-y-auto pr-1 sm:max-h-72">
        <li v-for="candidate in candidates" :key="candidate.id" data-test="scenic-candidate" class="flex items-center gap-2 text-sm text-ink">
          <input :id="`scenic-candidate-${candidate.id}`" :data-test="`scenic-candidate-${candidate.id}`" type="checkbox" class="accent-pine"
            :checked="selected.includes(candidate.id)" :disabled="disabledCandidate(candidate.id)" @change="toggle(candidate.id, ($event.target as HTMLInputElement).checked)" />
          <label :for="`scenic-candidate-${candidate.id}`" class="min-w-0 flex-1">{{ candidate.name }}</label>
          <span v-if="candidate.status === 'INACTIVE'" class="text-xs text-ember">已停用</span>
        </li>
      </ul>
      <p v-else class="mt-3 text-xs text-ink-soft">这座城市还没有可关联的景点。</p>
      <div v-if="pages > 1" class="mt-2 flex items-center gap-2 text-xs text-ink-soft">
        <button type="button" class="btn-ghost px-2 py-1" :disabled="loading || page <= 1" @click="turn(-1)">上一页</button>
        <span data-test="scenic-page-status">{{ page }} / {{ pages }}</span>
        <button type="button" data-test="scenic-next-page" class="btn-ghost px-2 py-1" :disabled="loading || page >= pages" @click="turn(1)">下一页</button>
      </div>
    </template>
    <ul v-if="selected.length" class="mt-4 flex flex-wrap gap-2 sm:gap-3">
      <li v-for="id in selected" :key="id" :data-test="`scenic-selected-${id}`" class="flex items-center gap-2 rounded-full border border-pine/25 bg-pine-soft/30 px-3 py-1 text-xs text-pine">
        <span>{{ nameOf.get(id) ?? id }}</span>
        <button type="button" :data-test="`scenic-remove-${id}`" class="text-ink-soft" :disabled="disabled" :aria-label="`移除景点 ${nameOf.get(id) ?? id}`" @click="remove(id)">×</button>
      </li>
    </ul>
  </section>
</template>
