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

  it('uses the shared confidence display rule and hides provider choreography from the main story', () => {
    expect(source).toContain('formatAiConfidence')
    expect(source).not.toContain('Spring AI 编排 DeepSeek、业务工具、本地视觉与 Dify Cloud。')
    expect(source).not.toContain('Tool Calling')
  })
})
