<script setup lang="ts">
import { reactive,watch } from 'vue'
import DestinationPicker from './DestinationPicker.vue'
import type { ItineraryDetail,ItineraryInput } from '@/types/itinerary'
const props=defineProps<{initial:ItineraryDetail|null;busy:boolean}>()
const emit=defineEmits<{save:[value:ItineraryInput];cancel:[]}>()
const form=reactive<ItineraryInput>({title:'',destinationId:null,startDate:'',endDate:'',notes:'',status:'ACTIVE'})
watch(()=>props.initial,value=>Object.assign(form,{title:value?.title??'',destinationId:value?.destination?.id??null,startDate:value?.startDate??'',endDate:value?.endDate??'',notes:value?.notes??'',status:value?.status??'ACTIVE'}),{immediate:true})
function submit():void{if(!props.busy)emit('save',{...form,title:form.title.trim(),notes:form.notes?.trim()||null})}
</script>
<template>
  <form data-test="itinerary-form" class="space-y-5" novalidate @submit.prevent="submit">
    <div><label for="itinerary-title" class="field-label">行程名称</label><input id="itinerary-title" v-model="form.title" data-test="itinerary-title" class="field-input" :disabled="busy" placeholder="例如：三天两夜，沿溪慢行" /><p class="mt-1 text-xs text-ink-soft">1–160个字符；日期范围最多366天。</p></div>
    <div class="grid gap-4 sm:grid-cols-2"><div><label for="itinerary-start" class="field-label">开始日期</label><input id="itinerary-start" v-model="form.startDate" data-test="itinerary-start" class="field-input" type="date" min="1000-01-01" max="9999-12-31" :disabled="busy" /></div><div><label for="itinerary-end" class="field-label">结束日期</label><input id="itinerary-end" v-model="form.endDate" data-test="itinerary-end" class="field-input" type="date" min="1000-01-01" max="9999-12-31" :disabled="busy" /></div></div>
    <DestinationPicker v-model="form.destinationId" :retained="initial?.destination" :disabled="busy" />
    <div><label for="itinerary-notes" class="field-label">行程备注（可选）</label><textarea id="itinerary-notes" v-model="form.notes" data-test="itinerary-notes" class="field-input min-h-24" rows="3" :disabled="busy" placeholder="记下这一程的期待、预算或提醒。" /></div>
    <div><label for="itinerary-state" class="field-label">计划状态</label><select id="itinerary-state" v-model="form.status" class="field-input" :disabled="busy"><option value="ACTIVE">进行中的计划</option><option value="ARCHIVED">已归档</option></select><p class="mt-1 text-xs text-ink-soft">归档后仍可查看和编辑，仅你自己可见。</p></div>
    <div class="flex flex-wrap gap-3"><button type="submit" data-test="save-itinerary" class="btn-primary" :disabled="busy">{{busy?'正在保存…':initial?'保存行程':'创建行程'}}</button><button type="button" class="btn-ghost" :disabled="busy" @click="emit('cancel')">取消</button></div>
  </form>
</template>
