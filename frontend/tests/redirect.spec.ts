import { describe, expect, it } from 'vitest'

import { resolveRedirectTarget } from '@/utils/redirect'

describe('resolveRedirectTarget', () => {
  it('保留内部路径与查询参数', () => {
    expect(resolveRedirectTarget('/me')).toBe('/me')
    expect(resolveRedirectTarget('/me?tab=profile')).toBe('/me?tab=profile')
    expect(resolveRedirectTarget('/me#joined')).toBe('/me#joined')
  })

  it('拒绝外部地址', () => {
    expect(resolveRedirectTarget('https://evil.example/login')).toBe('/me')
    expect(resolveRedirectTarget('http://evil.example/me')).toBe('/me')
    expect(resolveRedirectTarget('javascript:alert(1)')).toBe('/me')
  })

  it('拒绝协议相对与双斜线地址', () => {
    expect(resolveRedirectTarget('//evil.example/me')).toBe('/me')
    expect(resolveRedirectTarget('/%2F%2Fevil.example')).toBe('/me')
  })

  it('拒绝反斜杠地址', () => {
    expect(resolveRedirectTarget('/\\evil.example/me')).toBe('/me')
    expect(resolveRedirectTarget('\\\\evil.example')).toBe('/me')
    expect(resolveRedirectTarget('/%5Cevil.example')).toBe('/me')
  })

  it('拒绝控制字符', () => {
    expect(resolveRedirectTarget('/me\nx', '/')).toBe('/')
    expect(resolveRedirectTarget('/me\u0000x', '/')).toBe('/')
    expect(resolveRedirectTarget('/me%00x', '/')).toBe('/')
    expect(resolveRedirectTarget('/\t/me', '/')).toBe('/')
  })

  it('拒绝相对路径、非字符串与空值', () => {
    expect(resolveRedirectTarget('me', '/')).toBe('/')
    expect(resolveRedirectTarget('', '/')).toBe('/')
    expect(resolveRedirectTarget(undefined, '/')).toBe('/')
    expect(resolveRedirectTarget(null, '/')).toBe('/')
    expect(resolveRedirectTarget(['/me'], '/')).toBe('/')
    expect(resolveRedirectTarget({ path: '/me' }, '/')).toBe('/')
  })

  it('拒绝回到登录或注册流程', () => {
    expect(resolveRedirectTarget('/login')).toBe('/me')
    expect(resolveRedirectTarget('/register?from=home')).toBe('/me')
  })
})
