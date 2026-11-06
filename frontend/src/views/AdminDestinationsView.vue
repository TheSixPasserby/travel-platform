<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ApiError } from '@/api/client'
import { changeDestinationStatus, createDestination, fetchAdminDestinations, fetchDestination, fetchDestinations, updateDestination } from '@/api/destinations'
import ImageUpload from '@/components/ImageUpload.vue'
import AdminNavigation from '@/components/AdminNavigation.vue'
import type { PageResult } from '@/types/api'
import {
  destinationTypeLabels, type Destination, type DestinationImageInput, type DestinationInput, type DestinationStatus, type DestinationType
} from '@/types/destination'

const PARENT_PAGE_SIZE = 10
const MAX_IMAGES = 20
const MAX_IMAGE_ALT = 200
const MAX_IMAGE_AUTHOR = 300
const MAX_IMAGE_LICENSE = 100
const MAX_TEXT_LINK = 1000

interface GalleryDraft { url: string; altText: string; sourceUrl: string; author: string; license: string; licenseUrl: string; retrievedDate: string }

const router = useRouter()
const keyword = ref(''), statusFilter = ref<DestinationStatus | ''>(''), page = ref(1)
const result = ref<PageResult<Destination>>({ records: [], total: 0, current: 1, size: 10, pages: 0 })
const loading = ref(false), loadError = ref(''), saveError = ref(''), notice = ref('')
const saving = ref(false), uploading = ref(false), changing = ref<string | null>(null)
const editingId = ref<string | null>(null), originalParentId = ref<string | null>(null)
const nameInput = ref<HTMLInputElement | null>(null)
const parentKeyword = ref(''), parentLoading = ref(false), parentError = ref('')
const parentPage = ref(1), parentPages = ref(0)
const parents = ref<Destination[]>([])
const retainedParent = ref<{ id: string; name: string; status: DestinationStatus } | null>(null)
let listVersion = 0
let parentVersion = 0
let active = true

function defaults() {
  return { name: '', destinationType: 'CITY' as DestinationType, parentId: '', country: '中国', province: '', city: '',
    description: '', coverImageUrl: null as string | null, recommendedSeasons: '', status: 'ACTIVE' as DestinationStatus, sortOrder: 0,
    address: '', longitude: '', latitude: '',
    recommendedDurationMinutes: '' as string | number, recommendedDays: '' as string | number, openingHours: '', ticketInfo: '',
    officialUrl: '', sourceUrl: '', verifiedAt: '', images: [] as GalleryDraft[] }
}
const form = reactive(defaults())
/** 城市禁止父级；景点必须直接归属城市；其他目的地保留原有父链语义。 */
const needsParent = computed(() => form.destinationType !== 'CITY')
const parentOptions = computed(() => {
  const options = parents.value.filter(parent => parent.id !== editingId.value)
  if (retainedParent.value && !options.some(parent => parent.id === retainedParent.value?.id)) return [...options, retainedParent.value]
  return options
})
const galleryFull = computed(() => form.images.length >= MAX_IMAGES)
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
  if (!needsParent.value) return
  const request = ++parentVersion
  const selected = form.parentId
  parentLoading.value = true; parentError.value = ''
  try {
    const data = await fetchDestinations({ page: parentPage.value, size: PARENT_PAGE_SIZE, keyword: parentKeyword.value.trim() || undefined, destinationType: form.destinationType === 'OTHER' ? undefined : 'CITY' })
    const retained = selected && !data.records.some(parent => parent.id === selected)
      ? await fetchDestination(selected).catch(() => ({ id: selected, name: '原上级目的地', status: 'INACTIVE' as const })) : null
    if (request === parentVersion) {
      parents.value = data.records; retainedParent.value = retained; parentPages.value = data.pages
    }
  } catch (error) { if (request === parentVersion) parentError.value = message(error) }
  finally { if (request === parentVersion) parentLoading.value = false }
}
function searchParents(): void { parentPage.value = 1; void loadParents() }
function turnParents(delta: number): void { parentPage.value += delta; void loadParents() }
function resetEditor(): void {
  parentVersion++; parentLoading.value = false; parentPage.value = 1; parentPages.value = 0
  editingId.value = null; originalParentId.value = null; retainedParent.value = null
  Object.assign(form, defaults()); saveError.value = ''
}
function startNew(): void {
  if (saving.value || uploading.value) return
  resetEditor(); notice.value = ''; void loadParents(); void nextTick(() => nameInput.value?.focus())
}
function galleryFrom(destination: Destination): GalleryDraft[] {
  return [...destination.images].sort((left, right) => left.sortOrder - right.sortOrder).map(image => ({
    url: image.url, altText: image.altText, sourceUrl: image.sourceUrl, author: image.author, license: image.license,
    licenseUrl: image.licenseUrl, retrievedDate: image.retrievedAt.slice(0, 10)
  }))
}
async function edit(destination: Destination): Promise<void> {
  if (saving.value || uploading.value) return
  resetEditor(); notice.value = ''; editingId.value = destination.id; originalParentId.value = destination.parentId
  Object.assign(form, {
    name: destination.name, destinationType: destination.destinationType, parentId: destination.parentId ?? '',
    country: destination.country, province: destination.province ?? '', city: destination.city ?? '', description: destination.description ?? '',
    coverImageUrl: destination.coverImageUrl, recommendedSeasons: destination.recommendedSeasons ?? '', status: destination.status, sortOrder: destination.sortOrder,
    address: destination.address ?? '', longitude: destination.longitude ?? '', latitude: destination.latitude ?? '',
    recommendedDurationMinutes: destination.recommendedDurationMinutes === null ? '' : String(destination.recommendedDurationMinutes),
    recommendedDays: destination.recommendedDays === null ? '' : String(destination.recommendedDays),
    openingHours: destination.openingHours ?? '', ticketInfo: destination.ticketInfo ?? '',
    officialUrl: destination.officialUrl ?? '', sourceUrl: destination.sourceUrl ?? '',
    verifiedAt: destination.verifiedAt ? destination.verifiedAt.slice(0, 16) : '', images: galleryFrom(destination)
  })
  await loadParents(); nameInput.value?.focus()
}
function enqueueImage(url: string | null): void {
  if (!url) return
  if (form.images.some(image => image.url === url)) { saveError.value = '该图片已在图库中，不要重复添加'; return }
  if (galleryFull.value) { saveError.value = `图库最多 ${MAX_IMAGES} 张图片`; return }
  form.images.push({ url, altText: '', sourceUrl: '', author: '', license: '', licenseUrl: '', retrievedDate: '' })
}
function moveImage(index: number, delta: number): void {
  const next = index + delta
  if (next < 0 || next >= form.images.length) return
  const [item] = form.images.splice(index, 1)
  form.images.splice(next, 0, item!)
}
function removeImageItem(index: number): void { form.images.splice(index, 1) }

const nullable = (text: string): string | null => text.trim() || null
const HTTP_URL = /^https?:\/\/[^\s/$.?#].[^\s]*$/i
function isHttpUrl(value: string, max = MAX_TEXT_LINK): boolean {
  if (value.length > max || !HTTP_URL.test(value)) return false
  try {
    const parsed = new URL(value)
    return (parsed.protocol === 'http:' || parsed.protocol === 'https:') && !parsed.username && !parsed.password
  } catch { return false }
}
function localDateTimeToIso(value: string): string | null {
  const at = value.trim()
  if (!at) return null
  const [day, time] = at.split('T')
  if (!day || !time) return null
  const clock = time.length === 5 ? `${time}:00` : time
  return `${day}T${clock}.000+08:00`
}
function dateToIso(value: string): string | null {
  const day = value.trim()
  return /^\d{4}-\d{2}-\d{2}$/.test(day) ? `${day}T00:00:00.000+08:00` : null
}
/** 坐标最多六位小数，成对填写；留空代表"未核实"，绝不使用 0 代替。 */
function coordinateIssue(source: string): string | null {
  const longitude = form.longitude.trim(), latitude = form.latitude.trim()
  if (!longitude && !latitude) return null
  if (!longitude || !latitude) return '经纬度须成对填写，或都留空表示未核实'
  for (const value of [longitude, latitude]) {
    if (!/^-?\d+(\.\d+)?$/.test(value) || (value.split('.')[1]?.length ?? 0) > 6) return `${source}坐标最多保留六位小数，且必须是数字`
  }
  const lon = Number(longitude), lat = Number(latitude)
  if (!Number.isFinite(lon) || lon < -180 || lon > 180) return `${source}经度须在 -180 到 180 之间`
  if (!Number.isFinite(lat) || lat < -90 || lat > 90) return `${source}纬度须在 -90 到 90 之间`
  return null
}
function galleryIssues(): string[] {
  const issues: string[] = []
  if (form.images.length > MAX_IMAGES) issues.push(`图库最多 ${MAX_IMAGES} 张图片`)
  const seen = new Set<string>()
  form.images.forEach((image, index) => {
    const at = `第 ${index + 1} 张图库图片`
    if (seen.has(image.url)) issues.push(`${at}重复使用同一文件`)
    seen.add(image.url)
    if (!image.altText.trim() || image.altText.trim().length > MAX_IMAGE_ALT) issues.push(`${at}需要 1–${MAX_IMAGE_ALT} 字的替代文本（alt）`)
    if (!isHttpUrl(image.sourceUrl.trim())) issues.push(`${at}需要有效的 http(s) 来源链接`)
    if (!image.author.trim() || image.author.trim().length > MAX_IMAGE_AUTHOR) issues.push(`${at}需要填写 1–${MAX_IMAGE_AUTHOR} 字的作者`)
    if (!image.license.trim() || image.license.trim().length > MAX_IMAGE_LICENSE) issues.push(`${at}需要填写 1–${MAX_IMAGE_LICENSE} 字的许可名称`)
    if (!isHttpUrl(image.licenseUrl.trim())) issues.push(`${at}需要有效的 http(s) 许可链接`)
    if (!dateToIso(image.retrievedDate)) issues.push(`${at}需要填写获取日期`)
  })
  return issues
}
async function save(): Promise<void> {
  if (saving.value || uploading.value || changing.value !== null) return
  saveError.value = ''; notice.value = ''
  if (!form.name.trim() || !form.country.trim()) { saveError.value = '请填写目的地名称和国家'; return }
  if (!Number.isInteger(form.sortOrder) || form.sortOrder < 0) { saveError.value = '排序须为非负整数'; return }
  const coordinateError = coordinateIssue('')
  if (coordinateError) { saveError.value = coordinateError; return }
  /** type=number 输入框会按数字回写，这里统一转成字符串再校验。 */
  const asText = (value: string | number): string => String(value ?? '').trim()
  const minutes = asText(form.recommendedDurationMinutes)
  const days = asText(form.recommendedDays)
  if (minutes && (!Number.isInteger(Number(minutes)) || Number(minutes) < 1 || Number(minutes) > 1440)) {
    saveError.value = '推荐游玩时长须为 1–1440 之间的整数分钟'; return
  }
  if (days && (!Number.isInteger(Number(days)) || Number(days) < 1 || Number(days) > 30)) {
    saveError.value = '推荐游玩天数须为 1–30 之间的整数'; return
  }
  for (const [label, value] of [['官方网站', form.officialUrl], ['资料来源', form.sourceUrl]] as const) {
    if (value.trim() && !isHttpUrl(value.trim())) { saveError.value = `${label}需要有效的 http(s) 绝对地址`; return }
  }
  const verifiedAt = localDateTimeToIso(form.verifiedAt)
  if (form.verifiedAt.trim() && !verifiedAt) { saveError.value = '资料核验时间格式不正确'; return }
  const hasVerifiedFacts = Boolean(form.longitude.trim() || form.latitude.trim() || form.openingHours.trim() || form.ticketInfo.trim())
  if (hasVerifiedFacts && (!nullable(form.sourceUrl) || !verifiedAt)) {
    saveError.value = '填写坐标、开放时间或票务信息时，必须同时填写资料来源与资料核验时间'; return
  }
  if (verifiedAt && !nullable(form.sourceUrl)) { saveError.value = '填写资料核验时间时，必须同时填写资料来源'; return }
  const galleryProblem = galleryIssues()
  if (galleryProblem.length) { saveError.value = `图库信息不完整：${galleryProblem[0]}`; return }
  const images: DestinationImageInput[] = form.images.map(image => ({
    url: image.url, altText: image.altText.trim(), sourceUrl: image.sourceUrl.trim(), author: image.author.trim(),
    license: image.license.trim(), licenseUrl: image.licenseUrl.trim(), retrievedAt: dateToIso(image.retrievedDate)!
  }))
  const payload: DestinationInput = {
    name: form.name.trim(), destinationType: form.destinationType,
    parentId: form.destinationType === 'CITY' ? null : (form.parentId || null),
    country: form.country.trim(), province: nullable(form.province), city: nullable(form.city), description: nullable(form.description),
    coverImageUrl: form.coverImageUrl, recommendedSeasons: nullable(form.recommendedSeasons), status: form.status, sortOrder: form.sortOrder,
    address: nullable(form.address), longitude: nullable(form.longitude), latitude: nullable(form.latitude),
    recommendedDurationMinutes: minutes ? Number(minutes) : null, recommendedDays: days ? Number(days) : null,
    openingHours: nullable(form.openingHours), ticketInfo: nullable(form.ticketInfo),
    officialUrl: nullable(form.officialUrl), sourceUrl: nullable(form.sourceUrl), verifiedAt, images
  }
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
/** 类型切换是用户主动行为：城市清空父级，其余类型重新拉取城市候选。 */
function onTypeChange(): void {
  if (form.destinationType === 'CITY') { form.parentId = ''; retainedParent.value = null; parentPage.value = 1; parentPages.value = 0 }
  else { parentPage.value = 1; void loadParents() }
}
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
        <div class="grid grid-cols-2 gap-3"><div><label for="edit-type" class="field-label">类型</label><select id="edit-type" v-model="form.destinationType" class="field-input" :disabled="saving" @change="onTypeChange"><option v-for="(label, value) in destinationTypeLabels" :key="value" :value="value">{{ label }}</option></select></div><div><label for="edit-state" class="field-label">状态</label><select id="edit-state" v-model="form.status" class="field-input" :disabled="saving"><option value="ACTIVE">可用</option><option value="INACTIVE">已停用</option></select></div></div>
        <div><label for="edit-country" class="field-label">国家</label><input id="edit-country" v-model="form.country" class="field-input" :disabled="saving" required /></div>
        <div class="grid grid-cols-2 gap-3"><div><label for="edit-province" class="field-label">省份（可选）</label><input id="edit-province" v-model="form.province" data-test="destination-province" class="field-input" :disabled="saving" /></div><div><label for="edit-city" class="field-label">城市（可选）</label><input id="edit-city" v-model="form.city" class="field-input" :disabled="saving" /></div></div>

        <fieldset class="space-y-3 rounded-2xl border border-pine/15 p-4">
          <legend class="px-1 text-sm font-medium text-pine-deep">资料与核验</legend>
          <p class="text-xs text-ink-soft">留空的字段在前台会显示为"未核实"，请不要用 0 或虚假坐标、价格占位。</p>
          <div><label for="edit-address" class="field-label">地址（可选）</label><input id="edit-address" v-model="form.address" data-test="destination-address" class="field-input" maxlength="300" :disabled="saving" placeholder="例如：浙江省杭州市西湖区龙井路 1 号" /></div>
          <div class="grid grid-cols-2 gap-3">
            <div><label for="edit-longitude" class="field-label">经度（最多六位小数）</label><input id="edit-longitude" v-model="form.longitude" data-test="destination-longitude" class="field-input" inputmode="decimal" :disabled="saving" placeholder="留空表示未核实" /></div>
            <div><label for="edit-latitude" class="field-label">纬度（最多六位小数）</label><input id="edit-latitude" v-model="form.latitude" data-test="destination-latitude" class="field-input" inputmode="decimal" :disabled="saving" placeholder="留空表示未核实" /></div>
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div><label for="edit-duration" class="field-label">推荐时长（分钟）</label><input id="edit-duration" v-model="form.recommendedDurationMinutes" data-test="destination-duration" class="field-input" type="number" min="1" max="1440" step="1" :disabled="saving" /></div>
            <div><label for="edit-days" class="field-label">推荐游玩天数</label><input id="edit-days" v-model="form.recommendedDays" data-test="destination-days" class="field-input" type="number" min="1" max="30" step="1" :disabled="saving" /></div>
          </div>
          <div><label for="edit-opening" class="field-label">开放时间（可选）</label><input id="edit-opening" v-model="form.openingHours" data-test="destination-opening" class="field-input" maxlength="1000" :disabled="saving" placeholder="例如：08:00-17:30（周一闭馆）" /></div>
          <div><label for="edit-ticket" class="field-label">票务信息（可选）</label><input id="edit-ticket" v-model="form.ticketInfo" data-test="destination-ticket" class="field-input" maxlength="1000" :disabled="saving" placeholder="例如：免费开放，需提前预约" /></div>
          <div><label for="edit-official" class="field-label">官方网站（可选）</label><input id="edit-official" v-model="form.officialUrl" data-test="destination-official-url" class="field-input" type="url" :disabled="saving" placeholder="https://…" /></div>
          <div><label for="edit-source" class="field-label">资料来源链接（可选）</label><input id="edit-source" v-model="form.sourceUrl" data-test="destination-source-url" class="field-input" type="url" :disabled="saving" placeholder="https://…，填写坐标/开放/票务时必填" /></div>
          <div><label for="edit-verified" class="field-label">资料核验时间（可选）</label><input id="edit-verified" v-model="form.verifiedAt" data-test="destination-verified-at" class="field-input" type="datetime-local" :disabled="saving" /></div>
        </fieldset>

        <div v-if="needsParent">
          <label for="parent-keyword" class="field-label">查找上级目的地（仅城市，服务端分页）</label>
          <div class="flex gap-2"><input id="parent-keyword" v-model="parentKeyword" class="field-input min-w-0" placeholder="输入城市名称" @keydown.enter.prevent="searchParents" /><button type="button" class="btn-ghost shrink-0" data-test="parent-search" :disabled="parentLoading" @click="searchParents">查找</button></div>
          <label for="edit-parent" class="sr-only">上级目的地</label><select id="edit-parent" v-model="form.parentId" data-test="destination-parent" class="field-input mt-2" :disabled="saving || parentLoading"><option value="">无上级目的地</option><option v-for="parent in parentOptions" :key="parent.id" :value="parent.id">{{ parent.name }}{{ parent.status === 'INACTIVE' ? '（已停用，保留历史关联）' : '' }}</option></select>
          <div v-if="parentPages > 1" class="mt-2 flex items-center gap-2 text-xs text-ink-soft"><button type="button" class="btn-ghost px-2 py-1" :disabled="parentLoading || parentPage <= 1" @click="turnParents(-1)">上一页</button><span data-test="parent-page-status">{{ parentPage }} / {{ parentPages }}</span><button type="button" class="btn-ghost px-2 py-1" data-test="parent-next-page" :disabled="parentLoading || parentPage >= parentPages" @click="turnParents(1)">下一页</button></div>
          <p v-if="form.parentId && form.parentId === originalParentId && retainedParent?.status === 'INACTIVE'" class="mt-2 text-xs text-ink-soft">可保留原有停用上级，或选择新的可用目的地。</p><p v-if="parentError" class="mt-2 text-xs text-ember">{{ parentError }}</p>
        </div>
        <p v-else class="rounded-xl border border-pine/15 bg-pine-soft/20 p-3 text-xs text-ink-soft">城市属于顶层目的地，不需要也不能设置上级目的地。景点请先在类型中选择"景点"，再挂到所属城市下。</p>

        <div><label for="edit-description" class="field-label">介绍（可选）</label><textarea id="edit-description" v-model="form.description" data-test="destination-description" class="field-input min-h-28" rows="4" :disabled="saving" /></div>
        <ImageUpload :key="editingId ?? 'new'" v-model="form.coverImageUrl" label="上传封面" :disabled="saving" @busy="uploading = $event" @session-expired="sessionExpired" />

        <section data-test="gallery-editor" class="space-y-3 rounded-2xl border border-pine/15 p-4">
          <div class="flex flex-wrap items-baseline justify-between gap-2">
            <h3 class="text-sm font-medium text-pine-deep">图库（{{ form.images.length }} / {{ MAX_IMAGES }}）</h3>
            <p class="text-xs text-ink-soft">作者、许可与来源必须如实填写，不得伪造。</p>
          </div>
          <ul v-if="form.images.length" class="space-y-3">
            <li v-for="(image, index) in form.images" :key="image.url" :data-test="`gallery-item-${index}`" class="rounded-xl border border-pine/10 p-3">
              <div class="flex gap-3">
                <img :src="image.url" :alt="image.altText || '图库图片'" class="h-16 w-20 shrink-0 rounded-lg object-cover" />
                <div class="min-w-0 flex-1 space-y-2">
                  <input v-model="image.altText" :data-test="`gallery-alt-${index}`" class="field-input" maxlength="200" :disabled="saving" placeholder="替代文本（必填）" />
                  <input v-model="image.sourceUrl" :data-test="`gallery-source-${index}`" class="field-input" :disabled="saving" placeholder="来源链接（必填，https://…）" />
                  <div class="grid grid-cols-2 gap-2">
                    <input v-model="image.author" :data-test="`gallery-author-${index}`" class="field-input" maxlength="300" :disabled="saving" placeholder="作者（必填）" />
                    <input v-model="image.license" :data-test="`gallery-license-${index}`" class="field-input" maxlength="100" :disabled="saving" placeholder="许可名称（必填）" />
                  </div>
                  <input v-model="image.licenseUrl" :data-test="`gallery-license-url-${index}`" class="field-input" :disabled="saving" placeholder="许可链接（必填，https://…）" />
                  <div class="flex flex-wrap items-end gap-2">
                    <input v-model="image.retrievedDate" :data-test="`gallery-retrieved-${index}`" class="field-input max-w-[12rem]" type="date" :disabled="saving" />
                    <button type="button" class="btn-ghost px-2 py-1" :data-test="`gallery-up-${index}`" :disabled="saving || index === 0" @click="moveImage(index, -1)">上移</button>
                    <button type="button" class="btn-ghost px-2 py-1" :data-test="`gallery-down-${index}`" :disabled="saving || index === form.images.length - 1" @click="moveImage(index, 1)">下移</button>
                    <button type="button" class="text-xs text-ember underline underline-offset-4" :data-test="`gallery-remove-${index}`" :disabled="saving" @click="removeImageItem(index)">删除</button>
                  </div>
                </div>
              </div>
            </li>
          </ul>
          <p v-else class="text-xs text-ink-soft">还没有图库图片。上传后会要求补全作者与许可信息。</p>
          <ImageUpload :key="`gallery-${editingId ?? 'new'}-${form.images.length}`" :model-value="null" label="添加图库图片" :disabled="saving || galleryFull" @update:model-value="enqueueImage" @busy="uploading = $event" @session-expired="sessionExpired" />
        </section>

        <div><label for="edit-seasons" class="field-label">适合季节（可选）</label><input id="edit-seasons" v-model="form.recommendedSeasons" class="field-input" :disabled="saving" placeholder="例如：春、秋" /></div>
        <div><label for="edit-order" class="field-label">展示顺序</label><input id="edit-order" v-model.number="form.sortOrder" class="field-input" type="number" min="0" step="1" :disabled="saving" /><p class="mt-1 text-xs text-ink-soft">数值越小，展示越靠前。</p></div>
        <p v-if="saveError" role="alert" data-test="destination-save-error" class="rounded-xl bg-ember/10 p-3 text-sm text-ember">{{ saveError }}</p>
        <div class="flex flex-wrap gap-3"><button type="submit" data-test="destination-save" class="btn-primary" :disabled="saving || uploading || changing !== null">{{ saving ? '正在保存…' : '保存目的地' }}</button><button v-if="editingId" type="button" class="btn-ghost" :disabled="saving || uploading" @click="startNew">取消编辑</button></div>
      </form>
    </div>
  </section>
</template>
