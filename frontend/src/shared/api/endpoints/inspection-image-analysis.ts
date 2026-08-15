import { apiGet, apiPost } from '../client'
import type { AiInferenceExecution, AiInferenceTask } from './ai-inference'

export type InspectionImageAnalysisTrigger = 'UPLOAD_AUTO' | 'MANUAL_SINGLE' | 'MANUAL_BATCH'
export type InspectionImageAnalysisStatus = 'NOT_ANALYZED' | 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'

export interface InspectionImageAnalysisSubmission {
  taskId: string
  status: 'PENDING'
  inferenceProfile: 'ACCURACY'
  triggerType?: InspectionImageAnalysisTrigger
  assetId: string
}

export interface InspectionImageExecution extends AiInferenceExecution {
  triggerType?: InspectionImageAnalysisTrigger | null
  preferredProvider?: string | null
  actualProvider?: string | null
  orchestrationMode?: 'DIFY_PREFERRED' | 'SPRING_AI_LOCAL' | string | null
  fallback?: boolean
  fallbackReason?: string | null
}

export interface InspectionTaskInferencePage {
  content: AiInferenceTask[]
  page: { page: number; size: number; totalElements: number; totalPages: number }
}

export function submitInspectionImageAnalysis(
  assetId: string,
  triggerType: Exclude<InspectionImageAnalysisTrigger, 'UPLOAD_AUTO'>,
): Promise<InspectionImageAnalysisSubmission> {
  return apiPost<InspectionImageAnalysisSubmission>('/api/v1/ai-inferences', {
    assetId,
    mode: 'REAL',
    modelId: 'AI-VISION-LOCAL-001',
    providerCode: 'FAST_API',
    capabilityType: 'VISION_INFERENCE',
    inferenceProfile: 'ACCURACY',
    triggerType,
    idempotencyKey: `inspection-${triggerType.toLowerCase()}-${assetId}-${Date.now()}`,
  })
}

export function getInspectionImageExecution(taskId: string): Promise<InspectionImageExecution> {
  return apiGet<InspectionImageExecution>(
    `/api/v1/ai-inference-executions/${encodeURIComponent(taskId)}`,
  )
}

export function listInspectionTaskExecutions(taskId: string): Promise<InspectionImageExecution[]> {
  return apiGet<InspectionImageExecution[]>('/api/v1/ai-inference-executions', {
    inspectionTaskId: taskId,
  })
}

export function listInspectionTaskInferences(taskId: string): Promise<InspectionTaskInferencePage> {
  return apiGet<InspectionTaskInferencePage>('/api/v1/ai-inferences', {
    inspectionTaskId: taskId,
    page: 0,
    size: 100,
  })
}

export async function getInspectionImageRichResult(inferenceId: string): Promise<AiInferenceTask> {
  const result = await apiGet<AiInferenceTask>(
    `/api/v1/ai-inferences/${encodeURIComponent(inferenceId)}/rich-result`,
  )
  const drawable = (result.detections ?? []).filter((detection) => Boolean(detection.boundingBox))
  if (drawable.length && result.structuredResult) {
    return {
      ...result,
      structuredResult: {
        ...result.structuredResult,
        detections: drawable,
        modelVersion: result.modelVersion || result.structuredResult.modelVersion,
      },
    }
  }
  return result
}

export function mapInferenceToImageStatus(
  status: AiInferenceTask['status'] | AiInferenceExecution['status'] | null | undefined,
): InspectionImageAnalysisStatus {
  if (!status) return 'NOT_ANALYZED'
  if (status === 'PENDING' || status === 'READY' || status === 'RETRY_WAIT') return 'QUEUED'
  if (status === 'RUNNING') return 'RUNNING'
  if (status === 'SUCCEEDED' || status === 'REJECTED') return 'SUCCEEDED'
  return 'FAILED'
}
