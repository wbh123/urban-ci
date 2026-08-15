export const AI_COPY = {
  vision: 'AI 视觉识别',
  analysis: 'AI 综合研判',
  riskExplanation: 'AI 风险解读',
  inspectionSummary: 'AI 巡检摘要',
  archiveHint: 'AI 档案提示',
  assistedReview: 'AI 辅助复核',
  review: 'AI 人工复核',
  assistant: 'AI 治理助手',
} as const

const BUSINESS_ERROR_COPY: Record<string, string> = {
  AI_PROVIDER_NOT_CONFIGURED: '智能工作流不可用，已使用本地高精度模型',
  AI_PROVIDER_UNAVAILABLE: '智能服务暂时不可用，基础业务不受影响',
  AI_PROVIDER_AUTH_ERROR: '智能服务暂时不可用，基础业务不受影响',
  AI_PROVIDER_TIMEOUT: '智能服务响应较慢，基础业务不受影响',
}

export function translateAiBusinessError(errorCode?: string | null): string {
  if (!errorCode) return 'AI 辅助能力暂时不可用，基础业务不受影响'
  return BUSINESS_ERROR_COPY[errorCode] ?? 'AI 辅助能力暂时不可用，基础业务不受影响'
}
