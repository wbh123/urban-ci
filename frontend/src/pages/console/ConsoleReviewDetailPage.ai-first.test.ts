import { describe, expect, it } from 'vitest'
import source from './ConsoleReviewDetailPage.vue?raw'

describe('P2 AI review detail', () => {
  it('organizes desktop review around AI judgment, evidence and human decision', () => {
    expect(source).toContain('AI判断')
    expect(source).toContain('原始证据')
    expect(source).toContain('人工复核')
    expect(source).toContain('确认')
    expect(source).toContain('修正')
    expect(source).toContain('驳回')
  })

  it('places comprehensive analysis at the top and keeps the empty state compact', () => {
    const analysisStageIndex = source.indexOf('class="analysis-stage')
    const reviewLayoutIndex = source.indexOf('class="review-layout"')
    expect(analysisStageIndex).toBeGreaterThan(-1)
    expect(reviewLayoutIndex).toBeGreaterThan(-1)
    expect(analysisStageIndex).toBeLessThan(reviewLayoutIndex)
    expect(source).toContain('analysis-stage--compact')
    expect(source).toContain('analysis-stage__idle')
    expect(source).toContain('AiStructuredAnalysisPanel')
    expect(source).toContain('本楼栋综合研判')
  })

  it('uses one top-level analysis action and equal-height desktop column bodies', () => {
    expect(source).toContain('review-column__body')
    expect(source).toContain('align-items:stretch')
    expect(source).toContain('刷新综合研判')
    expect(source).toContain('生成综合研判')
  })

  it('automatically starts or restores intelligent analysis after REAL vision succeeds', () => {
    expect(source).toContain('ensureAutoAgentAnalysis')
    expect(source).toContain("runAgentAnalysis('AUTO')")
    expect(source).toContain("mode === 'AUTO'")
    expect(source).toContain('context.sourceInferenceId = task.value.inferenceId')
    expect(source).toContain("next.status === 'SUCCEEDED'")
  })

  it('opens technical details by default and uses more sensitive auxiliary warning thresholds', () => {
    expect(source).toContain('<details open class="technical-details">')
    expect(source).toContain("value.confidence >= 0.55 ? 'HIGH'")
    expect(source).toContain("value.confidence >= 0.25 ? 'MEDIUM'")
  })

  it('uses the shared confidence display rule and hides provider choreography from the main story', () => {
    expect(source).toContain('formatAiConfidence')
    expect(source).not.toContain('Spring AI 编排 DeepSeek、业务工具、本地视觉与 Dify Cloud。')
    expect(source).not.toContain('<p v-if="agentAnalysis">{{ agentAnalysis.answer }}</p>')
  })
})