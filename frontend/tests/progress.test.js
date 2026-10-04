import { describe, it, expect } from 'vitest'
import {
  MIN_FEEDBACK_MS,
  TICK_MS,
  progressPercentOf,
  phaseTextOf,
  waitTextOf
} from '../src/utils/progress'

describe('AgentGenerate 进度条纯函数', () => {
  it('进度从 8% 起步、按 200ms/1% 增长、封顶 95%', () => {
    expect(progressPercentOf(0)).toBe(8)
    expect(progressPercentOf(199)).toBe(8)
    expect(progressPercentOf(200)).toBe(9)
    expect(progressPercentOf(1000)).toBe(13)
    expect(progressPercentOf(17400)).toBe(95)
    expect(progressPercentOf(99999)).toBe(95)
  })

  it('阶段文案边界：1000 / 5000 / 12000 毫秒', () => {
    expect(phaseTextOf(0)).toContain('连接大模型')
    expect(phaseTextOf(999)).toContain('连接大模型')
    expect(phaseTextOf(1000)).toContain('生成设计规范')
    expect(phaseTextOf(4999)).toContain('生成设计规范')
    expect(phaseTextOf(5000)).toContain('整理输出')
    expect(phaseTextOf(11999)).toContain('整理输出')
    expect(phaseTextOf(12000)).toContain('即将完成')
  })

  it('等待文案按秒取整', () => {
    expect(waitTextOf(0)).toContain('已等待 0 秒')
    expect(waitTextOf(900)).toContain('已等待 0 秒')
    expect(waitTextOf(1000)).toContain('已等待 1 秒')
    expect(waitTextOf(15500)).toContain('已等待 15 秒')
  })

  it('最小反馈时长 900ms 与计时粒度 300ms 边界', () => {
    expect(MIN_FEEDBACK_MS).toBe(900)
    expect(TICK_MS).toBe(300)
    // 补足等待以 300ms 粒度对齐，避免出现非整拍的等待时长
    expect(MIN_FEEDBACK_MS % TICK_MS).toBe(0)
  })
})
