import { describe, it, expect } from 'vitest'
import { PRESET_COLORS, normalizeColor } from '../src/utils/palette'

describe('品牌色板工具', () => {
  it('预置色板 8 色且均为合法大写 hex', () => {
    expect(PRESET_COLORS).toHaveLength(8)
    PRESET_COLORS.forEach(c => expect(c).toMatch(/^#[0-9A-F]{6}$/))
  })

  it('normalizeColor 去空格并转大写', () => {
    expect(normalizeColor(' #ff6b35 ')).toBe('#FF6B35')
    expect(normalizeColor('')).toBe('')
    expect(normalizeColor(null)).toBe('')
    expect(normalizeColor(undefined)).toBe('')
  })
})
