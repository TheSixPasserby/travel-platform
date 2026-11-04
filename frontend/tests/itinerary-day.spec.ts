import { expect,it } from 'vitest'
import { mount } from '@vue/test-utils'
import ItineraryDayCard from '@/components/ItineraryDay.vue'
import type { ItineraryDay,ItineraryItem } from '@/types/itinerary'
const time='2026-11-03T10:00:00.000+08:00'
const item=(id:string,sortOrder:number):ItineraryItem=>({id,itineraryDayId:'9007199254740993',destination:null,placeName:'地点'+id,addressText:null,startTime:'09:00',endTime:'10:00',notes:null,sortOrder,createdAt:time,updatedAt:time})
const day:ItineraryDay={id:'9007199254740993',itineraryId:'9007199254740994',dayNo:3,travelDate:'2026-11-05',title:'溪谷一日',notes:'<script>window.x=1</script>',createdAt:time,updatedAt:time,items:[item('9007199254740995',0),item('9007199254740996',1),item('9007199254740997',2)]}
it('按日展示日期时间地点和纯文本备注',()=>{
  const wrapper=mount(ItineraryDayCard,{props:{day,busy:false}})
  expect(wrapper.text()).toContain('2026-11-05');expect(wrapper.text()).toContain('溪谷一日');expect(wrapper.text()).toContain('<script>window.x=1</script>');expect(wrapper.find('script').exists()).toBe(false)
  expect(wrapper.text()).toContain('09:00');expect(wrapper.findAll('[data-test=itinerary-item]')).toHaveLength(3);wrapper.unmount()
})
it('上移下移提交当天全部字符串ID，首尾不越界',async()=>{
  const wrapper=mount(ItineraryDayCard,{props:{day,busy:false}})
  expect(wrapper.get('[data-test="move-up-'+day.items[0]!.id+'"]').attributes('disabled')).toBeDefined()
  expect(wrapper.get('[data-test="move-down-'+day.items[2]!.id+'"]').attributes('disabled')).toBeDefined()
  await wrapper.get('[data-test="move-up-'+day.items[2]!.id+'"]').trigger('click')
  expect(wrapper.emitted('order')?.[0]).toEqual([day.id,[day.items[0]!.id,day.items[2]!.id,day.items[1]!.id]])
  expect(day.items.map(i=>i.id)).toEqual(['9007199254740995','9007199254740996','9007199254740997']);wrapper.unmount()
})
it('忙碌时阻止排序和编辑事件',async()=>{
  const wrapper=mount(ItineraryDayCard,{props:{day,busy:true}})
  for(const button of wrapper.findAll('button'))expect(button.attributes('disabled')).toBeDefined()
  await wrapper.get('[data-test="move-up-'+day.items[1]!.id+'"]').trigger('click');expect(wrapper.emitted('order')).toBeUndefined();wrapper.unmount()
})
it('空日程保留添加项目入口并向父级传完整日ID',async()=>{
  const wrapper=mount(ItineraryDayCard,{props:{day:{...day,items:[]},busy:false}})
  await wrapper.get('[data-test="add-item-'+day.id+'"]').trigger('click');expect(wrapper.emitted('add-item')?.[0]).toEqual([day.id]);wrapper.unmount()
})
