import { apiPost } from '../client'

export interface InspectionAiCombinedSummary {
  mode: 'AI' | 'RULE_FALLBACK'
  fieldDescription: string
  visualFindings: string
  agreement: string
  keyLocations: string
  evidenceGaps: string
  reviewSuggestion: string
  durationMs: number
  modelCode?: string | null
  disclaimer: string
}

/**
 * 将巡检员已填写记录与已经完成的 AI 视觉结果做综合总结。
 * 该接口不会重新触发视觉推理。
 */
export function getInspectionAiCombinedSummary(
  taskId: string,
  inferenceId: string,
): Promise<InspectionAiCombinedSummary> {
  return apiPost<InspectionAiCombinedSummary>(
    `/api/v1/inspection-tasks/${encodeURIComponent(taskId)}/ai-summary`,
    { inferenceId },
  )
}
