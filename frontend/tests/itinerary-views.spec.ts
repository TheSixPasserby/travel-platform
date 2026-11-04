import { beforeEach,expect,it,vi } from 'vitest'
import { flushPromises,mount } from '@vue/test-utils'
import { createMemoryHistory,createRouter,RouterView } from 'vue-router'
import { createPinia,setActivePinia } from 'pinia'
import MyItinerariesView from '@/views/MyItinerariesView.vue'
import ItineraryDetailView from '@/views/ItineraryDetailView.vue'
import ItineraryDayCard from '@/components/ItineraryDay.vue'
import ItineraryItemForm from '@/components/ItineraryItemForm.vue'
import * as api from '@/api/itineraries'
import { fetchDestinations,fetchDestination } from '@/api/destinations'
import { ApiError } from '@/api/client'
import type { ItineraryDetail,ItineraryItem } from '@/types/itinerary'
vi.mock('@/api/itineraries',()=>({ fetchItineraries:vi.fn(),fetchItinerary:vi.fn(),createItinerary:vi.fn(),updateItinerary:vi.fn(),deleteItinerary:vi.fn(),createItineraryDay:vi.fn(),updateItineraryDay:vi.fn(),deleteItineraryDay:vi.fn(),createItineraryItem:vi.fn(),updateItineraryItem:vi.fn(),deleteItineraryItem:vi.fn(),reorderItineraryItems:vi.fn() }))
vi.mock('@/api/destinations',()=>({fetchDestinations:vi.fn(),fetchDestination:vi.fn()}))
const time='2026-11-03T10:00:00.000+08:00',id='9007199254740993',dayId='9007199254740994'
const item=(itemId:string,sortOrder:number):ItineraryItem=>({id:itemId,itineraryDayId:dayId,destination:null,placeName:'沿溪地点'+itemId,addressText:'溪边',startTime:'09:00',endTime:'10:00',notes:'保留这条经验',sortOrder,createdAt:time,updatedAt:time})
const itinerary:ItineraryDetail={id,title:'山谷慢行',destination:{id:'7',name:'山谷',status:'ACTIVE'},startDate:'2026-11-03',endDate:'2026-11-05',status:'ACTIVE',dayCount:1,itemCount:2,createdAt:time,updatedAt:time,notes:'原备注需要保留',days:[{id:dayId,itineraryId:id,dayNo:1,travelDate:'2026-11-03',title:'溪谷第一日',notes:null,createdAt:time,updatedAt:time,items:[item('9007199254740995',0),item('9007199254740996',1)]}]}
const page=(records:ItineraryDetail[],extra={})=>({records,total:records.length,current:1,size:10,pages:records.length?1:0,...extra})
async function render(path='/me/itineraries'){
  const pinia=createPinia();setActivePinia(pinia)
  const stub={template:'<div />'},router=createRouter({history:createMemoryHistory(),routes:[{path:'/me/itineraries',name:'my-itineraries',component:MyItinerariesView,meta:{requiresAuth:true}},{path:'/itineraries/:id',name:'itinerary-detail',component:ItineraryDetailView,meta:{requiresAuth:true}},{path:'/login',name:'login',component:stub},{path:'/guides',name:'guides',component:stub},{path:'/me',name:'me',component:stub}]})
  await router.push(path);await router.isReady();const wrapper=mount(RouterView,{global:{plugins:[pinia,router]}});await flushPromises();return {wrapper,router}
}
beforeEach(()=>{
  vi.resetAllMocks();vi.mocked(api.fetchItineraries).mockResolvedValue(page([structuredClone(itinerary)]));vi.mocked(api.fetchItinerary).mockImplementation(async()=>structuredClone(itinerary))
  vi.mocked(api.createItinerary).mockResolvedValue(structuredClone(itinerary));vi.mocked(api.updateItinerary).mockResolvedValue(structuredClone(itinerary));vi.mocked(api.deleteItinerary).mockResolvedValue(null)
  vi.mocked(api.createItineraryDay).mockResolvedValue(structuredClone(itinerary.days[0]!));vi.mocked(api.updateItineraryDay).mockResolvedValue(structuredClone(itinerary.days[0]!));vi.mocked(api.deleteItineraryDay).mockResolvedValue(null)
  vi.mocked(api.createItineraryItem).mockResolvedValue(structuredClone(itinerary.days[0]!.items[0]!));vi.mocked(api.updateItineraryItem).mockResolvedValue(structuredClone(itinerary.days[0]!.items[0]!));vi.mocked(api.deleteItineraryItem).mockResolvedValue(null);vi.mocked(api.reorderItineraryItems).mockResolvedValue(structuredClone(itinerary.days[0]!))
  vi.mocked(fetchDestinations).mockResolvedValue({records:[],total:0,current:1,size:50,pages:0});vi.mocked(fetchDestination).mockRejectedValue(new ApiError('不存在',{status:404}))
})
it('私人列表按服务端页码筛选状态并保持字符串链接',async()=>{
  vi.mocked(api.fetchItineraries).mockResolvedValueOnce(page([itinerary],{total:21,current:2,pages:3}))
  const {wrapper}=await render('/me/itineraries?page=2');expect(api.fetchItineraries).toHaveBeenCalledWith(expect.objectContaining({page:2,size:10}));expect(wrapper.get('[data-test=itinerary-card]').attributes('href')).toBe('/itineraries/'+id)
  await wrapper.get('[data-test=itinerary-keyword]').setValue('山谷');await wrapper.get('[data-test=itinerary-status]').setValue('ARCHIVED');await wrapper.get('[data-test=itinerary-search]').trigger('submit');await flushPromises()
  expect(api.fetchItineraries).toHaveBeenLastCalledWith(expect.objectContaining({page:1,keyword:'山谷',status:'ARCHIVED'}));wrapper.unmount()
})
it('列表错误可重试且超页保留显式第一页入口',async()=>{
  vi.mocked(api.fetchItineraries).mockRejectedValueOnce(new ApiError('暂时无法加载行程',{status:503})).mockResolvedValueOnce(page([],{total:2,current:999,pages:1}))
  const {wrapper}=await render('/me/itineraries?page=999');expect(wrapper.get('[role=alert]').text()).toContain('暂时无法加载行程');await wrapper.get('[data-test=retry-itineraries]').trigger('click');await flushPromises()
  await wrapper.get('[data-test=itineraries-first]').trigger('click');await flushPromises();expect(api.fetchItineraries).toHaveBeenLastCalledWith(expect.objectContaining({page:1}));wrapper.unmount()
})
it('新建失败保留表单、防重并在真正成功后进入详情',async()=>{
  let reject!:(error:Error)=>void;vi.mocked(api.createItinerary).mockReturnValueOnce(new Promise((_,fail)=>{reject=fail}))
  const {wrapper,router}=await render();await wrapper.get('[data-test=new-itinerary]').trigger('click')
  await wrapper.get('[data-test=itinerary-title]').setValue('新的溪谷之旅');await wrapper.get('[data-test=itinerary-start]').setValue('2026-11-03');await wrapper.get('[data-test=itinerary-end]').setValue('2026-11-05')
  await wrapper.get('[data-test=itinerary-form]').trigger('submit');await wrapper.get('[data-test=itinerary-form]').trigger('submit');expect(api.createItinerary).toHaveBeenCalledTimes(1)
  reject(new ApiError('暂时不能保存',{status:503}));await flushPromises();expect((wrapper.get('[data-test=itinerary-title]').element as HTMLInputElement).value).toBe('新的溪谷之旅');expect(router.currentRoute.value.name).toBe('my-itineraries')
  await wrapper.get('[data-test=itinerary-form]').trigger('submit');await flushPromises();expect(router.currentRoute.value.name).toBe('itinerary-detail');wrapper.unmount()
})
it('修改标题提交完整字段并保留原备注及目的地',async()=>{
  const {wrapper}=await render('/itineraries/'+id);await wrapper.get('[data-test=edit-itinerary]').trigger('click');await wrapper.get('[data-test=itinerary-title]').setValue('只改标题')
  await wrapper.get('[data-test=itinerary-form]').trigger('submit');await flushPromises()
  expect(api.updateItinerary).toHaveBeenCalledWith(id,expect.objectContaining({title:'只改标题',notes:'原备注需要保留',destinationId:'7',startDate:'2026-11-03',endDate:'2026-11-05',status:'ACTIVE'}));wrapper.unmount()
})
it('日期缩短冲突显示服务端原因并保留用户日期',async()=>{
  vi.mocked(api.updateItinerary).mockRejectedValueOnce(new ApiError('新日期范围不能排除已有日程',{status:409,code:40902}))
  const {wrapper}=await render('/itineraries/'+id);await wrapper.get('[data-test=edit-itinerary]').trigger('click');await wrapper.get('[data-test=itinerary-end]').setValue('2026-11-03');await wrapper.get('[data-test=itinerary-form]').trigger('submit');await flushPromises()
  expect(wrapper.get('[role=alert]').text()).toContain('不能排除已有日程');expect((wrapper.get('[data-test=itinerary-end]').element as HTMLInputElement).value).toBe('2026-11-03');wrapper.unmount()
})
it('添加日程请求失败保留未提交标题和日期',async()=>{
  vi.mocked(api.createItineraryDay).mockRejectedValueOnce(new ApiError('日程暂时无法保存',{status:503}))
  const {wrapper}=await render('/itineraries/'+id);await wrapper.get('[data-test=add-itinerary-day]').trigger('click');await wrapper.get('[data-test=day-date]').setValue('2026-11-04');await wrapper.get('[data-test=day-title]').setValue('山谷第二天')
  await wrapper.get('[data-test=day-form]').trigger('submit');await flushPromises();expect(api.createItineraryDay).toHaveBeenCalledWith(id,expect.objectContaining({travelDate:'2026-11-04',title:'山谷第二天'}));expect((wrapper.get('[data-test=day-title]').element as HTMLInputElement).value).toBe('山谷第二天');wrapper.unmount()
})
it('添加项目失败保持地点表单，不制造成功列表',async()=>{
  vi.mocked(api.createItineraryItem).mockRejectedValueOnce(new ApiError('地点暂时无法保存',{status:503}))
  const {wrapper}=await render('/itineraries/'+id);await wrapper.get('[data-test="add-item-'+dayId+'"]').trigger('click');await wrapper.get('[data-test=item-place-name]').setValue('未保存的溪谷');await wrapper.get('[data-test=item-form]').trigger('submit');await flushPromises()
  expect(wrapper.get('[role=alert]').text()).toContain('地点暂时无法保存');expect((wrapper.get('[data-test=item-place-name]').element as HTMLInputElement).value).toBe('未保存的溪谷');expect(wrapper.findAll('[data-test=itinerary-item]')).toHaveLength(2);wrapper.unmount()
})
it('排序提交全部ID并等待真实响应后刷新',async()=>{
  let finish!:(value:typeof itinerary.days[number])=>void;vi.mocked(api.reorderItineraryItems).mockReturnValueOnce(new Promise(resolve=>{finish=resolve}))
  const {wrapper}=await render('/itineraries/'+id);const first=itinerary.days[0]!.items[0]!,second=itinerary.days[0]!.items[1]!
  await wrapper.get('[data-test="move-up-'+second.id+'"]').trigger('click');expect(api.reorderItineraryItems).toHaveBeenCalledWith(dayId,[second.id,first.id]);expect(wrapper.findAll('[data-test=itinerary-item]')[0]!.text()).toContain(first.placeName)
  const updated=structuredClone(itinerary);updated.days[0]!.items.reverse();vi.mocked(api.fetchItinerary).mockResolvedValueOnce(updated);finish(updated.days[0]!);await flushPromises();expect(wrapper.findAll('[data-test=itinerary-item]')[0]!.text()).toContain(second.placeName);wrapper.unmount()
})
it('删除日程必须确认，成功后重新读取真实数据',async()=>{
  const {wrapper}=await render('/itineraries/'+id);wrapper.getComponent(ItineraryDayCard).vm.$emit('delete-day',itinerary.days[0]!);await flushPromises();expect(api.deleteItineraryDay).not.toHaveBeenCalled()
  expect(wrapper.get('[role=alertdialog]').text()).toContain('删除');await wrapper.get('[data-test=confirm-itinerary-delete]').trigger('click');await flushPromises();expect(api.deleteItineraryDay).toHaveBeenCalledWith(dayId);expect(api.fetchItinerary).toHaveBeenCalledTimes(2);wrapper.unmount()
})
it('已有局部删除确认时不能被顶部整份行程删除替换',async()=>{
  const {wrapper}=await render('/itineraries/'+id)
  await wrapper.getComponent(ItineraryDayCard).get('[data-test=delete-day]').trigger('click')
  expect(wrapper.get('[data-test=delete-itinerary]').attributes('disabled')).toBeDefined()
  expect(wrapper.get('[data-test=edit-itinerary]').attributes('disabled')).toBeDefined()
  expect(wrapper.get('[data-test=add-itinerary-day]').attributes('disabled')).toBeDefined()
  await wrapper.get('[data-test=delete-itinerary]').trigger('click')
  expect(wrapper.get('[role=alertdialog]').text()).toContain('确认删除这天的安排')
  await wrapper.get('[data-test=confirm-itinerary-delete]').trigger('click');await flushPromises()
  expect(api.deleteItineraryDay).toHaveBeenCalledWith(dayId);expect(api.deleteItinerary).not.toHaveBeenCalled();wrapper.unmount()
})
it('非法状态URL重试不能按旧筛选悄悄返回成功数据',async()=>{
  const {wrapper}=await render('/me/itineraries?status=BOGUS')
  expect(wrapper.get('[role=alert]').text()).toContain('状态筛选不正确');expect(api.fetchItineraries).not.toHaveBeenCalled()
  await wrapper.get('[data-test=retry-itineraries]').trigger('click');await flushPromises()
  expect(api.fetchItineraries).not.toHaveBeenCalled();expect(wrapper.get('[role=alert]').text()).toContain('状态筛选不正确');wrapper.unmount()
})
it('他人详情拒绝时不显示编辑表单或私人日程',async()=>{
  vi.mocked(api.fetchItinerary).mockRejectedValueOnce(new ApiError('无权访问该资源',{status:403}))
  const {wrapper}=await render('/itineraries/'+id);expect(wrapper.get('[role=alert]').text()).toContain('无权访问');expect(wrapper.find('[data-test=itinerary-form]').exists()).toBe(false);expect(wrapper.findComponent(ItineraryDayCard).exists()).toBe(false);wrapper.unmount()
})
it('受保护详情401回登录并保持本行程回跳',async()=>{
  vi.mocked(api.fetchItinerary).mockRejectedValueOnce(new ApiError('登录状态已失效',{status:401}))
  const {wrapper,router}=await render('/itineraries/'+id);expect(router.currentRoute.value.name).toBe('login');expect(router.currentRoute.value.query.redirect).toBe('/itineraries/'+id);wrapper.unmount()
})
it('旧对象与卸载后的响应不能覆盖当前详情或错误跳转',async()=>{
  let finish!:(value:ItineraryDetail)=>void;vi.mocked(api.fetchItinerary).mockReturnValueOnce(new Promise(resolve=>{finish=resolve}))
  const {wrapper,router}=await render('/itineraries/'+id);const next={...structuredClone(itinerary),id:'9007199254740998',title:'后来打开的行程'};vi.mocked(api.fetchItinerary).mockResolvedValueOnce(next)
  await router.push('/itineraries/'+next.id);await flushPromises();finish(itinerary);await flushPromises();expect(wrapper.get('h1').text()).toBe('后来打开的行程');wrapper.unmount()
  let reject!:(error:Error)=>void;vi.mocked(api.fetchItinerary).mockReturnValueOnce(new Promise((_,fail)=>{reject=fail}));const another=await render('/itineraries/'+id);await another.router.push('/guides');await flushPromises();reject(new ApiError('失效',{status:401}));await flushPromises();expect(another.router.currentRoute.value.name).toBe('guides');another.wrapper.unmount()
})
it('详情与项目备注只能作为纯文本显示',async()=>{
  const unsafe=structuredClone(itinerary);unsafe.notes='<img src=x onerror=alert(1)>';unsafe.days[0]!.items[0]!.notes='<script>window.x=1</script>';vi.mocked(api.fetchItinerary).mockResolvedValueOnce(unsafe)
  const {wrapper}=await render('/itineraries/'+id);expect(wrapper.text()).toContain(unsafe.notes);expect(wrapper.text()).toContain('<script>window.x=1</script>');expect(wrapper.find('script,img[src=x]').exists()).toBe(false);wrapper.unmount()
})
it('项目表单完整提交可选字段并保留历史目的地ID',async()=>{
  const pinia=createPinia();setActivePinia(pinia);const original={...itinerary.days[0]!.items[0]!,destination:{id:'7',name:'历史山谷',status:'INACTIVE' as const}}
  const wrapper=mount(ItineraryItemForm,{props:{initial:original,busy:false},global:{plugins:[pinia]}});await flushPromises();await wrapper.get('[data-test=item-place-name]').setValue('只改地点名');await wrapper.get('[data-test=item-form]').trigger('submit')
  expect(wrapper.emitted('save')?.[0]?.[0]).toEqual({placeName:'只改地点名',destinationId:'7',addressText:'溪边',startTime:'09:00',endTime:'10:00',notes:'保留这条经验'});wrapper.unmount()
})
