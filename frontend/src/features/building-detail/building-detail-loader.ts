import {
  buildBuildingLifecycle,
  type AnalysisLifecycleItem,
  type AssessmentLifecycleSnapshot,
  type BuildingBusinessSnapshot,
  type BuildingEvidenceGalleryItem,
  type BuildingLifecycleNode,
  type BuildingRiskSummaryView,
  type BuildingSummaryView,
  type InspectionLifecycleItem,
  type ReportLifecycleItem,
} from '@/shared/components/business'

export type BuildingDetailWarningDomain =
  | 'COMMUNITY'
  | 'SPATIAL'
  | 'INSPECTION'
  | 'ANALYSIS'
  | 'ASSESSMENT'
  | 'REPORT'

export interface BuildingDetailWarning {
  domain: BuildingDetailWarningDomain
  message: string
}

export interface BuildingDetailBuilding {
  id: string
  communityId?: string
  buildingCode?: string
  buildingName?: string
  address?: string
  constructionYear?: number
  floorCount?: number
  residentCount?: number
}

export interface BuildingDetailCommunity {
  id?: string
  communityName?: string
}

export interface BuildingDetailBoundary {
  status?: 'VERIFIED' | 'UNVERIFIED' | 'REJECTED' | string
}

export interface BuildingDetailInspectionTask {
  taskId?: string
  status?: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED' | string
  updatedAt?: string
}

export interface BuildingDetailAiTask {
  inferenceId?: string
  assetId?: string
  status?: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'REJECTED' | 'CANCELLED' | string
  reviewStatus?: 'UNREVIEWED' | 'CONFIRMED' | 'CORRECTED' | 'REJECTED' | string
  evidenceReliability?: string
  structuredResult?: { summary?: string } | null
  completedAt?: string
  updatedAt?: string
  createdAt?: string
}

export interface BuildingDetailAssessment {
  freshness?: 'CURRENT' | 'STALE' | 'NO_RESULT'
  completeness?: { completenessScore?: number }
  risk?: {
    riskScore?: number
    riskLevel?: string
    confidenceScore?: number
    needManualReview?: boolean
    recommendations?: string[]
    assessedAt?: string
  }
  renewalPriorities?: Array<{
    priorityScore?: number
    priorityLevel?: string
    generatedAt?: string
  }>
}

export interface BuildingDetailReport {
  reportId?: string
  reportStatus?: 'GENERATING' | 'GENERATED' | 'FAILED' | 'STALE' | string
  generatedAt?: string
  createdAt?: string
}

export interface BuildingDetailSources {
  getBuilding(buildingId: string): Promise<BuildingDetailBuilding>
  getCommunity(communityId: string): Promise<BuildingDetailCommunity>
  getBuildingBoundary(buildingId: string): Promise<BuildingDetailBoundary | null | undefined>
  listInspectionTasks(params: { buildingId: string }): Promise<BuildingDetailInspectionTask[]>
  listAiInferences(params: { buildingId: string; page: number; size: number }): Promise<{
    content?: BuildingDetailAiTask[]
  }>
  getCurrentBuildingAssessment(buildingId: string): Promise<BuildingDetailAssessment>
  listRiskReports(params: { buildingId: string; page: number; size: number }): Promise<{
    content?: BuildingDetailReport[]
  }>
}

export interface BuildingDetailModel {
  summary: BuildingSummaryView
  lifecycle: BuildingLifecycleNode[]
  risk: BuildingRiskSummaryView
  evidence: BuildingEvidenceGalleryItem[]
  warnings: BuildingDetailWarning[]
}

export async function loadBuildingDetail(
  buildingId: string,
  sources: BuildingDetailSources,
): Promise<BuildingDetailModel> {
  const building = await sources.getBuilding(buildingId)
  const warnings: BuildingDetailWarning[] = []

  const communityPromise = building.communityId
    ? settleDomain('COMMUNITY', () => sources.getCommunity(building.communityId as string), warnings)
    : Promise.resolve<BuildingDetailCommunity | undefined>(undefined)
  const spatialPromise = settleDomain('SPATIAL', () => sources.getBuildingBoundary(buildingId), warnings)
  const inspectionPromise = settleDomain('INSPECTION', () => sources.listInspectionTasks({ buildingId }), warnings)
  const analysisPromise = settleDomain('ANALYSIS', () => sources.listAiInferences({ buildingId, page: 0, size: 50 }), warnings)
  const assessmentPromise = settleDomain('ASSESSMENT', () => sources.getCurrentBuildingAssessment(buildingId), warnings)
  const reportPromise = settleDomain('REPORT', () => sources.listRiskReports({ buildingId, page: 0, size: 20 }), warnings)

  const [community, boundary, inspectionResult, analysisResult, assessment, reportResult] = await Promise.all([
    communityPromise,
    spatialPromise,
    inspectionPromise,
    analysisPromise,
    assessmentPromise,
    reportPromise,
  ])

  const inspections = normaliseInspections(inspectionResult ?? [])
  const analyses = normaliseAnalyses(analysisResult?.content ?? [])
  const reports = normaliseReports(reportResult?.content ?? [])
  const assessmentSnapshot = normaliseAssessmentLifecycle(assessment)

  const snapshot: BuildingBusinessSnapshot = {
    building: {
      id: building.id,
      code: building.buildingCode || building.id,
      name: building.buildingName || building.buildingCode || '未命名楼栋',
      communityName: community?.communityName || '所属小区',
      address: building.address,
      constructionYear: building.constructionYear,
      floorCount: building.floorCount,
      residentCount: building.residentCount,
    },
    inspections,
    analyses,
    assessment: assessmentSnapshot,
    reports,
  }

  return {
    summary: {
      buildingId: building.id,
      buildingCode: building.buildingCode || building.id,
      buildingName: building.buildingName || building.buildingCode || '未命名楼栋',
      communityName: community?.communityName || '所属小区',
      address: building.address,
      constructionYear: building.constructionYear,
      floorCount: building.floorCount,
      residentCount: building.residentCount,
      spatialStatus: normaliseSpatialStatus(boundary?.status),
    },
    lifecycle: buildBuildingLifecycle(snapshot),
    risk: normaliseRisk(assessment),
    evidence: normaliseEvidence(analysisResult?.content ?? []),
    warnings,
  }
}

async function settleDomain<T>(
  domain: BuildingDetailWarningDomain,
  loader: () => Promise<T>,
  warnings: BuildingDetailWarning[],
): Promise<T | undefined> {
  try {
    return await loader()
  } catch (error) {
    warnings.push({
      domain,
      message: error instanceof Error ? error.message : String(error),
    })
    return undefined
  }
}

function normaliseSpatialStatus(status?: string): BuildingSummaryView['spatialStatus'] {
  if (status === 'VERIFIED' || status === 'UNVERIFIED' || status === 'REJECTED') return status
  return 'NONE'
}

function normaliseInspections(items: BuildingDetailInspectionTask[]): InspectionLifecycleItem[] {
  return items.map((item) => ({
    status: normaliseInspectionStatus(item.status),
    updatedAt: item.updatedAt,
  }))
}

function normaliseInspectionStatus(status?: string): InspectionLifecycleItem['status'] {
  if (status === 'IN_PROGRESS' || status === 'COMPLETED' || status === 'CANCELLED') return status
  return 'PENDING'
}

function normaliseAnalyses(items: BuildingDetailAiTask[]): AnalysisLifecycleItem[] {
  return items.map((item) => ({
    status: normaliseAnalysisStatus(item.status),
    reviewStatus: normaliseReviewStatus(item.reviewStatus),
    updatedAt: aiTimestamp(item),
  }))
}

function normaliseAnalysisStatus(status?: string): AnalysisLifecycleItem['status'] {
  if (status === 'RUNNING' || status === 'SUCCEEDED' || status === 'FAILED' || status === 'REJECTED' || status === 'CANCELLED') {
    return status
  }
  return 'PENDING'
}

function normaliseReviewStatus(status?: string): AnalysisLifecycleItem['reviewStatus'] {
  if (status === 'CONFIRMED' || status === 'CORRECTED' || status === 'REJECTED') return status
  return 'UNREVIEWED'
}

function normaliseAssessmentLifecycle(assessment?: BuildingDetailAssessment): AssessmentLifecycleSnapshot {
  if (!assessment || assessment.freshness === 'NO_RESULT' || !assessment.freshness) return { freshness: 'NO_RESULT' }
  return {
    freshness: assessment.freshness,
    needManualReview: assessment.risk?.needManualReview,
    updatedAt: assessment.risk?.assessedAt,
  }
}

function normaliseReports(items: BuildingDetailReport[]): ReportLifecycleItem[] {
  return items.map((item) => ({
    reportStatus: normaliseReportStatus(item.reportStatus),
    updatedAt: item.generatedAt || item.createdAt,
  }))
}

function normaliseReportStatus(status?: string): ReportLifecycleItem['reportStatus'] {
  if (status === 'GENERATING' || status === 'FAILED' || status === 'STALE') return status
  return 'GENERATED'
}

function normaliseRisk(assessment?: BuildingDetailAssessment): BuildingRiskSummaryView {
  if (!assessment || assessment.freshness === 'NO_RESULT' || !assessment.freshness) return { freshness: 'NO_RESULT' }

  const priority = [...(assessment.renewalPriorities ?? [])]
    .sort((left, right) => timestamp(right.generatedAt) - timestamp(left.generatedAt))[0]

  return {
    freshness: assessment.freshness,
    riskScore: assessment.risk?.riskScore,
    riskLevel: assessment.risk?.riskLevel,
    confidenceScore: assessment.risk?.confidenceScore,
    completenessScore: assessment.completeness?.completenessScore,
    priorityScore: priority?.priorityScore,
    priorityLevel: priority?.priorityLevel,
    needManualReview: assessment.risk?.needManualReview,
    recommendations: assessment.risk?.recommendations,
    assessedAt: assessment.risk?.assessedAt,
  }
}

function normaliseEvidence(items: BuildingDetailAiTask[]): BuildingEvidenceGalleryItem[] {
  const newestByAsset = new Map<string, BuildingDetailAiTask>()

  for (const item of items) {
    if (!item.assetId) continue
    const current = newestByAsset.get(item.assetId)
    if (!current || timestamp(aiTimestamp(item)) >= timestamp(aiTimestamp(current))) {
      newestByAsset.set(item.assetId, item)
    }
  }

  return [...newestByAsset.entries()]
    .sort(([, left], [, right]) => timestamp(aiTimestamp(right)) - timestamp(aiTimestamp(left)))
    .map(([assetId, item]) => ({
      id: assetId,
      title: item.structuredResult?.summary || `现场证据 ${assetId}`,
      sourceLabel: '现场巡检',
      reviewStatus: item.reviewStatus || 'UNREVIEWED',
      reliabilityLabel: reliabilityLabel(item.evidenceReliability),
      aiAssisted: true,
      capturedAt: aiTimestamp(item),
    }))
}

function reliabilityLabel(value?: string): string {
  if (value === 'PROFESSIONAL_REVIEWED') return '人工已复核'
  if (value === 'HUMAN_REJECTED') return '人工已排除'
  if (value === 'MODEL_UNREVIEWED') return '待人工复核'
  if (value === 'NOT_USABLE') return '不可用于正式评分'
  return '来源待核验'
}

function aiTimestamp(item: BuildingDetailAiTask): string | undefined {
  return item.completedAt || item.updatedAt || item.createdAt
}

function timestamp(value?: string): number {
  if (!value) return 0
  const parsed = Date.parse(value)
  return Number.isNaN(parsed) ? 0 : parsed
}
