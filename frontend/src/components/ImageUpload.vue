<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'

import { ApiError } from '@/api/client'
import { uploadImage } from '@/api/files'

const props = withDefaults(
  defineProps<{
    modelValue?: string | null
    disabled?: boolean
    label?: string
    showPreview?: boolean
  }>(),
  { modelValue: null, disabled: false, label: '上传图片', showPreview: true }
)

const emit = defineEmits<{
  'update:modelValue': [value: string | null]
  busy: [value: boolean]
  'session-expired': []
}>()

const MAX_SIZE = 5 * 1024 * 1024
const ACCEPT = 'image/jpeg,image/png,image/webp'
const ALLOWED_TYPES = new Set(ACCEPT.split(','))

const inputId = useId()
const inputRef = ref<HTMLInputElement | null>(null)
const busy = ref(false)
const message = ref('')
const messageKind = ref<'info' | 'success' | 'error'>('info')
const previewFailed = ref(false)

const isDisabled = computed(() => props.disabled || busy.value)
const hasImage = computed(() => Boolean(props.modelValue))
const showImage = computed(() => props.showPreview && hasImage.value && !previewFailed.value)

watch(
  () => props.modelValue,
  () => {
    previewFailed.value = false
  }
)

/** 客户端只做前置拦截，服务端仍会重新校验类型与大小。 */
function rejection(file: File): string | null {
  if (!ALLOWED_TYPES.has(file.type)) {
    return '仅支持 JPG/PNG/WebP 格式的图片'
  }
  if (file.size > MAX_SIZE) {
    return '图片不能超过 5MB，请压缩后重试'
  }
  return null
}

function isUnauthorized(error: unknown): boolean {
  if (error instanceof ApiError) {
    return error.status === 401
  }
  if (typeof error === 'object' && error !== null && 'status' in error) {
    return (error as { status?: unknown }).status === 401
  }
  return false
}

async function handleFile(file: File): Promise<void> {
  if (busy.value) {
    return
  }

  const problem = rejection(file)
  if (problem) {
    messageKind.value = 'error'
    message.value = problem
    return
  }

  busy.value = true
  messageKind.value = 'info'
  message.value = '正在上传图片…'
  emit('busy', true)
  try {
    const url = await uploadImage(file)
    emit('update:modelValue', url)
    messageKind.value = 'success'
    message.value = '上传成功'
  } catch (error) {
    const reason =
      error instanceof Error && error.message.trim().length > 0 ? error.message : '上传未能完成'
    if (isUnauthorized(error)) {
      messageKind.value = 'error'
      message.value = `${reason}，请重新登录后再上传`
      emit('session-expired')
    } else {
      messageKind.value = 'error'
      message.value = `${reason}，请重试`
    }
  } finally {
    busy.value = false
    emit('busy', false)
    if (inputRef.value) {
      inputRef.value.value = ''
    }
  }
}

async function onChange(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) {
    return
  }
  await handleFile(file)
}

function openPicker(): void {
  if (isDisabled.value) {
    return
  }
  inputRef.value?.click()
}

function removeImage(): void {
  if (props.disabled || busy.value) {
    return
  }
  previewFailed.value = false
  emit('update:modelValue', null)
  messageKind.value = 'info'
  message.value = '已移除当前图片'
}
</script>

<template>
  <div class="flex flex-col gap-2">
    <span class="flex flex-wrap items-center gap-3">
      <span class="min-w-0 flex-1">
        <label class="field-label !mb-0" :for="inputId">{{ label }}</label>
        <span class="text-xs text-ink-soft">
          JPG/PNG/WebP，单张最大 5MB
        </span>
      </span>
      <button
        type="button"
        data-test="image-upload-trigger"
        class="btn-ghost"
        :disabled="isDisabled"
        :aria-busy="busy ? 'true' : 'false'"
        @click="openPicker"
      >
        {{ busy ? '上传中…' : label }}
      </button>
    </span>

    <input
      :id="inputId"
      ref="inputRef"
      data-test="image-upload-input"
      class="sr-only"
      type="file"
      :accept="ACCEPT"
      :disabled="isDisabled"
      @change="onChange"
    />

    <p data-test="image-upload-hint" class="sr-only">
      请选择 JPG/PNG/WebP 格式图片，单张最大 5MB。
    </p>

    <div v-if="hasImage" class="flex items-center gap-3">
      <img
        v-if="showImage"
        data-test="image-upload-preview"
        :src="modelValue ?? ''"
        :alt="`${label}预览`"
        class="h-20 w-20 rounded-xl border border-pine/15 object-cover"
        loading="lazy"
        @error="previewFailed = true"
      />
      <span
        v-else-if="props.showPreview"
        data-test="image-upload-preview-fallback"
        class="flex h-20 w-20 items-center justify-center rounded-xl border border-pine/15 bg-cream-deep text-center text-xs text-ink-soft"
      >
        预览加载失败
      </span>
      <span v-if="props.showPreview" class="min-w-0 text-xs text-ink-soft">
        {{ showImage ? '当前图片' : '可重新上传，或移除当前图片' }}
      </span>
      <button
        v-if="!disabled"
        type="button"
        data-test="image-upload-remove"
        class="btn-ghost"
        :disabled="isDisabled"
        @click="removeImage"
      >
        移除图片
      </button>
    </div>

    <div
      data-test="image-upload-status"
      role="status"
      aria-live="polite"
      class="min-h-[1.25rem] text-xs"
      :class="{
        'text-ink-soft': messageKind === 'info',
        'text-pine': messageKind === 'success',
        'text-ember': messageKind === 'error'
      }"
    >
      {{ message }}
    </div>
  </div>
</template>
