<script setup lang="ts">
import { computed } from 'vue'
import type { AiBudgetEstimate, AiCandidate } from '@/types/ai'
/** 预览草稿在父页面持有；这里只负责结构化时间线的展示与就地编辑，全部按纯文本渲染。 */
import type { AiDraftDay as DraftDay, AiDraft as Draft } from '@/types/ai'
const props = withDefaults(
  defineProps<{
    draft: Draft
    candidates: AiCandidate[]
    warnings: string[]
    budget: AiBudgetEstimate | null
    busy?: boolean
    dayBusy?: string | null
    dayError?: string
  }>(),
  { busy: false, dayBusy: null, dayError: '' },
)
const emit = defineEmits<{
  edit: [string | null]
  regen: [string]
  add: [string, AiCandidate]
  addFree: [string]
  remove: [string, number]
  move: [string, number, number]
}>()
const used = computed(
  () =>
    new Set(
      props.draft.days.flatMap((day) =>
        day.items.map((item) => item.destinationId).filter((id): id is string => Boolean(id)),
      ),
    ),
)
const itemCount = computed(() =>
  props.draft.days.reduce((total, day) => total + day.items.length, 0),
)
const addable = (candidate: AiCandidate): boolean => !used.value.has(candidate.id)
const locked = computed(() => props.busy)
function move(day: DraftDay, index: number, delta: number): void {
  const target = index + delta
  if (locked.value || target < 0 || target >= day.items.length) return
  emit('move', day.travelDate, index, delta)
}
</script>
<template>
  <section data-test="ai-preview" class="space-y-6">
    <p
      v-if="dayError"
      role="alert"
      data-test="ai-day-error"
      class="rounded-xl bg-ember/10 p-3 text-sm text-ember"
    >
      {{ dayError }}
    </p>
    <section class="surface-card p-5 sm:p-7">
      <p class="text-xs tracking-[0.3em] text-ember">AI 只读预览 · 可修改后再保存</p>
      <p class="mt-2 text-xs text-ink-soft">
        预览不会自动保存；确认保存后才会创建一份属于你的新行程。
      </p>
      <div class="mt-5 space-y-4" @input="emit('edit', null)">
        <div>
          <label for="ai-root-title" class="field-label">行程名称</label>
          <input
            id="ai-root-title"
            v-model="draft.title"
            data-test="ai-root-title"
            class="field-input"
            :disabled="locked"
          />
        </div>
        <div>
          <label for="ai-root-summary" class="field-label">一句话总述</label>
          <input
            id="ai-root-summary"
            v-model="draft.summary"
            data-test="ai-root-summary"
            class="field-input"
            :disabled="locked"
          />
        </div>
        <div>
          <label for="ai-root-notes" class="field-label">我的备注（会写入行程说明）</label>
          <textarea
            id="ai-root-notes"
            v-model="draft.notes"
            data-test="ai-root-notes"
            class="field-input min-h-24"
            :disabled="locked"
            placeholder="例如：带父母，节奏慢一些"
          />
          <p class="mt-2 text-xs text-ink-soft">
            备注不会截断；超过 2000 个字符时需要你先删减才能保存。
          </p>
        </div>
      </div>
      <div
        data-test="ai-budget-estimate"
        class="mt-6 rounded-2xl border border-pine/15 bg-pine-soft/20 p-4"
      >
        <h3 class="text-sm font-medium text-pine-deep">初始 AI 费用估算（仅供参考）</h3>
        <p v-if="budget" class="mt-2 break-words text-sm text-ink">
          人均 {{ budget.perPerson }} {{ budget.currency }} · 合计 {{ budget.total }}
          {{ budget.currency }}
        </p>
        <p v-if="budget" class="mt-1 break-words text-xs text-ink-soft">{{ budget.notes }}</p>
      </div>
      <div
        v-if="warnings.length"
        data-test="ai-warnings"
        class="mt-4 rounded-2xl border border-ember/20 bg-ember/5 p-4"
      >
        <h3 class="text-sm font-medium text-ember">需要注意</h3>
        <ul class="mt-2 list-disc space-y-1 pl-5 text-xs text-ink-soft">
          <li v-for="warning in warnings" :key="warning" class="break-words">{{ warning }}</li>
        </ul>
      </div>
    </section>
    <section
      v-if="!draft.days.length"
      data-test="ai-preview-empty"
      class="surface-card p-8 text-center"
    >
      <h2 class="break-words font-serif text-2xl text-pine-deep">这一程还没有具体安排</h2>
      <p class="mt-3 break-words text-sm text-ink-soft">
        模型没有给出可用的逐日时间线。可以调整需求后重新生成，或先改用手动安排。
      </p>
    </section>
    <section
      v-for="day in draft.days"
      :key="day.travelDate"
      class="surface-card p-5 sm:p-7"
      :data-test="`ai-day-${day.travelDate}`"
    >
      <header class="flex flex-wrap items-center justify-between gap-3">
        <div class="min-w-0">
          <p class="text-xs tracking-[0.25em] text-ember">
            DAY {{ day.dayNo }} · {{ day.travelDate }}
          </p>
          <input
            v-model="day.title"
            :data-test="`ai-day-${day.travelDate}-title`"
            class="field-input mt-2"
            :disabled="locked"
            aria-label="这一天的安排名称"
            @input="emit('edit', day.travelDate)"
          />
        </div>
        <button
          type="button"
          :data-test="`ai-day-${day.travelDate}-regen`"
          class="btn-ghost shrink-0"
          :disabled="locked || props.dayBusy === day.travelDate"
          @click="emit('regen', day.travelDate)"
        >
          {{ props.dayBusy === day.travelDate ? '正在重新生成…' : '只重生成这一天' }}
        </button>
      </header>
      <p v-if="props.dayBusy === day.travelDate" role="status" class="mt-3 text-xs text-ink-soft">
        正在重新安排 {{ day.travelDate }}，其他天的改动会保留。
      </p>
      <ol v-if="day.items.length" class="mt-4 space-y-4">
        <li
          v-for="(item, index) in day.items"
          :key="`${day.travelDate}-${index}`"
          :data-test="`ai-day-${day.travelDate}-item-${index}`"
          class="rounded-2xl border border-pine/10 p-4"
          @input="emit('edit', day.travelDate)"
        >
          <div class="flex flex-wrap items-start justify-between gap-2">
            <div class="min-w-0">
              <p class="break-words text-sm font-medium text-pine-deep">
                <span :data-test="`ai-day-${day.travelDate}-item-${index}-name`">{{
                  item.placeName
                }}</span
                ><span
                  v-if="item.kind === 'FREE_TIME'"
                  class="ml-2 rounded-full bg-pine-soft/50 px-2 py-0.5 text-xs text-pine"
                  >自由安排</span
                >
              </p>
              <p
                v-if="item.addressText"
                :data-test="`ai-day-${day.travelDate}-item-${index}-address`"
                class="mt-1 break-words text-xs text-ink-soft"
              >
                {{ item.addressText }}
              </p>
            </div>
            <div class="flex shrink-0 items-center gap-2 text-xs">
              <button
                type="button"
                :data-test="`ai-day-${day.travelDate}-item-${index}-up`"
                class="btn-ghost px-2 py-1"
                :disabled="locked || index === 0"
                @click="move(day, index, -1)"
              >
                上移
              </button>
              <button
                type="button"
                :data-test="`ai-day-${day.travelDate}-item-${index}-down`"
                class="btn-ghost px-2 py-1"
                :disabled="locked || index >= day.items.length - 1"
                @click="move(day, index, 1)"
              >
                下移
              </button>
              <button
                type="button"
                :data-test="`ai-day-${day.travelDate}-item-${index}-remove`"
                class="text-ember underline underline-offset-4"
                :disabled="locked"
                @click="emit('remove', day.travelDate, index)"
              >
                移除
              </button>
            </div>
          </div>
          <div class="mt-3 grid gap-3 sm:grid-cols-2">
            <div>
              <label :for="`ai-start-${day.travelDate}-${index}`" class="field-label"
                >开始时间</label
              >
              <input
                :id="`ai-start-${day.travelDate}-${index}`"
                v-model="item.startTime"
                :data-test="`ai-day-${day.travelDate}-item-${index}-start`"
                class="field-input"
                type="time"
                :disabled="locked"
              />
            </div>
            <div>
              <label :for="`ai-end-${day.travelDate}-${index}`" class="field-label">结束时间</label>
              <input
                :id="`ai-end-${day.travelDate}-${index}`"
                v-model="item.endTime"
                :data-test="`ai-day-${day.travelDate}-item-${index}-end`"
                class="field-input"
                type="time"
                :disabled="locked"
              />
            </div>
            <div>
              <label :for="`ai-cost-${day.travelDate}-${index}`" class="field-label"
                >人均费用估算（元）</label
              >
              <input
                :id="`ai-cost-${day.travelDate}-${index}`"
                v-model="item.estimatedCost"
                :data-test="`ai-day-${day.travelDate}-item-${index}-cost`"
                class="field-input"
                inputmode="decimal"
                :disabled="locked"
              />
            </div>
            <div>
              <label :for="`ai-notes-${day.travelDate}-${index}`" class="field-label"
                >这一项的备注</label
              >
              <input
                :id="`ai-notes-${day.travelDate}-${index}`"
                v-model="item.notes"
                :data-test="`ai-day-${day.travelDate}-item-${index}-notes`"
                class="field-input"
                :disabled="locked"
              />
            </div>
          </div>
        </li>
      </ol>
      <p v-else class="mt-4 text-xs text-ink-soft">
        这一天还没有安排，可以从下面的真实景点里挑一个。
      </p>
      <div class="mt-4 flex flex-wrap items-center gap-2">
        <button
          type="button"
          :data-test="`ai-day-${day.travelDate}-add-free`"
          class="btn-ghost px-3 py-1 text-xs"
          :disabled="locked || day.items.length >= 8 || itemCount >= 60"
          @click="emit('addFree', day.travelDate)"
        >
          + 自由活动
        </button>
        <button
          v-for="spot in candidates.filter(addable)"
          :key="spot.id"
          type="button"
          :data-test="`ai-day-${day.travelDate}-add-${spot.id}`"
          class="btn-ghost px-3 py-1 text-xs"
          :disabled="locked || day.items.length >= 8 || itemCount >= 60"
          @click="emit('add', day.travelDate, spot)"
        >
          + {{ spot.name }}
        </button>
      </div>
      <p class="mt-2 text-xs text-ink-soft">
        同一座城市里同一个景点不会在整程重复出现；景点名称和地址都取自真实候选。
      </p>
    </section>
  </section>
</template>
