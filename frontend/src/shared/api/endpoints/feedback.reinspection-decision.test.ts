import { describe, expect, it } from 'vitest'
import source from './feedback.ts?raw'

describe('feedback reinspection decision api contract', () => {
  it('exposes the recommendation, human decision and waiver endpoints', () => {
    expect(source).toContain("export type FeedbackReinspectionDecision = 'REQUIRED' | 'WAIVED'")
    expect(source).toContain('getFeedbackReinspectionRecommendation')
    expect(source).toContain('/reinspection/recommendation')
    expect(source).toContain('reinspectionDecision?: FeedbackReinspectionDecision')
    expect(source).toContain('decisionReason?: string')
    expect(source).toContain('waiveFeedbackReinspection')
    expect(source).toContain('/reinspection/waive')
  })

  it('returns auditable decision metadata without claiming a formal risk change', () => {
    expect(source).toContain('recommendedDecision: FeedbackReinspectionDecision')
    expect(source).toContain('manualOverride?: boolean')
    expect(source).toContain('reinspectionDecisionReason?: string')
    expect(source).toContain('formalRiskChanged: false')
  })
})
