<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import UserIdentity from './UserIdentity.vue'
import type { GuideSummary } from '@/types/guide'
import { cardImageSource, cardImageSrcset, CARD_IMAGE_SIZES } from '@/utils/imageVariants'

const props = withDefaults(
  defineProps<{ guide: GuideSummary; animate?: boolean }>(),
  { animate: true }
)
const failed = ref(false)
const loaded = ref(false)
const originalFallback = ref(false)
function imageError(): void {
  loaded.value = false
  if (!originalFallback.value && cardImageSrcset(props.guide.coverImageUrl)) originalFallback.value = true
  else failed.value = true
}
const location = computed(() => ({
  name: 'guide-detail',
  params: { id: props.guide.id }
}))

/** 与 GuideGrid.vue 的比例表保持一致：稳定的伪随机比例，加载更多时不会换比例。 */
const RATIOS = [4 / 5, 1, 3 / 4, 4 / 3]

function ratioFor(id: string): number {
  let hash = 0
  for (let index = 0; index < id.length; index += 1) {
    hash = (hash * 31 + id.charCodeAt(index)) % 100003
  }
  return RATIOS[hash % RATIOS.length]
}

const ratio = computed(() => ratioFor(String(props.guide.id)))
const hasMeta = computed(
  () =>
    props.guide.tags.length > 0 ||
    Boolean(props.guide.travelDays) ||
    props.guide.budgetPerPerson !== null
)

watch(
  () => props.guide.coverImageUrl,
  () => {
    failed.value = false
    loaded.value = false
    originalFallback.value = false
  }
)
</script>

<template>
  <article
    class="guide-note surface-card min-w-0 overflow-hidden"
    :style="{ '--card-ratio': ratio, animation: animate ? undefined : 'none' }"
  >
    <RouterLink
      :to="location"
      data-test="guide-card"
      class="note-photo"
      :aria-label="`阅读：${guide.title}`"
    >
      <img
        v-if="guide.coverImageUrl && !failed"
        :src="originalFallback ? guide.coverImageUrl : cardImageSource(guide.coverImageUrl)"
        :srcset="originalFallback ? undefined : cardImageSrcset(guide.coverImageUrl)"
        :sizes="CARD_IMAGE_SIZES"
        :alt="guide.title"
        :class="{ 'is-loaded': loaded }"
        @load="loaded = true"
        loading="lazy"
        decoding="async"
        @error="imageError"
      />
      <div v-else data-test="card-placeholder" class="note-placeholder">
        <svg viewBox="0 0 160 96" aria-hidden="true">
          <path
            d="M0 96 45 32 74 68 108 12 160 96Z"
            fill="currentColor"
            opacity=".3"
          />
          <path d="m0 96 72-42 88 42Z" fill="currentColor" opacity=".15" />
        </svg>
        <span>翻开这篇旅行手记</span>
      </div>
      <span v-if="guide.destination" class="note-location">{{
        guide.destination.name
      }}</span>
    </RouterLink>

    <div class="note-body">
      <RouterLink :to="location" class="note-title">
        <h2
          class="line-clamp-2 break-words font-serif leading-snug text-pine-deep"
        >
          {{ guide.title }}
        </h2>
      </RouterLink>
      <p v-if="guide.summary" class="note-summary line-clamp-2">
        {{ guide.summary }}
      </p>
      <div v-if="hasMeta" class="note-meta">
        <span v-for="tag in guide.tags.slice(0, 2)" :key="tag.id"
          >#{{ tag.name }}</span
        >
        <span v-if="guide.travelDays">{{ guide.travelDays }}天</span>
        <span v-if="guide.budgetPerPerson !== null"
          >人均 ¥{{ guide.budgetPerPerson }}</span
        >
      </div>

      <footer class="note-foot">
        <div class="note-author">
          <UserIdentity :user="guide.author" size="sm" linked />
        </div>
        <p class="note-stats">
          <span :aria-label="`${guide.likeCount} 人点赞`"
            >♡ {{ guide.likeCount }}</span
          >
          <span class="note-dot" aria-hidden="true">·</span>
          <span :aria-label="`${guide.favoriteCount} 人收藏`"
            >{{ guide.favoriteCount }} 收藏</span
          >
        </p>
      </footer>
    </div>
  </article>
</template>

<style scoped>
.guide-note {
  --card-ratio: 0.8;
  box-shadow: none;
  animation: card-rise var(--motion-normal, 220ms) var(--ease-standard, ease)
    backwards;
  transition:
    border-color 0.15s ease,
    transform 0.18s ease,
    box-shadow 0.18s ease;
}
.guide-note:hover {
  border-color: var(--color-pine);
  transform: translateY(-2px);
  box-shadow: var(--ui-shadow-md);
}
@keyframes card-rise {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}
.note-photo {
  position: relative;
  display: block;
  aspect-ratio: var(--card-ratio);
  overflow: hidden;
  background: var(--color-pine-soft);
}
.note-photo img {
  opacity: 0;
  transition:
    transform var(--motion-normal, 220ms),
    opacity var(--motion-normal, 220ms);
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.note-photo img.is-loaded {
  opacity: 1;
}
.note-photo:hover img {
  transform: scale(1.035);
}
.note-location {
  position: absolute;
  bottom: 10px;
  left: 10px;
  max-width: calc(100% - 20px);
  overflow: hidden;
  padding: 4px 10px;
  border-radius: var(--ui-radius-pill);
  background: var(--color-surface);
  color: var(--color-pine-deep);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.note-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: 0.75rem;
  padding: 1rem;
  color: var(--color-pine);
  font-size: 13px;
}
.note-placeholder svg {
  width: 60%;
  max-width: 140px;
}
.note-body {
  padding: 0.85rem 0.9rem 0.9rem;
}
.note-title {
  display: block;
  min-width: 0;
}
.note-title h2 {
  font-size: 1.0625rem;
  overflow-wrap: anywhere;
  word-break: break-word;
  hyphens: auto;
}
.note-title:hover {
  text-decoration: underline;
  text-underline-offset: 4px;
}
.note-summary {
  margin-top: 0.4rem;
  font-size: 0.8125rem;
  line-height: 1.5;
  color: var(--color-ink-soft);
  overflow-wrap: anywhere;
}
.note-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem 0.6rem;
  margin-top: 0.5rem;
  font-size: 0.75rem;
  color: var(--color-pine);
}
.note-foot {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-width: 0;
  margin-top: 0.6rem;
  padding-top: 0.6rem;
  border-top: 1px solid color-mix(in srgb, var(--color-pine) 12%, transparent);
}
.note-author {
  display: flex;
  flex: 1 1 auto;
  align-items: center;
  min-width: 0;
  overflow: hidden;
}
.note-author :deep(*) {
  min-width: 0;
}
.note-author :deep(a) {
  overflow: hidden;
}
.note-stats {
  display: flex;
  flex: none;
  align-items: center;
  gap: 0.25rem;
  font-size: 0.75rem;
  color: var(--color-ink-soft);
  white-space: nowrap;
}
.note-dot {
  opacity: 0.5;
}
.note-photo:focus-visible,
.note-title:focus-visible {
  outline: 3px solid var(--color-pine);
  outline-offset: -3px;
}
@media (max-width: 479px) {
  .note-body {
    padding: 0.7rem 0.7rem 0.75rem;
  }
  .note-title h2 {
    font-size: 0.975rem;
  }
  .note-summary {
    font-size: 0.78125rem;
    -webkit-line-clamp: 2;
  }
  .note-foot {
    flex-wrap: wrap;
    gap: 0.4rem;
  }
  .note-author {
    flex-basis: 100%;
  }
  .note-stats {
    margin-left: auto;
    font-size: 0.7rem;
  }
}
@media (prefers-reduced-motion: reduce) {
  .note-photo:hover img {
    transform: none;
  }
  .note-photo img {
    transition: none;
  }
  .guide-note {
    animation: none;
    transition: border-color 0.15s ease;
  }
  .guide-note:hover {
    transform: none;
    box-shadow: none;
  }
}
</style>
