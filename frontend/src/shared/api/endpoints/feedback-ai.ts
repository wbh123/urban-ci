import { apiPost } from '../client'

export interface FeedbackAiAssistView {
  reportId: string
  reportCode: string
  communityName?: string
  buildingName?: string
  status: string
  answer: string
  category?: string
  relatedObject?: string
  recommendedAction?: string
  basis?: string
  fallback?: boolean
  durationMs: number
  modelCode?: string | null
  disclaimer: string
}

/**
 * 获取管理端公众反馈 AI 初步归类。
 *
 * 该接口只返回辅助建议，不会修改反馈状态或处理摘要；文本智能不可用时后端会返回基础规则辅助归类。
 */
export function getFeedbackAiAssist(reportId: string): Promise<FeedbackAiAssistView> {
  return apiPost<FeedbackAiAssistView>(
    `/api/v1/feedback/reports/${encodeURIComponent(reportId)}/ai-assist`,
    {},
  )
}
