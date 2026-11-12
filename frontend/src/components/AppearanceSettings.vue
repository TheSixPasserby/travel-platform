<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useThemeStore } from '@/stores/theme'
import type { ThemePreference } from '@/types/theme'
import SiteIcon from './SiteIcon.vue'

const theme = useThemeStore()
const modes: Array<{ value: ThemePreference; label: string; icon: string }> = [
  { value: 'light', label: '浅色', icon: 'sun' },
  { value: 'dark', label: '深色', icon: 'moon' },
  { value: 'system', label: '跟随系统', icon: 'monitor' }
]
const HEX = /^#[0-9a-f]{6}$/i

function currentHex(): string {
  const custom = theme.accent.custom
  if (typeof custom === 'string' && HEX.test(custom)) return custom
  return theme.presets.find((preset) => preset.id === theme.accent.preset)?.hex ?? '#187650'
}

const draft = ref(currentHex())
const invalid = computed(() => !HEX.test(draft.value))
const colorValue = computed(() => (invalid.value ? currentHex() : draft.value))
const selectedPreset = computed(() =>
  theme.accent.custom ? '' : (theme.accent.preset ?? '')
)

watch(
  () => [theme.accent.preset, theme.accent.custom] as const,
  () => {
    // Accent changes include presets, reset and other tabs. Mode changes do not touch this draft.
    const next = currentHex()
    if (next.toUpperCase() !== draft.value.toUpperCase()) draft.value = next
  }
)

function selectPreset(id: string): void {
  theme.setAccent({ preset: id })
  draft.value = currentHex()
}

function resetAccent(): void {
  theme.resetAccent()
  draft.value = currentHex()
}

function onHexInput(event: Event): void {
  const value = (event.target as HTMLInputElement).value.trim()
  draft.value = value
  // 只有合法且完整的 HEX 才立即应用到全站，不依赖 blur。
  if (HEX.test(value)) theme.setAccent({ custom: value.toUpperCase() })
}

function onColorInput(event: Event): void {
  const value = (event.target as HTMLInputElement).value
  if (!HEX.test(value)) return
  draft.value = value.toUpperCase()
  theme.setAccent({ custom: draft.value })
}
</script>
<template>
  <div class="appearance">
    <section class="appearance-group">
      <h3 class="appearance-title">主题模式</h3>
      <div class="mode-options" role="group" aria-label="外观主题">
        <button
          v-for="mode in modes"
          :key="mode.value"
          type="button"
          class="mode-option"
          :class="{ selected: theme.preference === mode.value }"
          :data-theme-option="mode.value"
          :aria-pressed="theme.preference === mode.value"
          @click="theme.setPreference(mode.value)"
        >
          <SiteIcon :name="mode.icon" /><span>{{ mode.label }}</span>
        </button>
      </div>
    </section>

    <section class="appearance-group">
      <h3 class="appearance-title">主题色调</h3>
      <div class="accent-presets" role="group" aria-label="主题色调">
        <button
          v-for="preset in theme.presets"
          :key="preset.id"
          type="button"
          class="accent-option"
          :class="{ selected: selectedPreset === preset.id }"
          :data-accent-option="preset.id"
          :aria-pressed="selectedPreset === preset.id"
          @click="selectPreset(preset.id)"
        >
          <span
            class="accent-dot"
            :style="{ backgroundColor: preset.hex }"
            aria-hidden="true"
          />
          <span class="accent-name">{{ preset.name }}</span>
          <span v-if="selectedPreset === preset.id" class="accent-check" aria-hidden="true">✓</span>
        </button>
      </div>
      <div class="custom-color">
        <label class="field-label" for="appearance-hex">自定义色值</label>
        <div class="hex-row">
          <input
            id="appearance-color"
            class="color-input"
            type="color"
            :value="colorValue"
            aria-label="自定义颜色取色器"
            @input="onColorInput"
          />
          <input
            id="appearance-hex"
            class="field-input hex-input"
            type="text"
            inputmode="text"
            autocomplete="off"
            spellcheck="false"
            placeholder="#RRGGBB"
            :value="draft"
            :aria-invalid="invalid ? 'true' : undefined"
            :aria-describedby="
              invalid ? 'appearance-hex-error' : 'appearance-hex-hint'
            "
            @input="onHexInput"
          />
        </div>
        <p
          v-if="invalid"
          id="appearance-hex-error"
          class="hex-message hex-error"
          role="alert"
        >
          请输入完整的 #RRGGBB 色值，例如 #3366CC。
        </p>
        <p v-else id="appearance-hex-hint" class="hex-message">
          #RRGGBB 格式，输入完整后立即全站生效。
        </p>
      </div>
      <button
        type="button"
        class="reset-button"
        data-test="reset-accent"
        @click="resetAccent"
      >
        恢复山水绿默认配色
      </button>
    </section>

    <section class="appearance-group">
      <h3 class="appearance-title">预览</h3>
      <div class="accent-preview" data-test="accent-preview" aria-hidden="true">
        <p class="preview-body">
          清晨的湖面安静得像一张纸，沿着栈道走进山林，风里都是松针的味道。
        </p>
        <div class="preview-actions">
          <span class="preview-button">开始规划</span>
          <span class="preview-tag">徒步</span>
          <span class="preview-link">浏览攻略</span>
        </div>
      </div>
    </section>
  </div>
</template>
<style scoped>
.appearance {
  display: flex;
  flex-direction: column;
  gap: var(--ui-space-6);
  padding: var(--ui-space-5);
}
.appearance-title {
  margin-bottom: 10px;
  color: var(--ui-text-secondary, var(--color-ink-soft));
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.02em;
}
.mode-options,
.accent-presets {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}
.mode-option,
.accent-option {
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid
    var(--ui-border-strong, var(--color-pine));
  border-radius: var(--ui-radius-md);
  background: var(--ui-bg-surface, var(--color-surface));
  color: var(--ui-text-primary, var(--color-ink));
  cursor: pointer;
  transition:
    border-color var(--motion-fast) var(--ease-standard),
    background-color var(--motion-fast) var(--ease-standard),
    color var(--motion-fast) var(--ease-standard);
}
.mode-option {
  flex-direction: column;
  gap: 8px;
  min-height: 88px;
  font-size: 13px;
}
.mode-option svg {
  width: 24px;
  height: 24px;
}
.mode-option:hover,
.accent-option:hover {
  border-color: var(--ui-accent, var(--color-pine));
  background: var(--ui-accent-soft, var(--color-pine-soft));
}
.mode-option.selected,
.accent-option.selected {
  border-color: var(--ui-accent, var(--color-pine));
  background: var(--ui-accent-soft, var(--color-pine-soft));
  color: var(--ui-accent, var(--color-pine));
  box-shadow: inset 0 0 0 1px var(--ui-accent, var(--color-pine));
}
.accent-option {
  position: relative;
  justify-content: flex-start;
  gap: 8px;
  min-height: 52px;
  padding: 8px 10px;
  font-size: 12px;
}
.accent-dot {
  width: 16px;
  height: 16px;
  flex: none;
  border-radius: 50%;
  border: 1px solid rgb(0 0 0 / 14%);
}
.accent-name {
  flex: 1;
  white-space: nowrap;
  text-align: left;
}
.accent-check {
  position: absolute;
  top: -5px;
  right: -4px;
  display: grid;
  place-items: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--ui-accent);
  color: var(--ui-accent-on);
  font-size: 12px;
  font-weight: 600;
}
.custom-color {
  margin-top: 14px;
}
.hex-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.color-input {
  width: 46px;
  height: 44px;
  flex: none;
  padding: 3px;
  border: 1px solid
    var(--ui-border-strong, color-mix(in srgb, var(--color-pine) 40%, transparent));
  border-radius: var(--ui-radius-md);
  background: var(--ui-bg-surface, var(--color-surface));
  cursor: pointer;
}
.hex-input {
  flex: 1;
  min-width: 0;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  font-variant-numeric: tabular-nums;
}
.hex-message {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--ui-text-secondary, var(--color-ink-soft));
}
.hex-error {
  color: var(--ui-danger, var(--color-ember));
  font-weight: 500;
}
.reset-button {
  width: 100%;
  min-height: 44px;
  margin-top: 14px;
  border: 1px solid
    var(--ui-border-strong, var(--color-pine));
  border-radius: var(--ui-radius-pill);
  background: transparent;
  color: var(--ui-text-primary, var(--color-ink));
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition:
    border-color var(--motion-fast) var(--ease-standard),
    background-color var(--motion-fast) var(--ease-standard),
    color var(--motion-fast) var(--ease-standard);
}
.reset-button:hover {
  border-color: var(--ui-accent, var(--color-pine));
  background: var(--ui-accent-soft, var(--color-pine-soft));
  color: var(--ui-accent, var(--color-pine));
}
.accent-preview {
  padding: 14px;
  border: 1px solid
    var(--ui-border-strong, var(--color-pine));
  border-radius: var(--radius-card);
  background: var(--ui-bg-muted, var(--color-cream));
}
.preview-body {
  margin: 0 0 12px;
  color: var(--ui-text-primary, var(--color-ink));
  font-size: 13px;
  line-height: 1.7;
}
.preview-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.preview-button {
  padding: 6px 14px;
  border-radius: var(--ui-radius-pill);
  background: var(--ui-accent, var(--color-pine));
  color: var(--ui-accent-on, var(--color-on-action));
  font-size: 12px;
  font-weight: 600;
}
.preview-tag {
  padding: 4px 10px;
  border: 1px solid
    var(--ui-border-strong, color-mix(in srgb, var(--color-pine) 40%, transparent));
  border-radius: var(--ui-radius-pill);
  color: var(--ui-text-secondary, var(--color-ink-soft));
  font-size: 12px;
}
.preview-link {
  color: var(--ui-link, var(--color-pine));
  font-size: 12px;
  font-weight: 600;
  text-decoration: underline;
  text-underline-offset: 3px;
}
.appearance :is(button, input):focus-visible {
  outline: 2px solid var(--ui-focus-ring, var(--color-pine));
  outline-offset: 2px;
}
@media (max-width: 359px) {
  .mode-options,
  .accent-presets {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (prefers-reduced-motion: reduce) {
  .mode-option,
  .accent-option,
  .reset-button {
    transition: none;
  }
}
</style>
