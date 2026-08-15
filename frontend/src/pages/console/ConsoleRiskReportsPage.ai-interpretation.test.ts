import { describe, expect, it } from 'vitest'
import source from './ConsoleRiskReportsPage.vue?raw'

describe('P2 risk assessment center AI interpretation', () => {
  it('separates formal deterministic scoring from AI natural-language interpretation', () => {
    expect(source).toContain('风险研判中心')
    expect(source).toContain('正式评分因子')
    expect(source).toContain('AI 风险解读')
    expect(source).toContain('runIntelligentAnalysis')
    expect(source).toContain('不修改正式风险分数')
  })
})
