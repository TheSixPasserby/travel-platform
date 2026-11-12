<script setup lang="ts">
import { onBeforeUnmount,reactive,ref,watch } from 'vue'
import { useRoute,useRouter } from 'vue-router'
import * as api from '@/api/itineraries'
import { ApiError } from '@/api/client'
import ItineraryDayCard from '@/components/ItineraryDay.vue'
import ItineraryForm from '@/components/ItineraryForm.vue'
import ItineraryItemForm from '@/components/ItineraryItemForm.vue'
import { itineraryStatusLabels,type DayInput,type ItemInput,type ItineraryDay,type ItineraryDetail,type ItineraryInput,type ItineraryItem } from '@/types/itinerary'
type Editor={kind:'root'}|{kind:'day';id:string|null}|{kind:'item';dayId:string;item:ItineraryItem|null}
type Confirmation={kind:'root'|'day'|'item';id:string;title:string}
const route=useRoute(),router=useRouter()
const itinerary=ref<ItineraryDetail|null>(null),loading=ref(true),loadError=ref(''),writeError=ref(''),notice=ref(''),pending=ref(false)
const editor=ref<Editor|null>(null),confirmation=ref<Confirmation|null>(null)
const dayForm=reactive({travelDate:'',title:'',notes:''})
let version=0,context=0
const message=(e:unknown)=>e instanceof ApiError?e.message:'操作没有完成，请稍后重试'
function expired(redirect:string):void{if(route.name!=='login')void router.replace({name:'login',query:{redirect}})}
async function load():Promise<void>{
  const request=++version,redirect=route.fullPath;loading.value=true;loadError.value=''
  try{const data=await api.fetchItinerary(String(route.params.id??''));if(request===version)itinerary.value=data}
  catch(e){if(request!==version)return;if(e instanceof ApiError&&e.status===401)expired(redirect);else loadError.value=message(e)}
  finally{if(request===version)loading.value=false}
}
async function mutate(action:()=>Promise<unknown>,success:string,leave=false):Promise<void>{
  if(pending.value||!itinerary.value)return
  const request=context,redirect=route.fullPath;pending.value=true;writeError.value='';notice.value=''
  try{
    await action();if(request!==context)return
    editor.value=null;confirmation.value=null;notice.value=success
    if(leave)await router.push({name:'my-itineraries'});else await load()
  }catch(e){if(request!==context)return;if(e instanceof ApiError&&e.status===401)expired(redirect);else writeError.value=message(e)}
  finally{if(request===context)pending.value=false}
}
function editRoot():void{if(!pending.value&&!confirmation.value){editor.value={kind:'root'};writeError.value='';notice.value=''}}
function openDay(day?:ItineraryDay):void{
  if(pending.value||confirmation.value||!itinerary.value)return
  editor.value={kind:'day',id:day?.id??null};confirmation.value=null;writeError.value='';notice.value=''
  Object.assign(dayForm,{travelDate:day?.travelDate??itinerary.value.startDate,title:day?.title??'',notes:day?.notes??''})
}
function openItem(dayId:string,item:ItineraryItem|null=null):void{if(!pending.value&&!confirmation.value){editor.value={kind:'item',dayId,item};writeError.value='';notice.value=''}}
function cancel():void{if(!pending.value){editor.value=null;writeError.value=''}}
function saveRoot(body:ItineraryInput):void{const current=itinerary.value;if(current)void mutate(()=>api.updateItinerary(current.id,body),'行程已保存')}
function saveDay():void{
  const mode=editor.value,current=itinerary.value;if(mode?.kind!=='day'||!current)return
  const body:DayInput={travelDate:dayForm.travelDate,title:dayForm.title.trim()||null,notes:dayForm.notes.trim()||null}
  void mutate(()=>mode.id?api.updateItineraryDay(mode.id,body):api.createItineraryDay(current.id,body),'每日安排已保存')
}
function saveItem(body:ItemInput):void{
  const mode=editor.value;if(mode?.kind!=='item')return
  void mutate(()=>mode.item?api.updateItineraryItem(mode.item.id,body):api.createItineraryItem(mode.dayId,body),'项目已保存')
}
function order(dayId:string,itemIds:string[]):void{void mutate(()=>api.reorderItineraryItems(dayId,itemIds),'当天顺序已更新')}
function confirm(kind:Confirmation['kind'],id:string,title:string):void{if(!pending.value&&!confirmation.value&&editor.value===null){confirmation.value={kind,id,title};writeError.value='';notice.value=''}}
function remove():void{
  const target=confirmation.value;if(!target)return
  const action=target.kind==='root'?()=>api.deleteItinerary(target.id):target.kind==='day'?()=>api.deleteItineraryDay(target.id):()=>api.deleteItineraryItem(target.id)
  void mutate(action,'已删除',target.kind==='root')
}
watch(()=>route.params.id,()=>{context++;version++;itinerary.value=null;pending.value=false;editor.value=null;confirmation.value=null;notice.value='';writeError.value='';void load()},{immediate:true})
onBeforeUnmount(()=>{context++;version++})
</script>
<template>
  <section class="app-container py-8 lg:py-12">
    <RouterLink :to="{name:'my-itineraries'}" class="text-sm text-pine hover:underline">← 我的行程</RouterLink>
    <div v-if="loading&&!itinerary" role="status" class="surface-card mt-6 p-12 text-center text-ink-soft">正在展开这一程…</div>
    <div v-else-if="loadError" role="alert" class="surface-card mt-6 p-8"><h1 class="font-serif text-2xl text-pine-deep">{{loadError}}</h1><button class="btn-ghost mt-5" @click="load">重新加载</button></div>
    <template v-else-if="itinerary">
      <header class="surface-card mt-6 overflow-hidden p-6 sm:p-9 lg:p-11">
        <div class="flex flex-wrap items-center gap-3 text-xs text-pine"><span class="rounded-full bg-pine-soft/60 px-3 py-1">{{itineraryStatusLabels[itinerary.status]}}</span><span>私人行程 · 仅你可见</span><span v-if="itinerary.destination">{{itinerary.destination.name}}{{itinerary.destination.status==='INACTIVE'?'（历史目的地）':''}}</span></div>
        <h1 class="mt-5 break-words font-serif text-3xl leading-tight text-pine-deep sm:text-5xl">{{itinerary.title}}</h1>
        <p class="mt-5 text-sm text-ink-soft">{{itinerary.startDate}} → {{itinerary.endDate}}</p>
        <p v-if="itinerary.notes" class="mt-5 whitespace-pre-wrap break-words text-sm leading-relaxed text-ink-soft [overflow-wrap:anywhere]">{{itinerary.notes}}</p>
        <div class="mt-7 flex flex-wrap items-center gap-3 border-t border-pine/10 pt-5"><span class="mr-auto text-xs text-ink-soft">{{itinerary.dayCount}} 天已安排 · {{itinerary.itemCount}} 个项目</span><button type="button" data-test="edit-itinerary" class="btn-ghost" :disabled="pending||editor!==null||confirmation!==null" @click="editRoot">编辑行程</button><button type="button" data-test="delete-itinerary" class="text-sm text-ember underline underline-offset-4" :disabled="pending||editor!==null||confirmation!==null" @click="confirm('root',itinerary.id,itinerary.title)">删除行程</button></div>
      </header>
      <p v-if="writeError" role="alert" class="mt-6 rounded-2xl border border-ember/20 bg-ember/10 p-4 text-sm text-ember">{{writeError}}</p>
      <p v-if="notice" role="status" class="mt-5 text-sm text-[var(--ui-success)]">{{notice}}</p>
      <div v-if="confirmation" role="alertdialog" aria-labelledby="itinerary-delete-label" class="surface-card mt-6 border-ember/30 p-6"><h2 id="itinerary-delete-label" class="font-serif text-xl text-pine-deep">确认删除{{confirmation.kind==='root'?'这份行程':confirmation.kind==='day'?'这天的安排':'这个项目'}}？</h2><p class="mt-3 break-words text-sm text-ink-soft">{{confirmation.title}}</p><p class="mt-2 text-sm text-ink-soft">{{confirmation.kind==='day'?'当天的全部项目也会删除，其他日期保留。':confirmation.kind==='root'?'删除后无法从页面恢复，请确认这份计划已不再需要。':'删除后当天剩余项目会按原先相对顺序排列。'}}</p><div class="mt-5 flex flex-wrap gap-3"><button type="button" data-test="confirm-itinerary-delete" class="btn-ghost text-ember" :disabled="pending" @click="remove">确认删除</button><button type="button" class="btn-ghost" :disabled="pending" @click="confirmation=null">保留</button></div></div>
      <section v-if="editor" class="surface-card mt-7 p-6 sm:p-8">
        <h2 class="mb-6 font-serif text-2xl text-pine-deep">{{editor.kind==='root'?'调整这一程':editor.kind==='day'?(editor.id?'编辑每日安排':'添加一天的安排'):(editor.item?'编辑项目':'添加当天项目')}}</h2>
        <ItineraryForm v-if="editor.kind==='root'" :initial="itinerary" :busy="pending" @save="saveRoot" @cancel="cancel" />
        <form v-else-if="editor.kind==='day'" data-test="day-form" class="space-y-5" novalidate @submit.prevent="saveDay"><div><label for="day-date" class="field-label">出行日期</label><input id="day-date" v-model="dayForm.travelDate" data-test="day-date" class="field-input" type="date" :min="itinerary.startDate" :max="itinerary.endDate" :disabled="pending" /><p class="mt-2 text-xs text-ink-soft">请选择行程范围内的日期，每天只需创建一份安排。</p></div><div><label for="day-title" class="field-label">这一天的名字（可选）</label><input id="day-title" v-model="dayForm.title" data-test="day-title" class="field-input" :disabled="pending" /></div><div><label for="day-notes" class="field-label">每日备注（可选）</label><textarea id="day-notes" v-model="dayForm.notes" data-test="day-notes" class="field-input min-h-24" :disabled="pending" /></div><div class="flex flex-wrap gap-3"><button type="submit" class="btn-primary" :disabled="pending">{{pending?'正在保存…':'保存每日安排'}}</button><button type="button" class="btn-ghost" :disabled="pending" @click="cancel">取消</button></div></form>
        <ItineraryItemForm v-else :initial="editor.item" :busy="pending" @save="saveItem" @cancel="cancel" />
      </section>
      <div class="mb-6 mt-10 flex flex-wrap items-end justify-between gap-4"><div><p class="text-xs tracking-[0.3em] text-ember">ONE DAY AT A TIME</p><h2 class="mt-2 font-serif text-2xl text-pine-deep">把期待，安放进每一天</h2><p class="mt-2 text-sm text-ink-soft">按日期展开；项目用上移、下移调整，时间可留空。</p></div><button type="button" data-test="add-itinerary-day" class="btn-primary" :disabled="pending||editor!==null||confirmation!==null" @click="openDay()">添加每日安排</button></div>
      <div v-if="!itinerary.days.length" class="surface-card p-10 text-center"><h3 class="font-serif text-2xl text-pine-deep">为第一天留一点期待</h3><p class="mt-3 text-sm text-ink-soft">添加一个出行日，再慢慢安排想去的地点。</p></div>
      <div v-else class="space-y-6"><ItineraryDayCard v-for="day in itinerary.days" :key="day.id" :day="day" :busy="pending||editor!==null||confirmation!==null" @edit-day="openDay" @delete-day="value=>confirm('day',value.id,value.title||value.travelDate)" @add-item="value=>openItem(value)" @edit-item="value=>openItem(value.itineraryDayId,value)" @delete-item="value=>confirm('item',value.id,value.placeName)" @order="order" /></div>
      <p v-if="pending" role="status" class="mt-5 text-sm text-ink-soft">正在保存，请稍候…</p>
    </template>
  </section>
</template>
