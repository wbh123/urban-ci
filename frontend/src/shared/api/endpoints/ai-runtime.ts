import { apiGet } from '../client'

export interface AiRuntimeServiceSummary {
  key: string
  label: string
  status: string
}

export interface AiRuntimeSummary {
  generatedAt: string
  state: 'READY' | 'DEGRADED' | 'UNAVAILABLE' | 'UNKNOWN'
  services: AiRuntimeServiceSummary[]
  policy: string
}

export function getAiRuntimeSummary(): Promise<AiRuntimeSummary> {
  return apiGet<AiRuntimeSummary>('/api/v1/ai-runtime/summary')
}
