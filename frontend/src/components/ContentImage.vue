<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import ImageLightbox from './ImageLightbox.vue'
import { useImageLightbox, type PreviewImage } from '@/composables/useImageLightbox'
import { isManagedImageSource } from '@/utils/markdown'
defineOptions({ inheritAttrs: false })
const props=defineProps<{ src: string; alt: string; images?: PreviewImage[]; position?: number; loading?: 'lazy'|'eager' }>()
const emit=defineEmits<{ error: [] }>()
const failed=ref(false),allowed=computed(()=>isManagedImageSource(props.src))
const {preview,open,close}=useImageLightbox()
watch(()=>props.src,()=>{failed.value=false;close()})
</script>
<template>
  <button v-if="allowed&&!failed" type="button" v-bind="$attrs" class="content-image" :aria-label="`预览图片：${alt}`" @click="open(images??[{src,alt}],src,$event.currentTarget,position)"><img :src="src" :alt="alt" :loading="loading??'lazy'" @error="failed=true;emit('error')" /></button>
  <div v-else v-bind="$attrs" class="content-image-fallback" role="img" :aria-label="alt">图片暂时无法显示</div>
  <ImageLightbox v-if="preview" :preview="preview" @close="close" />
</template>
<style scoped>
.content-image{display:block;overflow:hidden;cursor:zoom-in;padding:0;border:0;background:var(--color-cream-deep)}
.content-image img{transition:transform var(--motion-normal) var(--ease-standard);width:100%;height:100%;object-fit:cover;display:block}
.content-image:focus-visible{outline:3px solid var(--color-pine);outline-offset:3px}
.content-image-fallback{display:flex;align-items:center;justify-content:center;background:var(--color-cream-deep);color:var(--color-ink-soft);padding:1rem}
.content-image:hover img{transform:scale(1.025)}
@media(prefers-reduced-motion:reduce){.content-image img{transition:none}.content-image:hover img{transform:none}}
</style>
