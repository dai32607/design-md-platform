// Token 颜色 / 分类解析与 CSS 变量生成工具（等价抽取自 BrandDetail.vue，行为不变）

export const CATEGORY_LABELS = {
  COLORS: '配色',
  TYPOGRAPHY: '字体',
  SPACING: '间距',
  COMPONENTS: '组件'
}

export function categoryLabel(cat) {
  return CATEGORY_LABELS[cat] || cat
}

export function isColorValue(val) {
  return /^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$/.test(val.trim())
}

export function slugify(name) {
  return (
    name
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '') || 'token'
  )
}

export function getTypePreviewStyle(token) {
  const val = token.tokenValue
  const sizeMatch = val.match(/(\d+)(pt|px|dp)/)
  if (sizeMatch) {
    const size = Math.min(Math.max(parseInt(sizeMatch[1]), 10), 48)
    return {
      fontSize: size + 'px',
      fontWeight: val.toLowerCase().includes('bold') ? 700 : 400,
      fontFamily: val.toLowerCase().includes('roboto') ? 'Roboto' : 'inherit'
    }
  }
  return {}
}

// 由 Token 列表生成 CSS 变量文本（原组件内 buildCssVariables() 的纯函数化版本）
export function buildCssVariables(tokens) {
  const CATEGORY_COMMENTS = {
    COLORS: 'COLORS 配色',
    TYPOGRAPHY: 'TYPOGRAPHY 字体',
    SPACING: 'SPACING 间距',
    OTHER: '其他'
  }
  const usedNames = new Set()
  const lines = []

  function uniqueCssName(base) {
    let name = base
    let i = 2
    while (usedNames.has(name)) {
      name = `${base}-${i++}`
    }
    usedNames.add(name)
    return name
  }

  const groups = { COLORS: [], TYPOGRAPHY: [], SPACING: [], OTHER: [] }
  tokens.forEach(token => {
    const value = token.tokenValue.trim()
    if (groups[token.category]) {
      groups[token.category].push({ name: token.tokenName, value })
    } else {
      groups.OTHER.push({ name: token.tokenName, value })
    }
  })

  ;['COLORS', 'TYPOGRAPHY', 'SPACING', 'OTHER'].forEach(cat => {
    const list = groups[cat]
    if (!list.length) return
    lines.push(`  /* ${CATEGORY_COMMENTS[cat]} */`)
    list.forEach(item => {
      const nameLower = item.name.toLowerCase()
      let base
      if (cat === 'COLORS') {
        base = `--color-${slugify(item.name)}`
      } else if (cat === 'TYPOGRAPHY') {
        if (nameLower.includes('line-height')) {
          base = `--line-height-${slugify(item.name.replace(/line[- ]?height/i, ''))}`
        } else if (nameLower.includes('font-size')) {
          base = `--font-size-${slugify(item.name.replace(/font[- ]?size/i, ''))}`
        } else if (nameLower.includes('font-family')) {
          base = `--font-family-${slugify(item.name.replace(/font[- ]?family/i, ''))}`
        } else {
          base = `--font-${slugify(item.name)}`
        }
      } else if (cat === 'SPACING') {
        base = `--spacing-${slugify(item.name)}`
      } else {
        base = `--token-${slugify(item.name)}`
      }
      lines.push(`  ${uniqueCssName(base)}: ${item.value};`)
    })
  })

  return `:root {\n${lines.join('\n')}\n}`
}
