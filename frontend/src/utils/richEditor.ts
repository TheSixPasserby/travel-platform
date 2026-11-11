import MarkdownIt from 'markdown-it'
import type { Env } from 'markdown-it'
import { isManagedImageSource, isSafeLinkTarget, renderMarkdown } from './markdown'
const inspect=new MarkdownIt({html:true})
inspect.validateLink=()=>true
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

export function validateEditorImages(files:File[],galleryCount:number):string {
  if(!files.length)return '请选择图片'
  if(files.length+galleryCount>20)return '攻略图库最多20张图片，请先整理图库'
  for(const file of files){
    if(!['image/jpeg','image/png','image/webp'].includes(file.type))return '仅支持 JPG/PNG/WebP 格式的图片'
    if(file.size>5*1024*1024)return '图片不能超过5MB，请压缩后重试'
  }
  return ''
}

/** Source mode is editable too: it must not persist data/blob or unmanaged image references. */
export function editorImageIssue(source:string):string {
 let invalid=false
 const walk=(tokens:ReturnType<typeof inspect.parse>)=>tokens.forEach(token=>{
  if(token.type==='image'&&!isManagedImageSource(String(token.attrGet('src')??'')))invalid=true
  if(token.type.startsWith('html_')&&/<img\b/i.test(token.content))invalid=true
  if(token.children)walk(token.children)
 })
 walk(inspect.parse(source,{}))
 return invalid?'正文图片请通过平台上传；不能保存 Base64、blob、外链图片或 HTML 图片标签':''
}
