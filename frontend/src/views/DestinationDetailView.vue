<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchDestination, fetchDestinationGuides, fetchDestinations } from '@/api/destinations'
import { createItineraryItem, fetchItineraries, fetchItinerary } from '@/api/itineraries'
import { fetchTags } from '@/api/guides'
import type { Tag } from '@/types/guide'
import { ApiError } from '@/api/client'
import DestinationCard from '@/components/DestinationCard.vue'
import DestinationGallery from '@/components/DestinationGallery.vue'
import GuideCard from '@/components/GuideCard.vue'
import TravelArt from '@/components/TravelArt.vue'
import { useAuthStore } from '@/stores/auth'
import type { PageResult } from '@/types/api'
import { destinationTypeLabels, type Destination } from '@/types/destination'
import type { GuideSummary } from '@/types/guide'
import type { ItineraryDay, ItinerarySummary } from '@/types/itinerary'

const SPOT_PAGE_SIZE = 6
const GUIDE_PAGE_SIZE = 6

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const destination = ref<Destination | null>(null)
const parent = ref<Destination | null>(null)
const loading = ref(true)
const error = ref('')
const imageFailed = ref(false)

const spots = ref<PageResult<Destination>>({ records: [], total: 0, current: 1, size: SPOT_PAGE_SIZE, pages: 0 })
const spotsLoading = ref(false)
const spotsError = ref('')

const guides = ref<PageResult<GuideSummary>>({ records: [], total: 0, current: 1, size: GUIDE_PAGE_SIZE, pages: 0 })
const guideTheme=ref(''),tags=ref<Tag[]>([])
const guidesLoading = ref(false)
const guidesError = ref('')

const location = computed(() => destination.value ? [...new Set([destination.value.country, destination.value.province, destination.value.city].filter(Boolean))].join(' · ') : '')
const isCity = computed(() => destination.value?.destinationType === 'CITY')
const latitude = computed(() => destination.value?.latitude ?? null)
const longitude = computed(() => destination.value?.longitude ?? null)
const coordinates = computed(() => latitude.value && longitude.value ? `${latitude.value}, ${longitude.value}` : null)
const verifiedDate = computed(() => destination.value?.verifiedAt ? destination.value.verifiedAt.slice(0, 10) : null)
const duration = computed(() => destination.value?.recommendedDurationMinutes ?? null)
const recommendedDays = computed(() => destination.value?.recommendedDays ?? null)
// —— 加入本人行程：只列出登录用户自己的进行中行程，由用户确认后才写入项目 ——
const ITINERARY_PAGE_SIZE = 50
const itineraryOpen = ref(false)
const itineraryLoading = ref(false)
const itineraryError = ref('')
const itineraryNotice = ref('')
const itineraryOptions = ref<ItinerarySummary[]>([])
const itineraryId = ref('')
const itineraryDetailLoading = ref(false)
const itineraryDays = ref<ItineraryDay[]>([])
const itineraryDayId = ref('')
const itinerarySaving = ref(false)
const canJoinItinerary = computed(() => destination.value?.destinationType === 'SCENIC_SPOT' && destination.value.status === 'ACTIVE' && parent.value?.status === 'ACTIVE')
const itineraryEntry = computed(() => canJoinItinerary.value)

let active=true, itineraryVersion=0, itineraryListVersion=0, itineraryDayVersion=0
function currentItinerary(version:number,token:string|null):boolean{return active&&version===itineraryVersion&&token===auth.token}
function clearItinerary():void{
 itineraryVersion++;itineraryListVersion++;itineraryDayVersion++;itineraryOpen.value=false;itineraryOptions.value=[];itineraryDays.value=[];itineraryId.value='';itineraryDayId.value='';itineraryError.value='';itineraryNotice.value='';itineraryLoading.value=false;itineraryDetailLoading.value=false;itinerarySaving.value=false
}
watch([()=>auth.user?.id,()=>auth.token],clearItinerary)
async function openItinerary(): Promise<void> {
  if (!auth.isAuthenticated) return
  itineraryOpen.value = true
  itineraryError.value = ''
  itineraryNotice.value = ''
  if (itineraryOptions.value.length === 0) await loadItineraries()
}
async function loadItineraries(): Promise<void> {
  const epoch=itineraryVersion,session=auth.token,request=++itineraryListVersion
  const relevant=()=>currentItinerary(epoch,session)&&request===itineraryListVersion
  itineraryLoading.value = true
  itineraryError.value = ''
  try {
    const data = await fetchItineraries({ page: 1, size: ITINERARY_PAGE_SIZE, status: 'ACTIVE' })
    if(!relevant())return
    itineraryOptions.value = data.records
    if (!data.records.some(item => item.id === itineraryId.value)) { itineraryId.value = ''; itineraryDays.value = []; itineraryDayId.value = '' }
  } catch (failure) {
    if(!relevant())return
    if (failure instanceof ApiError && failure.status === 401) void router.push({ name: 'login', query: { redirect: route.fullPath } })
    else itineraryError.value = failure instanceof ApiError ? failure.message : '行程暂时无法加载，请稍后再试'
  } finally { if(relevant())itineraryLoading.value = false }
}
async function chooseItinerary(id: string): Promise<void> {
  const epoch=itineraryVersion,session=auth.token,request=++itineraryDayVersion
  const relevant=()=>currentItinerary(epoch,session)&&request===itineraryDayVersion
  itineraryId.value = id
  itineraryDays.value = []
  itineraryDayId.value = ''
  itineraryError.value = ''
  itineraryNotice.value = ''
  if (!id) return
  itineraryDetailLoading.value = true
  try {
    const data = await fetchItinerary(id)
    if (!relevant() || itineraryId.value !== id) return
    itineraryDays.value = data.days
    itineraryDayId.value = data.days[0]?.id ?? ''
  } catch (failure) {
    if(!relevant())return
    itineraryError.value = failure instanceof ApiError ? failure.message : '行程详情暂时无法加载'
  } finally { if(relevant())itineraryDetailLoading.value = false }
}
/** 用户显式确认后才写入，避免浏览页面就改动他人的行程。 */
async function confirmAddToItinerary(): Promise<void> {
  if (itinerarySaving.value || !destination.value || !canJoinItinerary.value || !auth.isAuthenticated) return
  const epoch=itineraryVersion,session=auth.token
  const day = itineraryDays.value.find(item => item.id === itineraryDayId.value)
  if (!day) { itineraryError.value = '请选择一个行程日期'; return }
  itinerarySaving.value = true
  itineraryError.value = ''
  itineraryNotice.value = ''
  const current = destination.value
  try {
    await createItineraryItem(day.id, {
      placeName: current.name,
      destinationId: current.id,
      addressText: current.address ?? null,
      startTime: null,
      endTime: null,
      notes: null
    })
    if(!currentItinerary(epoch,session))return
    itineraryNotice.value = `已加入「${itineraryOptions.value.find(item => item.id === itineraryId.value)?.title ?? '行程'}」${day.travelDate} 的日程。`
  } catch (failure) {
    if(!currentItinerary(epoch,session))return
    if (failure instanceof ApiError && failure.status === 401) void router.push({ name: 'login', query: { redirect: route.fullPath } })
    else itineraryError.value = failure instanceof ApiError ? failure.message : '加入行程未能完成，请稍后重试'
  } finally { if(currentItinerary(epoch,session))itinerarySaving.value = false }
}

let version = 0

function reset(): void {
  spotVersion += 1; guideVersion += 1; clearItinerary(); guideTheme.value=''
  spots.value = { records: [], total: 0, current: 1, size: SPOT_PAGE_SIZE, pages: 0 }
  guides.value = { records: [], total: 0, current: 1, size: GUIDE_PAGE_SIZE, pages: 0 }
  spotsError.value = ''; guidesError.value = ''
  spotsLoading.value = false; guidesLoading.value = false
  itineraryOpen.value = false; itineraryOptions.value = []; itineraryDays.value = []
  itineraryId.value = ''; itineraryDayId.value = ''; itineraryError.value = ''; itineraryNotice.value = ''
}

let spotVersion = 0
async function loadSpots(id: string, page = 1): Promise<void> {
  const request = ++spotVersion
  spotsLoading.value = true; spotsError.value = ''
  try {
    const data = await fetchDestinations({ page, size: SPOT_PAGE_SIZE, destinationType: 'SCENIC_SPOT', parentId: id })
    if (request === spotVersion) spots.value = data
  } catch (failure) {
    if (request === spotVersion) spotsError.value = failure instanceof ApiError ? failure.message : '景点列表加载失败，请稍后再试'
  } finally { if (request === spotVersion) spotsLoading.value = false }
}

let guideVersion = 0
async function loadGuides(id: string, page = 1): Promise<void> {
  const request = ++guideVersion
  guidesLoading.value = true; guidesError.value = ''
  try {
    const data = await fetchDestinationGuides(id, { page, size: GUIDE_PAGE_SIZE, ...(guideTheme.value?{tagId:guideTheme.value}:{}) })
    if (request === guideVersion) guides.value = data
  } catch (failure) {
    if (request === guideVersion) guidesError.value = failure instanceof ApiError ? failure.message : '相关攻略加载失败，请稍后再试'
  } finally { if (request === guideVersion) guidesLoading.value = false }
}

async function load(): Promise<void> {
  const request = ++version
  loading.value = true; error.value = ''; destination.value = null; parent.value = null; imageFailed.value = false
  reset()
  try {
    const value = await fetchDestination(String(route.params.id ?? ''))
    if (request !== version) return
    destination.value = value
    void loadGuides(value.id)
    if (value.destinationType === 'CITY') void loadSpots(value.id)
    if (value.parentId) {
      const ancestor = await fetchDestination(value.parentId).catch(() => null)
      if (request === version) parent.value = ancestor
    }
  } catch (failure) {
    if (request === version) error.value = failure instanceof ApiError ? failure.message : '目的地加载失败，请稍后再试'
  } finally { if (request === version) loading.value = false }
}
watch(() => route.params.id, () => { void load() }, { immediate: true })
void (async()=>{try{const values=await fetchTags();if(active)tags.value=values??[]}catch{/* Public details remain usable when tag lookup fails. */}})()
onBeforeUnmount(()=>{active=false;version++;spotVersion++;guideVersion++;clearItinerary()})
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

          <dl class="mt-8 grid gap-4 border-t border-pine/10 pt-5 text-sm sm:grid-cols-2">
            <div v-if="!isCity" data-test="fact-address"><dt class="text-xs tracking-wide text-ink-soft">地址</dt><dd class="mt-1 text-pine-deep">{{ destination.address || '未核实' }}</dd></div>
            <div v-if="isCity" data-test="fact-recommended-days"><dt class="text-xs tracking-wide text-ink-soft">推荐游玩天数</dt><dd class="mt-1 text-pine-deep">{{ recommendedDays ? `${recommendedDays} 天` : '未核实' }}</dd></div>
            <div v-if="!isCity" data-test="fact-duration"><dt class="text-xs tracking-wide text-ink-soft">建议停留</dt><dd class="mt-1 text-pine-deep">{{ duration ? `${duration} 分钟` : '未核实' }}</dd></div>
            <div v-if="!isCity" data-test="fact-opening"><dt class="text-xs tracking-wide text-ink-soft">开放时间</dt><dd class="mt-1 text-pine-deep">{{ destination.openingHours || '未核实' }}</dd></div>
            <div v-if="!isCity" data-test="fact-ticket"><dt class="text-xs tracking-wide text-ink-soft">票务信息</dt><dd class="mt-1 text-pine-deep">{{ destination.ticketInfo || '未核实' }}</dd></div>
            <div v-if="!isCity" data-test="fact-coordinates"><dt class="text-xs tracking-wide text-ink-soft">坐标</dt><dd class="mt-1 text-pine-deep">{{ coordinates || '未核实' }}</dd></div>
            <div data-test="fact-verified"><dt class="text-xs tracking-wide text-ink-soft">资料核验日期</dt><dd class="mt-1 text-pine-deep">{{ verifiedDate || '未核实' }}</dd></div>
            <div data-test="fact-source"><dt class="text-xs tracking-wide text-ink-soft">资料来源</dt><dd class="mt-1 text-pine-deep">
              <a v-if="destination.sourceUrl" :href="destination.sourceUrl" target="_blank" rel="noopener noreferrer nofollow" class="text-pine underline">查看原始来源</a>
              <span v-else>未核实</span>
            </dd></div>
            <div v-if="!isCity" data-test="fact-official"><dt class="text-xs tracking-wide text-ink-soft">官方网站</dt><dd class="mt-1 text-pine-deep">
              <a v-if="destination.officialUrl" :href="destination.officialUrl" target="_blank" rel="noopener noreferrer nofollow" class="text-pine underline">官方网站</a>
              <span v-else>未核实</span>
            </dd></div>
            <div><dt class="text-xs tracking-wide text-ink-soft">适合季节</dt><dd class="mt-1 text-pine-deep">{{ destination.recommendedSeasons || '未核实' }}</dd></div>
          </dl>
          <p v-if="destination.sourceUrl" class="mt-3 text-xs text-ink-soft">资料来自公开来源，核验日期与来源链接均可追溯；未填写项以"未核实"标注，不做推测。</p>

          <div class="mt-8 flex flex-wrap gap-3"><RouterLink v-if="isCity" :to="{ name:'guides',query:{ destinationId:destination.id } }" class="btn-primary">看看这里的旅行攻略</RouterLink><a v-else href="#destination-guides-title" class="btn-primary">查看本景点相关攻略</a><RouterLink to="/destinations" class="btn-ghost">继续探索目的地</RouterLink></div>
        </div>
      </div>
    </article>

    <DestinationGallery v-if="!loading && !error && destination" :images="destination.images" :name="destination.name" />

    <section v-if="!loading && !error && itineraryEntry" data-test="itinerary-entry" aria-labelledby="destination-itinerary-title" class="mt-10 surface-card p-6 sm:p-8">
      <div class="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 id="destination-itinerary-title" class="font-serif text-2xl text-pine-deep">加入我的行程</h2>
          <p class="mt-2 text-sm text-ink-soft">从这里把真实景点加入你本人的日程，由你确认后才写入。</p>
        </div>
        <RouterLink v-if="!auth.isAuthenticated" data-test="itinerary-login" :to="{ name: 'login', query: { redirect: route.fullPath } }" class="btn-ghost">登录后加入行程</RouterLink>
        <button v-else-if="!itineraryOpen" type="button" data-test="itinerary-open" class="btn-ghost" @click="openItinerary">选择本人行程</button>
      </div>
      <template v-if="auth.isAuthenticated && itineraryOpen">
        <div v-if="itineraryLoading" role="status" class="mt-5 text-sm text-ink-soft">正在加载你的行程…</div>
        <p v-else-if="itineraryError && !itineraryOptions.length" role="alert" class="mt-5 text-sm text-ember">{{ itineraryError }}</p>
        <p v-else-if="!itineraryOptions.length" class="mt-5 text-sm text-ink-soft">你还没有进行中的行程，先创建一段旅程再来添加景点吧。</p>
        <div v-else class="mt-5 grid gap-4 sm:grid-cols-2">
          <div>
            <label for="itinerary-choice" class="field-label">我的行程</label>
            <select id="itinerary-choice" data-test="itinerary-select" class="field-input" :value="itineraryId" :disabled="itinerarySaving" @change="chooseItinerary(($event.target as HTMLSelectElement).value)">
              <option value="">请选择行程</option>
              <option v-for="item in itineraryOptions" :key="item.id" :value="item.id">{{ item.title }}</option>
            </select>
          </div>
          <div>
            <label for="itinerary-day" class="field-label">选择日程</label>
            <select id="itinerary-day" v-model="itineraryDayId" data-test="itinerary-day" class="field-input" :disabled="itinerarySaving || itineraryDetailLoading || !itineraryDays.length">
              <option value="">{{ itineraryDetailLoading ? '正在加载日程…' : '请选择日期' }}</option>
              <option v-for="day in itineraryDays" :key="day.id" :value="day.id">第 {{ day.dayNo }} 天 · {{ day.travelDate }}</option>
            </select>
          </div>
        </div>
        <p v-if="itineraryEntry && auth.isAuthenticated && itineraryOpen && destination" class="mt-3 text-xs text-ink-soft">将加入：{{ destination.name }}{{ destination.address ? ` · ${destination.address}` : '' }}</p>
        <p v-if="itineraryError && itineraryOptions.length" role="alert" class="mt-3 text-sm text-ember">{{ itineraryError }}</p>
        <p v-if="itineraryNotice" role="status" data-test="itinerary-notice" class="mt-3 text-sm text-pine">{{ itineraryNotice }}</p>
        <div v-if="itineraryOptions.length" class="mt-4 flex flex-wrap gap-3">
          <button type="button" data-test="itinerary-confirm" class="btn-primary" :disabled="itinerarySaving || !itineraryDayId" @click="confirmAddToItinerary">{{ itinerarySaving ? '正在加入…' : '确认加入行程' }}</button>
          <RouterLink v-if="itineraryId" :to="{ name: 'itinerary-detail', params: { id: itineraryId } }" class="btn-ghost">查看这段行程</RouterLink>
        </div>
      </template>
    </section>

    <section v-if="!loading && !error && destination" data-test="destination-guides" aria-labelledby="destination-guides-title" class="mt-10">
      <div class="flex flex-wrap items-end justify-between gap-3">
        <h2 id="destination-guides-title" class="font-serif text-2xl text-pine-deep">{{ isCity ? '关于这里的公开攻略' : '与此地相关的公开攻略' }}</h2>
        <span v-if="!guidesLoading && !guidesError" class="text-sm text-ink-soft">当前主题 {{ guides.total }} 篇公开攻略</span>
      </div>
      <label class="mt-4 block max-w-xs text-sm text-ink-soft">旅行主题
        <select v-model="guideTheme" data-test="guides-theme" class="field-input mt-2" :disabled="guidesLoading" @change="loadGuides(destination.id,1)">
          <option value="">全部主题</option><option v-for="tag in tags" :key="tag.id" :value="tag.id">{{ tag.name }}</option>
        </select>
      </label>
      <div v-if="guidesLoading" role="status" class="surface-card mt-4 p-8 text-center text-ink-soft">正在加载相关攻略…</div>
      <div v-else-if="guidesError" data-test="guides-error" role="alert" class="surface-card mt-4 p-6 text-center text-sm text-ink">{{ guidesError }}</div>
      <p v-else-if="guides.records.length === 0" class="surface-card mt-4 p-8 text-center text-sm text-ink-soft">这里还没有公开的旅行攻略，等你写下第一篇。</p>
      <template v-else>
        <div class="mt-4 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          <GuideCard v-for="guide in guides.records" :key="guide.id" :guide="guide" />
        </div>
        <nav v-if="guides.pages > 1" aria-label="相关攻略分页" class="mt-6 flex items-center justify-center gap-4">
          <button class="btn-ghost" :disabled="guidesLoading || guides.current <= 1" @click="loadGuides(destination.id, guides.current - 1)">上一页</button>
          <span class="text-sm tabular-nums text-ink-soft">{{ guides.current }} / {{ guides.pages }}</span>
          <button class="btn-ghost" data-test="guides-next-page" :disabled="guidesLoading || guides.current >= guides.pages" @click="loadGuides(destination.id, guides.current + 1)">下一页</button>
        </nav>
      </template>
    </section>

    <section v-if="!loading && !error && destination && isCity" data-test="city-spots" aria-labelledby="city-spots-title" class="mt-10">
      <div class="flex flex-wrap items-end justify-between gap-3">
        <h2 id="city-spots-title" class="font-serif text-2xl text-pine-deep">这座城市的景点</h2>
        <span v-if="!spotsLoading && !spotsError" class="text-sm text-ink-soft">已收录 {{ spots.total }} 个可用景点</span>
      </div>
      <div v-if="spotsLoading" role="status" class="surface-card mt-4 p-8 text-center text-ink-soft">正在加载景点…</div>
      <div v-else-if="spotsError" data-test="spots-error" role="alert" class="surface-card mt-4 p-6 text-center text-sm text-ink">{{ spotsError }}</div>
      <p v-else-if="spots.records.length === 0" class="surface-card mt-4 p-8 text-center text-sm text-ink-soft">这座城市还没有登记景点。</p>
      <template v-else>
        <div class="mt-4 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          <DestinationCard v-for="item in spots.records" :key="item.id" :destination="item" />
        </div>
        <nav v-if="spots.pages > 1" aria-label="景点分页" class="mt-6 flex items-center justify-center gap-4">
          <button class="btn-ghost" :disabled="spotsLoading || spots.current <= 1" @click="loadSpots(destination.id, spots.current - 1)">上一页</button>
          <span data-test="spots-page-status" class="text-sm tabular-nums text-ink-soft">{{ spots.current }} / {{ spots.pages }}</span>
          <button class="btn-ghost" data-test="spots-next-page" :disabled="spotsLoading || spots.current >= spots.pages" @click="loadSpots(destination.id, spots.current + 1)">下一页</button>
        </nav>
      </template>
    </section>
  </section>
</template>
