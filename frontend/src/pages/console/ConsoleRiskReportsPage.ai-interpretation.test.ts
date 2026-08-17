import { describe, expect, it } from 'vitest'
import source from './ConsoleRiskReportsPage.vue?raw'

describe('P2 risk assessment center AI interpretation', () => {
  it('separates formal deterministic scoring from a full-width structured AI interpretation', () => {
    expect(source).toContain('风险研判中心')
    expect(source).toContain('正式评分因子')
    expect(source).toContain('AI 风险解读')
    expect(source).toContain('AiStructuredAnalysisPanel')
    expect(source).toContain('risk-ai-stage')
    expect(source).toContain('runIntelligentAnalysis')
    expect(source).toContain('不修改正式风险分数')
    expect(source).not.toContain(':summary="interpretationSummary"')
  })
})
