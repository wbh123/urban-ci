import { describe, expect, it } from 'vitest'
import { AI_CONFIDENCE_PERCENT_THRESHOLD, formatAiDetectionLabel, shouldDisplayAiConfidence } from './ai-display'

describe('AI 检测结果展示规则', () => {
  it('低于 40% 时保留病害名称但隐藏百分比', () => {
    expect(AI_CONFIDENCE_PERCENT_THRESHOLD).toBe(0.4)
    expect(shouldDisplayAiConfidence(0.3999)).toBe(false)
    expect(formatAiDetectionLabel('疑似裂缝', 0.3999)).toBe('疑似裂缝')
  })

  it('达到 40% 后显示病害名称和百分比', () => {
    expect(shouldDisplayAiConfidence(0.4)).toBe(true)
    expect(formatAiDetectionLabel('疑似剥落', 0.4)).toBe('疑似剥落 40%')
    expect(formatAiDetectionLabel('疑似裂缝', 0.876)).toBe('疑似裂缝 88%')
  })

  it('没有置信度时不制造百分比', () => {
    expect(shouldDisplayAiConfidence(undefined)).toBe(false)
    expect(formatAiDetectionLabel('其他表观异常', undefined)).toBe('其他表观异常')
  })

  it('没有病害名称时使用稳定展示兜底', () => {
    expect(formatAiDetectionLabel(undefined, 0.72)).toBe('疑似表观异常 72%')
    expect(formatAiDetectionLabel('', undefined)).toBe('疑似表观异常')
  })
})