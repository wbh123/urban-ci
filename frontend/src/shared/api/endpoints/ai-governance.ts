import { apiGet, apiPut } from '../client'

export interface AiProviderMetrics {
  totalTasks: number
  succeededTasks: number
  failedTasks: number
  reviewedTasks: number
  pendingReviewTasks: number
  averageDurationMs: number
  successRate: number
}

export interface AiProviderStatus {
  providerCode: string
  enabled: boolean
  configured: boolean
  configurationStatus: 'DISABLED' | 'NOT_CONFIGURED' | 'CONFIGURED'
  runtimeStatus:
    | 'READY'
    | 'DEGRADED'
    | 'UNCONFIGURED'
    | 'AUTH_ERROR'
    | 'UNAVAILABLE'
    | 'DISABLED'
    | 'UNKNOWN'
  connectivityStatus: string
  capabilities: string[]
  defaultFor: string[]
  metrics7d: AiProviderMetrics
}

export interface AiGovernanceStatus {
  generatedAt: string
  statisticsWindow: 'LAST_7_DAYS'
  providers: AiProviderStatus[]
  total7d: AiProviderMetrics
  unassignedLegacyTasks7d: number
  healthSemantics: string
  disclaimer: string
}

export interface AiAutomationSettings {
  autoInferenceOnUpload: boolean
  intelligentWorkflowEnabled: boolean
  knowledgeQaEnabled: boolean
  modelId: string
  providerCode: 'FAST_API'
  capabilityType: 'VISION_INFERENCE'
  updatedAt?: string | null
}

export interface AiAutomationSettingsUpdate {
  autoInferenceOnUpload: boolean
  intelligentWorkflowEnabled: boolean
  knowledgeQaEnabled: boolean
}

export function getAiGovernanceStatus(): Promise<AiGovernanceStatus> {
  return apiGet<AiGovernanceStatus>('/api/v1/ai-governance/status')
}

export function getAiAutomationSettings(): Promise<AiAutomationSettings> {
  return apiGet<AiAutomationSettings>('/api/v1/ai-governance/automation-settings')
}

export function updateAiAutomationSettings(
  settings: AiAutomationSettingsUpdate,
): Promise<AiAutomationSettings> {
  return apiPut<AiAutomationSettings>('/api/v1/ai-governance/automation-settings', settings)
}
