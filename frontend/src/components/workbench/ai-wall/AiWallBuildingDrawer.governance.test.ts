import { describe, expect, it } from 'vitest'
import source from './AiWallBuildingDrawer.vue?raw'

describe('AI 楼栋治理抽屉', () => {
  it('同时展示正式风险、AI 关注、治理优先级和数据时效，避免混淆事实来源', () => {
    for (const label of ['正式风险', 'AI 关注', '治理优先级', '数据时效']) {
      expect(source).toContain(label)
    }
    expect(source).toContain('riskScore')
    expect(source).toContain('freshnessLabel')
  })

  it('明确展示人工复核状态、证据数量与下一步建议', () => {
    expect(source).toContain('人工复核')
    expect(source).toContain('分析依据')
    expect(source).toContain('下一步建议')
    expect(source).toContain('governanceSuggestion')
  })

  it('跨页操作只保留一个明确的楼栋详情入口', () => {
    expect(source).toContain('进入楼栋详情')
    expect(source).not.toContain('查看 AI 研判</button>')
    expect(source).not.toContain('进入楼栋</button>')
  })
})
