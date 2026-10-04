// 品牌色板工具（等价抽取自 AgentGenerate.vue，行为不变）

export const PRESET_COLORS = [
  '#FF6B35',
  '#1A1A2E',
  '#E94560',
  '#2563EB',
  '#16A34A',
  '#B8860B',
  '#111827',
  '#F5F5F5'
]

export function normalizeColor(val) {
  return (val || '').trim().toUpperCase()
}
