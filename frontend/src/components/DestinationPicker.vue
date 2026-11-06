<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'
import { fetchDestination, fetchDestinations } from '@/api/destinations'
import type { DestinationStatus,DestinationType } from '@/types/destination'

type Option = { id:string; name:string; status?:DestinationStatus }
const props=withDefaults(defineProps<{ modelValue:string|null; retained?:Option|null; allowInactiveSelection?:boolean; disabled?:boolean; label?:string; destinationType?:DestinationType }>(),
  { retained:null,allowInactiveSelection:false,disabled:false,label:'目的地（可选）' })
const emit=defineEmits<{ 'update:modelValue':[value:string|null] }>()
const inputId=useId(),keyword=ref(''),loading=ref(false),error=ref('')
const choices=ref<Option[]>([]),selected=ref<Option|null>(null)
let version=0
const options=computed(()=>{
  const all=[...choices.value]
  const current=props.retained?.id===props.modelValue?props.retained:selected.value
  if(current&&!all.some(item=>item.id===current.id))all.push(current)
  return all
})
async function load():Promise<void>{
  const request=++version;loading.value=true;error.value=''
  const id=props.modelValue
  try{
    const page=await fetchDestinations({ page:1,size:50,keyword:keyword.value.trim()||undefined,...(props.destinationType?{destinationType:props.destinationType}:{}) })
    let current:Option|null=props.retained?.id===id?props.retained:null
    if(id&&!page.records.some(item=>item.id===id)&&!current){
      try{
        const detail=await fetchDestination(id)
        if(detail.status==='ACTIVE'||props.allowInactiveSelection)current=detail
        else current={ id,name:'当前选择已停用',status:'INACTIVE' }
      }catch{current={ id,name:'当前选择（信息暂不可用）' }}
    }
    if(request===version){choices.value=page.records;selected.value=current}
  }catch{
    if(request===version)error.value='目的地暂时无法加载，请重试'
  }finally{if(request===version)loading.value=false}
}
watch([()=>props.modelValue,()=>props.destinationType],()=>{void load()},{ immediate:true })
</script>

<template>
  <div class="space-y-2">
    <label :for="inputId" class="field-label">{{ label }}</label>
    <div class="flex gap-2">
      <input v-model="keyword" class="field-input min-w-0" type="search" aria-label="查找目的地" placeholder="输入名称查找目的地" :disabled="disabled" @keydown.enter.prevent="load" />
      <button type="button" class="btn-ghost shrink-0" :disabled="disabled||loading" @click="load">查找</button>
    </div>
    <select :id="inputId" :value="modelValue??''" class="field-input" :disabled="disabled||loading" @change="emit('update:modelValue',($event.target as HTMLSelectElement).value||null)">
      <option value="">不限目的地</option>
      <option v-for="place in options" :key="place.id" :value="place.id">{{ place.name }}{{ place.status==='INACTIVE'?'（已停用，历史关联）':'' }}</option>
    </select>
    <p v-if="loading" class="text-xs text-ink-soft">正在查找目的地…</p>
    <p v-if="error" class="text-xs text-ember">{{ error }}</p>
  </div>
</template>
