<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { fetchAiCapabilities, previewAiDay, previewAiItinerary } from '@/api/ai'
import { importItinerary } from '@/api/itineraries'
import { ApiError } from '@/api/client'
import AiItineraryForm from '@/components/AiItineraryForm.vue'
import AiItineraryPreview from '@/components/AiItineraryPreview.vue'
import { useAuthStore } from '@/stores/auth'
import type {
  AiBudgetEstimate,
  AiCandidate,
  AiCapabilities,
  AiItem,
  AiPreview,
  AiRequirements,
} from '@/types/ai'
import type { ItineraryImportInput } from '@/types/itinerary'

import type { AiDraftItem as DraftItem, AiDraft as Draft } from '@/types/ai'
type Caps = 'loading' | 'ready' | 'error'

const router = useRouter(),
  auth = useAuthStore()
const caps = ref<AiCapabilities | null>(null),
  capsState = ref<Caps>('loading'),
  capsError = ref('')
const requirements = ref<AiRequirements | null>(null),
  draft = ref<Draft | null>(null),
  candidates = ref<AiCandidate[]>([]),
  warnings = ref<string[]>([]),
  budget = ref<AiBudgetEstimate | null>(null),
  cityName = ref('')
const previewLoading = ref(false),
  previewError = ref(''),
  stale = ref(false),
  dayBusy = ref<string | null>(null),
  dayError = ref('')
const saving = ref(false),
  saveError = ref(''),
  saveOpen = ref(false),
  clientRequestId = ref('')
let capsVersion = 0,
  reqVersion = 0,
  keyDirty = true,
  context = 0,
  previewStarted = false,
  editVersion = 0
const dayVersions = new Map<string, number>()
const message = (error: unknown) =>
  error instanceof ApiError ? error.message : '操作没有完成，请稍后重试'
function expired(): void {
  void router.replace({ name: 'login', query: { redirect: '/itineraries/ai' } })
}
function uuid(): string {
  const random = crypto.randomUUID?.bind(crypto)
  if (random) return random()
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (char) => {
    const value = Math.floor(Math.random() * 16)
    return (char === 'x' ? value : (value & 0x3) | 0x8).toString(16)
  })
}
async function loadCaps(): Promise<void> {
  const request = ++capsVersion
  capsState.value = 'loading'
  capsError.value = ''
  try {
    const data = await fetchAiCapabilities()
    if (request !== capsVersion) return
    caps.value = data
    capsState.value = 'ready'
  } catch (error) {
    if (request !== capsVersion) return
    caps.value = null
    capsState.value = 'error'
    capsError.value = message(error)
  }
}
const basisLabel = computed(() =>
  requirements.value?.budgetBasis === 'TOTAL' ? '整程总预算' : '每人预算',
)
const usedIds = computed(
  () =>
    new Set(
      (draft.value?.days ?? []).flatMap((day) =>
        day.items.map((item) => item.destinationId).filter((id): id is string => Boolean(id)),
      ),
    ),
)
/** 需求变化即刻作废旧预览：迟到的响应会因版本号不匹配被丢弃，页面给出明确说明。 */
function markStale(): void {
  if (!previewStarted && !draft.value && !previewLoading.value) return
  reqVersion++
  previewLoading.value = false
  dayBusy.value = null
  dayError.value = ''
  draft.value = null
  candidates.value = []
  warnings.value = []
  budget.value = null
  stale.value = true
  previewError.value = ''
  saveOpen.value = false
  saveError.value = ''
  dayVersions.clear()
  keyDirty = true
}
function clearPrivate(): void {
  context++
  reqVersion++
  previewLoading.value = false
  dayBusy.value = null
  dayError.value = ''
  requirements.value = null
  draft.value = null
  candidates.value = []
  warnings.value = []
  budget.value = null
  stale.value = false
  saveOpen.value = false
  saveError.value = ''
  saving.value = false
  dayVersions.clear()
  keyDirty = true
  clientRequestId.value = ''
  previewStarted = false
}
function onDirty(next: AiRequirements): void {
  if (requirements.value && JSON.stringify(requirements.value) !== JSON.stringify(next)) {
    reqVersion++
    previewLoading.value = false
    dayBusy.value = null
    saveOpen.value = false
  }
  requirements.value = next
  keyDirty = true
}
function onInvalidate(next: AiRequirements): void {
  requirements.value = next
  keyDirty = true
  markStale()
}
async function generate(next: AiRequirements): Promise<void> {
  if (previewLoading.value) return
  const request = ++reqVersion
  context++
  requirements.value = next
  cityName.value = ''
  previewLoading.value = true
  previewError.value = ''
  dayError.value = ''
  saveOpen.value = false
  saveError.value = ''
  keyDirty = true
  previewStarted = true
  stale.value = false
  try {
    const data = await previewAiItinerary(next)
    if (request !== reqVersion) return
    applyPreview(data)
  } catch (error) {
    if (request !== reqVersion) return
    if (error instanceof ApiError && error.status === 401) {
      expired()
      return
    }
    // 失败时保留已有预览与用户编辑，只用错误提示告知本次生成没有成功。
    previewError.value = message(error)
  } finally {
    if (request === reqVersion) previewLoading.value = false
  }
}
/** 名称与地址必须以真实候选为准；同一景点在整程中只保留第一次出现。 */
function normalizeItem(item: AiItem, seen: Set<string>): DraftItem | null {
  if (item.kind === 'FREE_TIME')
    return {
      kind: 'FREE_TIME',
      destinationId: null,
      placeName: '自由活动',
      addressText: null,
      startTime: item.startTime,
      endTime: item.endTime,
      notes: item.notes ?? '',
      estimatedCost: item.estimatedCost,
    }
  const spot = item.destinationId
    ? (candidates.value.find((candidate) => candidate.id === item.destinationId) ?? null)
    : null
  if (item.destinationId && !spot) return null
  if (item.destinationId && seen.has(item.destinationId)) return null
  if (item.destinationId) seen.add(item.destinationId)
  return {
    kind: 'SCENIC_SPOT',
    destinationId: item.destinationId,
    placeName: spot?.name ?? item.placeName,
    addressText: spot?.address ?? item.addressText,
    startTime: item.startTime,
    endTime: item.endTime,
    notes: item.notes ?? '',
    estimatedCost: item.estimatedCost,
  }
}
function applyPreview(data: AiPreview): void {
  candidates.value = data.candidates
  const seen = new Set<string>()
  draft.value = {
    title: data.title,
    summary: data.summary,
    notes: '',
    days: data.days.map((day) => ({
      dayNo: day.dayNo,
      travelDate: day.travelDate,
      title: day.title,
      items: day.items
        .map((item) => normalizeItem(item, seen))
        .filter((item): item is DraftItem => item !== null),
    })),
  }
  warnings.value = data.warnings
  budget.value = data.budgetEstimate
  cityName.value = data.city.name
  stale.value = false
  dayVersions.clear()
  previewError.value = ''
}
function onEdit(date: string | null): void {
  keyDirty = true
  editVersion++
  if (date) dayVersions.set(date, (dayVersions.get(date) ?? 0) + 1)
}
const itemCount = computed(
  () => draft.value?.days.reduce((total, day) => total + day.items.length, 0) ?? 0,
)
function onAdd(date: string, spot: AiCandidate): void {
  const day = draft.value?.days.find((item) => item.travelDate === date)
  if (!day || usedIds.value.has(spot.id) || day.items.length >= 8 || itemCount.value >= 60) return
  day.items.push({
    kind: 'SCENIC_SPOT',
    destinationId: spot.id,
    placeName: spot.name,
    addressText: spot.address,
    startTime: '',
    endTime: '',
    notes: '',
    estimatedCost: '',
  })
  onEdit(date)
}
function onAddFree(date: string): void {
  const day = draft.value?.days.find((item) => item.travelDate === date)
  if (!day || day.items.length >= 8 || itemCount.value >= 60) return
  day.items.push({
    kind: 'FREE_TIME',
    destinationId: null,
    placeName: '自由活动',
    addressText: null,
    startTime: '',
    endTime: '',
    notes: '',
    estimatedCost: '',
  })
  onEdit(date)
}
function onRemove(date: string, index: number): void {
  const day = draft.value?.days.find((item) => item.travelDate === date)
  if (!day || index < 0 || index >= day.items.length) return
  day.items.splice(index, 1)
  onEdit(date)
}
function onMove(date: string, index: number, delta: number): void {
  const day = draft.value?.days.find((item) => item.travelDate === date)
  if (!day) return
  const target = index + delta
  if (target < 0 || target >= day.items.length) return
  const [moved] = day.items.splice(index, 1)
  if (moved) day.items.splice(target, 0, moved)
  onEdit(date)
}
async function regenerate(date: string): Promise<void> {
  const current = draft.value,
    body = requirements.value
  if (!current || !body || previewLoading.value || dayBusy.value) return
  const request = reqVersion,
    dayVersion = dayVersions.get(date) ?? 0,
    allEdits = editVersion
  dayBusy.value = date
  dayError.value = ''
  const used = [
    ...new Set(
      current.days
        .filter((day) => day.travelDate !== date)
        .flatMap((day) =>
          day.items.map((item) => item.destinationId).filter((id): id is string => Boolean(id)),
        ),
    ),
  ]
  try {
    const data = await previewAiDay({
      requirements: body,
      targetDate: date,
      usedDestinationIds: used,
    })
    if (request !== reqVersion) return
    if ((dayVersions.get(date) ?? 0) !== dayVersion || allEdits !== editVersion) {
      dayError.value = '行程已发生编辑，本次单日结果已忽略，可按最新安排再次生成'
      return
    }
    const day = current.days.find((item) => item.travelDate === date)
    if (!day || data.day.travelDate !== date || data.day.dayNo !== day.dayNo) return
    const blocked = new Set(
      current.days
        .filter((item) => item.travelDate !== date)
        .flatMap((item) =>
          item.items.map((entry) => entry.destinationId).filter((id): id is string => Boolean(id)),
        ),
    )
    const items: DraftItem[] = []
    for (const item of data.day.items) {
      if (item.kind === 'FREE_TIME') {
        items.push({
          kind: 'FREE_TIME',
          destinationId: null,
          placeName: '自由活动',
          addressText: null,
          startTime: item.startTime,
          endTime: item.endTime,
          notes: item.notes ?? '',
          estimatedCost: item.estimatedCost,
        })
        continue
      }
      if (!item.destinationId || blocked.has(item.destinationId)) continue
      const spot = candidates.value.find((candidate) => candidate.id === item.destinationId) ?? null
      blocked.add(item.destinationId)
      items.push({
        kind: 'SCENIC_SPOT',
        destinationId: item.destinationId,
        placeName: spot?.name ?? item.placeName,
        addressText: spot?.address ?? item.addressText,
        startTime: item.startTime,
        endTime: item.endTime,
        notes: item.notes ?? '',
        estimatedCost: item.estimatedCost,
      })
    }
    day.title = data.day.title || day.title
    day.items = items
    warnings.value = data.warnings
    onEdit(date)
  } catch (error) {
    if (request !== reqVersion) return
    if (error instanceof ApiError && error.status === 401) {
      expired()
      return
    }
    dayError.value = message(error)
  } finally {
    if (request === reqVersion && dayBusy.value === date) dayBusy.value = null
  }
}
const codepoints = (value: string): number => [...value].length
const moneyPattern = /^(0|[1-9][0-9]{0,7})(\.[0-9]{1,2})?$/
const editedCosts = computed(() => {
  let cents = 0,
    unknown = false
  for (const day of draft.value?.days ?? [])
    for (const item of day.items) {
      const amount = item.estimatedCost.trim()
      if (moneyPattern.test(amount)) cents += Math.round(Number(amount) * 100)
      else unknown = true
    }
  return {
    perPerson: (cents / 100).toFixed(2),
    total: ((cents * (requirements.value?.travelers ?? 1)) / 100).toFixed(2),
    unknown,
  }
})
function previewProblem(): string {
  const current = draft.value,
    request = requirements.value
  if (!current || !request) return '请先生成行程预览'
  if (!current.title.trim()) return '请填写行程名称'
  if (itemCount.value > 60) return 'AI行程最多60个项目，请先删减'
  if (current.days.some((day) => day.items.length < 1 || day.items.length > 8))
    return '每天须安排1至8个项目，可添加自由活动'
  const visited = new Set<string>()
  for (const day of current.days)
    for (const item of day.items) {
      if (item.estimatedCost.trim() && !moneyPattern.test(item.estimatedCost.trim()))
        return '项目费用须为非负金额，最多两位小数'
      if (item.kind === 'SCENIC_SPOT') {
        if (
          !item.destinationId ||
          visited.has(item.destinationId) ||
          request.avoidIds.includes(item.destinationId)
        )
          return '景点不能重复或包含避免的地点'
        visited.add(item.destinationId)
      }
    }
  if (request.mustVisitIds.some((id) => !visited.has(id)))
    return '必须去的景点尚未全部安排，请补充项目或调整需求'
  return ''
}
/** 用最终提交体校验长度：备注不会被截断，超长时要求用户自己删减。 */
function limitProblem(body: ItineraryImportInput): string {
  const check = (value: string | null | undefined, limit: number, label: string): string =>
    value && codepoints(value) > limit
      ? `${label}共 ${codepoints(value)} 个字符，超过 ${limit} 的上限，请先删减后再保存（内容不会被截断）。`
      : ''
  const root = check(body.notes, 2000, '我的备注（含预算说明）')
  if (root) return root
  const title = check(body.title, 160, '行程名称')
  if (title) return title
  for (const day of body.days) {
    const dayTitle = check(day.title, 160, `${day.travelDate} 的安排名称`)
    if (dayTitle) return dayTitle
    for (const item of day.items) {
      const itemNotes = check(
        item.notes,
        2000,
        `${day.travelDate}「${item.placeName}」的备注（含估算说明）`,
      )
      if (itemNotes) return itemNotes
      const place = check(item.placeName, 160, `${day.travelDate} 的地点名称`)
      if (place) return place
      const address = check(item.addressText, 300, `${day.travelDate}「${item.placeName}」的地址`)
      if (address) return address
    }
  }
  return ''
}
function openSave(): void {
  if (!draft.value || saving.value || dayBusy.value) return
  const body = importBody()
  if (!body) return
  const problem = previewProblem() || limitProblem(body)
  if (problem) {
    saveError.value = problem
    saveOpen.value = false
    return
  }
  if (keyDirty || !clientRequestId.value) {
    clientRequestId.value = uuid()
    keyDirty = false
  }
  saveError.value = ''
  saveOpen.value = true
}
function spotOf(destinationId: string | null): AiCandidate | null {
  if (!destinationId) return null
  return candidates.value.find((candidate) => candidate.id === destinationId) ?? null
}
function importBody(): ItineraryImportInput | null {
  const current = draft.value,
    body = requirements.value
  if (!current || !body) return null
  const marker = `【预算说明】币种 ${body.currency}，人数 ${body.travelers} 人，口径为${basisLabel.value}，预算金额 ${body.budgetAmount}。初始AI人均估算 ${budget.value?.perPerson ?? '待定'}，总额 ${budget.value?.total ?? '待定'}。当前已填写项目人均合计 ${editedCosts.value.perPerson}，总额 ${editedCosts.value.total}${editedCosts.value.unknown ? '，另有费用待定项目' : ''}。费用均为 AI 估算，存在不确定性，请以实际票价与消费为准。`
  const rootNotes = [
    current.notes.trim(),
    current.summary.trim() ? `【行程说明】${current.summary.trim()}` : '',
    marker,
    budget.value?.notes ? `【初始估算范围】${budget.value.notes}` : '',
  ]
    .filter(Boolean)
    .join('\n\n')
  return {
    clientRequestId: clientRequestId.value,
    title: current.title.trim() || 'AI 行程',
    destinationId: body.cityId,
    startDate: body.startDate,
    endDate: body.endDate,
    notes: rootNotes,
    days: current.days.map((day) => ({
      travelDate: day.travelDate,
      title: day.title.trim() || null,
      notes: null,
      items: day.items.map((item) => {
        if (item.kind === 'FREE_TIME')
          return {
            kind: 'FREE_TIME' as const,
            destinationId: null,
            placeName: '自由活动',
            addressText: null,
            startTime: item.startTime || null,
            endTime: item.endTime || null,
            notes: `${item.notes ? `${item.notes}；` : ''}费用约 ${item.estimatedCost.trim() || '待定'} 元/人（AI 估算）`,
          }
        const spot = spotOf(item.destinationId)
        const estimate = item.estimatedCost.trim() || '待定'
        return {
          kind: 'SCENIC_SPOT' as const,
          destinationId: item.destinationId,
          placeName: spot?.name ?? item.placeName,
          addressText: spot?.address ?? item.addressText,
          startTime: item.startTime || null,
          endTime: item.endTime || null,
          notes: `${item.notes ? `${item.notes}；` : ''}费用约 ${estimate} 元/人（AI 估算）`,
        }
      }),
    })),
  }
}
async function confirmSave(): Promise<void> {
  if (saving.value || dayBusy.value) return
  if (keyDirty || !clientRequestId.value) {
    clientRequestId.value = uuid()
    keyDirty = false
  }
  const body = importBody()
  if (!body) return
  const problem = previewProblem() || limitProblem(body)
  if (problem) {
    saveError.value = problem
    return
  }
  const requestContext = context,
    identity = auth.token
  const current = () => requestContext === context && identity === auth.token
  saving.value = true
  saveError.value = ''
  try {
    const created = await importItinerary(body)
    if (!current()) return
    saveOpen.value = false
    keyDirty = true
    await router.push({ name: 'itinerary-detail', params: { id: created.id } })
  } catch (error) {
    if (!current()) return
    if (error instanceof ApiError && error.status === 401) {
      expired()
      return
    }
    saveError.value = message(error)
  } finally {
    if (current()) saving.value = false
  }
}
const saveSummary = computed(() => {
  const current = draft.value
  if (!current) return []
  const body = importBody()
  return current.days.flatMap((day, dayIndex) =>
    day.items.map((item, index) => ({
      date: day.travelDate,
      name: item.placeName,
      notes: body?.days[dayIndex]?.items[index]?.notes,
      cost: item.estimatedCost,
    })),
  )
})
function closeSave(): void {
  saveOpen.value = false
  saveError.value = ''
}
const finalRootNotes = computed(() => importBody()?.notes ?? '')
watch(
  [() => auth.token, () => auth.user?.id],
  () => {
    clearPrivate()
  },
)
onMounted(() => {
  void loadCaps()
})
onBeforeUnmount(() => {
  context++
  reqVersion++
  capsVersion++
})
</script>
<template>
  <section data-test="ai-page" class="app-container px-4 py-8 sm:px-6 lg:px-8 lg:py-12">
    <header class="mb-8 flex flex-wrap items-end justify-between gap-4">
      <div class="min-w-0">
        <p class="text-xs tracking-[0.3em] text-ember">PLAN WITH A LITTLE HELP</p>
        <h1 class="mt-3 break-words font-serif text-3xl text-pine-deep sm:text-4xl">
          AI 帮我排一程
        </h1>
        <p class="mt-3 break-words text-sm text-ink-soft">
          先写下想要的旅行，再看一份可以修改的时间线；确认保存前不会创建任何行程。
        </p>
      </div>
      <RouterLink :to="{ name: 'my-itineraries' }" class="text-sm text-pine hover:underline"
        >改用手动安排 →</RouterLink
      >
    </header>
    <p v-if="capsState === 'loading'" role="status" class="surface-card p-8 text-sm text-ink-soft">
      正在确认 AI 服务是否可用…
    </p>
    <section v-else-if="capsState === 'error'" role="alert" class="surface-card p-6 sm:p-8">
      <h2 class="break-words font-serif text-2xl text-pine-deep">暂时无法确认 AI 服务状态</h2>
      <p data-test="ai-caps-error" class="mt-3 break-words text-sm text-ember">{{ capsError }}</p>
      <p class="mt-2 text-sm text-ink-soft">你可以重试，也可以先改用手动方式安排行程。</p>
      <div class="mt-5 flex flex-wrap gap-3">
        <button type="button" data-test="ai-caps-retry" class="btn-ghost" @click="loadCaps">
          重新确认
        </button>
        <RouterLink :to="{ name: 'my-itineraries' }" class="btn-ghost">手动安排行程</RouterLink>
      </div>
    </section>
    <section v-else-if="caps && (!caps.available || !caps.enabled)" class="surface-card p-6 sm:p-8">
      <h2 class="break-words font-serif text-2xl text-pine-deep">AI 行程助手暂未开放</h2>
      <p data-test="ai-unavailable" class="mt-3 break-words text-sm text-ember">
        {{ caps.reason || '服务提供方当前不可用，请稍后再试。' }}
      </p>
      <p class="mt-2 text-sm text-ink-soft">
        AI 关闭不影响手动安排：你仍然可以自己创建并编辑整程行程。
      </p>
      <div class="mt-5 flex flex-wrap gap-3">
        <RouterLink :to="{ name: 'my-itineraries' }" data-test="ai-manual-link" class="btn-primary"
          >去手动安排行程</RouterLink
        >
        <button type="button" class="btn-ghost" @click="loadCaps">再试一次</button>
      </div>
    </section>
    <template v-else-if="caps">
      <p class="mb-4 break-words text-xs text-ink-soft">
        当前模型提供方：<span data-test="ai-provider-label" class="text-pine">{{
          caps.providerLabel
        }}</span>
        · 生成需要真实模型调用，可能较慢。
      </p>
      <AiItineraryForm
        :key="auth.user?.id"
        :busy="previewLoading || saving || Boolean(dayBusy)"
        :max-days="caps.maxDays"
        @submit="generate"
        @dirty="onDirty"
        @invalidate="onInvalidate"
      />
      <p
        v-if="previewError"
        role="alert"
        data-test="ai-preview-error"
        class="mt-6 rounded-2xl border border-ember/20 bg-ember/10 p-4 text-sm text-ember"
      >
        {{ previewError }}
      </p>
      <p v-else-if="previewLoading" role="status" class="mt-6 text-sm text-ink-soft">
        正在生成结构化预览，请不要离开页面…
      </p>
      <p
        v-if="stale && !draft"
        role="status"
        data-test="ai-stale"
        class="mt-6 rounded-2xl border border-ember/20 bg-ember/5 p-4 text-sm text-ember"
      >
        需求已变更，之前的 AI 预览已作废；请重新生成后再编辑与保存。
      </p>
      <template v-if="draft">
        <AiItineraryPreview
          :draft="draft"
          :candidates="candidates"
          :warnings="warnings"
          :budget="budget"
          :busy="previewLoading || saving"
          :day-busy="dayBusy"
          :day-error="dayError"
          class="mt-8"
          @edit="onEdit"
          @regen="regenerate"
          @add="onAdd"
          @add-free="onAddFree"
          @remove="onRemove"
          @move="onMove"
        />
        <section class="surface-card mt-6 p-5 sm:p-7">
          <div class="flex flex-wrap items-center gap-3">
            <button
              type="button"
              data-test="ai-save"
              class="btn-primary"
              :disabled="saving || previewLoading"
              @click="openSave"
            >
              保存为新行程
            </button>
            <p class="min-w-0 break-words text-xs text-ink-soft">
              保存才会真正创建行程；取消不会写入任何数据。
            </p>
          </div>
          <div
            v-if="saveOpen"
            data-test="ai-save-panel"
            class="mt-5 rounded-2xl border border-pine/15 p-4 sm:p-5"
          >
            <h3 class="font-serif text-xl text-pine-deep">保存前确认</h3>
            <p class="mt-2 break-words text-sm text-ink">{{ draft.title }}</p>
            <p class="mt-1 break-words text-xs text-ink-soft">
              {{ requirements?.startDate }} → {{ requirements?.endDate }} · 目的地 {{ cityName }} ·
              {{ requirements?.travelers }} 人 · 口径 {{ basisLabel }} · 币种
              {{ requirements?.currency }}
            </p>
            <p class="mt-1 break-words text-xs text-ink-soft">
              当前项目人均合计 {{ editedCosts.perPerson }} CNY，总额 {{ editedCosts.total }} CNY。{{
                editedCosts.unknown ? '部分费用待定。' : ''
              }}估算范围与不确定性见最终说明。
            </p>
            <h4 class="mt-4 text-sm font-medium text-pine-deep">我的备注</h4>
            <p
              data-test="ai-save-notes"
              class="mt-1 whitespace-pre-wrap break-words text-xs text-ink-soft [overflow-wrap:anywhere]"
            >
              {{ finalRootNotes }}
            </p>
            <h4 class="mt-4 text-sm font-medium text-pine-deep">逐项备注与估算</h4>
            <ul class="mt-1 space-y-1">
              <li
                v-for="entry in saveSummary"
                :key="`${entry.date}-${entry.name}-${entry.cost}`"
                class="break-words text-xs text-ink-soft"
              >
                {{ entry.date }} · {{ entry.name }} · 费用约 {{ entry.cost || '待定' }} 元（AI
                估算）{{ entry.notes ? ` · ${entry.notes}` : '' }}
              </li>
            </ul>
            <p
              v-if="saveError"
              role="alert"
              data-test="ai-save-error"
              class="mt-4 break-words rounded-xl bg-ember/10 p-3 text-sm text-ember"
            >
              {{ saveError }}
            </p>
            <div class="mt-5 flex flex-wrap gap-3">
              <button
                type="button"
                data-test="ai-confirm-save"
                class="btn-primary"
                :disabled="saving || Boolean(dayBusy)"
                @click="confirmSave"
              >
                {{ saving ? '正在保存…' : '确认创建行程' }}
              </button>
              <button
                type="button"
                class="btn-ghost"
                :disabled="saving"
                @click="closeSave"
              >
                再检查一下
              </button>
            </div>
          </div>
          <p
            v-else-if="saveError"
            role="alert"
            data-test="ai-save-error"
            class="mt-4 break-words rounded-xl bg-ember/10 p-3 text-sm text-ember"
          >
            {{ saveError }}
          </p>
        </section>
      </template>
    </template>
  </section>
</template>
