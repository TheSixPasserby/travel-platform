import { describe,expect,it } from 'vitest'
import { normalizeComment } from '@/utils/comment'
describe('评论输入与Java契约一致',()=>{
  it('规范化换行与Unicode首尾空白',()=>{
    expect(normalizeComment(' \t\u00A0\u200B\u3000溪谷\r\n旅途\r很好\uFEFF ')).toBe('溪谷\n旅途\n很好')
  })
  it('拒绝空白和不可见边界空白',()=>{
    for(const value of ['', ' \t\n\r','\u00A0','\u200B\uFEFF','\u3000\u2007\u202F'])expect(()=>normalizeComment(value)).toThrow()
  })
  it('按码点接收2000emoji并拒2001',()=>{
    expect([...normalizeComment('🌿'.repeat(2000))]).toHaveLength(2000)
    expect(()=>normalizeComment('🌿'.repeat(2001))).toThrow()
  })
  it('去首尾空白后才计内容长度',()=>{
    expect([...normalizeComment(' '.repeat(20)+'溪'.repeat(2000)+'\u00A0\uFEFF')]).toHaveLength(2000)
  })
  it('组合符分别计数并保留joiner',()=>{
    expect(normalizeComment('e\u0301'.repeat(1000))).toBe('e\u0301'.repeat(1000))
    expect(()=>normalizeComment('e\u0301'.repeat(1001))).toThrow()
    expect(normalizeComment('👩\u200D👩\u200D👧\u200D👦 A\u200CB\u200DC')).toBe('👩\u200D👩\u200D👧\u200D👦 A\u200CB\u200DC')
  })
  it('内部空白不改写',()=>{
    const text='a\tb\nc\u00A0d\u3000e\u200Bf';expect(normalizeComment(text)).toBe(text)
  })
  it('控制字符在边界也不能被吞掉',()=>{
    for(const cp of [0,1,8,11,12,14,31,127,128,133,159])expect(()=>normalizeComment(String.fromCodePoint(cp)+'text')).toThrow()
  })
  it('HTML文本不在规范化步骤解析',()=>{
    const text='<img src=x onerror=alert(1)> \' OR 1=1 --';expect(normalizeComment(' '+text+' ')).toBe(text)
  })
})
