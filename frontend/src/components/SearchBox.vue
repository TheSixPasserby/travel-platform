<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, useId, watch } from 'vue'
import { useRouter } from 'vue-router'
import { searchPublicContent } from '@/api/search'
import type { SearchHit } from '@/types/search'
import SiteIcon from './SiteIcon.vue'

const props = defineProps<{ modelValue: string }>()
const emit = defineEmits<{ 'update:modelValue': [string]; submit: [string] }>()
const router = useRouter()
const id = useId()
const root = ref<HTMLElement | null>(null)
const input = ref<HTMLInputElement | null>(null)
const focused = ref(false), expanded = ref(false), composing = ref(false)
const loading = ref(false), error = ref(''), activeIndex = ref(-1)
const hits = ref<SearchHit[]>([])
const HISTORY_KEY = 'travel-notes.search-history.v1'
const labels: Record<SearchHit['kind'], string> = { GUIDE: '攻略', CITY: '目的地', SCENIC_SPOT: '景点', CREATOR: '作者', TAG: '话题' }
const history = ref<string[]>(readHistory())
const keyword = computed(() => props.modelValue.trim())
const options = computed(() => keyword.value
  ? hits.value.map(hit => ({ key: `${hit.kind}-${hit.id}`, label: hit.title, detail: labels[hit.kind], href: hit.href }))
  : history.value.map((text, index) => ({ key: `history-${index}`, label: text, detail: '最近搜索', href: '' })))
let timer: ReturnType<typeof setTimeout> | undefined
let controller: AbortController | null = null
let version = 0, compositionEndedAt = 0

function readHistory(): string[] {
  try {
    const value: unknown = JSON.parse(localStorage.getItem(HISTORY_KEY) || '[]')
    return Array.isArray(value) ? [...new Set(value.filter((item): item is string => typeof item === 'string' && Boolean(item.trim()) && Array.from(item).length <= 100))].slice(0, 8) : []
  } catch { return [] }
}
function writeHistory(): void {
  try { localStorage.setItem(HISTORY_KEY, JSON.stringify(history.value)) } catch { /* Searching works without storage. */ }
}
function remember(text: string): void {
  if (!text) return
  history.value = [text, ...history.value.filter(item => item !== text)].slice(0, 8)
  writeHistory()
}
function clearHistory(): void { history.value = []; writeHistory() }
function cancel(): void {
  version++; clearTimeout(timer); controller?.abort(); controller = null; loading.value = false
}
function schedule(): void {
  cancel(); hits.value = []; error.value = ''; activeIndex.value = -1
  if (!focused.value || composing.value || !keyword.value) return
  if (Array.from(keyword.value).length > 100) { error.value = '请输入100个字符以内的关键词'; return }
  const request = version, text = keyword.value
  timer = setTimeout(async () => {
    controller = new AbortController(); loading.value = true
    try {
      const page = await searchPublicContent({ keyword: text, size: 6 }, controller.signal)
      if (request === version) hits.value = page.records
    } catch (caught) {
      if (request === version && !(caught instanceof Error && ['AbortError', 'CanceledError'].includes(caught.name)))
        error.value = '建议暂时不可用，仍可提交关键词搜索'
    } finally { if (request === version) loading.value = false }
  }, 250)
}
function focus(): void { focused.value = true; expanded.value = true; schedule() }
function close(): void { expanded.value = false; activeIndex.value = -1 }
function submit(text = keyword.value): void {
  if (composing.value || Date.now() - compositionEndedAt < 80) return
  if (Array.from(text).length > 100) { error.value = '请输入100个字符以内的关键词'; expanded.value = true; return }
  remember(text.trim()); close(); cancel(); emit('submit', text.trim())
}
function choose(index: number): void {
  const option = options.value[index]
  if (!option) return
  remember(keyword.value || option.label); close(); cancel()
  if (option.href.startsWith('/') && !option.href.startsWith('//')) void router.push(option.href)
  else { emit('update:modelValue', option.label); emit('submit', option.label) }
}
function keydown(event: KeyboardEvent): void {
  if (composing.value || event.isComposing || Date.now() - compositionEndedAt < 80) return
  if (event.key === 'Escape') { event.preventDefault(); close(); return }
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault(); expanded.value = true
    const count = options.value.length
    if (count) activeIndex.value = activeIndex.value < 0
      ? (event.key === 'ArrowDown' ? 0 : count - 1)
      : (activeIndex.value + (event.key === 'ArrowDown' ? 1 : count - 1) + count) % count
  }
  if (event.key === 'Enter') {
    event.preventDefault()
    if (expanded.value && activeIndex.value >= 0) choose(activeIndex.value)
    else submit()
  }
}
function clearInput(): void {
  cancel(); hits.value = []; error.value = ''; activeIndex.value = -1; expanded.value = true
  emit('update:modelValue', ''); emit('submit', '')
  void nextTick(() => input.value?.focus())
}
function change(event: Event): void {
  const value = (event.target as HTMLInputElement).value
  if (!value && !composing.value) clearInput()
  else emit('update:modelValue', value)
}
function compositionStart(): void { composing.value = true; focused.value = true; expanded.value = true; cancel(); hits.value = [] }
function compositionEnd(): void { composing.value = false; compositionEndedAt = Date.now(); schedule() }
function focusOut(event: FocusEvent): void {
  if (!root.value?.contains(event.relatedTarget as Node | null)) { focused.value = false; close(); cancel() }
}
watch(() => props.modelValue, () => { if (!composing.value) schedule() })
onBeforeUnmount(cancel)
</script>

<template>
  <div ref="root" class="global-search" @focusout="focusOut">
    <form class="search-input-row" role="search" @submit.prevent="submit()">
      <SiteIcon name="explore" aria-hidden="true" />
      <input :id="`${id}-input`" ref="input" :value="modelValue" data-test="global-search-input"
        type="search" role="combobox" autocomplete="off" maxlength="200"
        aria-label="搜索攻略、城市、景点、作者或话题" aria-autocomplete="list"
        :aria-expanded="expanded" :aria-controls="`${id}-suggestions`"
        :aria-activedescendant="expanded && activeIndex >= 0 ? `${id}-option-${activeIndex}` : undefined"
        placeholder="攻略、城市、景点、作者或话题" @input="change" @focus="focus" @keydown="keydown"
        @compositionstart="compositionStart" @compositionend="compositionEnd" />
      <button v-if="modelValue" type="button" class="search-clear" aria-label="清空关键词" @click="clearInput"><SiteIcon name="close" /></button>
      <button type="submit" class="btn-primary">搜索</button>
    </form>
    <div v-if="expanded" class="suggestions-panel">
      <div v-if="!keyword && history.length" class="history-heading"><span>最近搜索</span><button type="button" data-test="clear-search-history" @click="clearHistory">清空记录</button></div>
      <p v-if="loading" role="status">正在查找公开内容…</p>
      <p v-else-if="error" role="status">{{ error }}</p>
      <ul :id="`${id}-suggestions`" role="listbox" aria-label="搜索建议">
        <li v-for="(option, index) in options" :id="`${id}-option-${index}`" :key="option.key" role="option"
          :aria-selected="activeIndex === index" :class="{ selected: activeIndex === index }"
          @mousedown.prevent @click="choose(index)" @pointermove="activeIndex = index">
          <span>{{ option.label }}</span><small>{{ option.detail }}</small>
        </li>
      </ul>
      <p v-if="!loading && !error && !options.length">{{ keyword ? '暂时没有建议，可直接搜索全部内容。' : '输入关键词，开始寻找旅行灵感。' }}</p>
    </div>
  </div>
</template>

<style scoped>
.global-search { position: relative; min-width: 0; }
.search-input-row { display: flex; gap: 10px; align-items: center; min-height: 78px; padding: 12px 18px; background: var(--ui-bg-elevated); border: 1px solid var(--ui-border-subtle); border-radius: var(--ui-radius-lg); box-shadow: var(--ui-shadow-md); }
.search-input-row:focus-within { border-color: var(--ui-focus-ring); }
.search-input-row > svg { width: 22px; height: 22px; flex: none; color: var(--ui-link); }
input { min-width: 0; flex: 1; width: 100%; min-height: 44px; background: transparent; outline: none; font-size: 16px; color: var(--ui-text-primary); }
.search-clear { display: grid; place-items: center; width: 44px; height: 44px; flex: none; border-radius: 50%; }
.search-clear svg { width: 18px; height: 18px; }
.search-clear:hover { background: var(--ui-bg-muted); }
.suggestions-panel { position: absolute; top: calc(100% + 8px); inset-inline: 0; z-index: 40; max-height: min(400px, 50dvh); overflow-y: auto; background: var(--ui-bg-elevated); border: 1px solid var(--ui-border-subtle); border-radius: var(--ui-radius-md); box-shadow: var(--ui-shadow-lg); padding: 8px; animation: suggestions-in 160ms ease-out; }
.suggestions-panel li { display: flex; align-items: center; gap: 12px; justify-content: space-between; min-height: 48px; padding: 10px 12px; border-radius: var(--ui-radius-sm); cursor: pointer; }
.suggestions-panel li span { overflow-wrap: anywhere; min-width: 0; }
.suggestions-panel li small { color: var(--ui-text-secondary); flex: none; }
.suggestions-panel .selected { background: var(--ui-accent-soft); }
.suggestions-panel p { padding: 12px; color: var(--ui-text-secondary); font-size: 14px; }
.history-heading { display: flex; justify-content: space-between; align-items: center; padding-inline: 12px; font-size: 13px; color: var(--ui-text-secondary); }
.history-heading button { min-height: 44px; text-decoration: underline; }
@keyframes suggestions-in { from { opacity: 0; transform: translateY(-4px); } to { opacity: 1; transform: translateY(0); } }
@media (max-width: 480px) { .search-input-row { gap: 4px; padding: 8px 10px; min-height: 66px; } .search-input-row > svg { display: none; } .search-input-row .btn-primary { padding-inline: 14px; } }
@media (prefers-reduced-motion: reduce) { .suggestions-panel { animation: none; } }
</style>
