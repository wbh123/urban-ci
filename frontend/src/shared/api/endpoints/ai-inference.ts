import { apiGet, apiPost } from '../client'

// ---- Types ----

export type AiInferenceStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'REJECTED' | 'CANCELLED'
export type AiInferenceMode = 'MOCK' | 'REAL'
export type AiProviderCode = 'FAST_API' | 'DIFY' | 'SPRING_AI'
export type AiCapabilityType = 'VISION_INFERENCE' | 'WORKFLOW' | 'TEXT_GENERATION'
export type AiReviewStatus = 'UNREVIEWED' | 'CONFIRMED' | 'CORRECTED' | 'REJECTED'
export type AiModelDeploymentStage = 'VALIDATING' | 'DEMO' | 'SHADOW' | 'ACTIVE' | 'SUSPENDED'
export type AiModelQualityStatus = 'UNKNOWN' | 'VALIDATING' | 'PASSED' | 'FAILED'
export type AiAssessmentEligibility = 'DEMO_ONLY' | 'REVIEW_REQUIRED' | 'ELIGIBLE' | 'EXCLUDED'
export type AiEvidenceReliability =
  | 'SIMULATED'
  | 'MODEL_UNREVIEWED'
  | 'PROFESSIONAL_REVIEWED'
  | 'HUMAN_REJECTED'
  | 'NOT_USABLE'
  | 'UNKNOWN_SOURCE'

export interface AiDetectionBox {
  x: number
  y: number
  width: number
  height: number
  coordinateType: 'NORMALIZED_XYWH'
}

export interface AiDetection {
  sequence: number
  classCode: string
  className: string
  confidence: number
  boundingBox: AiDetectionBox
}

export interface AiStructuredDetection {
  classCode?: string
  className?: string
  confidence?: number | null
  boundingBox?: AiDetectionBox | null
}

export interface AiRiskSignal {
  code?: string
  level?: string
  description?: string
  confidence?: number | null
}

export interface AiStructuredResult {
  requestId: string
  providerCode: AiProviderCode
  modelCode: string
  modelVersion?: string | null
  capabilityType: AiCapabilityType
  status: 'SUCCEEDED' | 'REJECTED'
  summary: string
  detections: AiStructuredDetection[]
  riskSignals: AiRiskSignal[]
  recommendations: string[]
  confidence?: number | null
  warnings: string[]
  rawResponseReference?: string | null
  durationMs: number
}

export interface AiModelCatalogItem {
  modelId: string
  modelName: string
  modelVersion: string
  modelType?: string
  mode: AiInferenceMode
  status: string
  license?: string
  supportedClasses?: unknown
  limitations?: unknown
  deploymentStage: AiModelDeploymentStage
  formalEvidenceEnabled: boolean
  qualityStatus: AiModelQualityStatus
  qualitySummary?: Record<string, unknown>
  qualityEvaluatedAt?: string | null
  providerCode?: AiProviderCode
  capabilityType?: AiCapabilityType
  runtimeReady: boolean
  executionProvider?: string | null
  deviceId?: number | null
  selectable: boolean
  runtimeErrorCode?: string
  runtimeErrorMessage?: string
}

export interface AiInferenceTask {
  inferenceId: string
  requestCode: string
  status: AiInferenceStatus
  mode: AiInferenceMode
  providerCode?: AiProviderCode
  capabilityType?: AiCapabilityType
  modelId: string
  modelName: string
  modelVersion: string
  workflowCode?: string | null
  workflowVersion?: string | null
  license: string
  assetId: string
  inspectionTaskId?: string
  inspectionRecordId?: string
  buildingId?: string
  communityId?: string
  requestedBy?: string
  attemptNo: number
  reviewStatus: AiReviewStatus
  durationMs?: number
  errorCode?: string
  errorMessage?: string
  requestedAt?: string
  startedAt?: string
  completedAt?: string
  createdAt?: string
  imageWidth?: number
  imageHeight?: number
  qualityStatus?: string
  applicability?: string
  summary?: { detectionCount?: number; classCounts?: Record<string, number>; summary?: string }
  structuredResult?: AiStructuredResult | null
  rawResponseReference?: string | null
  warnings?: string[]
  detections?: AiDetection[]
  fallbackUsed?: boolean
  fallbackProviderCode?: string | null
  fallbackReason?: string | null
  latestReview?: { reviewStatus: string; comment?: string; reviewedBy?: string; reviewedAt?: string } | null
  resultAvailable: boolean
  detectionCount: number
  assessmentEligibility: AiAssessmentEligibility
  eligibleForFormalAssessment: boolean
  evidenceReliability: AiEvidenceReliability
  assessmentNote: string
  disclaimer: string
}

export interface CreateAiInferenceRequest {
  assetId: string
  mode: AiInferenceMode
  modelId: string
  providerCode?: AiProviderCode
  capabilityType?: AiCapabilityType
  prompt?: string
  idempotencyKey?: string
}

export interface ListAiInferencesParams {
  page?: number
  size?: number
  status?: string
  mode?: string
  modelId?: string
  providerCode?: AiProviderCode
  capabilityType?: AiCapabilityType
  assetId?: string
  buildingId?: string
}

// ---- API Functions ----

export function listAiModels(): Promise<{ content: AiModelCatalogItem[] }> {
  return apiGet('/api/v1/ai-models')
}

export async function createAiInference(
  payload: CreateAiInferenceRequest,
): Promise<AiInferenceTask> {
  let effectivePayload = payload
  if (!payload.providerCode || !payload.capabilityType) {
    const catalog = await listAiModels()
    const selectedModel = catalog.content?.find((model) => model.modelId === payload.modelId)
    effectivePayload = {
      ...payload,
      providerCode: payload.providerCode ?? selectedModel?.providerCode,
      capabilityType: payload.capabilityType ?? selectedModel?.capabilityType,
    }
  }
  return apiPost<AiInferenceTask>('/api/v1/ai-inferences', effectivePayload)
}

export function getAiInference(inferenceId: string): Promise<AiInferenceTask> {
  return apiGet<AiInferenceTask>(`/api/v1/ai-inferences/${inferenceId}`)
}

export function listAiInferences(params: ListAiInferencesParams = {}): Promise<{
  content: AiInferenceTask[]
  page: { page: number; size: number; totalElements: number; totalPages: number }
}> {
  return apiGet('/api/v1/ai-inferences', params as Record<string, unknown>)
}

export function retryAiInference(inferenceId: string, modelId?: string): Promise<AiInferenceTask> {
  return apiPost<AiInferenceTask>(`/api/v1/ai-inferences/${inferenceId}/retry`, { modelId })
}

export function submitAiReview(
  inferenceId: string,
  reviewStatus: string,
  comment?: string,
): Promise<{ inferenceId: string; reviewStatus: string; reviewedAt: string }> {
  return apiPost(`/api/v1/ai-inferences/${inferenceId}/review`, { reviewStatus, comment })
}
