import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import GuideRichEditor from '@/components/GuideRichEditor.vue'
import { uploadImage } from '@/api/files'
import { ApiError } from '@/api/client'
import { writeToken } from '@/api/token'
vi.mock('@/api/files',()=>({uploadImage:vi.fn()}))
import { editorImageIssue, validateEditorImages } from '@/utils/richEditor'
const wrappers:VueWrapper[]=[]
const resources=[['js/lute/lute.min.js','vditorLuteScript'],['js/i18n/zh_CN.js','vditorI18nScriptzh_CN'],['js/icons/ant.js','vditorIconScript']]
beforeAll(()=>{
 Object.defineProperty(document,'execCommand',{configurable:true,value:()=>false})
 for(const [file,id] of resources){
  // Execute the exact installed official runtime; jsdom does not load script src.
  ;(0,eval)(readFileSync(`${process.cwd()}/node_modules/vditor/dist/${file}`,'utf8'))
  const script=document.createElement('script');script.id=id!;document.head.append(script)
 }
})
afterEach(()=>{wrappers.splice(0).forEach(w=>w.unmount());vi.resetAllMocks()})
async function render(content='第一段\n\n第二段'){
 const w=mount(GuideRichEditor,{props:{modelValue:content,galleryCount:0},attachTo:document.body});wrappers.push(w);await flushPromises();return w
}
describe('Vditor 真实运行时与旧稿兼容',()=>{
 it('默认所见即所得，禁用缓存，原文加载不产生修改',async()=>{
  const w=await render();expect(w.get('[data-test="rich-editor"]').attributes('data-mode')).toBe('wysiwyg')
  expect(w.find('[contenteditable="true"]').exists()).toBe(true);expect(w.emitted('update:modelValue')).toBeUndefined()
  expect(Object.keys(localStorage).some(key=>key.includes('vditor'))).toBe(false)
 })
 it.each([4,5,6])('旧攻略%d三模式往返保留原文和受管图片',async id=>{
  const content=readFileSync(`${process.cwd()}/../docs/m8/content/guide-${id}.md`,'utf8'),w=await render(content)
  expect(w.get('[data-test="rich-editor"]').attributes('data-mode')).toBe('wysiwyg')
  for(const mode of ['sv','ir','wysiwyg']){await w.get(`[data-test="mode-${mode}"]`).trigger('click');await flushPromises();expect(w.get('[data-test="rich-editor"]').attributes('data-mode')).toBe(mode)}
  expect((w.vm as unknown as {getContent():string}).getContent()).toBe(content);expect(w.findAll('img').length).toBeGreaterThanOrEqual(5)
  expect(w.emitted('update:modelValue')).toBeUndefined()
 })
 it('综合 Markdown 的标题、列表、引用、链接、表格、代码和受管图片三模式往返',async()=>{
  const content='# 标题\n\n## 行程\n\n中文 **粗体**、*斜体*、~~删除线~~和[链接](https://example.org)。\n\n- 无序一\n- 无序二\n\n1. 有序一\n2. 有序二\n\n> 引用建议\n\n| 地点 | 天数 |\n| --- | --- |\n| 成都 | 2 |\n\n```js\nconst x = "<tag>";\n```\n\n![沿途](/uploads/2026/11/0123456789abcdef0123456789abcdef.jpg)\n'
  const w=await render(content)
  for(const mode of ['sv','ir','wysiwyg']){await w.get(`[data-test="mode-${mode}"]`).trigger('click');await flushPromises();expect(w.get('[data-test="rich-editor"]').attributes('data-mode')).toBe(mode)}
  expect((w.vm as unknown as {getContent():string}).getContent()).toBe(content);expect(w.find('.vditor-wysiwyg table').exists()).toBe(true)
  expect(w.emitted('update:modelValue')).toBeUndefined()
 })
 it('100000字符原文完整导入且不产生格式化写入',async()=>{
  const content='中'.repeat(100000),w=await render(content)
  expect((w.vm as unknown as {getContent():string}).getContent()).toBe(content);expect(w.text()).toContain('100000 / 100000');expect(w.emitted('update:modelValue')).toBeUndefined()
 })
 it('复杂HTML与非法图片以源码保留，不创建外链请求或危险DOM',async()=>{
  const content='<div>原文</div>\n\n![x](https://example.org/x.jpg)',w=await render(content)
  expect(w.get('[data-test="guide-content"]').element).toHaveProperty('value',content)
  expect(w.find('img').exists()).toBe(false);expect(w.text()).toContain('保留 Markdown 源码')
 })
 it('会话失效后的源码读取仍保留输入回调前的最后文字',async()=>{
  const w=await render('原文');await w.get('[data-test="mode-sv"]').trigger('click');await flushPromises()
  const area=w.get<HTMLTextAreaElement>('.vditor-sv');area.element.value='失效前刚输入的最后文字'
  await w.get('.vditor-host').trigger('input');await w.setProps({disabled:true})
  expect((w.vm as unknown as {getContent():string}).getContent().trimEnd()).toBe('失效前刚输入的最后文字')
  expect(w.emitted('update:modelValue')?.at(-1)?.[0]).toContain('失效前刚输入的最后文字')
 })
 it('禁用时不能改模式或插图；图片插入在源码选区，不接受任意URL',async()=>{
  const w=await render('前文后文');await w.get('[data-test="mode-sv"]').trigger('click');await flushPromises()
  const area=w.get<HTMLTextAreaElement>('.vditor-sv');area.element.setSelectionRange(2,2);area.element.focus()
  const exposed=w.vm as unknown as {insertImage(url:string,alt:string):boolean;rememberSelection():void;getContent():string}
  exposed.rememberSelection();expect(exposed.insertImage('https://example.org/x.jpg','不接受')).toBe(false)
  expect(exposed.insertImage('/uploads/2026/11/0123456789abcdef0123456789abcdef.jpg','说明')).toBe(true)
  expect(exposed.getContent()).toContain('前文![说明](/uploads/2026/11/0123456789abcdef0123456789abcdef.jpg)后文')
  await w.setProps({disabled:true});expect(exposed.insertImage('/uploads/2026/11/0123456789abcdef0123456789abcdef.jpg','说明')).toBe(false)
 })
})
describe('图片上传前置边界',()=>{
 it('手输源码也不能把 Base64、blob 或非受管图片保存成正文图片',()=>{
  for(const src of ['data:image/png;base64,AAAA','blob:https://example.org/id','https://example.org/a.png'])expect(editorImageIssue(`![说明](${src})`)).not.toBe('')
  expect(editorImageIssue('![说明](/uploads/2026/11/0123456789abcdef0123456789abcdef.png)')).toBe('')
  expect(editorImageIssue('```md\n![代码示例](data:image/png;base64,AAAA)\n```')).toBe('')
 })

 it('粘贴图片走平台上传，失败不改正文，会话过期有反馈',async()=>{
  vi.mocked(uploadImage).mockRejectedValue(new ApiError('登录状态已过期',{status:401}))
  const w=await render('上传前文字');await w.get('.vditor-host').trigger('paste',{clipboardData:{files:[new File(['image'],'图片.png',{type:'image/png'})]}});await flushPromises()
  expect(uploadImage).toHaveBeenCalledTimes(1);expect(w.emitted('uploaded')).toBeUndefined();expect(w.emitted('session-expired')).toHaveLength(1)
  expect((w.vm as unknown as {getContent():string}).getContent()).toBe('上传前文字')
 })
 it('上传中的账号或令牌切换丢弃迟到结果，不插图不关联图库',async()=>{
  let finish!:(url:string)=>void;writeToken('owner-token');vi.mocked(uploadImage).mockReturnValue(new Promise(resolve=>finish=resolve))
  const w=await render('原账号文字');await w.get('.vditor-host').trigger('paste',{clipboardData:{files:[new File(['image'],'图片.png',{type:'image/png'})]}})
  writeToken('new-account-token');finish('/uploads/2026/11/0123456789abcdef0123456789abcdef.png');await flushPromises()
  expect(w.emitted('uploaded')).toBeUndefined();expect((w.vm as unknown as {getContent():string}).getContent()).toBe('原账号文字');expect(w.emitted('busy')?.at(-1)).toEqual([false])
 })
 it('达到20张时粘贴不发请求且反馈上限',async()=>{
  const w=await render();await w.setProps({galleryCount:20});await w.get('.vditor-host').trigger('paste',{clipboardData:{files:[new File(['image'],'图片.png',{type:'image/png'})]}});await flushPromises()
  expect(uploadImage).not.toHaveBeenCalled();expect(w.get('[role="alert"]').text()).toContain('20')
 })

 it('按批次校验类型、5MB和20张上限，不产生部分业务写入',()=>{
  const jpg=new File(['image'],'旅行.jpg',{type:'image/jpeg'})
  expect(validateEditorImages([jpg],19)).toBe('')
  expect(validateEditorImages([jpg,jpg],19)).toContain('20')
  expect(validateEditorImages([new File(['x'],'evil.svg',{type:'image/svg+xml'})],0)).toContain('JPG')
  expect(validateEditorImages([new File([new Uint8Array(5*1024*1024+1)],'big.png',{type:'image/png'})],0)).toContain('5MB')
 })
})
