<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { ApiError } from '@/api/client'
import ImageUpload from '@/components/ImageUpload.vue'
import UserIdentity from '@/components/UserIdentity.vue'
import { useAuthStore } from '@/stores/auth'
import type { CurrentUser, ProfileUpdatePayload } from '@/types/auth'

const auth = useAuthStore()
const router = useRouter()

const loading = ref(true)
const loadError = ref('')
const loadRetryable = ref(false)
const saving = ref(false)
const saveError = ref('')
const saveRetryable = ref(false)
const statusMessage = ref('')
const fieldErrors = ref<Record<string, string>>({})

const form = reactive({
  nickname: '',
  bio: ''
})
const avatarUrl = ref<string | null>(null)
const avatarUploading = ref(false)

const user = computed<CurrentUser | null>(() => auth.user)

function syncFromUser(profile: CurrentUser | null): void {
  form.nickname = profile?.nickname ?? ''
  form.bio = profile?.bio ?? ''
  avatarUrl.value = profile?.avatarUrl ?? null
}

/** 临时故障才值得重试：网络不可达、5xx 或无法归类的异常。 */
function isTransientError(error: unknown): boolean {
  if (!(error instanceof ApiError)) {
    return true
  }
  return error.isNetworkError || error.status === undefined || error.status >= 500
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  loadRetryable.value = false
  statusMessage.value = ''
  try {
    // 路由守卫已确保进入本页时持有会话；这里再向后端取一次最新资料。
    const profile = await auth.loadProfile()
    syncFromUser(profile)
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      await router.push({ name: 'login', query: { redirect: '/me' } })
      return
    }
    loadError.value =
      error instanceof ApiError ? error.message : '资料加载失败，请稍后再试'
    loadRetryable.value = isTransientError(error)
  } finally {
    loading.value = false
  }
}

/** 只提交发生变化（含清空）的字段，未改动字段保持服务端原值。 */
function collectChanges(): ProfileUpdatePayload | null {
  if (!user.value) {
    return null
  }
  const payload: ProfileUpdatePayload = {}

  const nickname = form.nickname.trim()
  if (nickname !== user.value.nickname) {
    if (nickname.length > 0) {
      payload.nickname = nickname
    }
  }

  const bio = form.bio.trim()
  const currentBio = user.value.bio ?? ''
  if (bio !== currentBio) {
    payload.bio = bio.length > 0 ? bio : null
  }

  if (avatarUrl.value !== user.value.avatarUrl) {
    payload.avatarUrl = avatarUrl.value
  }

  return Object.keys(payload).length > 0 ? payload : null
}

async function handleSubmit(): Promise<void> {
  if (saving.value || avatarUploading.value) {
    return
  }
  saveError.value = ''
  saveRetryable.value = false
  statusMessage.value = ''
  fieldErrors.value = {}

  const nickname = form.nickname.trim()
  if (nickname.length === 0) {
    // 昵称是必填项：清空后提交必须明确报错，不能静默忽略或只提示「没有修改」。
    fieldErrors.value = { nickname: '昵称不能为空' }
    return
  }

  const payload = collectChanges()
  if (!payload) {
    statusMessage.value = '没有需要保存的修改'
    return
  }

  saving.value = true
  try {
    const updated = await auth.updateProfile(payload)
    syncFromUser(updated)
    statusMessage.value = '资料已保存'
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      // 401 时全局拦截器已清空 user，若继续留在本页只会看到空白表单：
      // 与加载失败的逻辑保持一致，直接回登录页并记住回跳地址。
      await router.push({ name: 'login', query: { redirect: '/me' } })
      return
    }
    if (error instanceof ApiError) {
      saveError.value = error.message
      saveRetryable.value = isTransientError(error)
      fieldErrors.value = Object.fromEntries(
        error.fieldErrors.map((item) => [item.field, item.message])
      )
    } else {
      saveError.value = '资料保存失败，请稍后再试'
    }
  } finally {
    saving.value = false
  }
}

function expiredUpload(): void {
  void router.push({ name: 'login', query: { redirect: '/me' } })
}

function retrySave(): void {
  void handleSubmit()
}

onMounted(() => {
  if (!auth.token) {
    void router.push({ name: 'login', query: { redirect: '/me' } })
    loading.value = false
    return
  }
  syncFromUser(auth.user)
  void load()
})
</script>

<template>
  <section class="app-container py-10 lg:py-16">
    <div class="mx-auto w-full max-w-3xl">
      <h1 class="font-serif text-3xl text-pine-deep">我的资料</h1>
      <p class="mt-2 text-sm text-ink-soft">这里的名字和简介会显示在你的手记旁边。</p>

      <div v-if="loading" role="status" class="surface-card mt-6 p-6 text-sm text-ink-soft">
        正在加载资料…
      </div>

      <div
        v-else-if="loadError"
        class="surface-card mt-6 p-6"
        role="alert"
        aria-live="assertive"
      >
        <p class="text-sm text-ink">{{ loadError }}</p>
        <button
          v-if="loadRetryable"
          type="button"
          data-test="profile-retry"
          class="btn-ghost mt-3"
          @click="load"
        >
          重新加载
        </button>
      </div>

      <div v-else-if="user" class="surface-card mt-6 p-6 sm:p-8">
        <div class="flex flex-wrap items-center gap-5">
          <UserIdentity :user="{ ...user, avatarUrl }" size="lg" />
        </div>

        <form class="mt-8 space-y-5" novalidate @submit.prevent="handleSubmit">
          <div>
            <label for="username" class="field-label">账号</label>
            <input
              id="username"
              class="field-input"
              type="text"
              :value="user.username"
              readonly
              aria-readonly="true"
              autocomplete="username"
            />
            <p class="mt-1.5 text-xs text-ink-soft/80">账号创建后不可修改。</p>
          </div>

          <div>
            <label for="nickname" class="field-label">昵称</label>
            <input
              id="nickname"
              v-model="form.nickname"
              class="field-input"
              type="text"
              name="nickname"
              autocomplete="nickname"
              :aria-invalid="fieldErrors.nickname ? 'true' : undefined"
              :aria-describedby="fieldErrors.nickname ? 'nickname-error' : undefined"
              :disabled="saving"
            />
            <p
              v-if="fieldErrors.nickname"
              id="nickname-error"
              data-field-error="nickname"
              class="mt-1.5 text-xs text-ember"
            >
              {{ fieldErrors.nickname }}
            </p>
          </div>

          <div>
            <label for="bio" class="field-label">简介</label>
            <textarea
              id="bio"
              v-model="form.bio"
              class="field-input min-h-28 resize-y"
              name="bio"
              rows="4"
              placeholder="用一两句话介绍自己，比如常走哪条路线。"
              :aria-invalid="fieldErrors.bio ? 'true' : undefined"
              :aria-describedby="fieldErrors.bio ? 'bio-error' : undefined"
              :disabled="saving"
            />
            <div class="mt-1.5 flex items-start justify-between gap-3 text-xs text-ink-soft/80">
              <p v-if="fieldErrors.bio" id="bio-error" data-field-error="bio" class="text-ember">
                {{ fieldErrors.bio }}
              </p>
              <span class="ml-auto tabular-nums">{{ [...form.bio].length }} / 300</span>
            </div>
          </div>

          <div v-if="saveError" role="alert" aria-live="assertive">
            <div
              class="flex flex-wrap items-center gap-x-3 gap-y-2 rounded-xl border border-ember/30 bg-ember/10 px-3 py-2 text-sm text-ink"
            >
              <span>{{ saveError }}</span>
              <button
                v-if="saveRetryable"
                type="button"
                class="font-medium text-pine underline underline-offset-2"
                @click="retrySave"
              >
                重试
              </button>
            </div>
          </div>

          <p v-if="statusMessage" role="status" class="text-sm text-[var(--ui-success)]">
            {{ statusMessage }}
          </p>

          <div class="rounded-2xl border border-pine/10 p-4">
            <ImageUpload
              v-model="avatarUrl"
              label="上传头像"
              :show-preview="false"
              :disabled="saving"
              @busy="avatarUploading = $event"
              @session-expired="expiredUpload"
            />
            <p class="mt-2 text-xs text-ink-soft">上传或移除头像后，点击保存修改。</p>
          </div>

          <div class="flex flex-wrap items-center gap-3">
            <button
              type="submit"
              data-test="profile-submit"
              class="btn-primary"
              :disabled="saving || avatarUploading"
            >
              {{ saving ? '正在保存…' : '保存修改' }}
            </button>
            <RouterLink to="/me/guides" class="btn-ghost">我的攻略</RouterLink>
            <RouterLink to="/me/favorites" class="btn-ghost">我的收藏</RouterLink>
            <RouterLink to="/me/itineraries" class="btn-ghost">我的行程</RouterLink>
            <RouterLink to="/" class="btn-ghost">返回首页</RouterLink>
          </div>
        </form>
      </div>
    </div>
  </section>
</template>
