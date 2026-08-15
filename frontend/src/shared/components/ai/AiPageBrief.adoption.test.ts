import { describe, expect, it } from 'vitest'
import feedbackSource from '@/pages/console/ConsoleFeedbackPage.vue?raw'
import inspectionSource from '@/pages/console/ConsoleInspectionPage.vue?raw'
import reviewSource from '@/pages/console/ConsoleReviewQueuePage.vue?raw'
import riskSource from '@/pages/console/ConsoleRiskReportsPage.vue?raw'

describe('shared page AI brief adoption', () => {
  it('uses the shared brief where page-level aggregate facts exist', () => {
    expect(reviewSource).toContain('AiPageBrief')
    expect(riskSource).toContain('AiPageBrief')
  })

  it('keeps specialized AI boards on feedback and inspection instead of stacking duplicate cards', () => {
    expect(feedbackSource).toContain('AiInsightCard')
    expect(feedbackSource).toContain('AI 初步归类')
    expect(inspectionSource).toContain('AiInspectionCombinedSummary')
    expect(inspectionSource).toContain('AI 巡检综合总结')
  })

  it('risk brief is built from formal overview facts and keeps AI recommendation separate', () => {
    expect(riskSource).toContain('overview.summary.highRiskCount')
    expect(riskSource).toContain('overview.summary.lowConfidenceCount')
    expect(riskSource).toContain('overview.summary.staleCount')
    expect(riskSource).toContain('overview.summary.highPriorityCount')
    expect(riskSource).toContain('不改变正式风险评分')
  })
})
