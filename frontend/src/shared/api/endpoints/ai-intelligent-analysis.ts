import { apiGet, apiPost } from '../client'

const DEFAULT_POLL_INTERVAL_MS = 1000
const MAX_POLLS = 300

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
  errorCode?: string | null
  errorMessage?: string | null
}

export interface RunIntelligentAnalysisRequest {
  businessType: string
  businessId?: string
  question?: string
  context?: Record<string, unknown>
}

export interface AiIntelligentAnalysisSubmission {
  taskId: string
  status: string
  pollAfterMs?: number
}

export interface AiIntelligentAnalysisTask {
  taskId: string
  status: string
  attemptCount: number
  maxAttempts: number
  executionId?: string | null
  errorCode?: string | null
  errorMessage?: string | null
  result?: AiIntelligentAnalysisResult | null
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

/** 仅提交后台综合研判任务，立即返回 taskId。 */
export function submitIntelligentAnalysis(
  request: RunIntelligentAnalysisRequest,
): Promise<AiIntelligentAnalysisSubmission> {
  return apiPost<AiIntelligentAnalysisSubmission>('/api/v1/ai-intelligent-analysis/tasks', request)
}

/** 查询后台综合研判任务状态。 */
export function getIntelligentAnalysisTask(taskId: string): Promise<AiIntelligentAnalysisTask> {
  return apiGet<AiIntelligentAnalysisTask>(`/api/v1/ai-intelligent-analysis/tasks/${taskId}`)
}

/**
 * 运行 Spring AI 智能综合分析。
 *
 * 对页面保持原有 Promise<AiIntelligentAnalysisResult> 调用契约，但网络链路已改为：
 * 提交持久化任务 -> 短轮询 -> 获取结果。不会再用一个长时间 HTTP 请求等待大模型完成。
 */
export async function runIntelligentAnalysis(
  request: RunIntelligentAnalysisRequest,
): Promise<AiIntelligentAnalysisResult> {
  const submission = await submitIntelligentAnalysis(request)
  const pollInterval = Math.max(500, submission.pollAfterMs ?? DEFAULT_POLL_INTERVAL_MS)

  for (let poll = 0; poll < MAX_POLLS; poll += 1) {
    const task = await getIntelligentAnalysisTask(submission.taskId)
    if (task.status === 'SUCCEEDED') {
      if (!task.result) {
        throw new Error('综合研判任务已完成，但未找到研判结果')
      }
      return task.result
    }
    if (task.status === 'FAILED' || task.status === 'REJECTED' || task.status === 'CANCELLED') {
      throw new Error(task.errorMessage || '综合研判后台任务执行失败')
    }
    await delay(pollInterval)
  }

  throw new Error('综合研判仍在后台运行，请稍后重试或查看执行记录')
}
