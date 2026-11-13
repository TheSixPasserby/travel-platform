<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'

import { ApiError } from '@/api/client'
import { fetchItineraries, fetchItinerary, importGuidePlaces } from '@/api/itineraries'
import { useAuthStore } from '@/stores/auth'
import type { GuideDetail } from '@/types/guide'
import type {
  GuideTripImportInput,
  GuideTripImportResult,
  ItineraryDay,
  ItineraryDetail,
  ItinerarySummary
} from '@/types/itinerary'

/**
 * 攻略详情页「加入我的行程」右侧模态抽屉。
 *
 * - 只展示 `guide.scenicSpots` 的真实数据：ACTIVE 可勾选、失效资源禁用，不推测正文里的地点 ID；
 * - 读取当前用户自己的 ACTIVE 行程（每页 50，可继续加载），选中后拉真实 days 并选加入日期；
 * - 仅「确认加入」按需写出：POST /itineraries/guide-imports，UUID 幂等，输入变化才换新 UUID；
 * - 取消 / 预览 / 切换模式均为只读；pending 时禁止关闭与重复提交；
 * - 打开、关闭、攻略切换、登录账号变化都会让迟到响应作废；401 保留当前选择并引导登录；
 * - AI 按钮只 emit `plan-ai`（公开的城市/景点 ID），由父级进入确认表单，不直接调用模型。
 */
const props = withDefaults(
  defineProps<{
    open: boolean
    guide: GuideDetail | null
    initialSelectedIds?: string[]
  }>(),
  { initialSelectedIds: () => [] }
)

const emit = defineEmits<{
  close: []
  'plan-ai': [ids: string[]]
}>()

const auth = useAuthStore()

const panel = ref<HTMLElement | null>(null)
/** 关闭动画期间仍需保留 DOM，因此用独立可见性标记而不是 open 本身。 */
const visible = ref(props.open)
const mode = ref<'existing' | 'new'>('existing')
const selectedIds = ref<string[]>([])
const itineraries = ref<ItinerarySummary[]>([])
const listPage = ref(0)
const listPages = ref(0)
const listLoading = ref(false)
const listError = ref('')
const selectedItineraryId = ref('')
const detail = ref<ItineraryDetail | null>(null)
const detailLoading = ref(false)
const detailError = ref('')
const selectedDayId = ref('')
const form = ref({ title: '', startDate: '', endDate: '', travelDate: '' })
const pending = ref(false)
const result = ref<GuideTripImportResult | null>(null)
const submittedKey = ref('')
const errorMessage = ref('')
const unauthorized = ref(false)
/** 幂等键：同一 payload 重试复用，输入变化换新。 */
const requestId = ref('')
const lastKey = ref('')
/** 请求代次：打开/关闭、攻略切换、账号变化都会递增，使迟到响应作废。 */
let epoch = 0
let detailVersion = 0

/* --------------------------------- 派生只读数据 -------------------------------- */

const spots = computed(() => props.guide?.scenicSpots ?? [])
const city = computed(() => props.guide?.destination ?? null)
const cityUsable = computed(() => Boolean(city.value && city.value.status === 'ACTIVE'))
const hasSpots = computed(() => spots.value.length > 0)
const activeSpots = computed(() => spots.value.filter((spot) => spot.status === 'ACTIVE'))
const importable = computed(() => Boolean(props.guide && cityUsable.value && hasSpots.value))
const selectedSet = computed(() => new Set(selectedIds.value))
const allActiveSelected = computed(
  () =>
    activeSpots.value.length > 0 &&
    activeSpots.value.every((spot) => selectedSet.value.has(spot.id))
)
const days = computed<ItineraryDay[]>(() =>
  [...(detail.value?.days ?? [])].sort((a, b) => a.dayNo - b.dayNo)
)
const selectedDay = computed<ItineraryDay | null>(
  () => days.value.find((day) => day.id === selectedDayId.value) ?? null
)
const selectedDayLabel = computed(() => {
  const day = selectedDay.value
  if (!day) return ''
  return '第 ' + day.dayNo + ' 天 · ' + day.travelDate
})
/** 当天已存在的景点（只统计真实 destination.id，不做名称猜测）。 */
const presentSelected = computed(() => {
  const present = new Set<string>()
  for (const item of selectedDay.value?.items ?? []) {
    if (item.destination) present.add(item.destination.id)
  }
  return spots.value.filter(
    (spot) => selectedSet.value.has(spot.id) && present.has(spot.id)
  )
})
const presentSelectedNames = computed(() => presentSelected.value.map((spot) => spot.name))
const newCount = computed(() => selectedIds.value.length - presentSelected.value.length)

/** 提交体（不含 UUID）：用于判断输入是否变化，决定复用还是换新幂等键。 */
const payloadKey = computed(() =>
  JSON.stringify({
    guideId: props.guide?.id ?? '',
    destinationIds: selectedIds.value,
    itineraryId: mode.value === 'existing' ? selectedItineraryId.value || null : null,
    dayId: mode.value === 'existing' ? selectedDayId.value || null : null,
    newItinerary:
      mode.value === 'new'
        ? {
            title: form.value.title.trim(),
            startDate: form.value.startDate,
            endDate: form.value.endDate,
            travelDate: form.value.travelDate
          }
        : null
  })
)
const alreadySubmitted = computed(
  () => result.value !== null && submittedKey.value === payloadKey.value
)

const newFormMessage = computed(() => {
  if (mode.value !== 'new') return ''
  const value = form.value
  const title = value.title.trim()
  if (!title) return '请填写新行程名称'
  if (title.length > 160) return '新行程名称最多 160 个字符'
  if (!value.startDate || !value.endDate || !value.travelDate) {
    return '请填写开始日期、结束日期与旅行日期'
  }
  if (value.endDate < value.startDate) return '结束日期不能早于开始日期'
  if (value.travelDate < value.startDate || value.travelDate > value.endDate) {
    return '旅行日期需在开始与结束日期之间'
  }
  return ''
})

const blockingMessage = computed(() => {
  if (!importable.value) return ''
  if (!auth.isAuthenticated) return ''
  if (!selectedIds.value.length) return '请至少选择一个可加入的景点'
  if (selectedIds.value.length > 20) return '一次最多加入 20 个景点，请减少选择'
  if (mode.value === 'existing') {
    if (!selectedItineraryId.value) return '请选择要加入的行程'
    if (detailLoading.value) return '正在读取行程日期…'
    if (detailError.value) return '行程日期读取失败，请重新选择行程'
    // 没有 days 时后端无法定位写入日期，这里直接拦截，避免提交必然失败。
    if (!detail.value || !days.value.length) {
      return '该行程还没有日期，请先到行程详情手动添加日期'
    }
    if (!selectedDayId.value) return '请选择加入的日期'
    return ''
  }
  return newFormMessage.value
})

const hint = computed(() => {
  if (!importable.value) return ''
  if (!auth.isAuthenticated) return ''
  if (pending.value) return '正在提交，请勿关闭…'
  if (alreadySubmitted.value) return '已提交成功；如需继续添加，请调整选择或日期'
  return blockingMessage.value
})

const canSubmit = computed(
  () =>
    importable.value &&
    auth.isAuthenticated &&
    !pending.value &&
    !alreadySubmitted.value &&
    !blockingMessage.value
)

const loginTarget = computed(() => ({
  path: '/login',
  query: { redirect: props.guide ? '/guides/' + props.guide.id + '?trip=1' : '/' }
}))

/* ----------------------------------- 工具函数 ---------------------------------- */

function createRequestId(): string {
  const cryptoApi = globalThis.crypto
  if (cryptoApi && typeof cryptoApi.randomUUID === 'function') {
    return cryptoApi.randomUUID()
  }
  const bytes = new Uint8Array(16)
  if (cryptoApi && typeof cryptoApi.getRandomValues === 'function') {
    cryptoApi.getRandomValues(bytes)
  } else {
    for (let index = 0; index < bytes.length; index += 1) {
      bytes[index] = Math.floor(Math.random() * 256)
    }
  }
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0'))
  return (
    hex.slice(0, 4).join('') +
    '-' +
    hex.slice(4, 6).join('') +
    '-' +
    hex.slice(6, 8).join('') +
    '-' +
    hex.slice(8, 10).join('') +
    '-' +
    hex.slice(10, 16).join('')
  )
}

function normalizeSelection(ids: string[]): string[] {
  const wanted = new Set(ids)
  return spots.value.filter((spot) => wanted.has(spot.id)).map((spot) => spot.id)
}

function defaultSelection(): string[] {
  const usable = activeSpots.value.map((spot) => spot.id)
  const usableSet = new Set(usable)
  const picked = props.initialSelectedIds.filter((id) => usableSet.has(id))
  return props.initialSelectedIds.length ? picked : usable
}

function resetState(): void {
  epoch += 1
  mode.value = 'existing'
  selectedIds.value = defaultSelection()
  itineraries.value = []
  listPage.value = 0
  listPages.value = 0
  listLoading.value = false
  listError.value = ''
  selectedItineraryId.value = ''
  detail.value = null
  detailLoading.value = false
  detailError.value = ''
  selectedDayId.value = ''
  form.value = { title: '', startDate: '', endDate: '', travelDate: '' }
  pending.value = false
  result.value = null
  submittedKey.value = ''
  errorMessage.value = ''
  unauthorized.value = false
  requestId.value = ''
  lastKey.value = ''
}

function switchMode(next: 'existing' | 'new'): void {
  if (pending.value) return
  mode.value = next
}

function toggleSpot(id: string, event: Event): void {
  const checked = (event.target as HTMLInputElement).checked
  const next = checked
    ? normalizeSelection([...selectedIds.value, id])
    : selectedIds.value.filter((value) => value !== id)
  selectedIds.value = next
}

function toggleAll(): void {
  if (pending.value) return
  selectedIds.value = allActiveSelected.value
    ? []
    : activeSpots.value.map((spot) => spot.id)
}

/* ----------------------------------- 数据加载 ---------------------------------- */

async function loadList(reset: boolean): Promise<void> {
  if (!importable.value || !auth.isAuthenticated) return
  if (listLoading.value) return
  if (reset) {
    itineraries.value = []
    listPage.value = 0
    listPages.value = 0
    listError.value = ''
    selectedItineraryId.value = ''
    detail.value = null
    detailError.value = ''
    selectedDayId.value = ''
  }
  const currentEpoch = epoch
  const page = reset ? 1 : listPage.value + 1
  listLoading.value = true
  try {
    const data = await fetchItineraries({ page, size: 50, status: 'ACTIVE' })
    if (currentEpoch !== epoch) return
    itineraries.value = reset ? data.records : [...itineraries.value, ...data.records]
    listPage.value = data.current || page
    listPages.value = data.pages
  } catch (error) {
    if (currentEpoch !== epoch) return
    if (error instanceof ApiError && error.status === 401) unauthorized.value = true
    listError.value =
      error instanceof ApiError ? error.message : '行程列表加载失败，请稍后重试'
  } finally {
    if (currentEpoch === epoch) listLoading.value = false
  }
}

async function loadDetail(id: string): Promise<void> {
  const request = ++detailVersion
  detail.value = null
  detailError.value = ''
  selectedDayId.value = ''
  if (!id) return
  const currentEpoch = epoch
  detailLoading.value = true
  try {
    const loaded = await fetchItinerary(id)
    if (currentEpoch !== epoch || request !== detailVersion || selectedItineraryId.value !== id) return
    detail.value = loaded
    const ordered = [...loaded.days].sort((a, b) => a.dayNo - b.dayNo)
    selectedDayId.value = ordered[0]?.id ?? ''
  } catch (error) {
    if (currentEpoch !== epoch || request !== detailVersion || selectedItineraryId.value !== id) return
    if (error instanceof ApiError && error.status === 401) unauthorized.value = true
    detailError.value =
      error instanceof ApiError ? error.message : '行程详情加载失败，请稍后重试'
  } finally {
    if (currentEpoch === epoch && request === detailVersion) detailLoading.value = false
  }
}

function onItineraryChange(event: Event): void {
  if (pending.value) return
  const value = (event.target as HTMLSelectElement).value
  selectedItineraryId.value = value
  void loadDetail(value)
}

function onDayChange(event: Event): void {
  if (pending.value) return
  selectedDayId.value = (event.target as HTMLSelectElement).value
}

/* ------------------------------------ 提交 ------------------------------------ */

async function submit(): Promise<void> {
  const guide = props.guide
  if (!guide || pending.value || !canSubmit.value) return
  const key = payloadKey.value
  if (requestId.value === '' || lastKey.value !== key) {
    requestId.value = createRequestId()
    lastKey.value = key
  }
  const body: GuideTripImportInput =
    mode.value === 'existing'
      ? {
          clientRequestId: requestId.value,
          guideId: guide.id,
          destinationIds: [...selectedIds.value],
          itineraryId: selectedItineraryId.value,
          dayId: selectedDayId.value,
          newItinerary: null
        }
      : {
          clientRequestId: requestId.value,
          guideId: guide.id,
          destinationIds: [...selectedIds.value],
          itineraryId: null,
          dayId: null,
          newItinerary: {
            title: form.value.title.trim(),
            startDate: form.value.startDate,
            endDate: form.value.endDate,
            travelDate: form.value.travelDate
          }
        }
  const currentEpoch = epoch
  pending.value = true
  errorMessage.value = ''
  unauthorized.value = false
  try {
    const saved = await importGuidePlaces(body)
    if (currentEpoch !== epoch) return
    result.value = saved
    submittedKey.value = key
  } catch (error) {
    if (currentEpoch !== epoch) return
    if (error instanceof ApiError && error.status === 401) {
      unauthorized.value = true
      errorMessage.value = '登录状态已失效，请重新登录后再确认；已选景点与行程选择会保留。'
    } else {
      errorMessage.value =
        error instanceof ApiError
          ? error.message
          : '加入失败，请检查网络后重试；已选景点与行程选择会保留。'
    }
  } finally {
    if (currentEpoch === epoch) pending.value = false
  }
}

function planWithAi(): void {
  if (pending.value || !selectedIds.value.length) return
  emit('plan-ai', [...selectedIds.value])
  emit('close')
}

/* ------------------------------ 焦点 / 滚动锁（模态） ----------------------------- */

let scrollLocked = false
let previousOverflow = ''
let restoreTarget: HTMLElement | null = null

function lockScroll(): void {
  if (typeof document === 'undefined' || scrollLocked) return
  previousOverflow = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  scrollLocked = true
}

function unlockScroll(): void {
  if (typeof document === 'undefined' || !scrollLocked) return
  document.body.style.overflow = previousOverflow
  scrollLocked = false
}

function focusable(): HTMLElement[] {
  const root = panel.value
  if (!root) return []
  return Array.from(
    root.querySelectorAll<HTMLElement>(
      'button:not(:disabled), [href], input:not(:disabled), textarea:not(:disabled), select:not(:disabled), [tabindex]:not([tabindex="-1"])'
    )
  )
}

async function focusPanel(): Promise<void> {
  await nextTick()
  const first = focusable()[0] ?? panel.value
  first?.focus({ preventScroll: true })
}

function restoreFocus(): void {
  const target = restoreTarget
  restoreTarget = null
  if (target && typeof target.focus === 'function' && document.contains(target)) {
    target.focus({ preventScroll: true })
  }
}

function requestClose(): void {
  if (pending.value) return
  emit('close')
}

function onKeydown(event: KeyboardEvent): void {
  if (!props.open || !visible.value) return
  if (event.key === 'Escape') {
    event.preventDefault()
    requestClose()
    return
  }
  if (event.key !== 'Tab') return
  const items = focusable()
  if (!items.length) {
    event.preventDefault()
    panel.value?.focus({ preventScroll: true })
    return
  }
  const first = items[0]
  const last = items[items.length - 1]
  const active = document.activeElement as HTMLElement | null
  if (!active || !panel.value?.contains(active)) {
    event.preventDefault()
    first.focus({ preventScroll: true })
    return
  }
  if (event.shiftKey && active === first) {
    event.preventDefault()
    last.focus({ preventScroll: true })
  } else if (!event.shiftKey && active === last) {
    event.preventDefault()
    first.focus({ preventScroll: true })
  }
}

/* ----------------------------------- 生命周期 ---------------------------------- */

function openDrawer(): void {
  visible.value = true
  restoreTarget =
    document.activeElement instanceof HTMLElement ? document.activeElement : null
  resetState()
  lockScroll()
  void focusPanel()
  if (importable.value && auth.isAuthenticated) void loadList(true)
}

function closeDrawer(): void {
  visible.value = false
  epoch += 1
  pending.value = false
  unlockScroll()
  restoreFocus()
}

watch(
  () => props.open,
  (open) => {
    if (open) openDrawer()
    else closeDrawer()
  },
  { immediate: true }
)

watch(
  () => props.guide?.id ?? null,
  () => {
    if (!props.open) return
    resetState()
    if (importable.value && auth.isAuthenticated) void loadList(true)
  }
)

watch(
  () => [auth.user?.id ?? null, auth.token],
  () => {
    if (!props.open) return
    resetState()
    if (importable.value && auth.isAuthenticated) void loadList(true)
  }
)

function onMountedSetup(): void {
  document.addEventListener('keydown', onKeydown, true)
}
onMounted(onMountedSetup)

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown, true)
  unlockScroll()
  epoch += 1
})
</script>

<template>
  <div class="trip-drawer-root" :class="{ 'is-open': visible }" data-test="add-to-trip-drawer">
    <div
      class="trip-drawer__scrim"
      :class="{ 'is-visible': visible }"
      data-test="trip-scrim"
      aria-hidden="true"
      @click="requestClose"
    />

    <aside
      id="add-to-trip-drawer"
      ref="panel"
      class="trip-drawer"
      :class="{ 'is-visible': visible }"
      role="dialog"
      aria-modal="true"
      aria-labelledby="add-to-trip-title"
      :aria-hidden="visible ? undefined : 'true'"
      :inert="!visible"
      data-test="trip-drawer-panel"
      tabindex="-1"
    >
      <header class="trip-drawer__header">
        <div class="trip-drawer__title">
          <h2 id="add-to-trip-title" class="font-serif text-lg text-pine-deep">加入我的行程</h2>
          <p v-if="guide" class="trip-drawer__guide truncate" data-test="trip-guide-title">
            {{ guide.title }}
          </p>
        </div>
        <button
          type="button"
          class="trip-drawer__close"
          data-test="trip-close"
          aria-label="关闭加入行程抽屉"
          :disabled="pending"
          @click="requestClose"
        >
          <svg
            viewBox="0 0 24 24"
            aria-hidden="true"
            class="h-4 w-4"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
          >
            <path d="M6 6l12 12M18 6 6 18" />
          </svg>
        </button>
      </header>

      <div class="trip-drawer__body">
        <p v-if="!guide" class="trip-notice" data-test="trip-no-guide">
          暂无可加入的攻略内容。
        </p>

        <template v-else>
          <template v-if="!cityUsable">
            <p class="trip-notice" data-test="trip-city-invalid">
              这篇攻略关联的城市已失效或未设置，无法自动加入景点。可先到「我的行程」手动安排。
            </p>
            <p class="trip-notice__link">
              <RouterLink to="/me/itineraries" class="trip-link" data-test="trip-manual-link">
                去我的行程手动安排
              </RouterLink>
            </p>
          </template>
          <template v-else-if="!hasSpots">
            <p class="trip-notice" data-test="trip-no-spots">
              这篇攻略还没有关联景点，无法自动加入行程。可先到「我的行程」手动安排。
            </p>
            <p class="trip-notice__link">
              <RouterLink to="/me/itineraries" class="trip-link" data-test="trip-manual-link">
                去我的行程手动安排
              </RouterLink>
            </p>
          </template>

          <template v-if="importable">
            <p v-if="!auth.isAuthenticated" class="trip-notice" data-test="trip-login-needed">
              请先登录，登录后才能读取你自己的行程并加入景点。
              <RouterLink :to="loginTarget" class="trip-link" data-test="trip-login-link">
                去登录
              </RouterLink>
            </p>

            <template v-else>
              <section class="trip-section" aria-labelledby="trip-spots-title">
                <div class="trip-section__head">
                  <h3 id="trip-spots-title" class="trip-section__title">攻略关联景点</h3>
                  <button
                    type="button"
                    class="trip-link"
                    data-test="trip-select-all"
                    :disabled="pending"
                    @click="toggleAll"
                  >
                    {{ allActiveSelected ? '取消全选' : '全选可加入景点' }}
                  </button>
                </div>
                <ul class="trip-spots">
                  <li
                    v-for="spot in spots"
                    :key="spot.id"
                    class="trip-spots__row"
                    :class="{ 'is-inactive': spot.status !== 'ACTIVE' }"
                  >
                    <label class="trip-spots__label">
                      <input
                        type="checkbox"
                        :data-test="'spot-' + spot.id"
                        :checked="selectedSet.has(spot.id)"
                        :disabled="spot.status !== 'ACTIVE' || pending"
                        @change="toggleSpot(spot.id, $event)"
                      />
                      <span class="trip-spots__name">{{ spot.name }}</span>
                      <span v-if="spot.status !== 'ACTIVE'" class="trip-badge">已失效</span>
                    </label>
                  </li>
                </ul>
              </section>

              <div class="trip-modes" role="group" aria-label="加入方式">
                <button
                  type="button"
                  class="trip-mode"
                  :class="{ 'is-active': mode === 'existing' }"
                  :aria-pressed="mode === 'existing'"
                  data-test="trip-mode-existing"
                  :disabled="pending"
                  @click="switchMode('existing')"
                >
                  加入已有行程
                </button>
                <button
                  type="button"
                  class="trip-mode"
                  :class="{ 'is-active': mode === 'new' }"
                  :aria-pressed="mode === 'new'"
                  data-test="trip-mode-new"
                  :disabled="pending"
                  @click="switchMode('new')"
                >
                  新建行程并加入
                </button>
              </div>

              <section v-if="mode === 'existing'" class="trip-section" aria-labelledby="trip-target-title">
                <h3 id="trip-target-title" class="trip-section__title">目标行程与日期</h3>
                <p v-if="listLoading && !itineraries.length" class="trip-status" data-test="trip-loading">
                  正在读取你的行程…
                </p>
                <template v-else>
                  <label for="trip-itinerary" class="trip-label">选择行程</label>
                  <select
                    id="trip-itinerary"
                    class="trip-input"
                    data-test="trip-itinerary-select"
                    :value="selectedItineraryId"
                    :disabled="pending || !itineraries.length"
                    @change="onItineraryChange"
                  >
                    <option value="">
                      {{ itineraries.length ? '请选择行程' : '暂无进行中的行程' }}
                    </option>
                    <option v-for="item in itineraries" :key="item.id" :value="item.id">
                      {{ item.title }}（{{ item.startDate }} 至 {{ item.endDate }}）
                    </option>
                  </select>
                  <p v-if="listError" class="trip-error" data-test="trip-list-error">
                    {{ listError }}
                    <button type="button" class="trip-link" @click="loadList(true)">重试</button>
                  </p>
                  <button
                    v-if="listPage < listPages"
                    type="button"
                    class="trip-link trip-load-more"
                    data-test="trip-load-more"
                    :disabled="listLoading || pending"
                    @click="loadList(false)"
                  >
                    {{ listLoading ? '正在加载…' : '加载更多行程（已显示 ' + itineraries.length + ' 个）' }}
                  </button>
                  <p v-if="!listLoading && !listError && !itineraries.length" class="trip-status" data-test="trip-list-empty">
                    还没有进行中的行程，可切换到「新建行程并加入」。
                  </p>

                  <p v-if="detailLoading" class="trip-status" data-test="trip-detail-loading">
                    正在读取行程日期…
                  </p>
                  <p v-else-if="detailError" class="trip-error" data-test="trip-detail-error">
                    {{ detailError }}
                  </p>
                  <template v-else-if="selectedItineraryId && detail">
                    <template v-if="days.length">
                      <label for="trip-day" class="trip-label">加入哪一天</label>
                      <select
                        id="trip-day"
                        class="trip-input"
                        data-test="trip-day-select"
                        :value="selectedDayId"
                        :disabled="pending"
                        @change="onDayChange"
                      >
                        <option value="">请选择日期</option>
                        <option v-for="day in days" :key="day.id" :value="day.id">
                          第 {{ day.dayNo }} 天 · {{ day.travelDate }}
                        </option>
                      </select>
                      <p v-if="selectedDay" class="trip-status" data-test="trip-day-summary">
                        当前选中：{{ selectedDayLabel }}
                      </p>
                    </template>
                    <p v-else class="trip-notice" data-test="trip-no-days">
                      该行程还没有日期。请先到
                      <RouterLink :to="'/itineraries/' + selectedItineraryId" class="trip-link">
                        行程详情
                      </RouterLink>
                      手动添加日期后再回来加入景点。
                    </p>
                  </template>
                </template>
              </section>

              <section v-else class="trip-section" aria-labelledby="trip-new-title">
                <h3 id="trip-new-title" class="trip-section__title">新建行程</h3>
                <label for="trip-title" class="trip-label">行程名称</label>
                <input
                  id="trip-title"
                  v-model="form.title"
                  class="trip-input"
                  data-test="trip-title"
                  :disabled="pending"
                  maxlength="160"
                  placeholder="例如：杭州两日慢行"
                />
                <div class="trip-grid">
                  <div>
                    <label for="trip-start" class="trip-label">开始日期</label>
                    <input
                      id="trip-start"
                      v-model="form.startDate"
                      class="trip-input"
                      data-test="trip-start"
                      type="date"
                      min="1000-01-01"
                      max="9999-12-31"
                      :disabled="pending"
                    />
                  </div>
                  <div>
                    <label for="trip-end" class="trip-label">结束日期</label>
                    <input
                      id="trip-end"
                      v-model="form.endDate"
                      class="trip-input"
                      data-test="trip-end"
                      type="date"
                      min="1000-01-01"
                      max="9999-12-31"
                      :disabled="pending"
                    />
                  </div>
                </div>
                <label for="trip-travel-date" class="trip-label">旅行日期（景点加入哪一天）</label>
                <input
                  id="trip-travel-date"
                  v-model="form.travelDate"
                  class="trip-input"
                  data-test="trip-travel-date"
                  type="date"
                  min="1000-01-01"
                  max="9999-12-31"
                  :disabled="pending"
                />
                <p class="trip-status">新建后会按所选旅行日期创建当天，把勾选景点写入该天。</p>
              </section>

              <section class="trip-preview" aria-live="polite" data-test="trip-preview">
                <p>
                  已选择
                  <strong class="tabular-nums" data-test="trip-selected-count">{{ selectedIds.length }}</strong>
                  个景点<span v-if="presentSelected.length">，其中 {{ presentSelected.length }} 个当天已有，提交后将自动跳过</span>。
                </p>
                <p v-if="selectedDay" class="trip-status">
                  当前日期：{{ selectedDayLabel }} · 本次将新增
                  <strong class="tabular-nums" data-test="trip-new-count">{{ newCount }}</strong> 个
                </p>
                <p v-if="presentSelectedNames.length" class="trip-status" data-test="trip-duplicates">
                  当天已存在：{{ presentSelectedNames.join('、') }}
                </p>
              </section>

              <p v-if="errorMessage" class="trip-error" role="alert" data-test="trip-error">
                {{ errorMessage }}
                <RouterLink v-if="unauthorized" :to="loginTarget" class="trip-link" data-test="trip-login-link">
                  去登录
                </RouterLink>
              </p>

              <div v-if="result" class="trip-result" role="status" data-test="trip-result">
                <p>
                  已写入行程：新增
                  <strong class="tabular-nums" data-test="trip-added-count">{{ result.added.length }}</strong>
                  个，跳过
                  <strong class="tabular-nums" data-test="trip-skipped-count">{{ result.alreadyPresent.length }}</strong>
                  个（当天已存在）。
                </p>
                <RouterLink :to="'/itineraries/' + result.itineraryId" class="trip-link" data-test="trip-result-link">
                  查看行程
                </RouterLink>
              </div>
            </template>
          </template>
        </template>
      </div>

      <footer class="trip-drawer__footer">
        <p v-if="hint" class="trip-hint" data-test="trip-hint">{{ hint }}</p>
        <div class="trip-actions">
          <button
            type="button"
            class="trip-btn trip-btn--ghost"
            data-test="trip-cancel"
            :disabled="pending"
            @click="requestClose"
          >
            取消
          </button>
          <button
            type="button"
            class="trip-btn trip-btn--ghost"
            data-test="trip-plan-ai"
            :disabled="pending || !selectedIds.length || !auth.isAuthenticated"
            @click="planWithAi"
          >
            交给 AI 规划
          </button>
          <button
            type="button"
            class="trip-btn trip-btn--primary"
            data-test="trip-confirm"
            :disabled="!canSubmit"
            @click="submit"
          >
            {{ pending ? '正在加入…' : '确认加入行程' }}
          </button>
        </div>
        <p class="trip-footnote">
          所选地点可直接加入行程，或带到 AI 需求表单继续调整。
        </p>
      </footer>
    </aside>
  </div>
</template>

<style scoped>
.trip-drawer-root {
  position: fixed;
  inset: 0;
  z-index: 60;
  pointer-events: none;
  overflow: hidden;
  visibility: hidden;
  transition: visibility 0s 260ms;
}

.trip-drawer-root.is-open {
  visibility: visible;
  transition-delay: 0s;
}

.trip-drawer__scrim {
  position: absolute;
  inset: 0;
  background: color-mix(in srgb, var(--color-scrim) 55%, transparent);
  opacity: 0;
  transition: opacity var(--ui-motion-normal) var(--ui-motion-ease);
  pointer-events: none;
}

.trip-drawer__scrim.is-visible {
  opacity: 1;
  pointer-events: auto;
}

.trip-drawer {
  position: absolute;
  top: 0;
  right: 0;
  display: flex;
  flex-direction: column;
  width: min(29rem, 100vw);
  height: 100dvh;
  max-height: 100dvh;
  overflow: hidden;
  background: var(--ui-bg-elevated);
  border-left: 1px solid color-mix(in srgb, var(--color-pine) 16%, transparent);
  box-shadow: -18px 0 48px -32px color-mix(in srgb, var(--color-pine-deep) 60%, transparent);
  transform: translateX(100%);
  opacity: 0;
  transition:
    transform var(--ui-motion-normal) var(--ui-motion-ease),
    opacity var(--ui-motion-normal) var(--ui-motion-ease);
  pointer-events: none;
  outline: none;
}

.trip-drawer.is-visible {
  transform: translateX(0);
  opacity: 1;
  pointer-events: auto;
}

.trip-drawer__header {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 1rem 1.1rem;
  border-bottom: 1px solid color-mix(in srgb, var(--color-pine) 12%, transparent);
  background: var(--ui-bg-elevated);
}

.trip-drawer__title {
  min-width: 0;
}

.trip-drawer__title h2 {
  margin: 0;
}

.trip-drawer__guide {
  margin: 0.15rem 0 0;
  color: var(--ui-text-muted, var(--color-ink-soft));
  font-size: 0.75rem;
}

.trip-drawer__close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  width: 2.75rem;
  height: 2.75rem;
  border: 1px solid color-mix(in srgb, var(--color-pine) 18%, transparent);
  border-radius: 999px;
  background: transparent;
  color: var(--color-ink-soft);
  cursor: pointer;
  transition: background-color 160ms ease, color 160ms ease;
}

.trip-drawer__close:hover:not(:disabled) {
  background: color-mix(in srgb, var(--color-pine-soft) 55%, transparent);
  color: var(--color-pine-deep);
}

.trip-drawer__close:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.trip-drawer__body {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 1rem 1.1rem 1.4rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
  -webkit-overflow-scrolling: touch;
}

.trip-drawer__footer {
  flex: none;
  border-top: 1px solid color-mix(in srgb, var(--color-pine) 12%, transparent);
  padding: 0.85rem 1.1rem calc(0.85rem + env(safe-area-inset-bottom));
  background: var(--ui-bg-elevated);
}

.trip-section {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.trip-section__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.trip-section__title {
  margin: 0;
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--color-pine-deep);
}

.trip-spots {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.trip-spots__row {
  border: 1px solid color-mix(in srgb, var(--color-pine) 12%, transparent);
  border-radius: var(--ui-radius-md);
  background: var(--ui-bg-surface);
  padding: 0;
}

.trip-spots__row.is-inactive {
  opacity: 0.55;
}

.trip-spots__label {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-height: 44px;
  padding: 0.35rem 0.7rem;
  cursor: pointer;
  font-size: 0.875rem;
}

.trip-spots__row.is-inactive .trip-spots__label {
  cursor: not-allowed;
}

.trip-spots__name {
  flex: 1 1 auto;
  min-width: 0;
  overflow-wrap: anywhere;
}

.trip-badge {
  flex: none;
  border-radius: var(--ui-radius-pill);
  background: var(--ui-danger-soft, #fbe9e7);
  color: var(--ui-danger, #b3261e);
  padding: 0.1rem 0.45rem;
  font-size: 0.68rem;
}

.trip-modes {
  display: flex;
  gap: 0.4rem;
  flex-wrap: wrap;
}

.trip-mode {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 44px;
  border: 1px solid color-mix(in srgb, var(--color-pine) 20%, transparent);
  border-radius: var(--ui-radius-pill);
  background: transparent;
  color: var(--color-ink-soft);
  padding: 0.35rem 0.75rem;
  font-size: 0.8rem;
  cursor: pointer;
}

.trip-mode.is-active {
  background: var(--ui-accent-soft);
  border-color: color-mix(in srgb, var(--color-pine) 45%, transparent);
  color: var(--color-pine-deep);
  font-weight: 600;
}

.trip-label {
  display: block;
  margin-top: 0.4rem;
  font-size: 0.78rem;
  font-weight: 600;
  color: var(--color-ink-soft);
}

.trip-input {
  width: 100%;
  min-height: 44px;
  border: 1px solid color-mix(in srgb, var(--color-pine) 20%, transparent);
  border-radius: var(--ui-radius-md);
  background: var(--ui-bg-surface);
  color: inherit;
  padding: 0.5rem 0.65rem;
  font-size: 0.875rem;
}

.trip-input:focus-visible {
  outline: 2px solid var(--color-pine);
  outline-offset: 1px;
}

.trip-grid {
  display: grid;
  gap: 0.5rem;
  grid-template-columns: 1fr 1fr;
}

.trip-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 44px;
  min-height: 44px;
  border: 0;
  background: none;
  padding: 0;
  color: var(--ui-link);
  font-size: 0.8rem;
  text-decoration: underline;
  text-underline-offset: 3px;
  cursor: pointer;
}

.trip-link:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.trip-load-more {
  align-self: flex-start;
  margin-top: 0.35rem;
}

.trip-status {
  margin: 0.25rem 0 0;
  font-size: 0.75rem;
  color: var(--color-ink-soft);
}

.trip-notice {
  margin: 0;
  border-radius: var(--ui-radius-md);
  background: color-mix(in srgb, var(--color-pine-soft) 45%, transparent);
  padding: 0.65rem 0.75rem;
  font-size: 0.82rem;
  line-height: 1.5;
  color: var(--color-pine-deep);
}

.trip-notice__link {
  margin: 0.35rem 0 0;
}

.trip-preview {
  border: 1px dashed color-mix(in srgb, var(--color-pine) 28%, transparent);
  border-radius: var(--ui-radius-md);
  padding: 0.6rem 0.75rem;
  font-size: 0.8rem;
}

.trip-preview p {
  margin: 0;
}

.trip-error {
  margin: 0;
  border-radius: var(--ui-radius-md);
  background: var(--ui-danger-soft, #fbe9e7);
  color: var(--ui-danger, #b3261e);
  padding: 0.6rem 0.75rem;
  font-size: 0.8rem;
}

.trip-result {
  border-radius: var(--ui-radius-md);
  background: color-mix(in srgb, var(--color-pine-soft) 55%, transparent);
  color: var(--color-pine-deep);
  padding: 0.6rem 0.75rem;
  font-size: 0.82rem;
}

.trip-result p {
  margin: 0 0 0.25rem;
}

.trip-hint {
  margin: 0 0 0.5rem;
  font-size: 0.75rem;
  color: var(--color-ink-soft);
}

.trip-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  justify-content: flex-end;
}

.trip-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 44px;
  border-radius: var(--ui-radius-pill);
  padding: 0.5rem 0.9rem;
  font-size: 0.85rem;
  cursor: pointer;
  border: 1px solid transparent;
}

.trip-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.trip-btn--ghost {
  background: transparent;
  border-color: color-mix(in srgb, var(--color-pine) 22%, transparent);
  color: var(--color-pine-deep);
}

.trip-btn--primary {
  background: var(--ui-accent);
  color: var(--ui-accent-on);
}

.trip-btn:focus-visible {
  outline: 2px solid var(--color-pine);
  outline-offset: 2px;
}

.trip-footnote {
  margin: 0.5rem 0 0;
  font-size: 0.68rem;
  line-height: 1.4;
  color: var(--color-ink-soft);
}

@media (max-width: 767px) {
  .trip-drawer {
    width: 100vw;
    border-left: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .trip-drawer,
  .trip-drawer__scrim,
  .trip-drawer-root {
    transition: none;
  }

  .trip-drawer {
    transform: none;
  }
}
</style>
