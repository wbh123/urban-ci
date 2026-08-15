import { apiGet, apiPost } from '../client'

// ---- Types ----

export type AiInferenceStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'REJECTED' | 'CANCELLED'
export type AiInferenceMode = 'MOCK' | 'REAL'
export type AiInferenceProfile = 'FAST' | 'PRECISION' | 'ACCURACY'
export type AiInferenceTriggerType = 'UPLOAD_AUTO' | 'MANUAL_SINGLE' | 'MANUAL_BATCH'
export type AiProviderCode = 'FAST_API' | 'DIFY' | 'SPRING_AI'
export type AiCapabilityType = 'VISION_INFERENCE' | 'WORKFLOW' | 'TEXT_GENERATION'
export type AiReviewStatus = 'UNREVIEWED' | 'CONFIRMED' | 'CORRECTED' | 'REJECTED'
export type AiReviewDecision = Exclude<AiReviewStatus, 'UNREVIEWED'>
export type AiReviewedRiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'VERY_HIGH'
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

export type AiExecutionStatus = 'PENDING' | 'READY' | 'RUNNING' | 'RETRY_WAIT' | 'SUCCEEDED' | 'FAILED' | 'REJECTED' | 'CANCELLED'

export interface AiInferenceExecution {
  taskId: string
  assetId?: string | null
  status: AiExecutionStatus
  inferenceId?: string | null
  attemptCount?: number
  maxAttempts?: number
  errorCode?: string | null
  errorMessage?: string | null
  createdAt?: string | null
  startedAt?: string | null
  finishedAt?: string | null
  updatedAt?: string | null
}

export interface AiAsyncInferenceSubmission {
  taskId: string
  status: 'PENDING'
  inferenceProfile: 'ACCURACY'
  triggerType?: AiInferenceTriggerType
  assetId: string
}

export interface AiDetectionBox {
  x: number
  y: number
  width: number
  height: number
  coordinateType: 'NORMALIZED_XYWH'
}

export interface AiSegmentation {
  type: 'POLYGON'
  points: number[][]
}

export type AiDetectionTrustLevel = 'HIGH' | 'MEDIUM' | 'LOW'

export interface AiDetection {
  sequence: number
  classCode: string
  className: string
  confidence: number
  boundingBox: AiDetectionBox
  segmentation?: AiSegmentation | null
  trustLevel?: AiDetectionTrustLevel | null
  trustReasons?: string[]
  diagnostics?: Record<string, unknown>
}

export interface AiStructuredDetection {
  classCode?: string
  className?: string
  confidence?: number | null
  boundingBox?: AiDetectionBox | null
  segmentation?: AiSegmentation | null
  trustLevel?: AiDetectionTrustLevel | null
  trustReasons?: string[]
  diagnostics?: Record<string, unknown>
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
  latestReview?: {
    reviewStatus: string
    comment?: string
    reviewedBy?: string
    reviewedAt?: string
    correctedData?: { reviewedRiskLevel?: AiReviewedRiskLevel }
  } | null
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
  inferenceProfile?: AiInferenceProfile
  triggerType?: AiInferenceTriggerType
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
  inspectionTaskId?: string
  inspectionRecordId?: string
  buildingId?: string
  communityId?: string
}

// ---- API Functions ----

export function listAiModels(): Promise<{ content: AiModelCatalogItem[] }> {
  return apiGet('/api/v1/ai-models')
}

async function completeInferencePayload(payload: CreateAiInferenceRequest): Promise<CreateAiInferenceRequest> {
  if (payload.providerCode && payload.capabilityType) return payload
  const catalog = await listAiModels()
  const selectedModel = catalog.content?.find((model) => model.modelId === payload.modelId)
  return {
    ...payload,
    providerCode: payload.providerCode ?? selectedModel?.providerCode,
    capabilityType: payload.capabilityType ?? selectedModel?.capabilityType,
  }
}

export async function createAiInference(
  payload: CreateAiInferenceRequest,
): Promise<AiInferenceTask> {
  let effectivePayload = await completeInferencePayload(payload)
  if (
    effectivePayload.mode === 'REAL'
    && effectivePayload.providerCode === 'FAST_API'
    && effectivePayload.capabilityType === 'VISION_INFERENCE'
    && !effectivePayload.inferenceProfile
  ) {
    effectivePayload = { ...effectivePayload, inferenceProfile: 'PRECISION' }
  }
  return apiPost<AiInferenceTask>('/api/v1/ai-inferences', effectivePayload)
}

export async function createAiAccuracyExecution(
  payload: Omit<CreateAiInferenceRequest, 'inferenceProfile'>,
): Promise<AiAsyncInferenceSubmission> {
  const effectivePayload = await completeInferencePayload({ ...payload, inferenceProfile: 'ACCURACY' })
  return apiPost<AiAsyncInferenceSubmission>('/api/v1/ai-inferences', effectivePayload)
}

export function getAiInferenceExecution(taskId: string): Promise<AiInferenceExecution> {
  return apiGet<AiInferenceExecution>(`/api/v1/ai-inference-executions/${taskId}`)
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
  return apiPost<AiInferenceTask>(`/api/v1/ai-inferences/${inferenceId}/retry`, modelId ? { modelId } : {})
}

export function submitAiReview(
  inferenceId: string,
  reviewStatus: AiReviewStatus,
  comment?: string,
  correctedData?: { reviewedRiskLevel?: AiReviewedRiskLevel },
): Promise<unknown> {
  if (reviewStatus === 'UNREVIEWED') {
    return Promise.reject(new TypeError('UNREVIEWED 不是可提交的复核结论'))
  }
  const decision: AiReviewDecision = reviewStatus
  return apiPost(`/api/v1/ai-inferences/${inferenceId}/review`, {
    reviewStatus: decision,
    comment,
    correctedData,
  })
}
