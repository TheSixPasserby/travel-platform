<script setup lang="ts">
import type { ItineraryDay, ItineraryItem } from '@/types/itinerary'

const props = defineProps<{ day: ItineraryDay; busy: boolean }>()

const emit = defineEmits<{
  'edit-day': [day: ItineraryDay]
  'delete-day': [day: ItineraryDay]
  'add-item': [dayId: string]
  'edit-item': [item: ItineraryItem]
  'delete-item': [item: ItineraryItem]
  order: [dayId: string, itemIds: string[]]
}>()

/** 只展示 props 原顺序；排序只把交换后的完整 ID 序列交给父组件落库。 */
function move(index: number, delta: number): void {
  if (props.busy) return
  const ids = props.day.items.map((item) => item.id)
  const target = index + delta
  if (target < 0 || target >= ids.length) return
  const next = [...ids]
  next[index] = ids[target]!
  next[target] = ids[index]!
  emit('order', props.day.id, next)
}

function editDay(): void { if (!props.busy) emit('edit-day', props.day) }
function deleteDay(): void { if (!props.busy) emit('delete-day', props.day) }
function addItem(): void { if (!props.busy) emit('add-item', props.day.id) }
function editItem(item: ItineraryItem): void { if (!props.busy) emit('edit-item', item) }
function deleteItem(item: ItineraryItem): void { if (!props.busy) emit('delete-item', item) }

/** 服务端时间可能是 HH:mm 或 HH:mm:ss，统一只展示 HH:mm。 */
function hhmm(value: string | null): string {
  const matched = value ? /^(\d{1,2}):(\d{2})/.exec(value) : null
  return matched ? `${matched[1]!.padStart(2, '0')}:${matched[2]}` : ''
}

function timeRange(item: ItineraryItem): string {
  const start = hhmm(item.startTime)
  const end = hhmm(item.endTime)
  if (start && end) return `${start} – ${end}`
  if (start) return `${start} 起`
  if (end) return `至 ${end}`
  return '时间待定'
}
</script>

<template>
  <section
    class="surface-card flex min-w-0 flex-col gap-3 px-4 py-4 sm:px-5"
    :aria-label="`第 ${props.day.dayNo} 天行程`"
  >
    <header class="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
      <div class="min-w-0 flex-1">
        <p class="text-xs font-semibold tracking-wide text-pine">第 {{ props.day.dayNo }} 天</p>
        <h3 class="mt-1 flex flex-wrap items-baseline gap-x-2 gap-y-1">
          <span class="whitespace-nowrap text-base font-semibold tabular-nums text-ink">{{ props.day.travelDate }}</span>
          <span v-if="props.day.title" class="break-words text-sm font-normal text-ink-soft">{{ props.day.title }}</span>
        </h3>
        <p v-if="props.day.notes" class="mt-1 whitespace-pre-wrap break-words text-sm leading-relaxed text-ink-soft">{{ props.day.notes }}</p>
      </div>
      <div class="flex flex-wrap gap-2">
        <button type="button" data-test="edit-day" class="btn-ghost disabled:cursor-not-allowed disabled:opacity-60" :disabled="props.busy" @click="editDay">编辑这一天</button>
        <button type="button" data-test="delete-day" class="btn-ghost text-ember disabled:cursor-not-allowed disabled:opacity-60" :disabled="props.busy" @click="deleteDay">删除这一天</button>
      </div>
    </header>

    <ol v-if="props.day.items.length" class="flex flex-col gap-2">
      <li v-for="(item, index) in props.day.items" :key="item.id" data-test="itinerary-item" class="min-w-0 rounded-2xl border border-pine/10 bg-cream px-3 py-3">
        <div class="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div class="min-w-0 flex-1">
            <p class="flex flex-wrap items-baseline gap-x-2 gap-y-1">
              <span class="break-words text-sm font-medium text-ink">{{ index + 1 }}. {{ item.placeName }}</span>
              <span class="text-xs tabular-nums text-pine-deep">{{ timeRange(item) }}</span>
            </p>
            <p v-if="item.addressText" class="mt-1 break-words text-xs text-ink-soft">{{ item.addressText }}</p>
            <p v-if="item.destination" class="mt-1 break-words text-xs text-ink-soft">
              {{ item.destination.name }}<span v-if="item.destination.status === 'INACTIVE'" class="text-ember">（已停用，历史关联）</span>
            </p>
            <p v-if="item.notes" class="mt-1 whitespace-pre-wrap break-words text-xs leading-relaxed text-ink-soft">{{ item.notes }}</p>
          </div>
          <div class="flex flex-wrap items-center gap-1.5">
            <button type="button" :data-test="'move-up-' + item.id" class="btn-ghost px-2.5 py-1 text-xs disabled:cursor-not-allowed disabled:opacity-60" :disabled="props.busy || index === 0" :aria-label="`上移「${item.placeName}」`" @click="move(index, -1)">↑ 上移</button>
            <button type="button" :data-test="'move-down-' + item.id" class="btn-ghost px-2.5 py-1 text-xs disabled:cursor-not-allowed disabled:opacity-60" :disabled="props.busy || index === props.day.items.length - 1" :aria-label="`下移「${item.placeName}」`" @click="move(index, 1)">↓ 下移</button>
            <button type="button" :data-test="'edit-item-' + item.id" class="btn-ghost px-2.5 py-1 text-xs disabled:cursor-not-allowed disabled:opacity-60" :disabled="props.busy" :aria-label="`编辑「${item.placeName}」`" @click="editItem(item)">编辑</button>
            <button type="button" :data-test="'delete-item-' + item.id" class="btn-ghost px-2.5 py-1 text-xs text-ember disabled:cursor-not-allowed disabled:opacity-60" :disabled="props.busy" :aria-label="`删除「${item.placeName}」`" @click="deleteItem(item)">删除</button>
          </div>
        </div>
      </li>
    </ol>
    <p v-else class="rounded-2xl border border-dashed border-pine/25 bg-cream px-3 py-4 text-sm text-ink-soft">这一天还没有安排，添加一个地点开始规划。</p>

    <button type="button" :data-test="'add-item-' + props.day.id" class="btn-ghost self-start disabled:cursor-not-allowed disabled:opacity-60" :disabled="props.busy" :aria-label="`为第 ${props.day.dayNo} 天添加项目`" @click="addItem">＋ 添加项目</button>
  </section>
</template>
