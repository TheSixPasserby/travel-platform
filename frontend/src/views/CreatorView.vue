<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute } from 'vue-router'
import { fetchCreator, fetchCreatorFeed } from '@/api/creators'
import { ApiError } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useInfiniteFeed } from '@/composables/useInfiniteFeed'
import GuideGrid from '@/components/GuideGrid.vue'
import SiteIcon from '@/components/SiteIcon.vue'
import UserAvatar from '@/components/UserAvatar.vue'
import type { CreatorProfile } from '@/types/creator'
import type { GuideSummary } from '@/types/guide'

const route = useRoute()
const auth = useAuthStore()
const username = computed(() => String(route.params.username ?? ''))
const creator = ref<CreatorProfile | null>(null)
const loading = ref(true)
const profileError = ref('')
const sentinel = ref<HTMLElement | null>(null)
const mine = computed(() =>
  Boolean(creator.value && auth.user?.id === creator.value.id)
)
const displayName = computed(
  () => creator.value?.nickname || creator.value?.username || '旅行者'
)
const guideCountText = computed(
  () => `${creator.value?.publishedGuideCount ?? 0} 篇公开手记`
)
const metrics = computed(() => [
  { label: '公开笔记', value: creator.value?.publishedGuideCount ?? 0 },
  { label: '获得点赞', value: creator.value?.likeCount ?? 0 },
  { label: '被收藏', value: creator.value?.favoriteCount ?? 0 }
])
const feed = useInfiniteFeed<GuideSummary>({
  fetchPage: (cursor, signal) =>
    fetchCreatorFeed(username.value, { size: 12, cursor }, signal),
  keyOf: (guide) => guide.id,
  cacheKey: () => `creator:${username.value}`,
  cacheScope: () => auth.token ?? 'anonymous'
})
const { items: notes, loading: feedLoading, loadingMore, hasMore } = feed
const feedError = computed(() => feed.error.value || feed.moreError.value)
const showEmpty = computed(
  () => !feedLoading.value && !feedError.value && !notes.value.length
)
const format = (value: number) => new Intl.NumberFormat('zh-CN').format(value)
let version = 0
let observer: IntersectionObserver | null = null
let armed = true
let savedTop = 0
const scrollKey = () =>
  `m13-creator:${auth.user?.id ?? 'public'}:${username.value}`

async function load(): Promise<void> {
  if (!username.value) return
  const ticket = ++version
  loading.value = true
  profileError.value = ''
  creator.value = null
  try {
    const profile = await fetchCreator(username.value)
    if (ticket !== version) return
    creator.value = profile
    await feed.loadFirst()
  } catch (error) {
    if (ticket === version)
      profileError.value =
        error instanceof ApiError
          ? error.message
          : '旅人的主页暂时无法加载，请重试'
  } finally {
    if (ticket === version) {
      loading.value = false
      await nextTick()
      if (savedTop > 0)
        requestAnimationFrame(() =>
          window.scrollTo({ top: savedTop, behavior: 'auto' })
        )
    }
  }
}
async function loadMore(): Promise<void> {
  await feed.loadMore()
}
async function retryFeed(): Promise<void> {
  await feed.retry()
}

watch(sentinel, (element) => {
  observer?.disconnect()
  if (!element || typeof IntersectionObserver !== 'function') return
  observer = new IntersectionObserver(
    (entries) => {
      if (!entries.some((entry) => entry.isIntersecting)) {
        armed = true
        return
      }
      if (armed && hasMore.value && !loadingMore.value && !feedError.value) {
        armed = false
        void loadMore()
      }
    },
    { rootMargin: '480px 0px' }
  )
  observer.observe(element)
})
watch(
  [username, () => auth.token],
  () => {
    armed = true
    savedTop = 0
    try {
      savedTop = Number(sessionStorage.getItem(scrollKey())) || 0
    } catch {
      /* Storage is optional. */
    }
    void load()
  },
  { immediate: true }
)
onBeforeRouteLeave(() => {
  try {
    const key = scrollKey()
    const keys = Object.keys(sessionStorage).filter(
      (item) => item.startsWith('m13-creator:') && item !== key
    )
    while (keys.length >= 6) sessionStorage.removeItem(keys.shift()!)
    sessionStorage.setItem(key, String(window.scrollY))
  } catch {
    /* Reading remains available when storage is disabled. */
  }
})
onBeforeUnmount(() => {
  version++
  observer?.disconnect()
  feed.dispose()
})
</script>
<template>
  <section class="creator-page" aria-labelledby="creator-title">
    <div class="stream-container py-5 lg:py-7">
      <RouterLink to="/" class="back-link">← 发现更多旅程</RouterLink>

      <div
        v-if="loading"
        role="status"
        class="surface-card mt-4 p-12 text-center text-ink-soft"
      >
        正在打开旅人的主页…
      </div>

      <div v-else-if="profileError" role="alert" class="surface-card mt-4 p-8">
        <h1 class="font-serif text-2xl text-pine-deep">{{ profileError }}</h1>
        <button
          type="button"
          data-test="creator-retry"
          class="btn-ghost mt-5"
          @click="load"
        >
          重新加载
        </button>
      </div>

      <template v-else-if="creator">
        <header class="profile-hero">
          <div class="profile-cover" aria-hidden="true">
            <span class="cover-sun"></span>
            <svg
              class="cover-art"
              viewBox="0 0 1440 300"
              preserveAspectRatio="none"
              focusable="false"
            >
              <path
                d="M0 236 168 118 300 202 432 92 566 190 706 116 838 208 972 104 1112 198 1250 140 1440 226V300H0Z"
                opacity=".38"
                fill="currentColor"
              ></path>
              <path
                d="M0 268 136 182 262 246 404 158 540 232 690 166 820 240 964 170 1114 244 1272 176 1440 252V300H0Z"
                opacity=".6"
                fill="currentColor"
              ></path>
              <path
                d="M0 292 120 244 268 288 420 236 566 282 720 240 862 286 1010 232 1168 284 1310 240 1440 286V300H0Z"
                opacity=".9"
                fill="currentColor"
              ></path>
            </svg>
          </div>

          <div class="profile-content">
            <div class="profile-avatar">
              <RouterLink
                :to="'/users/' + encodeURIComponent(creator.username)"
                :aria-label="displayName + '的主页'"
                ><UserAvatar
                  :src="creator.avatarUrl"
                  :name="displayName"
                  size="lg"
              /></RouterLink>
            </div>
            <div class="profile-id">
              <h1 id="creator-title" class="profile-name">{{ displayName }}</h1>
              <p class="profile-username">@{{ creator.username }}</p>
              <p class="profile-count">{{ guideCountText }}</p>
              <p v-if="creator.bio" class="profile-bio">{{ creator.bio }}</p>
            </div>
          </div>

          <dl class="profile-stats">
            <div
              v-for="metric in metrics"
              :key="metric.label"
              class="profile-stat"
              data-test="creator-stat"
            >
              <dd class="stat-value">{{ format(metric.value) }}</dd>
              <dt class="stat-label">{{ metric.label }}</dt>
            </div>
          </dl>

          <nav
            v-if="mine"
            class="owner-tools"
            aria-label="我的创作快捷入口"
            data-test="creator-owner-tools"
          >
            <RouterLink to="/me/guides?status=DRAFT" class="owner-tool"
              ><SiteIcon name="book" />草稿箱</RouterLink
            >
            <RouterLink to="/me/favorites" class="owner-tool"
              ><SiteIcon name="star" />我的收藏</RouterLink
            >
            <RouterLink to="/me/itineraries" class="owner-tool"
              ><SiteIcon name="calendar" />我的行程</RouterLink
            >
            <RouterLink to="/me/settings" class="owner-tool"
              ><SiteIcon name="settings" />编辑资料</RouterLink
            >
          </nav>
        </header>

        <section class="notes-section" aria-labelledby="creator-notes-title">
          <header class="notes-head">
            <div>
              <h2 id="creator-notes-title" class="font-serif">旅行笔记</h2>
              <p class="notes-hint">按发布时间排列</p>
            </div>
            <button
              v-if="feedError"
              type="button"
              class="btn-ghost"
              data-test="creator-feed-retry"
              @click="retryFeed"
            >
              重试加载手记
            </button>
          </header>

          <p v-if="feedError" role="alert" class="feed-alert">
            {{ feedError }}
          </p>

          <div
            v-if="feedLoading"
            class="notes-skeleton"
            role="status"
            aria-label="正在加载旅行笔记"
          >
            <div v-for="n in 4" :key="n" class="skeleton-card"></div>
          </div>
          <GuideGrid v-else-if="notes.length" :guides="notes" />
          <div v-else-if="showEmpty" class="surface-card empty-notes">
            <p>这位旅人还没有公开的旅行手记</p>
          </div>

          <div v-if="notes.length && hasMore" class="notes-more">
            <button
              type="button"
              class="btn-ghost"
              data-test="creator-load-more"
              :disabled="loadingMore"
              @click="loadMore"
            >
              {{ loadingMore ? '正在加载更多…' : '加载更多手记' }}
            </button>
          </div>
          <p
            v-else-if="notes.length"
            class="notes-end"
            data-test="creator-feed-end"
          >
            已经看到全部公开手记
          </p>
          <div ref="sentinel" class="notes-sentinel" aria-hidden="true"></div>
        </section>
      </template>
    </div>
  </section>
</template>

<style scoped>
.creator-page {
  min-height: 60vh;
}
/* 与 main.css 的 .stream-container 同义；此处保留一份 scoped 兜底，避免样式表未加载时贴边。 */
.stream-container {
  width: 100%;
  min-width: 0;
  max-width: var(--ui-stream-max, 96rem);
  margin-inline: auto;
  padding-inline: 1.25rem;
}
@media (min-width: 768px) {
  .stream-container {
    padding-inline: 2rem;
  }
}
.back-link {
  display: inline-flex;
  min-height: 44px;
  align-items: center;
  font-size: 0.875rem;
  color: var(--color-pine);
  transition: opacity var(--motion-fast) var(--ease-standard);
}
.back-link:hover {
  text-decoration: underline;
  text-underline-offset: 4px;
}
.profile-hero {
  overflow: hidden;
  border: 1px solid
    color-mix(in srgb, var(--color-pine) 18%, var(--color-surface));
  border-radius: var(--ui-radius-lg);
  background: var(--color-surface);
  box-shadow: var(--ui-shadow-md);
}
.profile-cover {
  position: relative;
  height: 140px;
  overflow: hidden;
  color: var(--color-pine-deep);
  background:
    radial-gradient(
      120% 160% at 18% 0%,
      color-mix(in srgb, var(--color-pine-soft) 88%, var(--color-surface)) 0%,
      transparent 62%
    ),
    linear-gradient(
      118deg,
      color-mix(in srgb, var(--color-pine) 26%, var(--color-pine-soft)) 0%,
      color-mix(in srgb, var(--color-pine-soft) 74%, var(--color-surface)) 52%,
      color-mix(in srgb, var(--color-cream-deep) 82%, var(--color-surface)) 100%
    );
}
.cover-art {
  position: absolute;
  inset: auto 0 0;
  width: 100%;
  height: 78%;
  color: color-mix(in srgb, var(--color-pine-deep) 82%, transparent);
}
.cover-sun {
  position: absolute;
  top: 18%;
  right: 12%;
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--color-surface) 72%, var(--color-ember));
  opacity: 0.55;
}
.profile-content {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 0.5rem 1.25rem;
  padding: 0 1.25rem;
}
.profile-avatar {
  margin-top: -2.75rem;
  flex-shrink: 0;
  padding: 4px;
  border-radius: 999px;
  background: var(--color-surface);
  box-shadow: var(--ui-shadow-sm);
}
.profile-id {
  min-width: 0;
  flex: 1 1 14rem;
  padding-block: 0.25rem 0.5rem;
}
.profile-identity {
  display: block;
}
.profile-count {
  margin-top: 0.25rem;
  font-size: 0.8125rem;
  font-weight: 600;
  letter-spacing: 0.02em;
  color: var(--color-pine);
}
.profile-bio {
  margin-top: 0.5rem;
  max-width: 46rem;
  white-space: pre-line;
  overflow-wrap: anywhere;
  font-size: 0.9375rem;
  line-height: 1.8;
  color: var(--color-ink-soft);
}
.profile-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.5rem;
  margin: 1rem 1.25rem 0;
  padding: 0.875rem 0 1rem;
  border-top: 1px solid color-mix(in srgb, var(--color-pine) 16%, transparent);
}
.profile-stat {
  min-width: 0;
}
.stat-value {
  font-family: var(--font-serif);
  font-size: 1.375rem;
  line-height: 1.2;
  font-weight: 600;
  color: var(--color-pine-deep);
  font-variant-numeric: tabular-nums;
}
.stat-label {
  margin-top: 0.125rem;
  font-size: 0.75rem;
  letter-spacing: 0.04em;
  color: var(--color-ink-soft);
}
.owner-tools {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin: 0 1.25rem 1.25rem;
  padding-top: 0.75rem;
  border-top: 1px solid color-mix(in srgb, var(--color-pine) 16%, transparent);
}
.owner-tool {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  min-height: 44px;
  padding: 0.375rem 0.875rem;
  border: 1px solid color-mix(in srgb, var(--color-pine) 26%, transparent);
  border-radius: 999px;
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--color-pine);
  transition:
    background-color var(--motion-fast) var(--ease-standard),
    border-color var(--motion-fast) var(--ease-standard),
    transform var(--motion-fast) var(--ease-out);
}
.owner-tool svg {
  width: 18px;
  height: 18px;
}
.owner-tool:hover {
  border-color: var(--color-pine);
  background: var(--color-pine-soft);
  transform: translateY(-1px);
}
.notes-section {
  margin-top: 2rem;
}
.notes-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  margin-bottom: 1rem;
}
.notes-head h2 {
  font-size: 1.375rem;
  color: var(--color-pine-deep);
}
.notes-hint {
  margin-top: 0.125rem;
  font-size: 0.75rem;
  color: var(--color-ink-soft);
}
.feed-alert {
  margin-bottom: 1rem;
  padding: 0.75rem 1rem;
  border: 1px solid color-mix(in srgb, var(--color-ember) 40%, transparent);
  border-radius: 0.875rem;
  background: color-mix(in srgb, var(--color-ember) 8%, transparent);
  font-size: 0.875rem;
  color: var(--color-ember);
}
.empty-notes {
  padding: 2.5rem 1.25rem;
  text-align: center;
  color: var(--color-ink-soft);
}
.notes-skeleton {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1.5rem;
}
.skeleton-card {
  aspect-ratio: 4 / 5;
  border-radius: var(--radius-card);
  background: linear-gradient(
    100deg,
    color-mix(in srgb, var(--color-pine-soft) 55%, var(--color-surface)) 0%,
    color-mix(in srgb, var(--color-cream-deep) 70%, var(--color-surface)) 48%,
    color-mix(in srgb, var(--color-pine-soft) 55%, var(--color-surface)) 100%
  );
  animation: skeleton-shimmer 1.4s ease-in-out infinite;
}
@keyframes skeleton-shimmer {
  0%,
  100% {
    opacity: 0.55;
  }
  50% {
    opacity: 1;
  }
}
.notes-more {
  display: flex;
  justify-content: center;
  margin-top: 1.75rem;
}
.notes-end {
  margin-top: 1.75rem;
  text-align: center;
  font-size: 0.8125rem;
  letter-spacing: 0.04em;
  color: var(--color-ink-soft);
}
.notes-sentinel {
  height: 1px;
}
@media (min-width: 600px) {
  .notes-skeleton {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (min-width: 960px) {
  .notes-skeleton {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
@media (min-width: 640px) {
  .profile-cover {
    height: 176px;
  }
  .profile-content {
    padding-inline: 1.75rem;
  }
  .profile-stats {
    margin-inline: 1.75rem;
  }
  .owner-tools {
    margin-inline: 1.75rem;
  }
  .stat-value {
    font-size: 1.625rem;
  }
}
@media (min-width: 1024px) {
  .profile-cover {
    height: 232px;
  }
  .profile-avatar {
    margin-top: -3.25rem;
  }
  .profile-stats {
    grid-template-columns: repeat(3, minmax(0, 11rem));
    justify-content: start;
    gap: 2.5rem;
  }
  .notes-skeleton {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}
@media (prefers-reduced-motion: reduce) {
  .owner-tool {
    transition: none;
  }
  .owner-tool:hover {
    transform: none;
  }
  .skeleton-card {
    animation: none;
  }
}
.profile-name {
  font-family: var(--font-serif);
  font-size: 30px;
  font-weight: 650;
  line-height: 1.4;
  color: var(--color-pine-deep);
}
.profile-username {
  margin-top: 6px;
  font-size: 13px;
  color: var(--color-ink-soft);
}
.profile-id {
  padding-bottom: 12px;
}
.profile-avatar :deep(a > span) {
  width: 104px;
  height: 104px;
}
@media (min-width: 1024px) {
  .profile-content {
    gap: 24px;
    padding-top: 12px;
  }
  .profile-name {
  font-family: var(--font-serif);
    font-size: 34px;
  }
}
</style>
