/**
 * 安全 Markdown 渲染。
 *
 * 采用两层防护：
 * 1. markdown-it 关闭原始 HTML 与自动链接；解析阶段用 validateLink 拒绝危险目标，
 *    图片在渲染阶段只接受受管路径，非法图片降级为转义后的 alt 文本；
 * 2. 独立的 DOMPurify 实例在最终输出前按白名单清理，并在 hook 中再次校验链接、
 *    图片与 class，避免解析器 URL 规范化后绕过第一层判断。
 *
 * 正文图片唯一合法形态：/uploads/YYYY/MM/<32 位小写十六进制>.<jpg|png|webp>。
 */
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import type { Config, DOMPurify as Purifier } from 'dompurify'

const MANAGED_IMAGE_PATTERN = /^\/uploads\/[0-9]{4}\/(0[1-9]|1[0-2])\/[0-9a-f]{32}\.(jpg|png|webp)$/
const CONTROL_CHARACTER_PATTERN = /[\u0000-\u001F\u007F]/
const ABSOLUTE_HTTP_PATTERN = /^https?:\/\//i
const LANGUAGE_CLASS_PATTERN = /^language-[A-Za-z0-9_+#.-]+$/
const EXTERNAL_LINK_REL = 'noopener noreferrer'
const IMAGE_FALLBACK_TEXT = '图片暂时无法显示'

const PURIFY_CONFIG: Config = {
  ALLOWED_TAGS: [
    'p',
    'br',
    'hr',
    'h1',
    'h2',
    'h3',
    'h4',
    'h5',
    'h6',
    'blockquote',
    'ul',
    'ol',
    'li',
    'pre',
    'code',
    'strong',
    'em',
    's',
    'table',
    'thead',
    'tbody',
    'tr',
    'th',
    'td',
    'a',
    'img',
  ],
  ALLOWED_ATTR: ['href', 'title', 'src', 'alt', 'rel', 'target', 'loading', 'class'],
  ALLOW_ARIA_ATTR: false,
  ALLOW_DATA_ATTR: false,
  ALLOW_UNKNOWN_PROTOCOLS: false,
  RETURN_TRUSTED_TYPE: false,
}

const md = new MarkdownIt({ html: false, linkify: false })

/** markdown-it 渲染出的文本/属性统一由此转义。 */
function escapeHtml(value: string): string {
  return md.utils.escapeHtml(value)
}

/**
 * 解析器（mdurl）会先规范化链接：反斜杠、控制字符可能被百分号编码。
 * 因此同时检查原值与一次解码后的值，避免编码绕过。
 */
function inspectUrl(raw: string): string | null {
  const value = raw.trim()
  if (value.length === 0) {
    return null
  }
  if (CONTROL_CHARACTER_PATTERN.test(value) || value.includes('\\')) {
    return null
  }
  let decoded = value
  try {
    decoded = decodeURIComponent(value)
  } catch {
    decoded = value
  }
  if (CONTROL_CHARACTER_PATTERN.test(decoded) || decoded.includes('\\')) {
    return null
  }
  return decoded
}

/** 只允许 http/https 绝对地址（必须有 host），或单个 / 开头的站内路径。 */
export function isSafeLinkTarget(raw: string): boolean {
  const value = raw.trim()
  const decoded = inspectUrl(raw)
  if (decoded === null) {
    return false
  }
  if (decoded.startsWith('//')) {
    return false
  }
  if (decoded.startsWith('/')) {
    return value.startsWith('/') && !value.startsWith('//')
  }
  if (!ABSOLUTE_HTTP_PATTERN.test(value) || !ABSOLUTE_HTTP_PATTERN.test(decoded)) {
    return false
  }
  try {
    const parsed = new URL(value)
    return (parsed.protocol === 'http:' || parsed.protocol === 'https:') && parsed.host.length > 0
  } catch {
    return false
  }
}

export function isManagedImageSource(value: string): boolean {
  return MANAGED_IMAGE_PATTERN.test(value)
}

// 解析阶段的链接白名单：markdown-it 对链接与图片 src 都会调用这里。
md.validateLink = (url: string): boolean => isSafeLinkTarget(url)

// 图片：仅受管路径保留 <img>，其余降级为转义后的 alt 文本，不产生任何资源请求。
md.renderer.rules.image = (tokens, idx, options, env, self) => {
  const token = tokens[idx]
  const source = token.attrGet('src')
  const alt = self.renderInlineAsText(token.children ?? [], options, env)
  if (typeof source !== 'string' || !isManagedImageSource(source)) {
    return escapeHtml(alt.length > 0 ? alt : IMAGE_FALLBACK_TEXT)
  }
  return `<img src="${escapeHtml(source)}" alt="${escapeHtml(alt)}" loading="lazy">`
}

// 链接：渲染阶段再次校验 href；外链在最终清理前加入固定的 target/rel。
md.renderer.rules.link_open = (tokens, idx, _options, _env, self) => {
  const token = tokens[idx]
  const rawHref = token.attrGet('href')
  const rawTitle = token.attrGet('title')
  token.attrs = []
  if (typeof rawHref === 'string' && isSafeLinkTarget(rawHref)) {
    token.attrSet('href', rawHref)
    if (typeof rawTitle === 'string' && rawTitle.length > 0) {
      token.attrSet('title', rawTitle)
    }
    if (ABSOLUTE_HTTP_PATTERN.test(rawHref)) {
      token.attrSet('target', '_blank')
      token.attrSet('rel', EXTERNAL_LINK_REL)
    }
  }
  return `<a${self.renderAttrs(token)}>`
}

/**
 * DOMPurify hook：对每个元素做第二次校验。
 * 注意此 hook 对文本节点也会触发，必须先用 nodeType 过滤。
 */
function hardenElementAttributes(node: Element): void {
  if (node.nodeType !== 1) {
    return
  }

  const tagName = node.tagName
  const className = node.getAttribute('class')
  if (className !== null) {
    const keepLanguage = tagName === 'CODE' && LANGUAGE_CLASS_PATTERN.test(className.trim())
    if (keepLanguage) {
      node.setAttribute('class', className.trim())
    } else {
      node.removeAttribute('class')
    }
  }

  if (tagName === 'A') {
    const href = node.getAttribute('href')
    if (href === null || !isSafeLinkTarget(href)) {
      node.removeAttribute('href')
      node.removeAttribute('target')
      node.removeAttribute('rel')
      return
    }
    if (ABSOLUTE_HTTP_PATTERN.test(href)) {
      node.setAttribute('target', '_blank')
      node.setAttribute('rel', EXTERNAL_LINK_REL)
    } else {
      node.removeAttribute('target')
      node.removeAttribute('rel')
    }
    return
  }

  if (tagName === 'IMG') {
    const src = node.getAttribute('src')
    if (src === null || !isManagedImageSource(src)) {
      const alt = (node.getAttribute('alt') ?? '').trim()
      node.removeAttribute('src')
      node.removeAttribute('loading')
      node.setAttribute('alt', alt.length > 0 ? alt : IMAGE_FALLBACK_TEXT)
      return
    }
    node.setAttribute('loading', 'lazy')
  }
}

let purifier: Purifier | null = null

/** 使用绑定到当前 window 的独立实例，避免污染全局 DOMPurify 的 hook 与配置。 */
function getPurifier(): Purifier | null {
  if (purifier !== null) {
    return purifier
  }
  if (typeof window === 'undefined') {
    return null
  }
  const instance = DOMPurify(window)
  if (instance.isSupported !== true) {
    return null
  }
  instance.addHook('beforeSanitizeAttributes', hardenElementAttributes)
  purifier = instance
  return instance
}

export function renderMarkdown(source: string): string {
  if (typeof source !== 'string' || source.trim().length === 0) {
    return ''
  }
  const instance = getPurifier()
  if (instance === null) {
    return ''
  }
  return instance.sanitize(md.render(source), PURIFY_CONFIG)
}

/** 可视化编辑器粘贴沿用展示端同一白名单，不扩大可执行 HTML 范围。 */
export function sanitizeEditorHtml(html: string): string {
  return getPurifier()?.sanitize(html, PURIFY_CONFIG) ?? ''
}
