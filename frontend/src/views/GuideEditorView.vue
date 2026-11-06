<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave,onBeforeRouteUpdate,useRoute,useRouter } from 'vue-router'
import { createGuide,deleteGuide,fetchOwnGuide,fetchTags,publishGuide,unpublishGuide,updateGuide } from '@/api/guides'
import { ApiError } from '@/api/client'
import DestinationPicker from '@/components/DestinationPicker.vue'
import ImageUpload from '@/components/ImageUpload.vue'
import ScenicSpotPicker from '@/components/ScenicSpotPicker.vue'
import SafeMarkdown from '@/components/SafeMarkdown.vue'
import type { GuideDetail,GuideInput,Tag } from '@/types/guide'

const route=useRoute(),router=useRouter()
const current=ref<GuideDetail|null>(null),loading=ref(true),loadError=ref(''),error=ref(''),notice=ref(''),saving=ref(false)
const coverBusy=ref(false),bodyBusy=ref(false),confirmDelete=ref(false),tags=ref<Tag[]>([]),tagsError=ref('')
const failedImages=reactive(new Set<string>())
const scenicNotice=ref('')
const form=reactive({ title:'',content:'',summary:'',destinationId:null as string|null,coverImageUrl:null as string|null,
  travelDays:'',budgetPerPerson:'',season:'',tagIds:[] as string[],images:[] as GuideInput['images'],scenicSpotIds:[] as string[] })
const id=computed(()=>typeof route.params.id==='string'?route.params.id:null)
const hidden=computed(()=>current.value?.status==='HIDDEN')
const uploading=computed(()=>coverBusy.value||bodyBusy.value)
const disabled=computed(()=>saving.value||hidden.value)
let version=0,internalNavigation=false

function fill(value:GuideDetail|null):void{
  Object.assign(form,{ title:value?.title??'',content:value?.content??'',summary:value?.summary??'',destinationId:value?.destination?.id??null,
    coverImageUrl:value?.coverImageUrl??null,travelDays:value?.travelDays==null?'':String(value.travelDays),budgetPerPerson:value?.budgetPerPerson??'',season:value?.season??'',
    tagIds:value?.tags.map(t=>t.id)??[],images:value?.images.map(i=>({ url:i.url,altText:i.altText }))??[],
    scenicSpotIds:value?.scenicSpots.map(spot=>spot.id)??[] })
  scenicNotice.value=''
}
/** 更换主城市后旧的景点关系不再成立，先清空并提示用户重新选择。 */
function changeDestination(value:string|null):void{
  const changed=value!==form.destinationId
  form.destinationId=value
  if(!changed)return
  if(form.scenicSpotIds.length){form.scenicSpotIds=[];scenicNotice.value='主城市已变更，已清空原先关联的景点，请按新的城市重新选择。'}
  else scenicNotice.value=''
}
function changeScenicSpots(ids:string[]):void{
  form.scenicSpotIds=[...ids]
  if(ids.length)scenicNotice.value=''
}
function expired():void{void router.replace({ name:'login',query:{ redirect:route.fullPath } })}
async function load():Promise<void>{
  const request=++version;loading.value=true;loadError.value='';error.value='';notice.value='';confirmDelete.value=false
  current.value=null;failedImages.clear()
  try{
    const value=id.value?await fetchOwnGuide(id.value):null
    if(request===version){current.value=value;fill(value);if(route.query.saved==='1')notice.value='草稿已保存'}
  }catch(e){if(e instanceof ApiError&&e.status===401)expired();else if(request===version)loadError.value=e instanceof ApiError?e.message:'攻略编辑数据暂时无法加载'}
  finally{if(request===version)loading.value=false}
}
async function loadTags():Promise<void>{try{tags.value=await fetchTags();tagsError.value=''}catch{tagsError.value='标签暂时无法加载，请重试'}}
function payload():GuideInput|null{
  if(!form.title.trim()){error.value='请先给攻略起一个名字';return null}
  let days:number|null=null
  if(form.travelDays!==''){
    days=Number(form.travelDays)
    if(!/^\d+$/.test(form.travelDays)||!Number.isInteger(days)||days<1||days>365){error.value='旅行天数须为1至365的整数';return null}
  }
  if(form.budgetPerPerson!==''&&!/^(0|[1-9][0-9]{0,7})(\.[0-9]{1,2})?$/.test(form.budgetPerPerson)){
    error.value='预算须为非负金额，最多两位小数';return null
  }
  const optional=(s:string)=>s.trim()||null
  return { title:form.title.trim(),content:form.content,summary:optional(form.summary),destinationId:form.destinationId,coverImageUrl:form.coverImageUrl,
    travelDays:days,budgetPerPerson:form.budgetPerPerson===''?null:form.budgetPerPerson,season:optional(form.season),tagIds:[...form.tagIds],
    images:form.images.map(i=>({ url:i.url,altText:i.altText?.trim()||null })),scenicSpotIds:[...form.scenicSpotIds] }
}
async function navigate(target:Parameters<typeof router.replace>[0]):Promise<void>{
  internalNavigation=true
  try{await router.replace(target)}finally{internalNavigation=false}
}
async function save(publish=false):Promise<void>{
  if(saving.value||uploading.value||hidden.value)return
  error.value='';notice.value='';const data=payload();if(!data)return
  if((publish||current.value?.status==='PUBLISHED')&&!data.content.trim()){error.value='发布的攻略需要填写正文';return}
  saving.value=true
  try{
    if(id.value){
      const updated=await updateGuide(id.value,data);current.value=updated;fill(updated)
      if(publish){notice.value='草稿已保存，正在发布…';const published=await publishGuide(id.value);current.value=published;await navigate({ name:'guide-detail',params:{ id:published.id } })}
      else notice.value='修改已保存'
    }else{
      const created=await createGuide({ ...data,status:publish?'PUBLISHED':'DRAFT' });current.value=created
      await navigate(publish?{ name:'guide-detail',params:{ id:created.id } }:{ name:'guide-edit',params:{ id:created.id },query:{ saved:'1' } })
    }
  }catch(e){
    if(e instanceof ApiError&&e.status===401)expired()
    else{if(notice.value==='草稿已保存，正在发布…')notice.value='草稿已保存，发布尚未完成';error.value=e instanceof ApiError?e.message:'保存未能完成，请稍后重试'}
  }finally{saving.value=false}
}
async function withdraw():Promise<void>{
  if(!id.value||saving.value||uploading.value||hidden.value)return
  saving.value=true;error.value='';notice.value=''
  try{const updated=await unpublishGuide(id.value);current.value=updated;notice.value='已撤回为草稿，可继续编辑并保存'}
  catch(e){if(e instanceof ApiError&&e.status===401)expired();else error.value=e instanceof ApiError?e.message:'撤回未能完成，请重试'}
  finally{saving.value=false}
}
async function remove():Promise<void>{
  if(!id.value||!confirmDelete.value||saving.value||uploading.value)return
  saving.value=true;error.value=''
  try{await deleteGuide(id.value);await navigate({ name:'my-guides' })}
  catch(e){if(e instanceof ApiError&&e.status===401)expired();else error.value=e instanceof ApiError?e.message:'删除未能完成，请重试'}
  finally{saving.value=false}
}
function addBodyImage(url:string|null):void{
  if(!url||form.images.length>=20||form.images.some(i=>i.url===url))return
  form.images.push({ url,altText:null });form.content+='\n\n![旅行影像]('+url+')\n'
}
function moveImage(index:number,delta:number):void{
  const target=index+delta;if(disabled.value||uploading.value||target<0||target>=form.images.length)return
  const image=form.images.splice(index,1)[0];if(image)form.images.splice(target,0,image)
}
function canNavigate(to:{ name?:unknown }):boolean{
  if(to.name==='login'||internalNavigation)return true
  if(saving.value||uploading.value){notice.value='正在保存或上传图片，请稍后切换页面';return false}
  return true
}
onBeforeRouteLeave(canNavigate);onBeforeRouteUpdate(canNavigate)
watch(()=>route.params.id,()=>{void load()},{ immediate:true })
void loadTags()
</script>

<template>
  <section class="app-container py-9 lg:py-12">
    <div class="mb-8 flex flex-wrap items-end justify-between gap-4"><div><p class="text-xs tracking-[0.3em] text-ember">A PAGE OF YOUR JOURNEY</p><h1 class="mt-3 font-serif text-3xl text-pine-deep">{{ id?'整理这篇旅行手记':'写下你走过的路' }}</h1></div><RouterLink :to="{ name:'my-guides' }" class="btn-ghost">我的攻略</RouterLink></div>
    <div v-if="loading" role="status" class="surface-card p-10 text-center text-ink-soft">正在打开手记…</div>
    <div v-else-if="loadError" role="alert" class="surface-card p-8"><p>{{ loadError }}</p><button class="btn-ghost mt-4" @click="load">重新加载</button></div>
    <form v-else class="space-y-6" novalidate @submit.prevent="save(false)">
      <p v-if="hidden" role="status" class="rounded-2xl border border-ember/30 bg-ember/10 p-4 text-sm text-ink">这篇攻略已被隐藏，暂时不能修改或重新发布。你仍可查看内容或删除自己的攻略。</p>
      <div class="surface-card space-y-5 p-5 sm:p-8">
        <div><label for="editor-title" class="field-label">攻略标题</label><input id="editor-title" v-model="form.title" data-test="guide-title" class="field-input text-lg" :disabled="disabled" placeholder="一个值得回看的名字" /><p class="mt-1 text-right text-xs text-ink-soft">{{ [...form.title].length }} / 180</p></div>
        <div><label for="editor-summary" class="field-label">摘要（可选）</label><textarea id="editor-summary" v-model="form.summary" class="field-input" rows="2" :disabled="disabled" placeholder="用几句话介绍这段旅程" /></div>
        <div class="grid gap-5 lg:grid-cols-2"><div class="space-y-3"><DestinationPicker destination-type="CITY" label="主城市（可选）" :model-value="form.destinationId" :retained="current?.destination" :disabled="disabled" @update:model-value="changeDestination" /><p v-if="scenicNotice" role="status" data-test="scenic-notice" class="text-xs text-ember">{{ scenicNotice }}</p></div><div class="space-y-4"><div class="grid gap-4 sm:grid-cols-2"><div><label for="editor-days" class="field-label">旅行天数（可选）</label><input id="editor-days" v-model="form.travelDays" class="field-input" type="number" min="1" max="365" step="1" :disabled="disabled" /></div><div><label for="editor-budget" class="field-label">人均预算（元，可选）</label><input id="editor-budget" v-model="form.budgetPerPerson" data-test="guide-budget" class="field-input" inputmode="decimal" :disabled="disabled" placeholder="例如：1280.00" /></div></div><div><label for="editor-season" class="field-label">旅行季节（可选）</label><input id="editor-season" v-model="form.season" class="field-input" :disabled="disabled" placeholder="例如：春天" /></div></div></div>
        <fieldset><legend class="field-label">旅行主题（最多10项）</legend><div class="flex flex-wrap gap-x-5 gap-y-3"><label v-for="tag in tags" :key="tag.id" class="flex items-center gap-2 text-sm text-ink"><input v-model="form.tagIds" type="checkbox" :value="tag.id" :disabled="disabled" class="accent-pine" />{{ tag.name }}</label></div><p v-if="tagsError" class="mt-2 text-sm text-ember">{{ tagsError }} <button type="button" class="underline" @click="loadTags">重新加载</button></p></fieldset>
        <ScenicSpotPicker :model-value="form.scenicSpotIds" :city-id="form.destinationId" :labels="current?.scenicSpots" :disabled="disabled" @update:model-value="changeScenicSpots" />
        <ImageUpload :key="'cover-'+(id??'new')" v-model="form.coverImageUrl" label="上传攻略封面" :disabled="disabled" @busy="coverBusy=$event" @session-expired="expired" />
      </div>
      <div class="grid items-start gap-6 xl:grid-cols-2">
        <div class="surface-card min-w-0 space-y-4 p-5 sm:p-7"><div><label for="editor-content" class="field-label">攻略正文 · Markdown</label><p class="mb-3 text-xs text-ink-soft">用 # 写标题、- 写清单。上传的正文图片会自动插入，预览在右侧或下方。</p><textarea id="editor-content" v-model="form.content" data-test="guide-content" class="field-input min-h-[28rem] font-mono text-sm leading-relaxed" rows="20" :disabled="disabled" placeholder="从出发那天写起…" /><p class="mt-2 text-right text-xs text-ink-soft">{{ [...form.content].length }} / 100000</p></div><ImageUpload :key="'body-'+(id??'new')" :model-value="null" label="上传正文图片" :show-preview="false" :disabled="disabled||form.images.length>=20" @update:model-value="addBodyImage" @busy="bodyBusy=$event" @session-expired="expired" /><p class="text-xs text-ink-soft">最多20张图片；正文中的图片和文字可继续编辑。</p></div>
        <div class="surface-card min-w-0 p-5 sm:p-7"><h2 class="mb-6 font-serif text-xl text-pine-deep">手记预览</h2><SafeMarkdown v-if="form.content" :content="form.content" /><p v-else class="py-10 text-center text-sm text-ink-soft">写下第一段文字，预览会出现在这里。</p></div>
      </div>
      <section v-if="form.images.length" class="surface-card p-5 sm:p-7"><h2 class="font-serif text-xl text-pine-deep">攻略图库</h2><p class="mt-2 text-xs text-ink-soft">这里的顺序用于图库展示；正文图片在正文中编辑或移除。</p><ol class="mt-5 grid gap-5 sm:grid-cols-2 lg:grid-cols-3"><li v-for="(image,index) in form.images" :key="image.url" class="rounded-2xl border border-pine/15 p-4"><img v-if="!failedImages.has(image.url)" :src="image.url" :alt="image.altText||'旅行影像预览'" class="aspect-[4/3] w-full rounded-xl object-cover" loading="lazy" @error="failedImages.add(image.url)" />
          <div v-else class="flex aspect-[4/3] items-center justify-center rounded-xl bg-pine-soft/30 p-4 text-sm text-ink-soft">图片暂时无法显示</div><label :for="'image-alt-'+index" class="field-label mt-3">图片说明</label><input :id="'image-alt-'+index" v-model="image.altText" class="field-input" :disabled="disabled" /><div class="mt-3 flex flex-wrap gap-3 text-xs text-pine"><button type="button" :disabled="disabled||uploading||index===0" @click="moveImage(index,-1)">前移</button><button type="button" :disabled="disabled||uploading||index===form.images.length-1" @click="moveImage(index,1)">后移</button><button type="button" :disabled="disabled||uploading" @click="form.images.splice(index,1)">移出图库</button></div></li></ol></section>
      <div class="surface-card p-5 sm:p-7"><p v-if="error" role="alert" class="mb-4 rounded-xl bg-ember/10 p-3 text-sm text-ember">{{ error }}</p><p v-if="notice" role="status" class="mb-4 text-sm text-pine">{{ notice }}</p><div class="flex flex-wrap items-center gap-3"><template v-if="!hidden"><button type="button" data-test="save-guide" class="btn-primary" :disabled="saving||uploading" @click="save(false)">{{ saving?'正在保存…':current?.status==='PUBLISHED'?'保存修改':'保存草稿' }}</button><button v-if="current?.status!=='PUBLISHED'" type="button" data-test="publish-guide" class="btn-ghost" :disabled="saving||uploading" @click="save(true)">保存并发布</button><button v-if="current?.status==='PUBLISHED'" type="button" class="btn-ghost" :disabled="saving||uploading" @click="withdraw">撤回为草稿</button></template><RouterLink v-if="current?.status==='PUBLISHED'" :to="{ name:'guide-detail',params:{ id:current.id } }" class="btn-ghost">公开预览</RouterLink><button v-if="id" type="button" data-test="delete-guide" class="ml-auto text-sm text-ember underline underline-offset-4" :disabled="saving||uploading" @click="confirmDelete=!confirmDelete">删除攻略</button></div><div v-if="confirmDelete" class="mt-5 rounded-xl border border-ember/25 p-4"><p class="text-sm text-ink">确认删除这篇攻略？删除后将无法从页面恢复。</p><div class="mt-3 flex gap-3"><button type="button" data-test="confirm-delete-guide" class="btn-ghost text-ember" :disabled="saving||uploading" @click="remove">确认删除</button><button type="button" class="btn-ghost" :disabled="saving" @click="confirmDelete=false">保留攻略</button></div></div></div>
    </form>
  </section>
</template>
