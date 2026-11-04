<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ApiError } from '@/api/client'
import { changeDestinationStatus, createDestination, fetchAdminDestinations, fetchDestination, fetchDestinations, updateDestination } from '@/api/destinations'
import ImageUpload from '@/components/ImageUpload.vue'
import AdminNavigation from '@/components/AdminNavigation.vue'
import type { PageResult } from '@/types/api'
import { destinationTypeLabels, type Destination, type DestinationInput, type DestinationStatus, type DestinationType } from '@/types/destination'

const router = useRouter()
const keyword = ref(''), statusFilter = ref<DestinationStatus | ''>(''), page = ref(1)
const result = ref<PageResult<Destination>>({ records: [], total: 0, current: 1, size: 10, pages: 0 })
const loading = ref(false), loadError = ref(''), saveError = ref(''), notice = ref('')
const saving = ref(false), uploading = ref(false), changing = ref<string | null>(null)
const editingId = ref<string | null>(null), originalParentId = ref<string | null>(null)
const nameInput = ref<HTMLInputElement | null>(null)
const parentKeyword = ref(''), parentLoading = ref(false), parentError = ref('')
const parents = ref<Destination[]>([])
const retainedParent = ref<{ id: string; name: string; status: DestinationStatus } | null>(null)
let listVersion = 0
let parentVersion = 0
let active = true

function defaults() {
  return { name: '', destinationType: 'CITY' as DestinationType, parentId: '', country: '中国', province: '', city: '',
    description: '', coverImageUrl: null as string | null, recommendedSeasons: '', status: 'ACTIVE' as DestinationStatus, sortOrder: 0 }
}
const form = reactive(defaults())
const parentOptions = computed(() => {
  const options = parents.value.filter(parent => parent.id !== editingId.value)
  if (retainedParent.value && !options.some(parent => parent.id === retainedParent.value?.id)) return [...options, retainedParent.value]
  return options
})
function sessionExpired(): void { if(active)void router.push({ name: 'login', query: { redirect: '/admin/destinations' } }) }
function message(error: unknown): string { return error instanceof ApiError ? error.message : '操作未能完成，请稍后再试' }

async function load(): Promise<void> {
  const request = ++listVersion
  loading.value = true; loadError.value = ''
  try {
    const data = await fetchAdminDestinations({ page: page.value, size: 10, keyword: keyword.value.trim() || undefined, status: statusFilter.value || undefined })
    if (request === listVersion) { result.value = data; page.value = data.current }
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) sessionExpired()
    else if (request === listVersion) loadError.value = message(error)
  } finally { if (request === listVersion) loading.value = false }
}
function search(): void { page.value = 1; void load() }
async function loadParents(): Promise<void> {
  const request = ++parentVersion
  const selected = form.parentId
  parentLoading.value = true; parentError.value = ''
  try {
    const data = await fetchDestinations({ page: 1, size: 50, keyword: parentKeyword.value.trim() || undefined })
    const retained = selected && !data.records.some(parent => parent.id === selected)
      ? await fetchDestination(selected).catch(() => ({ id: selected, name: '原上级目的地', status: 'INACTIVE' as const })) : null
    if (request === parentVersion) {
      parents.value = data.records; retainedParent.value = retained
    }
  } catch (error) { if (request === parentVersion) parentError.value = message(error) }
  finally { if (request === parentVersion) parentLoading.value = false }
}
function resetEditor(): void {
  parentVersion++; parentLoading.value = false
  editingId.value = null; originalParentId.value = null; retainedParent.value = null
  Object.assign(form, defaults()); saveError.value = ''
}
function startNew(): void {
  if (saving.value || uploading.value) return
  resetEditor(); notice.value = ''; void loadParents(); void nextTick(() => nameInput.value?.focus())
}
async function edit(destination: Destination): Promise<void> {
  if (saving.value || uploading.value) return
  resetEditor(); notice.value = ''; editingId.value = destination.id; originalParentId.value = destination.parentId
  Object.assign(form, { name: destination.name, destinationType: destination.destinationType, parentId: destination.parentId ?? '',
    country: destination.country, province: destination.province ?? '', city: destination.city ?? '', description: destination.description ?? '',
    coverImageUrl: destination.coverImageUrl, recommendedSeasons: destination.recommendedSeasons ?? '', status: destination.status, sortOrder: destination.sortOrder })
  await loadParents(); nameInput.value?.focus()
}
const nullable = (text: string): string | null => text.trim() || null
async function save(): Promise<void> {
  if (saving.value || uploading.value || changing.value !== null) return
  saveError.value = ''; notice.value = ''
  if (!form.name.trim() || !form.country.trim()) { saveError.value = '请填写目的地名称和国家'; return }
  if (!Number.isInteger(form.sortOrder) || form.sortOrder < 0) { saveError.value = '排序须为非负整数'; return }
  const payload: DestinationInput = { name: form.name.trim(), destinationType: form.destinationType, parentId: form.parentId || null,
    country: form.country.trim(), province: nullable(form.province), city: nullable(form.city), description: nullable(form.description),
    coverImageUrl: form.coverImageUrl, recommendedSeasons: nullable(form.recommendedSeasons), status: form.status, sortOrder: form.sortOrder }
  saving.value = true
  const updating = editingId.value
  try {
    if (updating) await updateDestination(updating, payload)
    else await createDestination(payload)
    await load(); resetEditor(); notice.value = updating ? '目的地已更新' : '目的地已创建'
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) sessionExpired()
    else saveError.value = message(error)
  } finally { saving.value = false }
}
async function toggle(destination: Destination): Promise<void> {
  if (changing.value) return
  changing.value = destination.id; saveError.value = ''; notice.value = ''
  try {
    const updated = await changeDestinationStatus(destination.id, destination.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE')
    if (editingId.value === destination.id) form.status = updated.status
    await load(); notice.value = '目的地状态已更新'
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) sessionExpired()
    else saveError.value = message(error)
  } finally { changing.value = null }
}
function turn(delta: number): void { page.value += delta; void load() }
onMounted(() => { void load(); void loadParents() })
onBeforeUnmount(() => { active=false;listVersion++;parentVersion++ })
</script>

<template>
  <section class="app-container py-10">
    <AdminNavigation />
    <div class="mb-8 flex flex-wrap items-center justify-between gap-4">
      <div><p class="text-xs tracking-[0.3em] text-ember">DESTINATION DESK</p><h1 class="mt-3 font-serif text-3xl text-pine-deep">目的地管理</h1><p class="mt-2 text-sm text-ink-soft">整理值得出发的地方，维护清晰、可靠的目的地信息。</p></div>
      <div class="flex gap-3"><RouterLink to="/destinations" class="btn-ghost">浏览目的地</RouterLink><button class="btn-primary" data-test="admin-new" :disabled="saving || uploading" @click="startNew">新增目的地</button></div>
    </div>
    <p v-if="notice" role="status" class="mb-5 rounded-xl border border-pine/20 bg-pine-soft/40 p-3 text-sm text-pine">{{ notice }}</p>
    <div class="grid items-start gap-6 xl:grid-cols-[minmax(0,1.25fr)_minmax(22rem,1fr)]">
      <div class="min-w-0">
        <form class="surface-card mb-5 grid gap-3 p-4 sm:grid-cols-[minmax(0,1fr)_8rem_auto]" @submit.prevent="search">
          <div><label for="admin-keyword" class="field-label">搜索名称</label><input id="admin-keyword" v-model="keyword" class="field-input" type="search" /></div>
          <div><label for="admin-status" class="field-label">状态</label><select id="admin-status" v-model="statusFilter" class="field-input" @change="search"><option value="">全部状态</option><option value="ACTIVE">可用</option><option value="INACTIVE">已停用</option></select></div>
          <button class="btn-ghost self-end" type="submit" :disabled="loading">查找</button>
        </form>
        <div v-if="loading" role="status" class="surface-card p-8 text-center text-ink-soft">正在加载目的地…</div>
        <div v-else-if="loadError" role="alert" class="surface-card p-6"><p>{{ loadError }}</p><button class="btn-ghost mt-4" data-test="retry-admin-destinations" @click="load">重新加载</button></div>
        <div v-else-if="!result.records.length" class="surface-card p-8 text-center text-ink-soft">没有符合筛选条件的目的地。</div>
        <div v-else class="surface-card overflow-x-auto">
          <table class="w-full min-w-[32rem] text-left text-sm">
            <thead class="border-b border-pine/10 text-xs text-ink-soft"><tr><th scope="col" class="p-4">目的地</th><th scope="col" class="p-4">状态</th><th scope="col" class="p-4">操作</th></tr></thead>
            <tbody><tr v-for="destination in result.records" :key="destination.id" class="border-b border-pine/10 last:border-0">
              <td class="p-4"><RouterLink :to="{ name: 'destination-detail', params: { id: destination.id } }" class="font-medium text-pine hover:underline">{{ destination.name }}</RouterLink><p class="mt-1 text-xs text-ink-soft">{{ destinationTypeLabels[destination.destinationType] }} · {{ destination.country }}</p></td>
              <td class="p-4"><span :class="destination.status === 'ACTIVE' ? 'text-pine' : 'text-ember'">{{ destination.status === 'ACTIVE' ? '可用' : '已停用' }}</span></td>
              <td class="p-4"><div class="flex gap-3"><button :data-test="`edit-${destination.id}`" class="font-medium text-pine underline underline-offset-4" :disabled="saving || uploading" @click="edit(destination)">编辑</button><button :data-test="`status-${destination.id}`" class="text-ink-soft underline underline-offset-4" :disabled="changing !== null || saving || uploading" @click="toggle(destination)">{{ destination.status === 'ACTIVE' ? '停用' : '启用' }}</button></div></td>
            </tr></tbody>
          </table>
        </div>
        <nav v-if="result.pages > 1" aria-label="管理列表分页" class="mt-5 flex items-center justify-center gap-3"><button class="btn-ghost" :disabled="loading || page <= 1" @click="turn(-1)">上一页</button><span class="text-sm text-ink-soft">{{ result.current }} / {{ result.pages }}</span><button class="btn-ghost" :disabled="loading || page >= result.pages" @click="turn(1)">下一页</button></nav>
      </div>
      <form data-test="destination-form" class="surface-card space-y-4 p-5 sm:p-6" novalidate @submit.prevent="save">
        <h2 class="font-serif text-2xl text-pine-deep">{{ editingId ? '编辑目的地' : '新增目的地' }}</h2>
        <div><label for="destination-name" class="field-label">名称</label><input id="destination-name" ref="nameInput" v-model="form.name" data-test="destination-name" class="field-input" :disabled="saving" required /></div>
        <div class="grid grid-cols-2 gap-3"><div><label for="edit-type" class="field-label">类型</label><select id="edit-type" v-model="form.destinationType" class="field-input" :disabled="saving"><option v-for="(label, value) in destinationTypeLabels" :key="value" :value="value">{{ label }}</option></select></div><div><label for="edit-state" class="field-label">状态</label><select id="edit-state" v-model="form.status" class="field-input" :disabled="saving"><option value="ACTIVE">可用</option><option value="INACTIVE">已停用</option></select></div></div>
        <div><label for="edit-country" class="field-label">国家</label><input id="edit-country" v-model="form.country" class="field-input" :disabled="saving" required /></div>
        <div class="grid grid-cols-2 gap-3"><div><label for="edit-province" class="field-label">省份（可选）</label><input id="edit-province" v-model="form.province" data-test="destination-province" class="field-input" :disabled="saving" /></div><div><label for="edit-city" class="field-label">城市（可选）</label><input id="edit-city" v-model="form.city" class="field-input" :disabled="saving" /></div></div>
        <div><label for="parent-keyword" class="field-label">查找上级目的地（可选）</label><div class="flex gap-2"><input id="parent-keyword" v-model="parentKeyword" class="field-input min-w-0" placeholder="输入上级名称" @keydown.enter.prevent="loadParents" /><button type="button" class="btn-ghost shrink-0" :disabled="parentLoading" @click="loadParents">查找</button></div>
          <label for="edit-parent" class="sr-only">上级目的地</label><select id="edit-parent" v-model="form.parentId" data-test="destination-parent" class="field-input mt-2" :disabled="saving || parentLoading"><option value="">无上级目的地</option><option v-for="parent in parentOptions" :key="parent.id" :value="parent.id">{{ parent.name }}{{ parent.status === 'INACTIVE' ? '（已停用，保留历史关联）' : '' }}</option></select>
          <p v-if="form.parentId && form.parentId === originalParentId && retainedParent?.status === 'INACTIVE'" class="mt-2 text-xs text-ink-soft">可保留原有停用上级，或选择新的可用目的地。</p><p v-if="parentError" class="mt-2 text-xs text-ember">{{ parentError }}</p>
        </div>
        <div><label for="edit-description" class="field-label">介绍（可选）</label><textarea id="edit-description" v-model="form.description" data-test="destination-description" class="field-input min-h-28" rows="4" :disabled="saving" /></div>
        <ImageUpload :key="editingId ?? 'new'" v-model="form.coverImageUrl" label="上传封面" :disabled="saving" @busy="uploading = $event" @session-expired="sessionExpired" />
        <div><label for="edit-seasons" class="field-label">适合季节（可选）</label><input id="edit-seasons" v-model="form.recommendedSeasons" class="field-input" :disabled="saving" placeholder="例如：春、秋" /></div>
        <div><label for="edit-order" class="field-label">展示顺序</label><input id="edit-order" v-model.number="form.sortOrder" class="field-input" type="number" min="0" step="1" :disabled="saving" /><p class="mt-1 text-xs text-ink-soft">数值越小，展示越靠前。</p></div>
        <p v-if="saveError" role="alert" class="rounded-xl bg-ember/10 p-3 text-sm text-ember">{{ saveError }}</p>
        <div class="flex flex-wrap gap-3"><button type="submit" data-test="destination-save" class="btn-primary" :disabled="saving || uploading || changing !== null">{{ saving ? '正在保存…' : '保存目的地' }}</button><button v-if="editingId" type="button" class="btn-ghost" :disabled="saving || uploading" @click="startNew">取消编辑</button></div>
      </form>
    </div>
  </section>
</template>
