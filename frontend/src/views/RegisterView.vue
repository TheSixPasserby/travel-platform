<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { registerAccount } from '@/api/auth'
import { ApiError } from '@/api/client'
import TravelArt from '@/components/TravelArt.vue'

const router = useRouter()

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  nickname: ''
})
const submitting = ref(false)
const errorMessage = ref('')
const retryable = ref(false)
const successMessage = ref('')
const fieldErrors = ref<Record<string, string>>({})

async function handleSubmit(): Promise<void> {
  if (submitting.value) {
    return
  }
  errorMessage.value = ''
  successMessage.value = ''
  retryable.value = false
  fieldErrors.value = {}

  if (!form.username.trim()) {
    fieldErrors.value.username = '请输入账号'
  }
  if (!form.password) {
    fieldErrors.value.password = '请输入密码'
  }
  if (fieldErrors.value.username || fieldErrors.value.password) {
    errorMessage.value = '请先补全账号和密码'
    return
  }

  if (form.password !== form.confirmPassword) {
    fieldErrors.value.confirmPassword = '两次输入的密码不一致'
    errorMessage.value = '两次输入的密码不一致，请重新确认'
    return
  }

  submitting.value = true
  try {
    const nickname = form.nickname.trim()
    await registerAccount({
      username: form.username.trim(),
      password: form.password,
      nickname: nickname.length > 0 ? nickname : undefined
    })
    successMessage.value = '注册成功，正在前往登录…'
    await router.push('/login?registered=1')
  } catch (error) {
    if (error instanceof ApiError) {
      errorMessage.value = error.message
      retryable.value = error.isNetworkError
      fieldErrors.value = Object.fromEntries(
        error.fieldErrors.map((item) => [item.field, item.message])
      )
    } else {
      errorMessage.value = '注册没有完成，请稍后再试'
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
  <section class="app-container grid gap-10 py-10 lg:grid-cols-[minmax(0,1.1fr)_minmax(0,1fr)] lg:py-16">
    <aside class="hidden lg:block">
      <div class="surface-card h-full overflow-hidden p-3">
        <TravelArt variant="trail" class="h-full w-full" />
      </div>
      <p class="mt-4 text-center text-sm text-ink-soft">
        先留下一个名字，再慢慢把故事填满。
      </p>
    </aside>

    <div class="surface-card p-6 sm:p-8">
      <h1 class="font-serif text-3xl text-pine-deep">创建账号</h1>
      <p class="mt-2 text-sm text-ink-soft">注册成功后回到登录页，用新账号进入手记。</p>

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
            placeholder="3–50 位字母、数字或下划线"
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
          <input
            id="password"
            v-model="form.password"
            class="field-input"
            type="password"
            name="password"
            autocomplete="new-password"
            placeholder="至少 8 个字符"
            :aria-invalid="fieldErrors.password ? 'true' : undefined"
            :aria-describedby="fieldErrors.password ? 'password-error' : undefined"
            :disabled="submitting"
          />
          <p
            v-if="fieldErrors.password"
            id="password-error"
            data-field-error="password"
            class="mt-1.5 text-xs text-ember"
          >
            {{ fieldErrors.password }}
          </p>
        </div>

        <div>
          <label for="confirmPassword" class="field-label">确认密码</label>
          <input
            id="confirmPassword"
            v-model="form.confirmPassword"
            class="field-input"
            type="password"
            name="confirmPassword"
            autocomplete="new-password"
            placeholder="再输入一次密码"
            :aria-invalid="fieldErrors.confirmPassword ? 'true' : undefined"
            :aria-describedby="fieldErrors.confirmPassword ? 'confirmPassword-error' : undefined"
            :disabled="submitting"
          />
          <p
            v-if="fieldErrors.confirmPassword"
            id="confirmPassword-error"
            data-field-error="confirmPassword"
            class="mt-1.5 text-xs text-ember"
          >
            {{ fieldErrors.confirmPassword }}
          </p>
        </div>

        <div>
          <label for="nickname" class="field-label">
            昵称 <span class="font-normal text-ink-soft/70">（可选）</span>
          </label>
          <input
            id="nickname"
            v-model="form.nickname"
            class="field-input"
            type="text"
            name="nickname"
            autocomplete="nickname"
            placeholder="不填则默认使用账号"
            :aria-invalid="fieldErrors.nickname ? 'true' : undefined"
            :disabled="submitting"
          />
          <p
            v-if="fieldErrors.nickname"
            data-field-error="nickname"
            class="mt-1.5 text-xs text-ember"
          >
            {{ fieldErrors.nickname }}
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
              data-test="register-retry"
              class="font-medium text-pine underline underline-offset-2"
              @click="retry"
            >
              重试
            </button>
          </div>
        </div>

        <p v-if="successMessage" role="status" class="text-sm text-pine">
          {{ successMessage }}
        </p>

        <button
          type="submit"
          data-test="register-submit"
          class="btn-primary w-full"
          :disabled="submitting"
        >
          {{ submitting ? '正在创建…' : '注册' }}
        </button>
      </form>

      <p class="mt-6 text-sm text-ink-soft">
        已经有账号？
        <RouterLink to="/login" class="font-medium text-pine underline underline-offset-4">
          去登录
        </RouterLink>
      </p>
    </div>
  </section>
</template>
