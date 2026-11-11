import MarkdownIt from 'markdown-it'
import type { Env } from 'markdown-it'
import { Editor, defaultValueCtx, editorViewOptionsCtx, rootCtx, prosePluginsCtx, serializerCtx } from '@milkdown/kit/core'
import { commonmark, remarkPreserveEmptyLinePlugin } from '@milkdown/kit/preset/commonmark'
import { gfm } from '@milkdown/kit/preset/gfm'
import { history } from '@milkdown/kit/plugin/history'
import { Plugin } from '@milkdown/kit/prose/state'
import type { Transaction } from '@milkdown/kit/prose/state'
import type { EditorView } from '@milkdown/kit/prose/view'
import { DOMParser as ProseParser } from '@milkdown/kit/prose/model'
import type { Node as ProseNode } from '@milkdown/kit/prose/model'
import { isManagedImageSource, isSafeLinkTarget, renderMarkdown, sanitizeEditorHtml } from './markdown'
const inspect = new MarkdownIt({ html: true })
// 这里只检查语法，不生成DOM；保留危险协议token，交给同一来源规则明确拒绝。
inspect.validateLink = () => true
export function editorDocumentIssue(doc: ProseNode): string {
  let issue = ''
  doc.descendants(node => {
    if (node.type.name === 'image' && !isManagedImageSource(String(node.attrs.src))) issue = '正文含非受管图片'
    if (node.marks.some(mark => mark.type.name === 'link' && !isSafeLinkTarget(String(mark.attrs.href)))) issue = '正文含可视化模式不支持的链接'
  })
  return issue
}
export function sourceIssue(source: string): string {
  let issue = ''
  const walk = (tokens: ReturnType<typeof inspect.parse>) => tokens.forEach(token => {
    if (token.type.startsWith('html_')) issue = '正文含原样 HTML'
    if (token.type === 'image' && !isManagedImageSource(String(token.attrGet('src') ?? ''))) issue = '正文含非受管图片'
    if (token.type === 'link_open' && !isSafeLinkTarget(String(token.attrGet('href') ?? ''))) issue = '正文含可视化模式不支持的链接'
    if (token.children) walk(token.children)
  })
  const environment: Env = {}
  walk(inspect.parse(source, environment))
  if (Object.keys(environment.references ?? {}).length) issue = '正文含需原样保留的引用定义'
  if (/^---\s*\n[\s\S]*?\n---\s*(?:\n|$)/.test(source)) issue = '正文含需原样保留的元数据'
  if (/^\s*\[\^.+\]:|^\s*:::/m.test(source)) issue = '正文含特殊 Markdown 扩展'
  return issue
}
/** 比较安全渲染语义；语法标记、无意义空行可变化，文本/结构/URL不可丢。 */
export function roundTripIssue(source: string, output: string): string {
  if (sourceIssue(source)) return sourceIssue(source)
  const canonical = (markdown: string) => {
    const doc = new DOMParser().parseFromString(renderMarkdown(markdown), 'text/html')
    const node = (value: Node, pre = false): unknown => {
      if (value.nodeType === 3) return pre ? value.textContent : value.textContent?.replace(/\s+/g, ' ')
      if (!(value instanceof Element)) return null
      const attrs = ['href', 'title', 'src', 'alt', 'class'].map(key => [key, value.getAttribute(key)]).filter(([, val]) => val)
      const structural = ['BODY','UL','OL','BLOCKQUOTE','TABLE','THEAD','TBODY','TR'].includes(value.tagName)
      return [value.tagName, attrs, [...value.childNodes].filter(child => pre || !structural || child.nodeType !== 3 || child.textContent?.trim()).map(child => node(child, pre || value.tagName === 'PRE'))]
    }
    return JSON.stringify(node(doc.body))
  }
  return canonical(source) === canonical(output) ? '' : '导入导出后的文本、结构或图片存在差异'
}
export async function createMarkdownEditor(root: HTMLElement, source: string, disabled: () => boolean, changed: (value: string) => void, selectionChanged: (transaction: Transaction) => void = () => {}) {
  if (sourceIssue(source)) throw new Error('正文须保留源码模式')
  const editor = Editor.make().config(ctx => {
    ctx.set(rootCtx, root); ctx.set(defaultValueCtx, source)
    ctx.update(prosePluginsCtx, plugins => [...plugins, new Plugin({
      filterTransaction(transaction) {
        return !editorDocumentIssue(transaction.doc)
      }
    })])
    ctx.set(editorViewOptionsCtx, {
      editable: () => !disabled(),
      attributes: { 'aria-label': '攻略正文', 'aria-multiline': 'true', 'data-test': 'visual-content', 'spellcheck': 'true' },
      dispatchTransaction(this: EditorView, transaction) {
        this.updateState(this.state.apply(transaction))
        if (transaction.docChanged) changed(ctx.get(serializerCtx)(this.state.doc))
        selectionChanged(transaction)
      },
      handlePaste(view, event) {
        const html = event.clipboardData?.getData('text/html')
        if (!html) return false
        const doc = new DOMParser().parseFromString(sanitizeEditorHtml(html), 'text/html')
        const slice = ProseParser.fromSchema(view.state.schema).parseSlice(doc.body)
        view.dispatch(view.state.tr.replaceSelection(slice).scrollIntoView())
        return true
      },
      handleDrop(_view, event) {
        // 文件必须经现有上传入口校验；粘贴/拖入远程HTML不能创建旁路。
        return Boolean(event.dataTransfer?.files.length || event.dataTransfer?.getData('text/html'))
      },
      handleClickOn(_view,_pos,node) { return node.type.name === 'image' ? false : undefined }
    })
  // 本平台原渲染器禁用HTML；空段落使用标准Markdown空行，不序列化<br />占位。
  }).use(commonmark.filter(plugin => !remarkPreserveEmptyLinePlugin.includes(plugin))).use(gfm).use(history)
  await editor.create()
  return editor
}
