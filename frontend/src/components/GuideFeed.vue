<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { fetchTags } from '@/api/guides'
import { fetchDestinations } from '@/api/destinations'
import { useGuideFeed, type FeedOrder } from '@/composables/useGuideFeed'
import DestinationPicker from './DestinationPicker.vue'
import GuideGrid from './GuideGrid.vue'
import type { Tag } from '@/types/guide'
import type { Destination } from '@/types/destination'
const props=withDefaults(defineProps<{discovery?:boolean}>(),{discovery:false})
const {keyword,destinationId,tagId,order,page,result,loading,error,load,search,turn,selectOrder,selectTag,selectCity}=useGuideFeed(props.discovery)
const tags=ref<Tag[]>([]),cities=ref<Destination[]>([]),topicsError=ref('')
let disposed=false,topicsVersion=0
const orders:Array<{value:FeedOrder;label:string}>=[{value:'recommended',label:'推荐'},{value:'latest',label:'最新'},{value:'popular',label:'热门'}]
async function loadTopics(){
 const request=++topicsVersion;topicsError.value=''
 const results=await Promise.allSettled([fetchTags(),...(props.discovery?[fetchDestinations({page:1,size:8,destinationType:'CITY'})]:[])])
 if(disposed||request!==topicsVersion)return
 const labels=results[0];if(labels?.status==='fulfilled')tags.value=labels.value as Tag[];else topicsError.value='主题暂时无法加载'
 const places=results[1];if(places?.status==='fulfilled')cities.value=(places.value as {records:Destination[]}).records;else if(props.discovery)topicsError.value='目的地或主题暂时无法加载'
}
onMounted(()=>void loadTopics());onBeforeUnmount(()=>{disposed=true;topicsVersion++})
</script>
<template>
 <div>
  <form data-test="guide-search" class="feed-search" @submit.prevent="search()">
   <div :class="discovery?'min-w-0 flex-1':'min-w-0'"><label :for="discovery?'discover-search':'guide-search-keyword'" class="field-label">搜索标题或摘要</label><input :id="discovery?'discover-search':'guide-search-keyword'" v-model="keyword" data-test="guide-keyword" type="search" class="field-input" placeholder="想去哪里，想看什么" /></div>
   <template v-if="!discovery"><DestinationPicker v-model="destinationId" label="目的地筛选" :allow-inactive-selection="true" /><div><label for="guide-search-tag" class="field-label">旅行主题</label><select id="guide-search-tag" v-model="tagId" class="field-input" @change="search()"><option value="">全部主题</option><option v-for="tag in tags" :key="tag.id" :value="tag.id">{{ tag.name }}</option></select></div><div><label for="guide-search-sort" class="field-label">排序</label><select id="guide-search-sort" v-model="order" class="field-input" @change="search()"><option value="latest">最新发布</option><option value="popular">最多点赞</option></select></div></template>
   <button type="submit" class="btn-primary shrink-0" :disabled="loading">{{ loading?'寻找中…':'寻找攻略' }}</button>
  </form>
  <template v-if="discovery">
   <nav aria-label="旅行内容排序" class="mt-7 flex flex-wrap items-center gap-x-6 gap-y-2 border-b border-pine/15 pb-3"><button v-for="option in orders" :key="option.value" type="button" :data-test="`order-${option.value}`" class="feed-order" :class="{selected:order===option.value}" :aria-pressed="order===option.value" @click="selectOrder(option.value)">{{ option.label }}</button><span class="ml-auto text-xs text-ink-soft">{{ order==='latest'?'按发布时间排列':'按点赞与发布时间排列' }}</span></nav>
   <div v-if="cities.length||tags.length" class="my-5 flex flex-wrap gap-2" aria-label="目的地与旅行主题"><button v-for="city in cities" :key="'city-'+city.id" type="button" class="topic-pill" :aria-pressed="destinationId===city.id" @click="selectCity(city.id)">{{ city.name }}</button><button v-for="tag in tags.slice(0,6)" :key="'tag-'+tag.id" type="button" :data-test="`topic-${tag.id}`" class="topic-pill" :aria-pressed="tagId===tag.id" @click="selectTag(tag.id)">#{{ tag.name }}</button></div>
  </template>
  <p v-if="topicsError" class="my-3 text-xs text-ink-soft">{{ topicsError }} <button type="button" class="min-h-11 text-pine underline" @click="loadTopics">重试</button></p>
  <div v-if="loading" role="status" class="surface-card my-6 p-12 text-center text-ink-soft">正在翻开路上的故事…</div>
  <div v-else-if="error" role="alert" class="surface-card my-6 p-8"><p>{{ error }}</p><button type="button" data-test="retry-guides" class="btn-ghost mt-4" @click="load">重新加载</button></div>
  <div v-else-if="!result.records.length" class="surface-card my-6 p-10 text-center"><h2 class="font-serif text-2xl text-pine-deep">{{ result.total?'这一页暂时没有攻略':'还没有找到攻略' }}</h2><p class="mt-3 text-sm text-ink-soft">{{ result.total?'回到第一页，继续浏览路上的故事。':'试试其他地点或主题，也欢迎分享你的第一篇手记。' }}</p><button v-if="result.total" type="button" class="btn-ghost mt-5" @click="turn(1)">回到第一页</button><RouterLink v-else :to="{name:'guide-new'}" class="btn-ghost mt-5">开始记录</RouterLink></div>
  <template v-else><p class="my-5 text-xs text-ink-soft">{{ result.total }} 篇旅行手记</p><GuideGrid :guides="result.records" /></template>
  <nav v-if="result.pages>1" aria-label="攻略分页" class="mt-8 flex items-center justify-center gap-3"><button type="button" class="btn-ghost" :disabled="loading||page<=1" @click="turn(page-1)">上一页</button><span class="text-sm text-ink-soft">{{ result.current }} / {{ result.pages }}</span><button type="button" class="btn-ghost" :disabled="loading||page>=result.pages" @click="turn(page+1)">下一页</button></nav>
 </div>
</template>
<style scoped>
.feed-search{display:flex;flex-wrap:wrap;align-items:end;gap:1rem;margin-block:1.5rem}.feed-search>*{min-width:0}.feed-order{min-height:44px;padding:.4rem 0;font-size:1rem;color:var(--color-ink-soft);cursor:pointer}.feed-order.selected{font-weight:600;color:var(--color-pine-deep);box-shadow:0 3px 0 var(--color-pine)}.topic-pill{min-height:44px;border:1px solid transparent;border-radius:99px;padding:8px 14px;font-size:13px;color:var(--color-ink-soft);cursor:pointer;background:var(--color-cream-deep)}.topic-pill[aria-pressed=true]{background:var(--color-pine-soft);color:var(--color-pine-deep);border-color:var(--color-pine)}
@media(min-width:1024px){.feed-search:has(#guide-search-keyword){display:grid;grid-template-columns:1.2fr 1fr 9rem 8rem auto}}
@media(max-width:599px){.feed-search{display:grid;grid-template-columns:1fr}.feed-search:has(#discover-search){grid-template-columns:minmax(0,1fr) auto}.feed-order{font-size:1rem}}
</style>
