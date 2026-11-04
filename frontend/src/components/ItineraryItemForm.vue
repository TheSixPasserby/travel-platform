<script setup lang="ts">
import { reactive,watch } from 'vue'
import DestinationPicker from './DestinationPicker.vue'
import type { ItemInput,ItineraryItem } from '@/types/itinerary'
const props=defineProps<{initial:ItineraryItem|null;busy:boolean}>()
const emit=defineEmits<{save:[value:ItemInput];cancel:[]}>()
const form=reactive<ItemInput>({placeName:'',destinationId:null,addressText:'',startTime:'',endTime:'',notes:''})
watch(()=>props.initial,value=>Object.assign(form,{placeName:value?.placeName??'',destinationId:value?.destination?.id??null,addressText:value?.addressText??'',startTime:value?.startTime??'',endTime:value?.endTime??'',notes:value?.notes??''}),{immediate:true})
function submit():void{
  if(props.busy)return
  emit('save',{placeName:form.placeName.trim(),destinationId:form.destinationId,addressText:form.addressText?.trim()||null,startTime:form.startTime||null,endTime:form.endTime||null,notes:form.notes?.trim()||null})
}
</script>
<template>
  <form data-test="item-form" class="space-y-5" novalidate @submit.prevent="submit">
    <div><label for="item-place" class="field-label">地点名称</label><input id="item-place" v-model="form.placeName" data-test="item-place-name" class="field-input" :disabled="busy" placeholder="例如：溪边步道、老街午餐" /></div>
    <DestinationPicker v-model="form.destinationId" :retained="initial?.destination" :disabled="busy" />
    <div><label for="item-address" class="field-label">地址（可选）</label><input id="item-address" v-model="form.addressText" class="field-input" :disabled="busy" /></div>
    <div class="grid gap-4 sm:grid-cols-2"><div><label for="item-start" class="field-label">开始时间（可选）</label><input id="item-start" v-model="form.startTime" class="field-input" type="time" :disabled="busy" /></div><div><label for="item-end" class="field-label">结束时间（可选）</label><input id="item-end" v-model="form.endTime" class="field-input" type="time" :disabled="busy" /></div></div>
    <p class="text-xs text-ink-soft">时间按当天填写，结束不早于开始；未定时间可以留空。</p>
    <div><label for="item-notes" class="field-label">项目备注（可选）</label><textarea id="item-notes" v-model="form.notes" class="field-input min-h-24" rows="3" :disabled="busy" /></div>
    <div class="flex flex-wrap gap-3"><button type="submit" class="btn-primary" :disabled="busy">{{busy?'正在保存…':'保存项目'}}</button><button type="button" class="btn-ghost" :disabled="busy" @click="emit('cancel')">取消</button></div>
  </form>
</template>
