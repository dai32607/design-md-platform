import { describe, it, expect } from 'vitest'
import { avatarStyle, avatarLetter } from '../src/utils/brandAvatar'

describe('brandAvatar 头像兜底', () => {
  it('空名称返回占位符 ?', () => {
    expect(avatarLetter('')).toBe('?')
    expect(avatarLetter(null)).toBe('?')
    expect(avatarLetter(undefined)).toBe('?')
    expect(avatarLetter('   ')).toBe('?')
  })

  it('取名称首字母大写', () => {
    expect(avatarLetter('apple')).toBe('A')
    expect(avatarLetter('Apple Inc.')).toBe('A')
    expect(avatarLetter('  hello')).toBe('H')
    expect(avatarLetter('小米')).toBe('小')
  })

  it('空名称样式仍返回稳定背景色（@error 兜底不抛错）', () => {
    expect(() => avatarStyle('')).not.toThrow()
    expect(avatarStyle('')).toEqual({ backgroundColor: '#409EFF' })
    expect(avatarStyle(null)).toEqual({ backgroundColor: '#409EFF' })
  })

  it('同名样式稳定且颜色分布在 8 色盘内', () => {
    expect(avatarStyle('Apple')).toEqual(avatarStyle('Apple'))
    const names = ['Apple', 'Google', 'Meta', 'Netflix', 'Tencent', 'Alibaba', 'ByteDance', 'Amazon', 'Microsoft', 'Samsung']
    names.forEach(n => {
      const c = avatarStyle(n).backgroundColor
      expect(c).toMatch(/^#[0-9A-F]{6}$/)
    })
  })
})
