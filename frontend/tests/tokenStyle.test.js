import { describe, it, expect } from 'vitest'
import {
  CATEGORY_LABELS,
  categoryLabel,
  isColorValue,
  slugify,
  getTypePreviewStyle,
  buildCssVariables
} from '../src/utils/tokenStyle'

describe('Token 分类解析', () => {
  it('已知分类映射中文标签', () => {
    expect(CATEGORY_LABELS.COLORS).toBe('配色')
    expect(categoryLabel('COLORS')).toBe('配色')
    expect(categoryLabel('TYPOGRAPHY')).toBe('字体')
    expect(categoryLabel('SPACING')).toBe('间距')
    expect(categoryLabel('COMPONENTS')).toBe('组件')
  })

  it('未知分类原样返回', () => {
    expect(categoryLabel('OTHER')).toBe('OTHER')
    expect(categoryLabel('')).toBe('')
    expect(categoryLabel(null)).toBe(null)
  })
})

describe('Token 颜色解析', () => {
  it('合法 #hex 3/6 位通过（大小写不敏感）', () => {
    expect(isColorValue('#FF6B35')).toBe(true)
    expect(isColorValue('#ff6b35')).toBe(true)
    expect(isColorValue('#FFF')).toBe(true)
    expect(isColorValue('#fff')).toBe(true)
    expect(isColorValue('  #2563EB  ')).toBe(true)
  })

  it('非法色值拒绝', () => {
    expect(isColorValue('')).toBe(false)
    expect(isColorValue('#')).toBe(false)
    expect(isColorValue('#12345')).toBe(false)
    expect(isColorValue('FF6B35')).toBe(false)
    expect(isColorValue('rgb(0,0,0)')).toBe(false)
    expect(isColorValue('#GGGGGG')).toBe(false)
  })
})

describe('字体预览样式', () => {
  it('字号 clamp 到 10-48px', () => {
    expect(getTypePreviewStyle({ tokenValue: '12pt' }).fontSize).toBe('12px')
    expect(getTypePreviewStyle({ tokenValue: '4px' }).fontSize).toBe('10px')
    expect(getTypePreviewStyle({ tokenValue: '72dp' }).fontSize).toBe('48px')
  })

  it('识别 bold 与 roboto', () => {
    const s = getTypePreviewStyle({ tokenValue: '16pt bold Roboto' })
    expect(s.fontWeight).toBe(700)
    expect(s.fontFamily).toBe('Roboto')
  })

  it('无字号信息返回空对象', () => {
    expect(getTypePreviewStyle({ tokenValue: '#333' })).toEqual({})
  })
})

describe('CSS 变量生成', () => {
  it('按分类生成分组注释与变量名', () => {
    const css = buildCssVariables([
      { tokenName: 'Primary', tokenValue: '#2563EB', category: 'COLORS' },
      { tokenName: 'font-size-title', tokenValue: '18pt', category: 'TYPOGRAPHY' },
      { tokenName: 'Gap', tokenValue: '16px', category: 'SPACING' }
    ])
    expect(css).toContain(':root {')
    expect(css).toContain('/* COLORS 配色 */')
    expect(css).toContain('--color-primary: #2563EB;')
    expect(css).toContain('--font-size-title: 18pt;')
    expect(css).toContain('--spacing-gap: 16px;')
  })

  it('未知分类归入其他，重名变量自动加序号去重', () => {
    const css = buildCssVariables([
      { tokenName: 'Foo', tokenValue: '#111', category: 'UNKNOWN' },
      { tokenName: 'Foo', tokenValue: '#222', category: 'UNKNOWN' }
    ])
    expect(css).toContain('/* 其他 */')
    expect(css).toContain('--token-foo: #111;')
    expect(css).toContain('--token-foo-2: #222;')
  })

  it('空 Token 列表生成空 :root', () => {
    expect(buildCssVariables([])).toBe(':root {\n\n}')
  })

  it('slugify 中文/符号归一化', () => {
    expect(slugify('Primary')).toBe('primary')
    expect(slugify('  Hello World  ')).toBe('hello-world')
    expect(slugify('!!!')).toBe('token')
  })
})
