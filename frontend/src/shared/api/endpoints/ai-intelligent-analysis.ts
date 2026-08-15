import { apiPost } from '../client'

export interface AiIntelligentAnalysisStep {
  seqNo: number
  type: string
  toolName?: string | null
  provider?: string | null
  status: string
  durationMs?: number | null
  errorCode?: string | null
  detail?: string | null
}

export interface AiIntelligentAnalysisResult {
  executionId: string
  status: string
  answer: string
  steps: AiIntelligentAnalysisStep[]
  durationMs: number
  modelCode?: string | null
}

export interface RunIntelligentAnalysisRequest {
  businessType: string
  businessId?: string
  question?: string
  context?: Record<string, unknown>
}

/**
 * 运行 Spring AI 智能综合分析。
 *
 * 后端统一响应外壳由 client.ts 自动剥离，这里只返回 data 部分。
 */
export function runIntelligentAnalysis(
  request: RunIntelligentAnalysisRequest,
): Promise<AiIntelligentAnalysisResult> {
  return apiPost<AiIntelligentAnalysisResult>('/api/v1/ai-intelligent-analysis', request)
}
