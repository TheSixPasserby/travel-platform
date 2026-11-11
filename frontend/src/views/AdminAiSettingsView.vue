<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AdminNavigation from '@/components/AdminNavigation.vue'
import { fetchAiSettings, saveAiSettings } from '@/api/aiSettings'
import { ApiError } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import type { AiSettings } from '@/types/aiSettings'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const settings = ref<AiSettings | null>(null)
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const notice = ref('')
const form = reactive({
  enabled: false,
  provider: 'custom',
  baseUrl: '',
  model: '',
  apiKey: '',
  clearApiKey: false,
  responseFormat: 'auto',
  connectTimeoutSeconds: 5,
  timeoutSeconds: 45,
  maxOutputTokens: 6000
})
let requestVersion = 0
const endpointChanged = computed(() => Boolean(settings.value
  && settings.value.baseUrl.replace(/\/+$/, '') !== form.baseUrl.trim().replace(/\/+$/, '')))

function fill(value: AiSettings) {
  settings.value = value
  Object.assign(form, {
    enabled: value.enabled,
    provider: value.provider,
    baseUrl: value.baseUrl,
    model: value.model,
    apiKey: '',
    clearApiKey: false,
    responseFormat: value.responseFormat,
    connectTimeoutSeconds: value.connectTimeoutSeconds,
    timeoutSeconds: value.timeoutSeconds,
    maxOutputTokens: value.maxOutputTokens
  })
}

function handleError(cause: unknown, fallback: string) {
  error.value = cause instanceof ApiError ? cause.message : fallback
  if (cause instanceof ApiError && cause.status === 401) {
    void router.replace({ name: 'login', query: { redirect: route.fullPath } })
  }
}

async function load() {
  const request = ++requestVersion
  loading.value = true
  error.value = ''
  notice.value = ''
  form.apiKey = ''
  try {
    const value = await fetchAiSettings()
    if (request === requestVersion) fill(value)
  } catch (cause) {
    if (request === requestVersion) handleError(cause, 'AI设置暂时无法加载，请重试')
  } finally {
    if (request === requestVersion) loading.value = false
  }
}

function changeProvider() {
  const preset = settings.value?.presets.find(value => value.id === form.provider)
  if (!preset) return
  form.baseUrl = preset.baseUrl
  form.model = preset.model
  form.responseFormat = preset.responseFormat
  form.apiKey = ''
  form.clearApiKey = false
  notice.value = ''
}

function changeKeyRemoval() {
  form.apiKey = ''
  if (form.clearApiKey) form.enabled = false
}

async function save() {
  if (!settings.value || saving.value || auth.user?.role !== 'ADMIN') return
  const request = requestVersion
  saving.value = true
  error.value = ''
  notice.value = ''
  try {
    const value = await saveAiSettings({
      version: settings.value.version,
      enabled: form.enabled,
      provider: form.provider,
      baseUrl: form.baseUrl.trim(),
      model: form.model.trim(),
      apiKey: form.apiKey.trim() || undefined,
      clearApiKey: form.clearApiKey,
      responseFormat: form.responseFormat,
      connectTimeoutSeconds: form.connectTimeoutSeconds,
      timeoutSeconds: form.timeoutSeconds,
      maxOutputTokens: form.maxOutputTokens
    })
    if (request !== requestVersion) return
    fill(value)
    notice.value = value.enabled
      ? '设置已保存，新的行程生成请求将使用此配置'
      : '设置已保存。当前 AI 已关闭，勾选启用后再次保存即可生成行程。'
  } catch (cause) {
    if (request === requestVersion) handleError(cause, '设置保存未能完成，请检查网络后重试')
  } finally {
    if (request === requestVersion) saving.value = false
  }
}

watch([() => auth.user?.id, () => auth.token], () => {
  requestVersion++
  form.apiKey = ''
  settings.value = null
  saving.value = false
  notice.value = ''
  if (auth.isAuthenticated && auth.user?.role === 'ADMIN') void load()
  else loading.value = false
}, { immediate: true })
onBeforeUnmount(() => {
  requestVersion++
  form.apiKey = ''
})
</script>
<template>
  <section class="app-container py-8 lg:py-12">
    <AdminNavigation />
    <header class="mb-8 flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 class="font-serif text-3xl text-pine-deep">AI 服务设置</h1>
        <p class="mt-3 text-sm leading-7 text-ink-soft">配置行程助手使用的模型服务，保存后立即应用于新的生成请求。</p>
      </div>
      <RouterLink to="/itineraries/ai" class="btn-ghost">打开 AI 行程助手</RouterLink>
    </header>
    <div v-if="loading" role="status" class="surface-card p-10 text-center text-ink-soft">正在读取AI设置…</div>
    <div v-else-if="!settings" class="surface-card p-7">
      <p v-if="error" role="alert" class="text-sm text-ember">{{ error }}</p>
      <p v-else class="text-sm text-ink-soft">请使用管理员账号打开AI设置。</p>
      <button v-if="auth.user?.role==='ADMIN'" type="button" class="btn-ghost mt-4" @click="load">重新加载</button>
    </div>
    <form v-else class="surface-card max-w-4xl space-y-6 p-5 sm:p-8" @submit.prevent="save">
      <p data-test="ai-service-state" class="rounded-xl bg-pine-soft/50 p-4 text-sm text-pine-deep">
        {{ settings.enabled ? '当前生效状态为已启用。' : '当前生效状态为已关闭，勾选启用并保存后可生成行程。' }}
      </p>
      <p v-if="settings.warning" role="status" class="rounded-xl bg-ember/10 p-4 text-sm text-ember">{{ settings.warning }}</p>
      <fieldset :disabled="saving" class="space-y-6">
        <label class="flex min-h-11 items-center gap-3 font-medium text-pine-deep">
          <input v-model="form.enabled" data-test="ai-enabled" type="checkbox" class="h-5 w-5 accent-pine" />
          启用 AI 行程助手
        </label>
        <div class="grid gap-5 sm:grid-cols-2">
          <div>
            <label for="ai-provider" class="field-label">服务供应商</label>
            <select id="ai-provider" v-model="form.provider" data-test="ai-provider" class="field-input" @change="changeProvider">
              <option v-for="preset in settings.presets" :key="preset.id" :value="preset.id">{{ preset.label }}</option>
            </select>
          </div>
          <div>
            <label for="ai-model" class="field-label">模型名称</label>
            <input id="ai-model" v-model="form.model" data-test="ai-model" class="field-input" maxlength="120" placeholder="按供应商控制台中的名称填写" />
            <p class="mt-2 text-xs leading-6 text-ink-soft">填写服务端的完整模型 ID，兼容网关可能要求供应商前缀。</p>
          </div>
        </div>
        <div>
          <label for="ai-base-url" class="field-label">API 地址</label>
          <input id="ai-base-url" v-model="form.baseUrl" data-test="ai-base-url" class="field-input" type="url" maxlength="2000" placeholder="https://api.example.com/v1" />
          <p class="mt-2 text-xs leading-6 text-ink-soft">填写兼容接口的根地址，生成请求会使用 chat/completions 接口。</p>
        </div>
        <div>
          <label for="ai-api-key" class="field-label">API Key</label>
          <input id="ai-api-key" v-model="form.apiKey" data-test="ai-api-key" class="field-input" type="password" autocomplete="new-password" maxlength="8192" :disabled="form.clearApiKey" :placeholder="settings.apiKeyConfigured?'已配置，留空可沿用':'填写供应商提供的API Key'" />
          <p class="mt-2 text-xs leading-6 text-ink-soft">密钥保存在服务器。{{ settings.apiKeyConfigured?'当前已配置密钥。':'当前尚未配置密钥。' }}</p>
          <p v-if="endpointChanged" class="mt-2 text-xs text-ember">API地址已变更，请重新填写对应的API Key。</p>
          <label v-if="settings.apiKeyConfigured" class="mt-2 flex min-h-11 items-center gap-2 text-sm text-ink-soft">
            <input v-model="form.clearApiKey" data-test="ai-clear-key" type="checkbox" class="accent-pine" @change="changeKeyRemoval" />清除已保存的密钥并关闭 AI
          </label>
        </div>
        <details class="rounded-xl border border-pine/15 p-4">
          <summary class="cursor-pointer text-sm font-medium text-pine-deep">连接与输出选项</summary>
          <div class="mt-5 grid gap-5 sm:grid-cols-2">
            <div><label for="ai-format" class="field-label">JSON响应格式</label><select id="ai-format" v-model="form.responseFormat" class="field-input"><option value="auto">供应商默认</option><option value="json_object">JSON Object</option><option value="json_schema">JSON Schema</option></select></div>
            <div><label for="ai-tokens" class="field-label">最大输出 Token 数</label><input id="ai-tokens" v-model.number="form.maxOutputTokens" class="field-input" type="number" min="128" max="12000" step="1" required /></div>
            <div><label for="ai-connect-timeout" class="field-label">连接超时（秒）</label><input id="ai-connect-timeout" v-model.number="form.connectTimeoutSeconds" class="field-input" type="number" min="1" max="30" step="1" required /></div>
            <div><label for="ai-timeout" class="field-label">请求超时（秒）</label><input id="ai-timeout" v-model.number="form.timeoutSeconds" class="field-input" type="number" min="1" max="90" step="1" required /></div>
          </div>
        </details>
      </fieldset>
      <p v-if="error" role="alert" class="rounded-xl bg-ember/10 p-3 text-sm text-ember">{{ error }}</p>
      <p v-if="notice" data-test="ai-save-notice" role="status" class="text-sm text-pine">{{ notice }}</p>
      <div class="flex flex-wrap items-center gap-3 border-t border-pine/15 pt-5">
        <button type="submit" data-test="save-ai-settings" class="btn-primary" :disabled="saving">{{ saving?'正在保存…':'保存 AI 设置' }}</button>
        <button type="button" class="btn-ghost" :disabled="saving" @click="load">重新加载</button>
        <span class="text-xs text-ink-soft">{{ settings.source==='saved'?'正在使用网页保存的配置':'当前使用启动时配置' }}</span>
      </div>
    </form>
  </section>
</template>
