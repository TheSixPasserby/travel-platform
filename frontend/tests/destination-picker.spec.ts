import { beforeEach, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import DestinationPicker from '@/components/DestinationPicker.vue'
import { fetchDestinations, fetchDestination } from '@/api/destinations'
import type { Destination } from '@/types/destination'
vi.mock('@/api/destinations',()=>({ fetchDestinations:vi.fn(),fetchDestination:vi.fn() }))
const old={ id:'9007199254740993',name:'历史山谷',status:'INACTIVE' as const }
beforeEach(()=>{vi.resetAllMocks();vi.mocked(fetchDestinations).mockResolvedValue({ records:[],total:0,current:1,size:50,pages:0 })})
it('按调用方指定的城市类型查询并保留历史选择',async()=>{
  const wrapper=mount(DestinationPicker,{props:{modelValue:old.id,retained:old,destinationType:'CITY'}})
  await flushPromises()
  expect(fetchDestinations).toHaveBeenCalledWith(expect.objectContaining({destinationType:'CITY'}))
  expect((wrapper.get('select').element as HTMLSelectElement).value).toBe(old.id)
  wrapper.unmount()
})
it('保留调用方给出的历史停用关联且不冒充可用目的地',async()=>{
  const wrapper=mount(DestinationPicker,{ props:{ modelValue:old.id,retained:old } });await flushPromises()
  expect((wrapper.get('select').element as HTMLSelectElement).value).toBe(old.id)
  expect(wrapper.text()).toContain('已停用')
  expect(fetchDestination).not.toHaveBeenCalled()
  wrapper.unmount()
})
it('迟到的当前选择查询不能替换后来选择',async()=>{
  let release!:(value:Destination)=>void
  const pending=new Promise<Destination>(resolve=>{release=resolve})
  vi.mocked(fetchDestination).mockReturnValueOnce(pending).mockResolvedValueOnce({ ...old,id:'9007199254740994',name:'新山谷',status:'ACTIVE' } as Destination)
  const wrapper=mount(DestinationPicker,{ props:{ modelValue:old.id,allowInactiveSelection:true } });await flushPromises()
  await wrapper.setProps({ modelValue:'9007199254740994' });await flushPromises()
  release(old as Destination);await flushPromises()
  expect((wrapper.get('select').element as HTMLSelectElement).value).toBe('9007199254740994')
  expect(wrapper.text()).toContain('新山谷')
  wrapper.unmount()
})
