import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { renderMarkdown } from '@/utils/markdown'
import SafeMarkdown from '@/components/SafeMarkdown.vue'

const image = '/uploads/2026/11/0123456789abcdef0123456789abcdef.png'
function dom(source: string): HTMLDivElement {
  const host = document.createElement('div')
  host.innerHTML = renderMarkdown(source)
  return host
}
function noActiveContent(host: HTMLElement): void {
  expect(host.querySelector('script,iframe,svg,math,form,input,style,object,embed')).toBeNull()
  for (const node of host.querySelectorAll('*')) {
    for (const attr of node.attributes) {
      expect(attr.name).not.toMatch(/^on|^(style|id|name|srcset)$|^data-|^aria-/i)
    }
  }
}

describe('安全 Markdown', () => {
  it('保留标题、列表、强调、表格和段落', () => {
    const host = dom('# 山谷\n\n**清晨**与*溪流*\n\n- 路线一\n- 路线二\n\n|地点|时间|\n|---|---|\n|溪谷|清晨|')
    expect(host.querySelector('h1')?.textContent).toBe('山谷')
    expect(host.querySelectorAll('li')).toHaveLength(2)
    expect(host.querySelector('strong')?.textContent).toBe('清晨')
    expect(host.querySelector('table td')?.textContent).toBe('溪谷')
  })
  it('原始HTML、事件和SVG只作为文字显示', () => {
    const host = dom('<script>alert(1)</script>\n<img src=x onerror=alert(1)>\n<svg onload=alert(1)><a href="javascript:alert(1)">x</a></svg>')
    noActiveContent(host)
    expect(host.querySelector('img')).toBeNull()
    expect(host.textContent).toContain('<script>')
  })
  it('代码块保留原文和语言而不创建脚本元素', () => {
    const host = dom('```js\n<script>alert(1)</script>\n```')
    noActiveContent(host)
    expect(host.querySelector('pre code')?.textContent).toContain('<script>alert(1)</script>')
    expect(host.querySelector('code')?.classList.contains('language-js')).toBe(true)
  })
  it('合法外链带固定安全属性，站内链接保留字符串ID', () => {
    const host = dom('[官网](https://example.com/path) [攻略](/guides/9007199254740993)')
    const links = host.querySelectorAll('a')
    expect(links).toHaveLength(2)
    expect(links[0]?.getAttribute('target')).toBe('_blank')
    expect(links[0]?.getAttribute('rel')?.split(' ')).toEqual(expect.arrayContaining(['noopener', 'noreferrer']))
    expect(links[1]?.getAttribute('href')).toBe('/guides/9007199254740993')
  })
  it('拒绝危险、编码和协议相对链接', () => {
    for (const link of ['javascript:alert%281%29', 'JaVaScRiPt:alert%281%29', 'jav&#x61;script:alert%281%29', 'data:text/html,test', 'vbscript:msgbox%281%29', '//evil.example/path', '/\\evil.example/path', 'https%3A%2F%2Fexample.com']) {
      const host = dom(`[链接](${link})`)
      noActiveContent(host)
      expect(host.querySelector('a[href]'), link).toBeNull()
    }
  })
  it('控制字符不能折叠成可执行协议', () => {
    for (const value of ['java\tscript:alert(1)', 'java\nscript:alert(1)', '\u0000javascript:alert(1)']) {
      const host = dom(`[链接](${value})`)
      noActiveContent(host)
      expect(host.querySelector('a[href]')).toBeNull()
    }
  })
  it('只加载规范的受管图片，并保留alt及延迟加载', () => {
    const host = dom(`![清晨的溪谷](${image})`)
    const img = host.querySelector('img')
    expect(img?.getAttribute('src')).toBe(image)
    expect(img?.getAttribute('alt')).toBe('清晨的溪谷')
    expect(img?.getAttribute('loading')).toBe('lazy')
  })
  it('拒绝远程、data、大小写和非法月份图片', () => {
    for (const value of ['https://evil.example/a.png', '//evil.example/a.png', 'data:image/png;base64,AAAA', image.replace('.png', '.PNG'), image.replace('/11/', '/13/')]) {
      const host = dom(`![山谷](${value})`)
      noActiveContent(host)
      expect(host.querySelector('img[src]'), value).toBeNull()
    }
  })
  it('拒绝点段、编码、查询串和fragment图片', () => {
    for (const value of [image+'?x=1', image+'#x', image.replace('/11/', '/11/../11/'), image.replace('/11/', '/11/%2e%2e/'), image.replace('/11/', '/11/%2f'), image.replace('/11/', '/11/\\')]) {
      const host = dom(`![山谷](${value})`)
      expect(host.querySelector('img[src]'), value).toBeNull()
    }
  })
  it('非法图片的恶意alt以文本占位，不拼入HTML', () => {
    const host = dom('![<img src=x onerror=alert(1)>](https://evil.example/a.png)')
    noActiveContent(host)
    expect(host.querySelector('img')).toBeNull()
    expect(host.textContent).toContain('<img src=x onerror=alert(1)>')
  })
  it('反复渲染不污染链接或清理规则', () => {
    dom('[坏链接](javascript:alert(1))')
    const host = dom('[官网](https://example.com)')
    expect(host.querySelector('a')?.getAttribute('href')).toBe('https://example.com')
    expect(dom('<style>body{display:none}</style>').querySelector('style')).toBeNull()
  })
  it('图片读取失败显示可读占位，后续内容更新正常', async () => {
    const wrapper = mount(SafeMarkdown, { props: { content: `![溪谷](${image})` } })
    await wrapper.get('img').trigger('error')
    expect(wrapper.text()).toContain('溪谷')
    expect(wrapper.find('img').exists()).toBe(false)
    await wrapper.setProps({ content: '# 新的一程' })
    expect(wrapper.get('h1').text()).toBe('新的一程')
    wrapper.unmount()
  })
})
