<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ApiError } from '@/api/client'
import {
  fetchInteraction,
  setGuideFavorite,
  setGuideLike
} from '@/api/interactions'
import { useAuthStore } from '@/stores/auth'
import type { GuideInteraction } from '@/types/interaction'

const props = withDefaults(
  defineProps<{
    guideId: string
    likeCount: number
    favoriteCount: number
    commentCount: number
    /** inline：详情页旧胶囊按钮；rail：右侧竖排图标栏。 */
    layout?: 'inline' | 'rail'
    /** 分享地址，缺省用当前页面地址。 */
    shareUrl?: string
    /** 评论抽屉是否已展开，用于 aria-expanded。 */
    commentsOpen?: boolean
  }>(),
  { layout: 'inline', shareUrl: undefined, commentsOpen: false }
)

const emit = defineEmits<{
  change: [interaction: GuideInteraction]
  'open-comments': []
}>()

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

/**
 * 服务端确认过的互动状态。null 表示「还不知道」：
 * 此时禁止按默认 false 推断反向动作，按钮只允许重试。
 */
const interaction = ref<GuideInteraction | null>(null)
/** 两个按钮共用同一把锁，保证互动请求串行。 */
const pending = ref<'like' | 'favorite' | null>(null)
const loading = ref(false)
const error = ref('')
const sessionExpired = ref(false)

/** 攻略切换、登录身份变化时自增，用于丢弃迟到的 GET / 动作结果。 */
let version = 0

/** 有令牌但用户资料尚未恢复：不当作匿名，避免误跳登录或误发请求。 */
const restoring = computed(() => Boolean(auth.token) && !auth.user)
/** 仅当登录身份已知且服务端状态已确认时，才对外表达选中状态。 */
const known = computed(() => auth.isAuthenticated && interaction.value !== null)

const likeActive = computed(
  () => known.value && interaction.value?.liked === true
)
const favoriteActive = computed(
  () => known.value && interaction.value?.favorited === true
)

const likePressed = computed(() =>
  known.value && interaction.value
    ? interaction.value.liked
      ? 'true'
      : 'false'
    : undefined
)
const favoritePressed = computed(() =>
  known.value && interaction.value
    ? interaction.value.favorited
      ? 'true'
      : 'false'
    : undefined
)

/** 未加载前先用父页传入的计数，加载后一律以真实状态为准。 */
const likeTotal = computed(() =>
  interaction.value ? interaction.value.likeCount : props.likeCount
)
const favoriteTotal = computed(() =>
  interaction.value ? interaction.value.favoriteCount : props.favoriteCount
)

const busy = computed(
  () => loading.value || pending.value !== null || restoring.value
)
const actionLocked = computed(
  () => busy.value || (auth.isAuthenticated && interaction.value === null)
)

/** 点赞真实成功后的轻弹提示，300ms 后自动复位。 */
const bumped = ref<'like' | 'favorite' | null>(null)
let bumpTimer: ReturnType<typeof setTimeout> | null = null
function bump(kind: 'like' | 'favorite'): void {
  bumped.value = kind
  if (bumpTimer) {
    clearTimeout(bumpTimer)
  }
  bumpTimer = setTimeout(() => {
    bumped.value = null
  }, 300)
}

type ShareState = 'idle' | 'shared' | 'copied' | 'failed'
const shareState = ref<ShareState>('idle')
let shareTimer: ReturnType<typeof setTimeout> | null = null
function settleShare(state: ShareState): void {
  shareState.value = state
  if (shareTimer) {
    clearTimeout(shareTimer)
  }
  shareTimer = setTimeout(() => {
    shareState.value = 'idle'
  }, 4200)
}

async function copyLink(url: string): Promise<boolean> {
  if (
    typeof navigator === 'undefined' ||
    typeof navigator.clipboard?.writeText !== 'function'
  ) {
    return false
  }
  try {
    await navigator.clipboard.writeText(url)
    return true
  } catch {
    return false
  }
}

/** 优先复制链接，其次调用系统分享；都不可用或失败时给出可操作提示。 */
async function share(): Promise<void> {
  const url = props.shareUrl || window.location.href
  const title = document.title || '旅行手记'
  if (await copyLink(url)) {
    settleShare('copied')
    return
  }
  if (typeof navigator.share === 'function') {
    try {
      await navigator.share({ title, url })
      settleShare('shared')
      return
    } catch (cause) {
      if (cause instanceof DOMException && cause.name === 'AbortError') return
    }
  }
  settleShare('failed')
}

function messageOf(cause: unknown, fallback: string): string {
  return cause instanceof ApiError && cause.message ? cause.message : fallback
}

function goLogin(): void {
  void router.push({ name: 'login', query: { redirect: route.fullPath } })
}

async function loadInteraction(): Promise<void> {
  const ticket = ++version
  interaction.value = null
  pending.value = null
  error.value = ''
  sessionExpired.value = false
  if (!auth.isAuthenticated) {
    // 匿名不请求 viewer 状态，避免无意义的 401 和多一次网络往返。
    loading.value = false
    return
  }
  loading.value = true
  try {
    const data = await fetchInteraction(props.guideId)
    if (ticket !== version) {
      return
    }
    interaction.value = data
  } catch (cause) {
    if (ticket !== version) {
      return
    }
    if (cause instanceof ApiError && cause.status === 401) {
      // 401 只清理本地已失效状态并给出登录入口，公共页不自动跳走。
      sessionExpired.value = true
      error.value = messageOf(cause, '登录状态已失效，请重新登录')
      return
    }
    // 5xx / 网络错误不清空任何已知状态，保留重试机会。
    error.value = messageOf(cause, '互动状态暂时无法加载，请稍后重试')
  } finally {
    if (ticket === version) {
      loading.value = false
    }
  }
}

async function toggle(kind: 'like' | 'favorite'): Promise<void> {
  if (!auth.isAuthenticated) {
    if (restoring.value) {
      return
    }
    goLogin()
    return
  }
  if (busy.value || interaction.value === null) {
    return
  }

  const current = interaction.value
  const target = kind === 'like' ? !current.liked : !current.favorited
  const ticket = version
  pending.value = kind
  error.value = ''
  try {
    // 不做乐观计数：成功后整体替换 VO，失败保留旧状态。
    const next =
      kind === 'like'
        ? await setGuideLike(props.guideId, target)
        : await setGuideFavorite(props.guideId, target)
    if (ticket !== version) {
      return
    }
    interaction.value = next
    emit('change', next)
    bump(kind)
  } catch (cause) {
    if (ticket !== version) {
      return
    }
    if (cause instanceof ApiError && cause.status === 401) {
      interaction.value = null
      sessionExpired.value = true
      error.value = messageOf(cause, '登录状态已失效，请重新登录')
      return
    }
    error.value = messageOf(cause, '操作没有完成，请稍后再试')
  } finally {
    if (ticket === version) {
      pending.value = null
    }
  }
}

watch(
  () => [props.guideId, auth.user?.id ?? null, auth.token] as const,
  () => {
    void loadInteraction()
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  version += 1
  if (bumpTimer) {
    clearTimeout(bumpTimer)
  }
  if (shareTimer) {
    clearTimeout(shareTimer)
  }
})
</script>

<template>
  <section
    v-if="layout === 'rail'"
    class="action-rail"
    aria-label="攻略互动"
    data-test="guide-actions"
    data-layout="rail"
  >
    <div class="action-rail__stack">
      <button
        type="button"
        data-test="like-guide"
        class="action-rail__btn"
        :class="{ 'is-active': likeActive, 'is-bumped': bumped === 'like' }"
        :disabled="actionLocked"
        :aria-pressed="likePressed"
        :aria-busy="pending === 'like' ? 'true' : undefined"
        :aria-label="likeActive ? '取消点赞' : '点赞'"
        @click="toggle('like')"
      >
        <svg
          viewBox="0 0 24 24"
          aria-hidden="true"
          class="h-5 w-5"
          :fill="likeActive ? 'currentColor' : 'none'"
          stroke="currentColor"
          stroke-width="1.7"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path
            d="M12 20.3 5.4 14a4.6 4.6 0 0 1 6.6-6.4l.02.02.02-.02A4.6 4.6 0 0 1 18.6 14Z"
          />
        </svg>
        <span class="action-rail__count tabular-nums">{{ likeTotal }}</span>
      </button>

      <button
        type="button"
        data-test="favorite-guide"
        class="action-rail__btn"
        :class="{
          'is-active': favoriteActive,
          'is-bumped': bumped === 'favorite'
        }"
        :disabled="actionLocked"
        :aria-pressed="favoritePressed"
        :aria-busy="pending === 'favorite' ? 'true' : undefined"
        :aria-label="favoriteActive ? '取消收藏' : '收藏'"
        @click="toggle('favorite')"
      >
        <svg
          viewBox="0 0 24 24"
          aria-hidden="true"
          class="h-5 w-5"
          :fill="favoriteActive ? 'currentColor' : 'none'"
          stroke="currentColor"
          stroke-width="1.7"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path d="M6 3h12v18l-6-4-6 4V3Z" />
        </svg>
        <span class="action-rail__count tabular-nums">{{ favoriteTotal }}</span>
      </button>

      <button
        type="button"
        data-test="comment-count"
        class="action-rail__btn"
        aria-controls="guide-comments-drawer"
        :aria-expanded="commentsOpen ? 'true' : 'false'"
        aria-label="查看评论"
        @click="emit('open-comments')"
      >
        <svg
          viewBox="0 0 24 24"
          aria-hidden="true"
          class="h-5 w-5"
          fill="none"
          stroke="currentColor"
          stroke-width="1.7"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path
            d="M20 12a7.4 7.4 0 0 1-8 7.3 8.6 8.6 0 0 1-2.6-.4L4 20.5l1.4-4A7.3 7.3 0 0 1 4 12a7.4 7.4 0 0 1 8-7.3 7.4 7.4 0 0 1 8 7.3Z"
          />
        </svg>
        <span class="action-rail__count tabular-nums">{{
          props.commentCount
        }}</span>
      </button>

      <button
        type="button"
        data-test="share-guide"
        class="action-rail__btn"
        aria-label="分享这篇攻略"
        @click="share"
      >
        <svg
          viewBox="0 0 24 24"
          aria-hidden="true"
          class="h-5 w-5"
          fill="none"
          stroke="currentColor"
          stroke-width="1.7"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path d="M12 3.5 16.5 8M12 3.5 7.5 8M12 3.5V15" />
          <path
            d="M5 13v5.5A1.5 1.5 0 0 0 6.5 20h11a1.5 1.5 0 0 0 1.5-1.5V13"
          />
        </svg>
        <span class="action-rail__label">分享</span>
      </button>
    </div>

    <p v-if="shareState === 'copied'" role="status" class="action-rail__note">
      链接已复制
    </p>
    <p
      v-else-if="shareState === 'shared'"
      role="status"
      class="action-rail__note"
    >
      已打开分享
    </p>
    <p
      v-else-if="shareState === 'failed'"
      role="alert"
      class="action-rail__note is-error"
    >
      当前浏览器无法直接分享，请复制地址栏链接。
    </p>

    <div v-if="error" role="alert" class="action-rail__error">
      <p>{{ error }}</p>
      <button
        v-if="sessionExpired"
        type="button"
        data-test="login-entry"
        class="action-rail__link"
        @click="goLogin"
      >
        前往登录
      </button>
      <button
        v-else
        type="button"
        data-test="retry-interaction"
        class="action-rail__link"
        @click="loadInteraction"
      >
        重试
      </button>
    </div>
    <p
      v-else-if="!auth.isAuthenticated && !restoring"
      class="action-rail__note"
    >
      登录后可点赞与收藏
    </p>
    <p v-if="loading" class="action-rail__note" aria-live="polite">加载中…</p>
  </section>

  <section
    v-else
    class="surface-card mt-8 flex flex-col gap-3 px-5 py-4"
    aria-label="攻略互动"
    data-test="guide-actions"
    data-layout="inline"
  >
    <div class="flex flex-wrap items-center gap-2.5">
      <button
        type="button"
        data-test="like-guide"
        class="btn-ghost disabled:cursor-not-allowed disabled:opacity-60"
        :class="[
          likeActive ? 'border-pine bg-pine-soft/70 text-pine-deep' : '',
          bumped === 'like' ? 'is-bumped' : ''
        ]"
        :disabled="actionLocked"
        :aria-pressed="likePressed"
        :aria-busy="pending === 'like' ? 'true' : undefined"
        @click="toggle('like')"
      >
        <svg
          viewBox="0 0 24 24"
          aria-hidden="true"
          class="h-4 w-4"
          :fill="likeActive ? 'currentColor' : 'none'"
          stroke="currentColor"
          stroke-width="1.7"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path
            d="M7 10.5v9H4.6A1.6 1.6 0 0 1 3 17.9v-5.8a1.6 1.6 0 0 1 1.6-1.6H7Z"
          />
          <path
            d="M7 10.8 10.9 3.9a1.9 1.9 0 0 1 3.4 1.6l-.9 3.4h4.9a2 2 0 0 1 1.95 2.45l-1 5.1A2 2 0 0 1 17.3 18.2H7"
          />
        </svg>
        <span>{{ likeActive ? '已赞' : '点赞' }}</span>
        <span class="tabular-nums">{{ likeTotal }}</span>
      </button>

      <button
        type="button"
        data-test="favorite-guide"
        class="btn-ghost disabled:cursor-not-allowed disabled:opacity-60"
        :class="
          favoriteActive ? 'border-pine bg-pine-soft/70 text-pine-deep' : ''
        "
        :disabled="actionLocked"
        :aria-pressed="favoritePressed"
        :aria-busy="pending === 'favorite' ? 'true' : undefined"
        @click="toggle('favorite')"
      >
        <svg
          viewBox="0 0 24 24"
          aria-hidden="true"
          class="h-4 w-4"
          :fill="favoriteActive ? 'currentColor' : 'none'"
          stroke="currentColor"
          stroke-width="1.7"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path d="M6 3h12v18l-6-4-6 4V3Z" />
        </svg>
        <span>{{ favoriteActive ? '已收藏' : '收藏' }}</span>
        <span class="tabular-nums">{{ favoriteTotal }}</span>
      </button>

      <button
        type="button"
        class="inline-flex items-center gap-1.5 rounded-full border border-pine/15 px-4 py-2 text-sm text-ink-soft"
        data-test="comment-count"
        aria-controls="guide-comments-drawer"
        :aria-expanded="commentsOpen ? 'true' : 'false'"
        :aria-label="`查看评论，共 ${props.commentCount} 条`"
        @click="emit('open-comments')"
      >
        <svg
          viewBox="0 0 24 24"
          aria-hidden="true"
          class="h-4 w-4"
          fill="none"
          stroke="currentColor"
          stroke-width="1.7"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path
            d="M20 12a7.4 7.4 0 0 1-8 7.3 8.6 8.6 0 0 1-2.6-.4L4 20.5l1.4-4A7.3 7.3 0 0 1 4 12a7.4 7.4 0 0 1 8-7.3 7.4 7.4 0 0 1 8 7.3Z"
          />
        </svg>
        <span>评论</span>
        <span class="tabular-nums">{{ props.commentCount }}</span>
      </button>

      <button
        type="button"
        data-test="share-guide"
        class="inline-flex items-center gap-1.5 rounded-full border border-pine/15 px-4 py-2 text-sm text-ink-soft hover:text-pine"
        aria-label="分享这篇攻略"
        @click="share"
      >
        <svg
          viewBox="0 0 24 24"
          aria-hidden="true"
          class="h-4 w-4"
          fill="none"
          stroke="currentColor"
          stroke-width="1.7"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path d="M12 3.5 16.5 8M12 3.5 7.5 8M12 3.5V15" />
          <path
            d="M5 13v5.5A1.5 1.5 0 0 0 6.5 20h11a1.5 1.5 0 0 0 1.5-1.5V13"
          />
        </svg>
        <span>分享</span>
      </button>

      <span v-if="loading" class="text-xs text-ink-soft" aria-live="polite"
        >互动状态加载中…</span
      >
    </div>

    <p v-if="shareState === 'copied'" role="status" class="text-xs text-pine">
      链接已复制，可以分享给旅伴了。
    </p>
    <p
      v-else-if="shareState === 'shared'"
      role="status"
      class="text-xs text-pine"
    >
      已打开系统分享。
    </p>
    <p
      v-else-if="shareState === 'failed'"
      role="alert"
      class="text-xs text-ember"
    >
      当前浏览器无法直接分享，请手动复制地址栏链接。
    </p>

    <div
      v-if="error"
      role="alert"
      aria-live="assertive"
      class="flex flex-wrap items-center gap-x-3 gap-y-2 rounded-xl border border-ember/30 bg-ember/10 px-3 py-2 text-sm text-ink"
    >
      <span>{{ error }}</span>
      <button
        v-if="sessionExpired"
        type="button"
        data-test="login-entry"
        class="font-medium text-pine underline underline-offset-2"
        @click="goLogin"
      >
        前往登录
      </button>
      <button
        v-else
        type="button"
        data-test="retry-interaction"
        class="font-medium text-pine underline underline-offset-2"
        @click="loadInteraction"
      >
        重试
      </button>
    </div>

    <p
      v-else-if="!auth.isAuthenticated && !restoring"
      class="text-xs leading-relaxed text-ink-soft"
    >
      登录后即可点赞和收藏，把喜欢的手记留进自己的清单。
    </p>
  </section>
</template>

<style scoped>
.action-rail {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.85rem;
  min-width: 0;
}

.action-rail__stack {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.35rem;
  padding: 0.5rem 0.35rem;
  border-radius: 999px;
  background: var(--color-surface);
  border: 1px solid color-mix(in srgb, var(--color-pine) 16%, transparent);
  box-shadow: var(--ui-shadow-sm);
}

.action-rail__btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.15rem;
  width: 3.4rem;
  min-height: 3.4rem;
  padding: 0.45rem 0.25rem;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: var(--color-ink-soft);
  cursor: pointer;
  transition:
    background-color 180ms ease,
    color 180ms ease,
    transform 180ms ease;
}

.action-rail__btn:hover:not(:disabled) {
  background: color-mix(in srgb, var(--color-pine-soft) 55%, transparent);
  color: var(--color-pine-deep);
}

.action-rail__btn:focus-visible {
  outline: 2px solid var(--color-pine);
  outline-offset: 2px;
}

.action-rail__btn:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.action-rail__btn.is-active {
  color: var(--ui-accent);
  background: var(--ui-accent-soft);
}

.action-rail__btn.is-bumped {
  animation: rail-bump 300ms ease-out;
}

.action-rail__count {
  font-size: 0.72rem;
  line-height: 1;
  letter-spacing: 0.01em;
}

.action-rail__label {
  font-size: 0.7rem;
  line-height: 1;
}

.action-rail__note {
  margin: 0;
  max-width: 9rem;
  text-align: center;
  font-size: 0.7rem;
  line-height: 1.4;
  color: var(--color-ink-soft);
}

.action-rail__note.is-error {
  color: var(--color-ember);
}

.action-rail__error {
  max-width: 10rem;
  padding: 0.5rem 0.6rem;
  border-radius: 0.75rem;
  border: 1px solid color-mix(in srgb, var(--color-ember) 35%, transparent);
  background: color-mix(in srgb, var(--color-ember) 10%, transparent);
  font-size: 0.7rem;
  line-height: 1.45;
  color: var(--color-ink);
  text-align: center;
}

.action-rail__error p {
  margin: 0;
}

.action-rail__link {
  margin-top: 0.3rem;
  padding: 0;
  border: 0;
  background: none;
  color: var(--color-pine);
  font-size: 0.7rem;
  text-decoration: underline;
  text-underline-offset: 2px;
  cursor: pointer;
  min-height: 1.75rem;
}

.is-bumped {
  animation: rail-bump 300ms ease-out;
}

@keyframes rail-bump {
  0% {
    transform: scale(1);
  }
  35% {
    transform: scale(1.18) translateY(-2px);
  }
  70% {
    transform: scale(0.96);
  }
  100% {
    transform: scale(1);
  }
}

@media (prefers-reduced-motion: reduce) {
  .action-rail__btn,
  .is-bumped,
  .action-rail__btn.is-bumped {
    animation: none;
    transition: none;
    transform: none;
  }
}
</style>
