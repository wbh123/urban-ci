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
  connectivityStatus: 'NOT_PROBED'
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
  modelId: string
  providerCode: 'DIFY'
  capabilityType: 'WORKFLOW'
  updatedAt?: string | null
}

export function getAiGovernanceStatus(): Promise<AiGovernanceStatus> {
  return apiGet<AiGovernanceStatus>('/api/v1/ai-governance/status')
}

export function getAiAutomationSettings(): Promise<AiAutomationSettings> {
  return apiGet<AiAutomationSettings>('/api/v1/ai-governance/automation-settings')
}

export function updateAiAutomationSettings(
  autoInferenceOnUpload: boolean,
): Promise<AiAutomationSettings> {
  return apiPut<AiAutomationSettings>('/api/v1/ai-governance/automation-settings', {
    autoInferenceOnUpload,
  })
}
