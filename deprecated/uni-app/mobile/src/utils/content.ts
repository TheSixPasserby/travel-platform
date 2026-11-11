export interface ContentBlock { kind: 'heading' | 'text' | 'quote' | 'code' | 'image'; text: string; src?: string }
const managedImage = /^\/uploads\/[0-9]{4}\/(0[1-9]|1[0-2])\/[a-f0-9]{32}\.(jpg|png|webp)$/

/** Native text nodes cannot execute HTML. Unsupported Markdown stays readable text. */
export function contentBlocks(content: string): ContentBlock[] {
  const blocks: ContentBlock[] = []
  let code = false
  for (const line of content.slice(0, 100000).split('\n')) {
    if (/^\s*```/.test(line)) { code = !code; continue }
    if (code) { blocks.push({ kind: 'code', text: line }); continue }
    if (!line.trim()) continue
    let position = 0
    for (const match of line.matchAll(/!\[([^\]]*)\]\(([^\s)]+)\)/g)) {
      if (match.index > position) blocks.push({ kind: 'text', text: line.slice(position, match.index) })
      blocks.push(managedImage.test(match[2]) ? { kind: 'image', src: match[2], text: match[1] || '旅行影像' } : { kind: 'text', text: match[1] || '图片地址不受支持' })
      position = match.index + match[0].length
    }
    const remaining = line.slice(position)
    if (!remaining) continue
    const kind = /^#{1,6}\s/.test(remaining) ? 'heading' : /^>\s?/.test(remaining) ? 'quote' : 'text'
    const text = remaining.replace(/^#{1,6}\s+|^>\s?/, '').replace(/\*\*([^*]+)\*\*/g, '$1').replace(/\[([^\]]+)\]\(([^)]+)\)/g, '$1（$2）')
    blocks.push({ kind, text })
  }
  return blocks
}
