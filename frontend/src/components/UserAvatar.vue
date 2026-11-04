<script setup lang="ts">
import { computed, ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    src?: string | null
    name?: string
    size?: 'sm' | 'md' | 'lg'
  }>(),
  { src: null, name: '旅行者', size: 'md' }
)

const failed = ref(false)

watch(
  () => props.src,
  () => {
    failed.value = false
  }
)

const showImage = computed(() => Boolean(props.src) && !failed.value)

/** 昵称为空时退化为第一个可见字符，保证占位图始终有内容。 */
const initial = computed(() => props.name.trim().charAt(0) || '旅')

const sizeClass = computed(() => {
  switch (props.size) {
    case 'sm':
      return 'h-8 w-8 text-sm'
    case 'lg':
      return 'h-20 w-20 text-3xl'
    default:
      return 'h-12 w-12 text-lg'
  }
})
</script>

<template>
  <span
    class="inline-flex shrink-0 items-center justify-center overflow-hidden rounded-full border border-pine/15 bg-pine-soft/70"
    :class="sizeClass"
  >
    <img
      v-if="showImage"
      :src="src ?? ''"
      :alt="`${name}的头像`"
      class="h-full w-full object-cover"
      loading="lazy"
      @error="failed = true"
    />
    <span
      v-else
      data-test="avatar-fallback"
      aria-hidden="true"
      class="font-serif font-semibold text-pine"
    >
      {{ initial }}
    </span>
  </span>
</template>
