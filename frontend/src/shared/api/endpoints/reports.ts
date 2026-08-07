import { apiGet, apiPost, httpClient } from '../client'

export type RiskScopeType = 'ALL' | 'REGION' | 'COMMUNITY'

export interface DistributionBucket {
  code: string
  label: string
  count: number
}

export interface DashboardBuilding {
  buildingId: string
  buildingCode: string
  buildingName: string
  communityId: string
  communityName: string
  longitude?: number
  latitude?: number
  riskScore?: number
  riskLevel?: string
  confidenceScore?: number
  completenessScore?: number
  priorityScore?: number
  priorityLevel?: string
  ranking?: number
  freshness: 'CURRENT' | 'STALE' | 'NO_RESULT'
  needManualReview?: boolean
}

export interface RiskOverview {
  scopeKey: string
  generatedAt: string
  summary: {
    communityCount: number
    buildingCount: number
    assessedBuildingCount: number
    highRiskCount: number
    lowConfidenceCount: number
    highPriorityCount: number
    staleCount: number
    noResultCount: number
  }
  riskDistribution: DistributionBucket[]
  completenessDistribution: DistributionBucket[]
  priorityDistribution: DistributionBucket[]
  freshnessDistribution: DistributionBucket[]
  topRiskBuildings: DashboardBuilding[]
  topPriorityBuildings: DashboardBuilding[]
  reviewRequiredBuildings: DashboardBuilding[]
  disclaimer: string
}

export interface RiskMapResponse {
  scopeKey: string
  generatedAt: string
  buildings: DashboardBuilding[]
  disclaimer: string
}

export interface RiskReportRow {
  reportId: string
  reportCode: string
  buildingId: string
  buildingCode: string
  buildingName: string
  communityId: string
  communityName: string
  reportStatus: 'GENERATING' | 'GENERATED' | 'FAILED' | 'STALE'
  reportFormat: 'PDF'
  templateVersion: string
  sourceChecksum: string
  riskLevel?: string
  priorityLevel?: string
  generatedAt?: string
  createdAt: string
}

export interface RiskReportPage {
  content: RiskReportRow[]
  page: { page: number; size: number; totalElements: number; totalPages: number }
}

export interface RiskReportPreview {
  buildingId: string
  buildingCode: string
  buildingName: string
  communityName: string
  freshness: 'CURRENT' | 'STALE' | 'NO_RESULT'
  sourceChecksum: string
  templateVersion: string
  sections: Record<string, unknown>
  warnings: string[]
  disclaimer: string
}

export interface RiskReportGeneration {
  reportId: string
  reportCode: string
  reportStatus: string
  reportFormat: string
  templateVersion: string
  sourceChecksum: string
  reused: boolean
  generatedAt?: string
  warnings?: string[]
}

export function getRiskOverview(scopeType: RiskScopeType, scopeId?: string): Promise<RiskOverview> {
  return apiGet('/api/v1/dashboard/risk-overview', { scopeType, scopeId })
}

export function getRiskMap(scopeType: RiskScopeType, scopeId?: string): Promise<RiskMapResponse> {
  return apiGet('/api/v1/dashboard/risk-map', { scopeType, scopeId })
}

export function listRiskReports(params: {
  buildingId?: string
  communityId?: string
  status?: string
  page?: number
  size?: number
} = {}): Promise<RiskReportPage> {
  return apiGet('/api/v1/risk-reports', params)
}

export function previewBuildingRiskReport(buildingId: string): Promise<RiskReportPreview> {
  return apiGet(`/api/v1/risk-reports/buildings/${encodeURIComponent(buildingId)}/preview`)
}

export function generateBuildingRiskReport(
  buildingId: string,
  force = false,
): Promise<RiskReportGeneration> {
  return apiPost(`/api/v1/risk-reports/buildings/${encodeURIComponent(buildingId)}/generate`, {
    force,
    includeEvidenceImages: true,
  })
}

export async function downloadRiskReport(reportId: string): Promise<Blob> {
  const response = await httpClient.get<Blob>(
    `/api/v1/risk-reports/${encodeURIComponent(reportId)}/download`,
    { responseType: 'blob' },
  )
  return response.data
}
