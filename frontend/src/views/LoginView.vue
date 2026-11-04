<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ApiError } from '@/api/client'
import TravelArt from '@/components/TravelArt.vue'
import { useAuthStore } from '@/stores/auth'
import { resolveRedirectTarget } from '@/utils/redirect'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const form = reactive({ username: '', password: '' })
const submitting = ref(false)
const errorMessage = ref('')
const retryable = ref(false)
const fieldErrors = ref<Record<string, string>>({})
const showPassword = ref(false)

const passwordInvalid = computed(() => Boolean(fieldErrors.value.password))

const notice = computed(() =>
  route.query.registered === '1' ? '注册成功，请用新账号登录。' : ''
)

/** 登录后只接受站内路径，防止被构造成开放重定向。 */
const redirectTarget = computed(() =>
  resolveRedirectTarget(route.query.redirect, '/me')
)

async function handleSubmit(): Promise<void> {
  if (submitting.value) {
    return
  }
  errorMessage.value = ''
  retryable.value = false
  fieldErrors.value = {}

  if (!form.username.trim() || !form.password) {
    if (!form.username.trim()) {
      fieldErrors.value.username = '请输入账号'
    }
    if (!form.password) {
      fieldErrors.value.password = '请输入密码'
    }
    errorMessage.value = '请先补全账号和密码'
    return
  }

  submitting.value = true
  try {
    await auth.login({ username: form.username.trim(), password: form.password })
    await router.push(redirectTarget.value)
  } catch (error) {
    if (error instanceof ApiError) {
      errorMessage.value = error.message
      retryable.value = error.isNetworkError
      fieldErrors.value = Object.fromEntries(
        error.fieldErrors.map((item) => [item.field, item.message])
      )
      if (error.code === 40102) {
        fieldErrors.value.password = fieldErrors.value.password ?? '请检查密码是否正确'
      }
    } else {
      errorMessage.value = '登录没有完成，请稍后再试'
    }
  } finally {
    submitting.value = false
  }
}

function retry(): void {
  void handleSubmit()
}
</script>

<template>
  <section class="app-container grid gap-10 py-10 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.1fr)] lg:py-16">
    <div class="surface-card order-2 p-6 sm:p-8 lg:order-1">
      <h1 class="font-serif text-3xl text-pine-deep">欢迎回来</h1>
      <p class="mt-2 text-sm text-ink-soft">登录后继续写你的旅行手记。</p>

      <p
        v-if="notice"
        role="status"
        class="mt-5 rounded-xl border border-pine/20 bg-pine-soft/50 px-3 py-2 text-sm text-pine-deep"
      >
        {{ notice }}
      </p>

      <form class="mt-6 space-y-5" novalidate @submit.prevent="handleSubmit">
        <div>
          <label for="username" class="field-label">账号</label>
          <input
            id="username"
            v-model="form.username"
            class="field-input"
            type="text"
            name="username"
            autocomplete="username"
            placeholder="请输入账号"
            :aria-invalid="fieldErrors.username ? 'true' : undefined"
            :aria-describedby="fieldErrors.username ? 'username-error' : undefined"
            :disabled="submitting"
          />
          <p
            v-if="fieldErrors.username"
            id="username-error"
            data-field-error="username"
            class="mt-1.5 text-xs text-ember"
          >
            {{ fieldErrors.username }}
          </p>
        </div>

        <div>
          <label for="password" class="field-label">密码</label>
          <div class="relative">
            <input
              id="password"
              v-model="form.password"
              class="field-input pr-16"
              :type="showPassword ? 'text' : 'password'"
              name="password"
              autocomplete="current-password"
              placeholder="请输入密码"
              :aria-invalid="passwordInvalid ? 'true' : undefined"
              :aria-describedby="passwordInvalid ? 'password-error' : undefined"
              :disabled="submitting"
            />
            <button
              type="button"
              class="absolute inset-y-0 right-2 my-auto h-8 rounded-full px-3 text-xs text-ink-soft hover:text-pine"
              :aria-pressed="showPassword"
              @click="showPassword = !showPassword"
            >
              {{ showPassword ? '隐藏' : '显示' }}
            </button>
          </div>
          <p
            v-if="fieldErrors.password"
            id="password-error"
            data-field-error="password"
            class="mt-1.5 text-xs text-ember"
          >
            {{ fieldErrors.password }}
          </p>
        </div>

        <div v-if="errorMessage" role="alert" aria-live="assertive">
          <div
            class="flex flex-wrap items-center gap-x-3 gap-y-2 rounded-xl border border-ember/30 bg-ember/10 px-3 py-2 text-sm text-ink"
          >
            <span>{{ errorMessage }}</span>
            <button
              v-if="retryable"
              type="button"
              data-test="login-retry"
              class="font-medium text-pine underline underline-offset-2"
              @click="retry"
            >
              重试
            </button>
          </div>
        </div>

        <button
          type="submit"
          data-test="login-submit"
          class="btn-primary w-full"
          :disabled="submitting"
        >
          {{ submitting ? '正在登录…' : '登录' }}
        </button>
      </form>

      <p class="mt-6 text-sm text-ink-soft">
        还没有账号？
        <RouterLink to="/register" class="font-medium text-pine underline underline-offset-4">
          去注册
        </RouterLink>
      </p>
    </div>

    <aside class="order-1 hidden lg:order-2 lg:block">
      <div class="surface-card h-full overflow-hidden p-3">
        <TravelArt variant="trail" class="h-full w-full" />
      </div>
      <p class="mt-4 text-center text-sm text-ink-soft">
        每一次出发，都值得被安静地记下来。
      </p>
    </aside>
  </section>
</template>
