import { describe, expect, it } from 'vitest'
import { aiReviewStatusLabel, aiTaskStatusLabel, aiTaskStatusTone } from './ai-status'

describe('AI 状态业务化语义', () => {
  it('统一推理任务状态文案', () => {
    expect(aiTaskStatusLabel('PENDING')).toBe('等待分析')
    expect(aiTaskStatusLabel('RUNNING')).toBe('分析中')
    expect(aiTaskStatusLabel('SUCCEEDED')).toBe('分析完成')
    expect(aiTaskStatusLabel('FAILED')).toBe('分析失败')
    expect(aiTaskStatusLabel('REJECTED')).toBe('图片不适用')
  })

  it('统一人工复核状态文案', () => {
    expect(aiReviewStatusLabel('UNREVIEWED')).toBe('待人工复核')
    expect(aiReviewStatusLabel('CONFIRMED')).toBe('人工已确认')
    expect(aiReviewStatusLabel('CORRECTED')).toBe('人工已修正')
    expect(aiReviewStatusLabel('REJECTED')).toBe('人工已排除')
  })

  it('提供稳定的状态色调但不暴露底层技术实现', () => {
    expect(aiTaskStatusTone('SUCCEEDED')).toBe('success')
    expect(aiTaskStatusTone('RUNNING')).toBe('warning')
    expect(aiTaskStatusTone('FAILED')).toBe('danger')
  })
})
