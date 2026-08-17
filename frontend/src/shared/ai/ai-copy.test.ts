import { describe, expect, it } from 'vitest'
import { AI_COPY, translateAiBusinessError } from './ai-copy'

describe('AI 统一文案', () => {
  it('固定全平台 AI 能力名称', () => {
    expect(AI_COPY.vision).toBe('AI 视觉识别')
    expect(AI_COPY.analysis).toBe('AI 综合研判')
    expect(AI_COPY.riskExplanation).toBe('AI 风险解读')
    expect(AI_COPY.inspectionSummary).toBe('AI 巡检摘要')
    expect(AI_COPY.archiveHint).toBe('AI 档案提示')
    expect(AI_COPY.review).toBe('AI 人工复核')
    expect(AI_COPY.assistant).toBe('AI 治理助手')
  })

  it('普通业务页面不直接展示 Provider 配置错误码', () => {
    expect(translateAiBusinessError('AI_PROVIDER_NOT_CONFIGURED')).toBe('智能工作流不可用，已使用本地高精度模型')
    expect(translateAiBusinessError('AI_PROVIDER_UNAVAILABLE')).toBe('智能服务暂时不可用，基础业务不受影响')
    expect(translateAiBusinessError('AI_PROVIDER_INSUFFICIENT_BALANCE')).toBe('DeepSeek 账户余额不足，已降级展示本地结构化证据')
    expect(translateAiBusinessError('SOME_INTERNAL_CODE')).toBe('AI 辅助能力暂时不可用，基础业务不受影响')
  })
})
