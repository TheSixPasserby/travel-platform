<script setup lang="ts">
/**
 * 安全 Markdown 展示组件。
 *
 * 渲染结果全部来自 renderMarkdown 的清理输出，只通过 v-html 注入已清理字符串；
 * 正文图片加载失败时在 DOM 层用 textContent 换成可读占位，不拼接任何 HTML。
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { renderMarkdown } from '@/utils/markdown'

const IMAGE_FALLBACK_TEXT = '图片暂时无法显示'

const props = defineProps<{ content: string }>()

const root = ref<HTMLElement | null>(null)

const html = computed(() => renderMarkdown(props.content))

/** 图片加载失败：用 textContent 写入 alt，绝不用 innerHTML 拼接。 */
function handleImageError(event: Event): void {
  const target = event.target
  if (!(target instanceof HTMLImageElement)) {
    return
  }
  const alt = target.getAttribute('alt')?.trim() ?? ''
  const placeholder = document.createElement('span')
  placeholder.className = 'markdown-image-fallback'
  placeholder.textContent = alt.length > 0 ? alt : IMAGE_FALLBACK_TEXT
  target.replaceWith(placeholder)
}

onMounted(() => {
  root.value?.addEventListener('error', handleImageError, true)
})

onBeforeUnmount(() => {
  root.value?.removeEventListener('error', handleImageError, true)
})
</script>

<template>
  <!-- eslint-disable-next-line vue/no-v-html -- 内容已由 renderMarkdown 双层清理 -->
  <div ref="root" class="safe-markdown" v-html="html"></div>
</template>

<style scoped>
.safe-markdown {
  color: var(--color-ink);
  font-size: 1rem;
  line-height: 1.8;
  overflow-wrap: anywhere;
}

.safe-markdown :deep(> *:first-child) {
  margin-top: 0;
}

.safe-markdown :deep(> *:last-child) {
  margin-bottom: 0;
}

.safe-markdown :deep(p) {
  margin-block: 0.9em;
}

.safe-markdown :deep(h1),
.safe-markdown :deep(h2),
.safe-markdown :deep(h3),
.safe-markdown :deep(h4),
.safe-markdown :deep(h5),
.safe-markdown :deep(h6) {
  margin-block: 1.5em 0.65em;
  color: var(--color-pine-deep);
  font-family: var(--font-serif);
  font-weight: 600;
  line-height: 1.35;
}

.safe-markdown :deep(h1) {
  font-size: 1.65rem;
}

.safe-markdown :deep(h2) {
  font-size: 1.4rem;
}

.safe-markdown :deep(h3) {
  font-size: 1.2rem;
}

.safe-markdown :deep(h4),
.safe-markdown :deep(h5),
.safe-markdown :deep(h6) {
  font-size: 1.05rem;
}

.safe-markdown :deep(a) {
  color: var(--color-pine);
  text-decoration: underline;
  text-decoration-thickness: 1px;
  text-underline-offset: 0.2em;
}

.safe-markdown :deep(a:hover) {
  color: var(--color-pine-deep);
}

.safe-markdown :deep(ul),
.safe-markdown :deep(ol) {
  margin-block: 0.9em;
  padding-inline-start: 1.5em;
}

.safe-markdown :deep(li) {
  margin-block: 0.3em;
}

.safe-markdown :deep(ul) {
  list-style-type: disc;
}

.safe-markdown :deep(ol) {
  list-style-type: decimal;
}

.safe-markdown :deep(li > ul),
.safe-markdown :deep(li > ol) {
  margin-block: 0.2em;
}

.safe-markdown :deep(blockquote) {
  margin-block: 1.1em;
  border-inline-start: 3px solid color-mix(in srgb, var(--color-pine) 45%, white);
  background-color: color-mix(in srgb, var(--color-pine-soft) 32%, white);
  border-radius: 0 0.75rem 0.75rem 0;
  padding: 0.75rem 1rem;
  color: var(--color-ink-soft);
}

.safe-markdown :deep(blockquote > *:first-child) {
  margin-top: 0;
}

.safe-markdown :deep(blockquote > *:last-child) {
  margin-bottom: 0;
}

.safe-markdown :deep(hr) {
  margin-block: 1.75em;
  border: 0;
  border-top: 1px solid color-mix(in srgb, var(--color-pine) 18%, white);
}

.safe-markdown :deep(code) {
  border-radius: 0.4rem;
  background-color: var(--color-cream-deep);
  padding: 0.15em 0.4em;
  font-family: 'JetBrains Mono', 'SFMono-Regular', Consolas, monospace;
  font-size: 0.875em;
  color: var(--color-pine-deep);
}

.safe-markdown :deep(pre) {
  margin-block: 1.1em;
  max-width: 100%;
  overflow-x: auto;
  border-radius: 0.9rem;
  background-color: var(--color-pine-deep);
  padding: 0.95rem 1.1rem;
  -webkit-overflow-scrolling: touch;
}

.safe-markdown :deep(pre code) {
  display: block;
  background-color: transparent;
  padding: 0;
  color: #f2f6ef;
  font-size: 0.85rem;
  line-height: 1.7;
  white-space: pre;
}

.safe-markdown :deep(table) {
  display: block;
  width: 100%;
  max-width: 100%;
  margin-block: 1.1em;
  overflow-x: auto;
  border-collapse: collapse;
  font-size: 0.9375rem;
  -webkit-overflow-scrolling: touch;
}

.safe-markdown :deep(thead) {
  background-color: var(--color-cream-deep);
}

.safe-markdown :deep(th),
.safe-markdown :deep(td) {
  border: 1px solid color-mix(in srgb, var(--color-pine) 16%, white);
  padding: 0.5rem 0.75rem;
  text-align: start;
  white-space: nowrap;
}

.safe-markdown :deep(th) {
  color: var(--color-pine-deep);
  font-weight: 600;
}

.safe-markdown :deep(strong) {
  color: var(--color-pine-deep);
  font-weight: 600;
}

.safe-markdown :deep(em) {
  color: var(--color-ink);
}

.safe-markdown :deep(s) {
  color: var(--color-ink-soft);
}

.safe-markdown :deep(img) {
  display: block;
  max-width: 100%;
  height: auto;
  margin-block: 1.1em;
  border-radius: var(--radius-card);
  background-color: var(--color-cream-deep);
}

.safe-markdown :deep(.markdown-image-fallback) {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  margin-block: 0.5em;
  border: 1px dashed color-mix(in srgb, var(--color-pine) 30%, white);
  border-radius: 999px;
  background-color: color-mix(in srgb, var(--color-cream-deep) 70%, white);
  padding: 0.3rem 0.85rem;
  color: var(--color-ink-soft);
  font-size: 0.875rem;
}
</style>
