import { apiGet } from '../client'

export type AiDashboardAttentionLevel = 'HIGH' | 'MEDIUM' | 'LOW' | 'NONE'
export type AiDashboardLayerMode = 'RISK' | 'AI_DEFECT' | 'AI_ATTENTION' | 'REVIEW' | 'PRIORITY'

export interface AiDashboardFinding {
  classCode: string
  className: string
  count: number
  maxConfidence?: number | null
}

export interface AiDashboardEvidenceCounts {
  visual: number
  inspection: number
  archive: number
  formalRisk: number
}

export interface AiDashboardBuilding {
  buildingId: string
  buildingCode?: string | null
  buildingName: string
  communityId?: string | null
  communityName?: string | null
  longitude?: number | null
  latitude?: number | null
  riskLevel?: string | null
  riskScore?: number | null
  priorityLevel?: string | null
  freshness?: 'CURRENT' | 'STALE' | 'NO_RESULT' | string | null
  needManualReview?: boolean
  aiAttentionLevel: AiDashboardAttentionLevel
  aiAttentionReasons: string[]
  latestAiSummary?: string | null
  latestAiAt?: string | null
  latestInspectionAt?: string | null
  pendingReviewCount: number
  findings: AiDashboardFinding[]
  evidenceCounts: AiDashboardEvidenceCounts
}

export interface AiDashboardOverview {
  generatedAt: string
  metrics: {
    buildingCount: number
    aiAnalyzedBuildingCount: number
    aiAnalyzedImageCount: number
    detectionCount: number
    highRiskCount: number
    pendingReviewCount: number
    inspectionAttentionCount: number
    dataIssueCount: number
    analysisCoverageRate: number
  }
  today: {
    totalAnalyses: number
    succeeded: number
    running: number
    failed: number
    crackCount: number
    spallingCount: number
    waterStainCount: number
    otherDetectionCount: number
  }
  attention: AiDashboardBuilding[]
}

export interface AiDashboardActivityItem {
  id: string
  occurredAt: string
  type: 'AI_ANALYSIS' | 'AI_REVIEW' | 'INSPECTION' | string
  status?: string | null
  buildingId?: string | null
  buildingName?: string | null
  communityName?: string | null
  title: string
  description?: string | null
}

export interface AiDashboardActivity {
  generatedAt: string
  items: AiDashboardActivityItem[]
}

export function getAiDashboardOverview(): Promise<AiDashboardOverview> {
  return apiGet<AiDashboardOverview>('/api/v1/ai-dashboard/overview')
}

export function getAiDashboardActivity(limit = 20): Promise<AiDashboardActivity> {
  return apiGet<AiDashboardActivity>('/api/v1/ai-dashboard/activity', { limit })
}

export function getAiDashboardBuildings(): Promise<AiDashboardBuilding[]> {
  return apiGet<AiDashboardBuilding[]>('/api/v1/ai-dashboard/buildings')
}
