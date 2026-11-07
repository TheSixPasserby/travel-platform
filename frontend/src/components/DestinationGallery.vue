<script setup lang="ts">
import { computed, reactive } from 'vue'
import type { DestinationImage } from '@/types/destination'

const props = defineProps<{
  images: DestinationImage[]
  /** 目的地名称，仅用于无障碍标题，不影响服务端顺序。 */
  name?: string
}>()

/** 服务端已按 sortOrder 排序，这里只做稳定兜底，不重新编造顺序。 */
const ordered = computed(() => [...props.images].sort((left, right) => left.sortOrder - right.sortOrder))
const visible = computed(() => ordered.value.length > 0)
/** 记录加载失败的图片 id，避免同一张图片反复请求。 */
const failed = reactive(new Set<string>())

function photoDate(value: string): string {
  return value.slice(0, 10)
}
</script>

<template>
  <section v-if="visible" data-test="destination-gallery" class="mt-10" :aria-label="`${name || '目的地'}的实景图库`">
    <h2 class="font-serif text-2xl text-pine-deep">实景图库</h2>
    <p class="mt-2 text-sm text-ink-soft">
      以下图片均来自受管图库，逐张标注作者、原始来源与使用许可。
    </p>
    <ul class="mt-5 grid gap-5 sm:grid-cols-2">
      <li
        v-for="image in ordered"
        :key="image.id"
        data-test="gallery-image"
        class="overflow-hidden rounded-2xl border border-pine/10 bg-cream/60"
      >
        <img
          v-if="!failed.has(image.id)"
          :src="image.url"
          :alt="image.altText"
          loading="lazy"
          class="aspect-[4/3] w-full object-cover"
          @error="failed.add(image.id)"
        />
        <span
          v-else
          data-test="gallery-placeholder"
          class="flex aspect-[4/3] w-full items-center justify-center bg-pine-soft/30 px-4 text-center text-sm text-ink-soft"
        >
          图片暂时无法显示
        </span>
        <div class="space-y-2 p-4 text-xs text-ink-soft [overflow-wrap:anywhere]">
          <p class="text-sm text-pine-deep">{{ image.altText }}</p>
          <p>作者：{{ image.author }}</p>
          <p>
            许可：<a :href="image.licenseUrl" target="_blank" rel="noopener noreferrer nofollow" class="text-pine underline">{{ image.license }}</a>
          </p>
          <p>
            来源：<a :href="image.sourceUrl" target="_blank" rel="noopener noreferrer nofollow" class="text-pine underline">原始页面</a>
          </p>
          <p>获取日期：{{ photoDate(image.retrievedAt) }}</p>
        </div>
      </li>
    </ul>
  </section>
</template>
