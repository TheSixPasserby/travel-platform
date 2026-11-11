import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ContentImage from '@/components/ContentImage.vue'
import SafeMarkdown from '@/components/SafeMarkdown.vue'
const urls=Array.from({length:5},(_,i)=>`/uploads/2026/11/${String(i+1).padStart(32,'0')}.jpg`)
afterEach(()=>{document.body.innerHTML='';document.body.style.overflow='';vi.restoreAllMocks()})
async function open(){
 const wrapper=mount(SafeMarkdown,{props:{content:urls.map((url,i)=>`![照片${i+1}](${url})`).join('\n\n')},attachTo:document.body})
 await flushPromises();const image=wrapper.findAll('img')[2]!;image.element.focus();await image.trigger('click');await flushPromises()
 return wrapper
}
describe('内容图片灯箱',()=>{
 it('详情图库的重复URL按位置打开第二处说明',async()=>{
  const wrapper=mount(ContentImage,{props:{src:urls[0]!,alt:'第二处',position:1,images:[{src:urls[0]!,alt:'第一处'},{src:urls[0]!,alt:'第二处'}]},attachTo:document.body})
  await wrapper.get('button').trigger('click');await flushPromises()
  const dialog=document.querySelector('[role="dialog"]')!;expect(dialog.textContent).toContain('2 / 2');expect(dialog.textContent).toContain('第二处');wrapper.unmount()
 })

 it('可放大和平移，切换图片恢复适应窗口；缩放中滑动不误翻页',async()=>{
  const wrapper=await open(),dialog=document.querySelector('[role="dialog"]')!
  const zoom=dialog.querySelector<HTMLButtonElement>('[aria-label="放大图片"]')!;zoom.click();await flushPromises()
  expect(dialog.querySelector('.is-zoomed')).not.toBeNull();expect(zoom.getAttribute('aria-pressed')).toBe('true')
  for(const [type,x] of [['touchstart',220],['touchend',40]] as const){const e=new Event(type,{bubbles:true});Object.defineProperty(e,type==='touchstart'?'touches':'changedTouches',{value:[{clientX:x,clientY:100}]});dialog.dispatchEvent(e)}
  await flushPromises();expect(dialog.textContent).toContain('3 / 5')
  document.dispatchEvent(new KeyboardEvent('keydown',{key:'ArrowRight',bubbles:true}));await flushPromises()
  expect(dialog.textContent).toContain('4 / 5');expect(dialog.querySelector('.is-zoomed')).toBeNull();wrapper.unmount()
 })

 it('重复URL按实际点击位置定位，保留该处说明',async()=>{
  const wrapper=mount(SafeMarkdown,{props:{content:`![第一处](${urls[0]})\n\n![第二处](${urls[0]})`},attachTo:document.body});await flushPromises();await wrapper.findAll('img')[1]!.trigger('click');await flushPromises()
  const dialog=document.querySelector('[role="dialog"]')!;expect(dialog.textContent).toContain('2 / 2');expect(dialog.textContent).toContain('第二处');wrapper.unmount()
 })
 it('正文五图按点击索引分组、键盘切换、Esc关闭并归还焦点/滚动',async()=>{
  const wrapper=await open();const dialog=document.querySelector('[role="dialog"]')!
  expect(dialog).not.toBeNull();expect(dialog.textContent).toContain('3 / 5');expect(document.body.style.overflow).toBe('hidden')
  document.dispatchEvent(new KeyboardEvent('keydown',{key:'ArrowRight',bubbles:true}));await flushPromises()
  expect(dialog.textContent).toContain('4 / 5')
  document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}));await flushPromises()
  expect(document.querySelector('[role="dialog"]')).toBeNull();expect(document.activeElement).toBe(wrapper.findAll('img')[2]!.element);expect(document.body.style.overflow).toBe('')
  wrapper.unmount()
 })
 it('图片失败后提示、触摸翻页、组件卸载清理背景锁',async()=>{
  const wrapper=await open();const img=document.querySelector('[role="dialog"] img')!
  img.dispatchEvent(new Event('error'));await flushPromises();expect(document.body.textContent).toContain('图片加载失败')
  const dialog=document.querySelector('[role="dialog"]')!
  for(const [type,x] of [['touchstart',220],['touchend',40]] as const){const e=new Event(type,{bubbles:true});Object.defineProperty(e,type==='touchstart'?'touches':'changedTouches',{value:[{clientX:x,clientY:100}]});dialog.dispatchEvent(e)}
  await flushPromises();expect(dialog.textContent).toContain('4 / 5');wrapper.unmount();expect(document.body.style.overflow).toBe('')
 })
 it('非法图片和恶意HTML不创建远程图片或灯箱入口',async()=>{
  const wrapper=mount(SafeMarkdown,{props:{content:'![远程](https://example.org/a.jpg)\n\n<img src=x onerror=alert(1)><script>alert(1)</script>'},attachTo:document.body})
  await flushPromises();expect(wrapper.find('img').exists()).toBe(false);expect(wrapper.find('script').exists()).toBe(false);expect(document.querySelector('[role="dialog"]')).toBeNull();wrapper.unmount()
 })
 it('单图不显示翻页，Tab焦点循环，关闭按钮可用',async()=>{
  const wrapper=mount(SafeMarkdown,{props:{content:`![独照](${urls[0]})`},attachTo:document.body});await flushPromises();await wrapper.get('img').trigger('keydown',{key:'Enter'});await flushPromises()
  const dialog=document.querySelector('[role="dialog"]')!
  expect(dialog).not.toBeNull();expect(dialog.querySelector('[aria-label="下一张"]')).toBeNull()
  const close=dialog.querySelector<HTMLButtonElement>('[aria-label="关闭图片预览"]')!;close.focus();document.dispatchEvent(new KeyboardEvent('keydown',{key:'Tab',bubbles:true,cancelable:true}));expect(dialog.contains(document.activeElement)).toBe(true)
  close.click();await flushPromises();expect(document.querySelector('[role="dialog"]')).toBeNull();wrapper.unmount()
 })
})
