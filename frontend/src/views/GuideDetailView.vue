<script setup lang="ts">
import ContentImage from '@/components/ContentImage.vue'
import RelatedGuides from '@/components/RelatedGuides.vue'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchGuide } from '@/api/guides'
import { ApiError } from '@/api/client'
import GuideActions from '@/components/GuideActions.vue'
import CommentDrawer from '@/components/CommentDrawer.vue'
import AddToTripDrawer from '@/components/AddToTripDrawer.vue'
import SiteIcon from '@/components/SiteIcon.vue'
import { useTripIntentStore } from '@/stores/tripIntent'
import UserAvatar from '@/components/UserAvatar.vue'
import SafeMarkdown from '@/components/SafeMarkdown.vue'
import TravelArt from '@/components/TravelArt.vue'
import UserIdentity from '@/components/UserIdentity.vue'
import { useAuthStore } from '@/stores/auth'
import type { GuideDetail } from '@/types/guide'
import type { GuideInteraction } from '@/types/interaction'
const route = useRoute(),
  router = useRouter(),
  auth = useAuthStore()
const windowOrigin = window.location.origin
const tripIntent = useTripIntentStore()
const tripOpen = ref(false)
async function openTrip(): Promise<void> {
  if (!auth.isAuthenticated) {
    const redirect = router.resolve({ path: route.path, query: { ...route.query, trip: '1', comments: undefined } }).fullPath
    void router.push({ name: 'login', query: { redirect } }); return
  }
  if (commentsOpen.value) await router.replace({ query: { ...route.query, comments: undefined } })
  tripOpen.value = true
}
function closeTrip(): void {
  const wasOpen = tripOpen.value
  tripOpen.value = false
  if (wasOpen && route.query.trip) void router.replace({ query: { ...route.query, trip: undefined } })
}
function planFromGuide(ids: string[]): void {
  if (guide.value && tripIntent.select(guide.value, ids)) {
    tripOpen.value = false
    void router.push('/itineraries/ai')
  }
}
const commentsOpen = computed(() => route.query.comments === '1')
function setComments(open: boolean): void {
  void router.replace({
    query: { ...route.query, comments: open ? '1' : undefined },
    hash: route.hash
  })
}
const guide = ref<GuideDetail | null>(null),
  loading = ref(true),
  error = ref(''),
  coverFailed = ref(false)
const failedImages = reactive(new Set<string>())
const isAuthor = computed(() => guide.value?.author.id === auth.user?.id)
let version = 0
function updateInteraction(state: GuideInteraction): void {
  if (!guide.value || guide.value.id !== state.guideId) return
  guide.value.likeCount = state.likeCount
  guide.value.favoriteCount = state.favoriteCount
}
function updateCommentCount(count: number): void {
  if (guide.value) guide.value.commentCount = count
}
async function load(): Promise<void> {
  const request = ++version
  loading.value = true
  error.value = ''
  guide.value = null
  coverFailed.value = false
  failedImages.clear()
  try {
    const data = await fetchGuide(String(route.params.id ?? ''))
    if (request === version) guide.value = data
  } catch (e) {
    if (request === version)
      error.value =
        e instanceof ApiError ? e.message : '攻略暂时无法加载，请稍后重试'
  } finally {
    if (request === version) loading.value = false
  }
}
watch(
  () => route.params.id,
  () => {
    tripOpen.value = false
    void load()
  },
  { immediate: true }
)
watch(() => [guide.value?.id, auth.isAuthenticated, route.query.trip], () => {
  if (guide.value && auth.isAuthenticated && route.query.trip === '1') tripOpen.value = true
})
onBeforeUnmount(() => version++)
</script>
<template>
  <section class="article-page py-6 lg:py-8">
    <RouterLink
      :to="{ name: 'guides' }"
      class="text-sm text-pine hover:underline"
      >← 浏览旅行攻略</RouterLink
    >
    <div
      v-if="loading"
      role="status"
      class="surface-card mt-6 p-12 text-center text-ink-soft"
    >
      正在翻开这篇手记…
    </div>
    <div
      v-else-if="error"
      role="alert"
      class="surface-card mt-6 p-10 text-center"
    >
      <h1 class="font-serif text-2xl text-pine-deep">{{ error }}</h1>
      <div class="mt-6 flex flex-wrap justify-center gap-3">
        <RouterLink :to="{ name: 'guides' }" class="btn-primary"
          >返回攻略列表</RouterLink
        ><button class="btn-ghost" @click="load">重新加载</button>
      </div>
    </div>
    <article v-else-if="guide" class="mt-6">
      <div class="reading-layout" :class="{ 'comments-open': commentsOpen }">
        <div class="reading-body surface-card overflow-hidden">
          <div>
            <div
              class="relative aspect-[16/9] max-h-[28rem] min-h-44 overflow-hidden bg-pine-soft/40"
            >
              <ContentImage
                v-if="guide.coverImageUrl && !coverFailed"
                :src="guide.coverImageUrl"
                :alt="guide.title"
                class="h-full w-full object-cover"
                @error="coverFailed = true"
              />
              <TravelArt
                v-else
                variant="trail"
                class="h-full w-full"
                aria-hidden="true"
              />
            </div>
            <header class="article-heading">
              <div class="flex flex-wrap gap-3 text-xs text-pine">
                <RouterLink
                  v-for="tag in guide.tags"
                  :key="tag.id"
                  :to="{ name: 'guides', query: { tagId: tag.id } }"
                  class="rounded-full bg-pine-soft/50 px-3 py-1"
                  >#{{ tag.name }}</RouterLink
                >
              </div>
              <h1 class="article-title">{{ guide.title }}</h1>
              <p
                v-if="guide.summary"
                class="mt-5 max-w-3xl text-lg leading-relaxed text-ink-soft"
              >
                {{ guide.summary }}
              </p>
              <div class="article-byline">
                <time :datetime="guide.publishedAt || undefined">{{
                  guide.publishedAt?.slice(0, 10)
                }}</time
                ><RouterLink
                  v-if="isAuthor"
                  data-test="edit-guide"
                  :to="{ name: 'guide-edit', params: { id: guide.id } }"
                  class="text-pine underline underline-offset-4"
                  >编辑这篇攻略</RouterLink
                >
              </div>
              <dl class="mt-6 flex flex-wrap gap-x-10 gap-y-4 text-sm">
                <div v-if="guide.destination">
                  <dt class="text-xs text-ink-soft">目的地</dt>
                  <dd class="mt-1">
                    <RouterLink
                      :to="{
                        name: 'destination-detail',
                        params: { id: guide.destination.id }
                      }"
                      class="text-pine hover:underline"
                      >{{ guide.destination.name
                      }}{{
                        guide.destination.status === 'INACTIVE'
                          ? '（历史目的地）'
                          : ''
                      }}</RouterLink
                    >
                  </dd>
                </div>
                <div v-if="guide.travelDays">
                  <dt class="text-xs text-ink-soft">旅行天数</dt>
                  <dd class="mt-1 text-pine-deep">{{ guide.travelDays }} 天</dd>
                </div>
                <div v-if="guide.budgetPerPerson !== null">
                  <dt class="text-xs text-ink-soft">人均预算</dt>
                  <dd class="mt-1 text-pine-deep">
                    ¥ {{ guide.budgetPerPerson }}
                  </dd>
                </div>
                <div v-if="guide.season">
                  <dt class="text-xs text-ink-soft">旅行季节</dt>
                  <dd class="mt-1 text-pine-deep">{{ guide.season }}</dd>
                </div>
              </dl>
            </header>
          </div>
          <section
            v-if="guide.scenicSpots.length"
            data-test="guide-scenic-spots"
            class="surface-card mt-7 p-6 sm:p-8"
          >
            <h2 class="font-serif text-xl text-pine-deep">
              这篇攻略提到的景点
            </h2>
            <p class="mt-2 text-xs text-ink-soft">
              点开可查看景点地址、推荐游玩时间与开放信息。
            </p>
            <ul class="mt-4 flex flex-wrap gap-3">
              <li v-for="spot in guide.scenicSpots" :key="spot.id">
                <RouterLink
                  :to="{ name: 'destination-detail', params: { id: spot.id } }"
                  class="inline-flex items-center gap-2 rounded-full border border-pine/25 bg-pine-soft/30 px-4 py-2 text-sm text-pine hover:bg-pine-soft/50"
                  >{{ spot.name
                  }}<span
                    v-if="spot.status === 'INACTIVE'"
                    class="text-xs text-ember"
                    >（已停用）</span
                  ></RouterLink
                >
              </li>
            </ul>
          </section>
          <div class="article-content">
            <SafeMarkdown :content="guide.content" />
            <section
              v-if="guide.images.length"
              class="mt-10 border-t border-pine/10 pt-6"
            >
              <h2 class="font-serif text-xl text-pine-deep">沿途影像</h2>
              <div class="mt-4 grid gap-4 sm:grid-cols-2">
                <figure v-for="(image, index) in guide.images" :key="image.url">
                  <ContentImage
                    :position="index"
                    :images="
                      guide.images.map((i) => ({
                        src: i.url,
                        alt: i.altText || '旅行影像'
                      }))
                    "
                    v-if="!failedImages.has(image.url)"
                    :src="image.url"
                    :alt="image.altText || '攻略中的旅行影像'"
                    class="aspect-[4/3] w-full rounded-xl object-cover"
                    loading="lazy"
                    @error="failedImages.add(image.url)"
                  />
                  <div
                    v-else
                    class="flex aspect-[4/3] items-center justify-center rounded-xl bg-pine-soft/30 p-4 text-sm text-ink-soft"
                  >
                    图片暂时无法显示
                  </div>
                  <figcaption
                    v-if="image.altText"
                    class="mt-2 text-sm text-ink-soft"
                  >
                    {{ image.altText }}
                  </figcaption>
                </figure>
              </div>
            </section>
          </div>
        </div>
        <aside class="article-tools" aria-label="文章操作">
          <button type="button" data-test="add-guide-to-trip" class="trip-rail-button" @click="openTrip"><SiteIcon name="calendar" /><span>加入行程</span></button>
          <GuideActions
            layout="rail"
            :guide-id="guide.id"
            :like-count="guide.likeCount"
            :favorite-count="guide.favoriteCount"
            :comment-count="guide.commentCount"
            :comments-open="commentsOpen"
            :share-url="`${windowOrigin}/guides/${guide.id}`"
            @open-comments="setComments(!commentsOpen)"
            @change="updateInteraction"
          />
          <RouterLink
            v-if="guide.author.username"
            :to="`/users/${encodeURIComponent(guide.author.username)}`"
            class="rail-author"
            :aria-label="`查看${guide.author.nickname}的主页`"
            ><UserAvatar
              :src="guide.author.avatarUrl"
              :name="guide.author.nickname"
              size="md"
            /><span>作者</span></RouterLink
          >
        </aside>
        <aside class="article-author surface-card">
          <h2>这篇手记的作者</h2>
          <UserIdentity class="mt-5" :user="guide.author" linked />
          <p class="mt-4 whitespace-pre-line text-sm leading-7 text-ink-soft">
            {{ guide.author.bio || '把路上的风景，写成值得回看的手记。' }}
          </p>
          <p class="mt-5 border-t border-pine/10 pt-4 text-xs text-ink-soft">
            {{ guide.likeCount }} 赞 · {{ guide.favoriteCount }} 收藏 ·
            {{ guide.commentCount }} 评论
          </p>
        </aside>
      </div>
      <CommentDrawer
        :open="commentsOpen"
        :guide-id="guide.id"
        :guide-author-id="guide.author.id"
        :comment-count="guide.commentCount"
        @close="setComments(false)"
        @count-changed="updateCommentCount"
      />
      <RelatedGuides :guide-id="guide.id" />
      <AddToTripDrawer :open="tripOpen" :guide="guide" @close="closeTrip" @plan-ai="planFromGuide" />
    </article>
  </section>
</template>

<style scoped>
.trip-rail-button { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 5px; min-width: 64px; min-height: 64px; margin-bottom: 10px; border-radius: var(--ui-radius-lg); background: var(--ui-accent-soft); color: var(--ui-link); font-size: 12px; }
.trip-rail-button svg { width: 24px; height: 24px; }
.article-page {
  width: min(100% - 48px, 1560px);
  margin-inline: auto;
  min-width: 0;
  padding-bottom: 100px;
}
.reading-layout {
  display: grid;
  grid-template-columns: minmax(0, 780px) 76px minmax(160px, 220px);
  gap: 24px;
  justify-content: center;
  align-items: start;
}
.reading-body {
  min-width: 0;
  box-shadow: none;
}
.article-heading,
.article-content {
  padding: 32px 40px;
}
.article-title {
  font-family: var(--font-serif);
  margin-top: 18px;
  font-size: clamp(26px, 2.4vw, 36px);
  font-weight: 650;
  line-height: 1.4;
  letter-spacing: -0.035em;
  color: var(--color-pine-deep);
}
.article-byline {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid color-mix(in srgb, var(--color-pine) 12%, transparent);
  font-size: 12px;
  color: var(--color-ink-soft);
}
.article-content {
  padding-top: 8px;
}
.article-content :deep(.safe-markdown) {
  font-size: 17px;
  line-height: 1.95;
}
.article-tools {
  position: sticky;
  top: 96px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
}
.rail-author {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: var(--color-ink-soft);
}
.article-author {
  position: sticky;
  top: 96px;
  padding: 24px;
  box-shadow: none;
}
.article-author h2 {
  font-size: 14px;
  font-weight: 600;
}
@media (min-width: 1440px) {
  .reading-layout.comments-open {
    grid-template-columns: minmax(0, 780px) 76px;
    padding-right: 440px;
  }
  .comments-open .article-author {
    display: none;
  }
}
@media (max-width: 1199px) {
  .reading-layout {
    grid-template-columns: minmax(0, 780px) 76px;
  }
  .article-author {
    display: none;
  }
}
@media (max-width: 899px) {
  .article-page {
    width: min(100% - 24px, 780px);
  }
  .reading-layout {
    display: block;
  }
  .article-heading,
  .article-content {
    padding: 24px;
  }
  .article-tools {
    position: fixed;
    z-index: 35;
    top: auto;
    bottom: max(12px, env(safe-area-inset-bottom));
    left: 50%;
    transform: translateX(-50%);
    flex-direction: row;
    gap: 14px;
    padding: 8px 14px;
    border: 1px solid color-mix(in srgb, var(--color-pine) 18%, transparent);
    border-radius: var(--ui-radius-lg);
    background: var(--color-surface);
    box-shadow: var(--ui-shadow-lg);
  }
  .article-tools :deep(.action-rail__stack) {
    flex-direction: row;
    gap: 10px;
  }
  .article-tools :deep(.action-rail__btn) {
    width: 44px;
    height: 50px;
  }
  .article-tools :deep(.action-rail__note),
  .article-tools :deep(.action-rail__error) {
    position: absolute;
    bottom: 100%;
    left: 0;
    max-width: none;
    width: 100%;
    padding: 8px;
    border-radius: 12px;
    background: var(--color-surface);
  }
  .article-tools :deep(.action-rail) {
    padding: 0;
  }
  .rail-author {
    font-size: 10px;
    gap: 2px;
  }
  .rail-author :deep(img),
  .rail-author :deep([role='img']) {
    width: 32px;
    height: 32px;
  }
  .article-content :deep(.safe-markdown) {
    font-size: 16px;
  }
  .article-author {
    display: none;
  }
}
</style>
