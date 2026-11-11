<script setup lang="ts">
import ContentImage from '@/components/ContentImage.vue'
import RelatedGuides from '@/components/RelatedGuides.vue'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { fetchGuide } from '@/api/guides'
import { ApiError } from '@/api/client'
import GuideActions from '@/components/GuideActions.vue'
import GuideComments from '@/components/GuideComments.vue'
import SafeMarkdown from '@/components/SafeMarkdown.vue'
import TravelArt from '@/components/TravelArt.vue'
import UserIdentity from '@/components/UserIdentity.vue'
import { useAuthStore } from '@/stores/auth'
import type { GuideDetail } from '@/types/guide'
import type { GuideInteraction } from '@/types/interaction'
const route=useRoute(),auth=useAuthStore()
const guide=ref<GuideDetail|null>(null),loading=ref(true),error=ref(''),coverFailed=ref(false)
const failedImages=reactive(new Set<string>())
const isAuthor=computed(()=>guide.value?.author.id===auth.user?.id)
let version=0
function updateInteraction(state:GuideInteraction):void{
  if(!guide.value||guide.value.id!==state.guideId)return
  guide.value.likeCount=state.likeCount;guide.value.favoriteCount=state.favoriteCount
}
function updateCommentCount(count:number):void{if(guide.value)guide.value.commentCount=count}
async function load():Promise<void>{
  const request=++version;loading.value=true;error.value='';guide.value=null;coverFailed.value=false;failedImages.clear()
  try{const data=await fetchGuide(String(route.params.id??''));if(request===version)guide.value=data}
  catch(e){if(request===version)error.value=e instanceof ApiError?e.message:'攻略暂时无法加载，请稍后重试'}
  finally{if(request===version)loading.value=false}
}
watch(()=>route.params.id,()=>{void load()},{ immediate:true })
onBeforeUnmount(()=>version++)
</script>
<template>
  <section class="app-container py-8 lg:py-12">
    <RouterLink :to="{ name:'guides' }" class="text-sm text-pine hover:underline">← 浏览旅行攻略</RouterLink>
    <div v-if="loading" role="status" class="surface-card mt-6 p-12 text-center text-ink-soft">正在翻开这篇手记…</div>
    <div v-else-if="error" role="alert" class="surface-card mt-6 p-10 text-center"><h1 class="font-serif text-2xl text-pine-deep">{{ error }}</h1><div class="mt-6 flex flex-wrap justify-center gap-3"><RouterLink :to="{ name:'guides' }" class="btn-primary">返回攻略列表</RouterLink><button class="btn-ghost" @click="load">重新加载</button></div></div>
    <article v-else-if="guide" class="mt-6">
      <div class="surface-card overflow-hidden">
        <div class="relative aspect-[16/7] max-h-[30rem] min-h-48 overflow-hidden bg-pine-soft/40">
          <ContentImage v-if="guide.coverImageUrl&&!coverFailed" :src="guide.coverImageUrl" :alt="guide.title" class="h-full w-full object-cover" @error="coverFailed=true" />
          <TravelArt v-else variant="trail" class="h-full w-full" aria-hidden="true" />
        </div>
        <header class="p-6 sm:p-9 lg:p-12">
          <div class="flex flex-wrap gap-3 text-xs text-pine"><RouterLink v-for="tag in guide.tags" :key="tag.id" :to="{ name:'guides',query:{ tagId:tag.id } }" class="rounded-full bg-pine-soft/50 px-3 py-1">#{{ tag.name }}</RouterLink></div>
          <h1 class="mt-4 break-words font-serif text-3xl leading-tight text-pine-deep sm:text-5xl">{{ guide.title }}</h1>
          <p v-if="guide.summary" class="mt-5 max-w-3xl text-lg leading-relaxed text-ink-soft">{{ guide.summary }}</p>
          <div class="mt-7 flex flex-wrap items-center justify-between gap-4 border-t border-pine/10 pt-5"><UserIdentity :user="guide.author" linked><p class="mt-1 flex flex-wrap gap-x-3 gap-y-1 text-xs text-ink-soft"><span>发布于 {{ guide.publishedAt?.slice(0,10) }}</span></p></UserIdentity><RouterLink v-if="isAuthor" data-test="edit-guide" :to="{ name:'guide-edit',params:{ id:guide.id } }" class="btn-ghost">编辑这篇攻略</RouterLink></div>
          <dl class="mt-6 flex flex-wrap gap-x-10 gap-y-4 text-sm"><div v-if="guide.destination"><dt class="text-xs text-ink-soft">目的地</dt><dd class="mt-1"><RouterLink :to="{ name:'destination-detail',params:{ id:guide.destination.id } }" class="text-pine hover:underline">{{ guide.destination.name }}{{ guide.destination.status==='INACTIVE'?'（历史目的地）':'' }}</RouterLink></dd></div><div v-if="guide.travelDays"><dt class="text-xs text-ink-soft">旅行天数</dt><dd class="mt-1 text-pine-deep">{{ guide.travelDays }} 天</dd></div><div v-if="guide.budgetPerPerson!==null"><dt class="text-xs text-ink-soft">人均预算</dt><dd class="mt-1 text-pine-deep">¥ {{ guide.budgetPerPerson }}</dd></div><div v-if="guide.season"><dt class="text-xs text-ink-soft">旅行季节</dt><dd class="mt-1 text-pine-deep">{{ guide.season }}</dd></div></dl>
        </header>
      </div>
      <section v-if="guide.scenicSpots.length" data-test="guide-scenic-spots" class="surface-card mt-7 p-6 sm:p-8">
        <h2 class="font-serif text-xl text-pine-deep">这篇攻略提到的景点</h2>
        <p class="mt-2 text-xs text-ink-soft">点开可查看景点地址、推荐游玩时间与开放信息。</p>
        <ul class="mt-4 flex flex-wrap gap-3">
          <li v-for="spot in guide.scenicSpots" :key="spot.id">
            <RouterLink :to="{ name:'destination-detail',params:{ id:spot.id } }" class="inline-flex items-center gap-2 rounded-full border border-pine/25 bg-pine-soft/30 px-4 py-2 text-sm text-pine hover:bg-pine-soft/50">{{ spot.name }}<span v-if="spot.status==='INACTIVE'" class="text-xs text-ember">（已停用）</span></RouterLink>
          </li>
        </ul>
      </section>
      <GuideActions :guide-id="guide.id" :guide-author-id="guide.author.id" :like-count="guide.likeCount" :favorite-count="guide.favoriteCount" :comment-count="guide.commentCount" @change="updateInteraction" />
      <div class="mt-7 grid items-start gap-7 lg:grid-cols-[minmax(0,1fr)_17rem]">
        <div class="surface-card min-w-0 p-6 sm:p-9 lg:p-12"><SafeMarkdown :content="guide.content" /><section v-if="guide.images.length" class="mt-10 border-t border-pine/10 pt-6"><h2 class="font-serif text-xl text-pine-deep">沿途影像</h2><div class="mt-4 grid gap-4 sm:grid-cols-2"><figure v-for="(image,index) in guide.images" :key="image.url"><ContentImage :position="index" :images="guide.images.map(i=>({src:i.url,alt:i.altText||'旅行影像'}))" v-if="!failedImages.has(image.url)" :src="image.url" :alt="image.altText||'攻略中的旅行影像'" class="aspect-[4/3] w-full rounded-xl object-cover" loading="lazy" @error="failedImages.add(image.url)" /><div v-else class="flex aspect-[4/3] items-center justify-center rounded-xl bg-pine-soft/30 p-4 text-sm text-ink-soft">图片暂时无法显示</div><figcaption v-if="image.altText" class="mt-2 text-sm text-ink-soft">{{ image.altText }}</figcaption></figure></div></section></div>
        <aside class="surface-card p-6"><h2 class="font-serif text-lg text-pine-deep">这篇手记的作者</h2><UserIdentity class="mt-4" :user="guide.author" linked /><p class="mt-3 whitespace-pre-line text-sm leading-relaxed text-ink-soft">{{ guide.author.bio||'把路上的风景，写成值得回看的手记。' }}</p><p class="mt-6 border-t border-pine/10 pt-4 text-xs text-ink-soft">{{ guide.likeCount }} 赞 · {{ guide.favoriteCount }} 收藏 · {{ guide.commentCount }} 评论</p></aside>
      </div>
      <GuideComments class="mt-7" :guide-id="guide.id" :guide-author-id="guide.author.id" @count-changed="updateCommentCount" />
      <RelatedGuides :guide-id="guide.id" />
    </article>
  </section>
</template>
