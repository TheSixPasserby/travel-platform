<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ApiError } from '@/api/client'
import { fetchDestinations } from '@/api/destinations'
import type { AiInterest, AiRequirements } from '@/types/ai'
import type { Destination } from '@/types/destination'
/** 需求表单：只收集用户意图，不调用生成接口；城市与候选全部来自真实目的地接口。 */
const props = withDefaults(defineProps<{ busy?: boolean; maxDays?: number }>(), {
  busy: false,
  maxDays: 14,
})
const emit = defineEmits<{
  submit: [AiRequirements]
  dirty: [AiRequirements]
  invalidate: [AiRequirements]
}>()
const INTERESTS: { value: AiInterest; label: string }[] = [
  { value: 'FOOD', label: '美食' },
  { value: 'NATURE', label: '自然' },
  { value: 'CULTURE', label: '人文' },
  { value: 'PHOTOGRAPHY', label: '摄影' },
  { value: 'FAMILY', label: '亲子' },
  { value: 'HIKING', label: '徒步' },
  { value: 'CITY_WALK', label: '城市漫步' },
]
const LIMIT = 20
const cities = ref<Destination[]>([]),
  cityError = ref('')
const cityId = ref(''),
  startDate = ref(''),
  endDate = ref(''),
  travelers = ref('2'),
  budgetAmount = ref(''),
  budgetBasis = ref<AiRequirements['budgetBasis']>('PER_PERSON'),
  pace = ref<AiRequirements['pace']>('BALANCED'),
  transport = ref<AiRequirements['transport']>('MIXED'),
  interests = ref<AiInterest[]>([]),
  preferredIds = ref<string[]>([]),
  mustVisitIds = ref<string[]>([]),
  avoidIds = ref<string[]>([]),
  candidates = ref<Destination[]>([]),
  page = ref(1),
  pages = ref(0),
  keyword = ref(''),
  candLoading = ref(false),
  candError = ref(''),
  error = ref('')
let cityVersion = 0,
  candVersion = 0
const dayCount = computed(() => {
  const start = Date.parse(`${startDate.value}T00:00:00Z`),
    end = Date.parse(`${endDate.value}T00:00:00Z`)
  if (!Number.isFinite(start) || !Number.isFinite(end)) return 0
  return Math.round((end - start) / 86_400_000) + 1
})
const full = (list: string[]): boolean => list.length >= LIMIT
/** 表单当前值的快照：保存时使用最新输入，即使预览是上一轮需求生成的。 */
function snapshot(): AiRequirements {
  return {
    cityId: cityId.value,
    startDate: startDate.value,
    endDate: endDate.value,
    travelers: Number(travelers.value) || 0,
    budgetAmount: budgetAmount.value.trim(),
    budgetBasis: budgetBasis.value,
    currency: 'CNY',
    interests: [...interests.value],
    pace: pace.value,
    transport: transport.value,
    preferredIds: [...preferredIds.value],
    mustVisitIds: [...mustVisitIds.value],
    avoidIds: [...avoidIds.value],
  }
}
function dirty(): void {
  emit('dirty', snapshot())
}
/** 城市或日期变化会让上一轮预览与需求不一致，必须明确作废而不是悄悄沿用。 */
function invalidate(): void {
  emit('invalidate', snapshot())
}
async function loadCities(): Promise<void> {
  const request = ++cityVersion
  cityError.value = ''
  try {
    const data = await fetchDestinations({ page: 1, size: 50, destinationType: 'CITY' })
    if (request === cityVersion) cities.value = data.records
  } catch (reason) {
    if (request === cityVersion)
      cityError.value = reason instanceof ApiError ? reason.message : '城市候选暂时无法加载'
  }
}
async function loadCandidates(): Promise<void> {
  const city = cityId.value,
    request = ++candVersion
  candError.value = ''
  if (!city) {
    candidates.value = []
    pages.value = 0
    page.value = 1
    candLoading.value = false
    return
  }
  candLoading.value = true
  try {
    const data = await fetchDestinations({
      page: page.value,
      size: 20,
      keyword: keyword.value.trim() || undefined,
      destinationType: 'SCENIC_SPOT',
      parentId: city,
    })
    if (request !== candVersion) return
    candidates.value = data.records
    pages.value = data.pages
    page.value = data.current
  } catch (reason) {
    if (request === candVersion)
      candError.value = reason instanceof ApiError ? reason.message : '本城景点候选暂时无法加载'
  } finally {
    if (request === candVersion) candLoading.value = false
  }
}
function chooseCity(value: string): void {
  cityId.value = value
  page.value = 1
  keyword.value = ''
  preferredIds.value = []
  mustVisitIds.value = []
  avoidIds.value = []
  void loadCandidates()
  invalidate()
}
function turn(delta: number): void {
  page.value += delta
  void loadCandidates()
}
function search(): void {
  page.value = 1
  void loadCandidates()
}
function toggleInterest(value: AiInterest, checked: boolean): void {
  const set = new Set(interests.value)
  checked ? set.add(value) : set.delete(value)
  interests.value = INTERESTS.filter((item) => set.has(item.value)).map((item) => item.value)
}
function toggle(key: 'preferred' | 'must' | 'avoid', id: string, checked: boolean): void {
  const target = key === 'preferred' ? preferredIds : key === 'must' ? mustVisitIds : avoidIds
  if (!checked) {
    target.value = target.value.filter((item) => item !== id)
    return
  }
  if (target.value.includes(id) || target.value.length >= LIMIT) return
  target.value = [...target.value, id]
}
const prefDisabled = (id: string): boolean =>
  props.busy || (!preferredIds.value.includes(id) && full(preferredIds.value))
const mustDisabled = (id: string): boolean =>
  props.busy || (!mustVisitIds.value.includes(id) && full(mustVisitIds.value))
const avoidDisabled = (id: string): boolean =>
  props.busy || (!avoidIds.value.includes(id) && full(avoidIds.value))
function validate(): string {
  if (!cityId.value) return '请先选择目的地城市'
  if (!startDate.value || !endDate.value) return '请选择出发和结束日期'
  const days = dayCount.value
  if (days < 1) return '结束日期不能早于出发日期'
  if (days > props.maxDays)
    return `AI 首版支持 1–${props.maxDays} 天（当前 ${days} 天），请缩短日期或改用手动安排`
  if (
    !/^\d+$/.test(travelers.value.trim()) ||
    Number(travelers.value) < 1 ||
    Number(travelers.value) > 20
  )
    return '出行人数需为 1–20 的整数'
  if (!/^\d{1,9}(\.\d{1,2})?$/.test(budgetAmount.value.trim()))
    return '预算金额需为不超过两位小数的非负数字'
  for (const list of [preferredIds.value, mustVisitIds.value, avoidIds.value]) {
    if (list.length > LIMIT) return `想去、必须去、避免每类最多 ${LIMIT} 个景点`
  }
  const avoid = new Set(avoidIds.value)
  if ([...preferredIds.value, ...mustVisitIds.value].some((id) => avoid.has(id)))
    return '想去或必须去的景点不能同时放进避免列表，请调整后再生成'
  return ''
}
function submit(): void {
  const message = validate()
  if (message) {
    error.value = message
    return
  }
  error.value = ''
  emit('submit', {
    cityId: cityId.value,
    startDate: startDate.value,
    endDate: endDate.value,
    travelers: Number(travelers.value),
    budgetAmount: budgetAmount.value.trim(),
    budgetBasis: budgetBasis.value,
    currency: 'CNY',
    interests: [...interests.value],
    pace: pace.value,
    transport: transport.value,
    preferredIds: [...preferredIds.value],
    mustVisitIds: [...mustVisitIds.value],
    avoidIds: [...avoidIds.value],
  })
}
onMounted(() => {
  void loadCities()
})
</script>
<template>
  <form
    data-test="ai-form"
    class="surface-card space-y-6 p-5 sm:p-7"
    novalidate
    @input="dirty"
    @change="dirty"
    @submit.prevent="submit"
  >
    <div class="grid gap-5 sm:grid-cols-2">
      <div class="sm:col-span-2">
        <label for="ai-city" class="field-label">目的地城市</label>
        <select
          id="ai-city"
          data-test="ai-city"
          class="field-input"
          :value="cityId"
          @change="chooseCity(($event.target as HTMLSelectElement).value)"
        >
          <option value="">选择一座城市</option>
          <option v-for="city in cities" :key="city.id" :value="city.id">{{ city.name }}</option>
        </select>
        <p v-if="cityError" role="alert" class="mt-2 text-xs text-ember">{{ cityError }}</p>
        <p v-else class="mt-2 text-xs text-ink-soft">候选景点只会来自你选择的这座城市。</p>
      </div>
      <div>
        <label for="ai-start-date" class="field-label">出发日期</label>
        <input
          id="ai-start-date"
          data-test="ai-start-date"
          v-model="startDate"
          class="field-input"
          type="date"
          @input="invalidate"
        />
      </div>
      <div>
        <label for="ai-end-date" class="field-label">返程日期</label>
        <input
          id="ai-end-date"
          data-test="ai-end-date"
          v-model="endDate"
          class="field-input"
          type="date"
          @input="invalidate"
        />
        <p class="mt-2 text-xs text-ink-soft">
          1–{{ maxDays }} 天，当前 {{ dayCount > 0 ? dayCount : 0 }} 天。
        </p>
      </div>
      <div>
        <label for="ai-travelers" class="field-label">出行人数</label>
        <input
          id="ai-travelers"
          data-test="ai-travelers"
          class="field-input"
          type="number"
          min="1"
          max="20"
          :value="travelers"
          @input="travelers = ($event.target as HTMLInputElement).value"
        />
      </div>
      <div>
        <label for="ai-budget-amount" class="field-label">预算金额（人民币元）</label>
        <input
          id="ai-budget-amount"
          data-test="ai-budget-amount"
          v-model="budgetAmount"
          class="field-input"
          inputmode="decimal"
          placeholder="例如 3000"
        />
      </div>
      <div>
        <label for="ai-budget-basis" class="field-label">预算口径</label>
        <select
          id="ai-budget-basis"
          data-test="ai-budget-basis"
          v-model="budgetBasis"
          class="field-input"
        >
          <option value="PER_PERSON">每人预算</option>
          <option value="TOTAL">整程总预算</option>
        </select>
      </div>
      <div>
        <label for="ai-pace" class="field-label">节奏</label>
        <select id="ai-pace" data-test="ai-pace" v-model="pace" class="field-input">
          <option value="RELAXED">松弛一些</option>
          <option value="BALANCED">均衡</option>
          <option value="COMPACT">紧凑一些</option>
        </select>
      </div>
      <div>
        <label for="ai-transport" class="field-label">主要交通</label>
        <select id="ai-transport" data-test="ai-transport" v-model="transport" class="field-input">
          <option value="PUBLIC">公共交通</option>
          <option value="WALK">步行</option>
          <option value="TAXI">打车</option>
          <option value="MIXED">混合</option>
        </select>
      </div>
      <div class="sm:col-span-2">
        <p class="field-label">兴趣（可多选）</p>
        <div class="flex flex-wrap gap-x-5 gap-y-2">
          <label
            v-for="interest in INTERESTS"
            :key="interest.value"
            class="flex items-center gap-2 text-sm text-ink"
          >
            <input
              type="checkbox"
              class="accent-pine"
              :data-test="`ai-interest-${interest.value}`"
              :checked="interests.includes(interest.value)"
              :disabled="busy"
              @change="toggleInterest(interest.value, ($event.target as HTMLInputElement).checked)"
            />{{ interest.label }}
          </label>
        </div>
      </div>
    </div>
    <section class="rounded-2xl border border-pine/15 p-4 sm:p-5">
      <div class="flex flex-wrap items-baseline justify-between gap-2">
        <h3 class="text-sm font-medium text-pine-deep">本城真实景点偏好</h3>
        <p class="text-xs text-ink-soft">
          想去 <span data-test="ai-pref-count">{{ preferredIds.length }} / {{ LIMIT }}</span> ·
          必须去 <span data-test="ai-must-count">{{ mustVisitIds.length }} / {{ LIMIT }}</span> ·
          避免 <span data-test="ai-avoid-count">{{ avoidIds.length }} / {{ LIMIT }}</span>
        </p>
      </div>
      <p class="mt-2 text-xs text-ink-soft">
        候选只来自所选城市下的真实景点；「必须去」是硬约束，「想去」是软偏好。
      </p>
      <div class="mt-3 flex flex-col gap-2 sm:flex-row">
        <label for="ai-candidate-keyword" class="sr-only">按名称搜索景点</label>
        <input
          id="ai-candidate-keyword"
          v-model="keyword"
          data-test="ai-candidate-keyword"
          class="field-input min-w-0"
          type="search"
          placeholder="搜索本城景点名称"
          :disabled="busy"
          @keydown.enter.prevent="search"
        />
        <button
          type="button"
          class="btn-ghost shrink-0"
          :disabled="busy || candLoading"
          @click="search"
        >
          搜索
        </button>
      </div>
      <p v-if="candLoading" role="status" class="mt-3 text-xs text-ink-soft">正在加载本城景点…</p>
      <p v-else-if="candError" role="alert" class="mt-3 text-xs text-ember">
        {{ candError }}
        <button type="button" class="underline" @click="loadCandidates">重试</button>
      </p>
      <ul v-else-if="candidates.length" class="mt-3 max-h-72 space-y-2 overflow-y-auto pr-1">
        <li
          v-for="spot in candidates"
          :key="spot.id"
          class="flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-ink"
        >
          <span class="min-w-0 flex-1 break-words"
            >{{ spot.name
            }}<span v-if="spot.status === 'INACTIVE'" class="ml-2 text-xs text-ember"
              >已停用</span
            ></span
          >
          <label class="flex items-center gap-1 text-xs"
            ><input
              type="checkbox"
              class="accent-pine"
              :data-test="`ai-pref-${spot.id}`"
              :checked="preferredIds.includes(spot.id)"
              :disabled="prefDisabled(spot.id)"
              @change="toggle('preferred', spot.id, ($event.target as HTMLInputElement).checked)"
            />想去</label
          >
          <label class="flex items-center gap-1 text-xs"
            ><input
              type="checkbox"
              class="accent-pine"
              :data-test="`ai-must-${spot.id}`"
              :checked="mustVisitIds.includes(spot.id)"
              :disabled="mustDisabled(spot.id)"
              @change="toggle('must', spot.id, ($event.target as HTMLInputElement).checked)"
            />必须去</label
          >
          <label class="flex items-center gap-1 text-xs"
            ><input
              type="checkbox"
              class="accent-pine"
              :data-test="`ai-avoid-${spot.id}`"
              :checked="avoidIds.includes(spot.id)"
              :disabled="avoidDisabled(spot.id)"
              @change="toggle('avoid', spot.id, ($event.target as HTMLInputElement).checked)"
            />避免</label
          >
        </li>
      </ul>
      <p v-else class="mt-3 text-xs text-ink-soft">
        {{ cityId ? '这座城市还没有可选的景点。' : '选择城市后加载本城景点。' }}
      </p>
      <div v-if="pages > 1" class="mt-2 flex items-center gap-2 text-xs text-ink-soft">
        <button
          type="button"
          data-test="ai-candidate-prev"
          class="btn-ghost px-2 py-1"
          :disabled="busy || candLoading || page <= 1"
          @click="turn(-1)"
        >
          上一页
        </button>
        <span data-test="ai-candidate-status">{{ page }} / {{ pages }}</span>
        <button
          type="button"
          data-test="ai-candidate-next"
          class="btn-ghost px-2 py-1"
          :disabled="busy || candLoading || page >= pages"
          @click="turn(1)"
        >
          下一页
        </button>
      </div>
    </section>
    <p
      v-if="error"
      role="alert"
      data-test="ai-form-error"
      class="rounded-xl bg-ember/10 p-3 text-sm text-ember"
    >
      {{ error }}
    </p>
    <div class="flex flex-wrap items-center gap-3">
      <button type="submit" data-test="ai-submit" class="btn-primary" :aria-busy="busy">
        {{ busy ? '正在生成…' : '生成行程预览' }}
      </button>
      <p class="text-xs text-ink-soft">生成后会先给出只读预览，不会自动保存。</p>
    </div>
  </form>
</template>
