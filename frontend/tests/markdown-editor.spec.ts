import { describe, expect, it, afterEach } from 'vitest'
import { readFileSync } from 'node:fs'
import { createMarkdownEditor, roundTripIssue, sourceIssue } from '@/utils/guideEditor'
import { editorViewCtx, parserCtx, serializerCtx } from '@milkdown/kit/core'
import { renderMarkdown } from '@/utils/markdown'
const image='/uploads/2026/11/0123456789abcdef0123456789abcdef.jpg'
const fixture=`# 一级标题\n\n## 二级标题\n\n正文 **粗体** 和 *斜体*，[链接](https://example.org)。\n\n- 清单一\n- 清单二\n\n1. 第一\n2. 第二\n\n> 引用\n\n![旅行说明](${image})\n\n| 城市 | 天数 |\n| --- | --- |\n| 成都 | 2 |\n\n\`\`\`js\nconst x = '<tag>';\n\`\`\`\n\n---\n`
const editors:Awaited<ReturnType<typeof createMarkdownEditor>>[]=[]
afterEach(async()=>{await Promise.all(editors.splice(0).map(editor=>editor.destroy()));document.body.innerHTML=''})
async function create(source:string,changed=(_:string)=>{}){const root=document.createElement('div');document.body.append(root);const editor=await createMarkdownEditor(root,source,()=>false,changed);editors.push(editor);return editor}
describe('原生 Markdown 可视化编辑 PoC',()=>{
 for(const id of [4,5,6])it(`真实旧攻略 ${id} 往返保留语义和五张受管配图`,async()=>{
  const source=readFileSync(`${process.cwd()}/../docs/m8/content/guide-${id}.md`,'utf8'),editor=await create(source)
  const output=editor.action(ctx=>ctx.get(serializerCtx)(ctx.get(editorViewCtx).state.doc))
  expect(sourceIssue(source)).toBe('');expect(roundTripIssue(source,output)).toBe('')
  expect(renderMarkdown(output).match(/<img /g)).toHaveLength(5)
 })
 it('综合夹具保留标题、列表、链接、引用、表格和代码语义',async()=>{
  const editor=await create(fixture),output=editor.action(ctx=>ctx.get(serializerCtx)(ctx.get(editorViewCtx).state.doc))
  expect(roundTripIssue(fixture,output)).toBe('');expect(output).toContain(image);expect(renderMarkdown(output)).toContain('<table>')
 })
 it('复杂扩展或HTML明确回退源码，不以安全渲染掩盖内容损失',()=>{
  expect(sourceIssue('<div>原样内容</div>')).not.toBe('')
  expect(sourceIssue('![外链图](https://example.org/image.jpg)')).not.toBe('')
  expect(sourceIssue('[邮件](mailto:travel@example.org)')).not.toBe('')
  expect(sourceIssue('[相对链接](nearby.html)')).not.toBe('')
  expect(sourceIssue('![x](javascript:alert(1))')).not.toBe('')
  expect(sourceIssue('[x](javascript:alert(1))')).not.toBe('')
  expect(sourceIssue('[x](file:///private/file)')).not.toBe('')
  expect(roundTripIssue('重要原文','丢失')).not.toBe('')
  expect(roundTripIssue('a\n\nb','a b')).not.toBe('')
  expect(roundTripIssue('[a](/a) [b](/b)','[a](/a)[b](/b)')).not.toBe('')
  expect(roundTripIssue('[unused]: https://example.org\n','')).not.toBe('')
 })
 it('编辑同步更新，不等防抖；非法图片事务不能触发请求或修改正文',async()=>{
  let content='';const editor=await create('原文',value=>content=value)
  editor.action(ctx=>{const view=ctx.get(editorViewCtx);view.dispatch(view.state.tr.insertText('新增',1))})
  expect(content).toContain('新增原文')
  editor.action(ctx=>{const view=ctx.get(editorViewCtx),parsed=ctx.get(parserCtx)('![外图](https://example.org/a.jpg)')!;view.dispatch(view.state.tr.replaceWith(0,view.state.doc.content.size,parsed.content))})
  expect(content).toContain('新增原文');expect(document.querySelector('img')).toBeNull()
 })
 it('可视化空段落不向旧Markdown注入HTML占位',async()=>{
  let content='';const editor=await create('原文\n\n尾段',value=>content=value)
  editor.action(ctx=>{const view=ctx.get(editorViewCtx);view.dispatch(view.state.tr.insert(view.state.doc.child(0).nodeSize,view.state.schema.nodes.paragraph!.create()))})
  expect(content).not.toContain('<br');expect(content).toContain('原文')
 })
})
