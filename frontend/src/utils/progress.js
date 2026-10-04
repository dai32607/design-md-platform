// 生成过程态反馈：已等待时长 / 阶段文案 / 进度条（等价抽取自 AgentGenerate.vue，行为不变）
// 计时粒度 300ms：mock（百毫秒级）与真实调用（秒级）场景都能看到过程态

// 反馈最小展示时长：mock 分支返回过快时也保证过程态可见（真实调用耗时更长时不受影响）
export const MIN_FEEDBACK_MS = 900
export const TICK_MS = 300

// 起始 8%：避免条宽为 0 时视觉不可见；之后按 200ms/1% 平滑增长，封顶 95%
export function progressPercentOf(ms) {
  return Math.min(95, 8 + Math.floor(ms / 200))
}

export function phaseTextOf(ms) {
  if (ms < 1000) return '正在连接大模型服务…'
  if (ms < 5000) return '大模型正在生成设计规范…'
  if (ms < 12000) return '规范内容较长，正在整理输出…'
  return '仍在生成中，即将完成，请耐心等待…'
}

export function waitTextOf(ms) {
  const s = Math.floor(ms / 1000)
  return `已等待 ${s} 秒 · 预计 10~30 秒，请勿关闭或刷新页面`
}
