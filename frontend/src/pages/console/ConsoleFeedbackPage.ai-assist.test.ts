import { describe, expect, it } from 'vitest'
import source from './ConsoleFeedbackPage.vue?raw'
import endpointSource from '@/shared/api/endpoints/feedback-ai.ts?raw'

describe('ConsoleFeedbackPage AI assist', () => {
  it('adds read-only AI initial triage without replacing manual status handling', () => {
    expect(source).toContain('AI 初步归类')
    expect(source).toContain('getFeedbackAiAssist')
    expect(source).toContain('AiInsightCard')
    expect(source).toContain('不会自动修改反馈状态')
    expect(source).toContain('处理')
    expect(source).not.toContain('modelCode }}</')
  })

  it('surfaces the backend business disclaimer and supports fallback details', () => {
    expect(source).toContain('aiAssist.disclaimer')
    expect(endpointSource).toContain('fallback?: boolean')
    expect(endpointSource).toContain('category?: string')
    expect(endpointSource).toContain('relatedObject?: string')
    expect(endpointSource).toContain('recommendedAction?: string')
    expect(endpointSource).toContain('basis?: string')
    expect(endpointSource).toContain('基础规则辅助归类')
  })
})
