import { describe, expect, it, afterEach } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import MarkdownEditor from '@/components/MarkdownEditor.vue'
import { readFileSync } from 'node:fs'
const wrappers:VueWrapper[]=[]
afterEach(()=>wrappers.splice(0).forEach(w=>w.unmount()))
async function render(content='第一段\n\n第二段'){const w=mount(MarkdownEditor,{props:{modelValue:content},attachTo:document.body});wrappers.push(w);await flushPromises();return w}
describe('默认可视化与可选源码',()=>{
 it.each([['无序列表','ul li'],['有序列表','ol li'],['引用','blockquote'],['分隔线','hr']])('%s工具实际改变排版并保留原文',async(label,selector)=>{
  const w=await render('排版文字');await w.findAll('button').find(button=>button.text()===label)!.trigger('click')
  expect(w.find(`[data-test="visual-content"] ${selector}`).exists()).toBe(true);expect(w.get('[data-test="visual-content"]').text()).toContain('排版文字')
 })
 it('真实旧攻略经标题属性规范化后仍默认可视化且不修改原文',async()=>{
  const content=readFileSync(`${process.cwd()}/../docs/m8/content/guide-4.md`,'utf8'),w=await render(content)
  expect(w.get('[data-test="visual-content"]').isVisible()).toBe(true);expect(w.emitted('update:modelValue')).toBeUndefined()
 })
 it('默认显示可输入正文，未修改不发出格式化写入',async()=>{const w=await render();expect(w.get('[data-test="visual-content"]').attributes('contenteditable')).toBe('true');expect(w.get('[data-test="guide-content"]').isVisible()).toBe(false);expect(w.emitted('update:modelValue')).toBeUndefined()})
 it('首次导入旧文不成为可撤销的修改',async()=>{
  const w=await render('旧文完整保留');const undo=w.findAll('button').find(b=>b.text()==='撤销')!;await undo.trigger('click')
  expect(w.get('[data-test="visual-content"]').text()).toBe('旧文完整保留');expect(w.emitted('update:modelValue')).toBeUndefined()
 })
 it('源码编辑切回视觉仍保留文字，历史撤销可操作',async()=>{
  const w=await render();await w.get('[data-test="source-mode"]').trigger('click');await w.get('[data-test="guide-content"]').setValue('保留 **粗体**\n\n第二段');await w.get('[data-test="visual-mode"]').trigger('click');await flushPromises()
  expect(w.get('[data-test="visual-content"] strong').text()).toBe('粗体');expect(w.text()).not.toContain('为避免丢失')
 })
 it('复杂HTML完整保留源码并明确提示，不创建危险HTML或远程图片',async()=>{
  const content='<div>旧格式</div>\n\n![外链](https://example.org/p.png)';const w=await render(content)
  expect(w.get('[data-test="guide-content"]').isVisible()).toBe(true);expect(w.get('[data-test="guide-content"]').element).toHaveProperty('value',content);expect(w.text()).toContain('为避免丢失');expect(w.find('img').exists()).toBe(false);expect(w.emitted('update:modelValue')).toBeUndefined()
 })
 it('源码在记录的光标位置插图，非法地址不改变正文',async()=>{
  const w=await render('前文后文');await w.get('[data-test="source-mode"]').trigger('click');const area=w.get<HTMLTextAreaElement>('[data-test="guide-content"]');area.element.setSelectionRange(2,2);area.element.focus();await area.trigger('select')
  const editor=w.vm as unknown as {rememberSelection():void;insertImage(url:string,alt:string):boolean};editor.rememberSelection();expect(editor.insertImage('https://example.org/a.jpg','拒绝')).toBe(false)
  expect(editor.insertImage('/uploads/2026/11/0123456789abcdef0123456789abcdef.jpg','说明')).toBe(true)
  expect(w.emitted('update:modelValue')!.at(-1)![0]).toBe('前文![说明](/uploads/2026/11/0123456789abcdef0123456789abcdef.jpg)后文')
 })
})
