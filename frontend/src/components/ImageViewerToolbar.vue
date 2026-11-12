<script setup lang="ts">
/**
 * 图片查看器工具栏：纯展示组件，所有状态与行为由父级灯箱传入/处理。
 * 定位、安全区与层级由父级灯箱负责（本组件不声明 position，尤其不使用 fixed）。
 */
interface Props {
  loaded: boolean
  percentage: string
  zoomInDisabled: boolean
  zoomOutDisabled: boolean
  downloading: boolean
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'rotate-left': []
  'rotate-right': []
  'zoom-out': []
  'zoom-in': []
  fit: []
  actual: []
  download: []
}>()

const rotateLeft = () => emit('rotate-left')
const rotateRight = () => emit('rotate-right')
const zoomOut = () => emit('zoom-out')
const zoomIn = () => emit('zoom-in')
const fit = () => emit('fit')
const actual = () => emit('actual')
const download = () => emit('download')
</script>

<template>
  <div class="image-viewer-toolbar" role="toolbar" aria-label="图片工具栏">
    <button
      type="button"
      class="toolbar-button"
      aria-label="向左旋转90度"
      title="向左旋转90度"
      :disabled="!props.loaded"
      @click="rotateLeft"
    >
      <svg
        class="toolbar-icon"
        viewBox="0 0 24 24"
        aria-hidden="true"
        focusable="false"
        fill="none"
        stroke="currentColor"
        stroke-width="1.75"
        stroke-linecap="round"
        stroke-linejoin="round"
      >
        <path d="M3.5 5.5v5h5" />
        <path d="M4.2 10.4a8 8 0 1 1 1.9 6.4" />
      </svg>
    </button>

    <button
      type="button"
      class="toolbar-button"
      aria-label="向右旋转90度"
      title="向右旋转90度"
      :disabled="!props.loaded"
      @click="rotateRight"
    >
      <svg
        class="toolbar-icon"
        viewBox="0 0 24 24"
        aria-hidden="true"
        focusable="false"
        fill="none"
        stroke="currentColor"
        stroke-width="1.75"
        stroke-linecap="round"
        stroke-linejoin="round"
      >
        <path d="M20.5 5.5v5h-5" />
        <path d="M19.8 10.4a8 8 0 1 0-1.9 6.4" />
      </svg>
    </button>

    <button
      type="button"
      class="toolbar-button"
      aria-label="缩小图片"
      title="缩小图片"
      :disabled="!props.loaded || props.zoomOutDisabled"
      @click="zoomOut"
    >
      <svg
        class="toolbar-icon"
        viewBox="0 0 24 24"
        aria-hidden="true"
        focusable="false"
        fill="none"
        stroke="currentColor"
        stroke-width="1.75"
        stroke-linecap="round"
        stroke-linejoin="round"
      >
        <circle cx="10.5" cy="10.5" r="6.5" />
        <path d="M15.5 15.5 21 21M7.5 10.5h6" />
      </svg>
    </button>

    <output
      class="toolbar-ratio"
      aria-label="当前缩放比例"
      aria-live="polite"
      >{{ props.percentage }}</output
    >

    <button
      type="button"
      class="toolbar-button"
      aria-label="放大图片"
      title="放大图片"
      :disabled="!props.loaded || props.zoomInDisabled"
      @click="zoomIn"
    >
      <svg
        class="toolbar-icon"
        viewBox="0 0 24 24"
        aria-hidden="true"
        focusable="false"
        fill="none"
        stroke="currentColor"
        stroke-width="1.75"
        stroke-linecap="round"
        stroke-linejoin="round"
      >
        <circle cx="10.5" cy="10.5" r="6.5" />
        <path d="M15.5 15.5 21 21M7.5 10.5h6M10.5 7.5v6" />
      </svg>
    </button>

    <button
      type="button"
      class="toolbar-button toolbar-button--text"
      aria-label="适应屏幕"
      title="适应屏幕"
      :disabled="!props.loaded"
      @click="fit"
    >
      <svg
        class="toolbar-icon"
        viewBox="0 0 24 24"
        aria-hidden="true"
        focusable="false"
        fill="none"
        stroke="currentColor"
        stroke-width="1.75"
        stroke-linecap="round"
        stroke-linejoin="round"
      >
        <path d="M9 4H4v5M15 4h5v5M9 20H4v-5M15 20h5v-5" />
      </svg>
      <span class="toolbar-label">适应屏幕</span>
    </button>

    <button
      type="button"
      class="toolbar-button toolbar-button--text"
      aria-label="实际尺寸"
      title="实际尺寸"
      :disabled="!props.loaded"
      @click="actual"
    >
      <span class="toolbar-actual" aria-hidden="true">1:1</span>
    </button>

    <button
      type="button"
      class="toolbar-button toolbar-button--text"
      aria-label="下载原图"
      :title="props.downloading ? '正在下载原图' : '下载原图'"
      :disabled="!props.loaded || props.downloading"
      @click="download"
    >
      <svg
        class="toolbar-icon"
        viewBox="0 0 24 24"
        aria-hidden="true"
        focusable="false"
        fill="none"
        stroke="currentColor"
        stroke-width="1.75"
        stroke-linecap="round"
        stroke-linejoin="round"
      >
        <path d="M12 3.5v11M7.5 10.5 12 15l4.5-4.5" />
        <path d="M4.5 18.5v1a1 1 0 0 0 1 1h13a1 1 0 0 0 1-1v-1" />
      </svg>
      <span class="toolbar-label" aria-live="polite">{{
        props.downloading ? '下载中' : '下载原图'
      }}</span>
    </button>
  </div>
</template>

<style scoped>
.image-viewer-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 0.125rem;
  width: max-content;
  max-width: 100%;
  min-width: 0;
  padding: 0.25rem;
  border: 1px solid
    color-mix(in srgb, var(--color-pine) 20%, transparent);
  border-radius: 999px;
  background-color: color-mix(
    in srgb,
    var(--color-surface) 72%,
    transparent
  );
  -webkit-backdrop-filter: blur(14px) saturate(1.2);
  backdrop-filter: blur(14px) saturate(1.2);
  box-shadow:
    0 1px 0 color-mix(in srgb, var(--color-surface) 60%, transparent) inset,
    0 14px 34px -24px var(--color-pine-deep);
  color: var(--color-ink);
}

.toolbar-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.375rem;
  flex: 0 0 auto;
  min-width: 2.75rem;
  min-height: 2.75rem;
  padding-inline: 0.6875rem;
  border: 1px solid transparent;
  border-radius: 999px;
  background-color: transparent;
  color: var(--color-ink);
  font-family: inherit;
  font-size: 0.8125rem;
  font-weight: 600;
  line-height: 1;
  letter-spacing: 0.01em;
  cursor: pointer;
  transition:
    background-color var(--motion-fast, 160ms) var(--ease-standard, ease),
    border-color var(--motion-fast, 160ms) var(--ease-standard, ease),
    color var(--motion-fast, 160ms) var(--ease-standard, ease),
    box-shadow var(--motion-fast, 160ms) var(--ease-standard, ease);
}

.toolbar-button:hover:not(:disabled) {
  background-color: color-mix(
    in srgb,
    var(--color-pine-soft) 72%,
    transparent
  );
  border-color: color-mix(in srgb, var(--color-pine) 26%, transparent);
  color: var(--color-pine-deep);
}

.toolbar-button:focus-visible {
  outline: 2px solid var(--color-pine);
  outline-offset: 2px;
  background-color: color-mix(
    in srgb,
    var(--color-pine-soft) 60%,
    transparent
  );
}

.toolbar-button:active:not(:disabled) {
  background-color: color-mix(in srgb, var(--color-pine) 16%, transparent);
}

.toolbar-button:disabled {
  opacity: 0.42;
  cursor: not-allowed;
}

.toolbar-button--text {
  padding-inline: 0.75rem;
}

.toolbar-icon {
  width: 1.25rem;
  height: 1.25rem;
  flex: none;
}

.toolbar-label {
  white-space: nowrap;
}

.toolbar-actual {
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.02em;
}

.toolbar-ratio {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  min-width: 3.25rem;
  min-height: 2rem;
  margin-inline: 0.125rem;
  padding-inline: 0.5rem;
  border-radius: 999px;
  background-color: color-mix(
    in srgb,
    var(--color-cream-deep) 82%,
    transparent
  );
  color: var(--color-ink-soft);
  font-size: 0.75rem;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  line-height: 1;
  white-space: nowrap;
}

@media (max-width: 600px) {
  .image-viewer-toolbar {
    display: grid;
    grid-template-columns: repeat(5, minmax(0, 1fr));
    width: 100%;
    max-width: 22rem;
    gap: 4px;
    border-radius: 28px;
  }
  .toolbar-button, .toolbar-ratio { justify-self: center; }
  .toolbar-button:nth-of-type(5) { grid-column: 1 / span 2; }
  .toolbar-button:nth-of-type(6) { grid-column: 3; }
  .toolbar-button:nth-of-type(7) { grid-column: 4 / span 2; }
}

@media (prefers-reduced-motion: reduce) {
  .toolbar-button {
    transition: none;
  }
}
</style>
