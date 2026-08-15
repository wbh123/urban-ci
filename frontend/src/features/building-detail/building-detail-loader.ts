import {
  getBuilding,
  getCommunity,
  getCurrentBuildingAssessment,
  listAiInferences,
  listInspectionTasks,
  listRiskReports,
  type AiInferenceTask,
  type BuildingCurrentAssessment,
  type InspectionTask,
  type RiskReportRow,
} from '@/shared/api'
import { getBuildingBoundary } from '@/shared/api/endpoints/spatial'
import {
  buildBuildingLifecycle,
  type BuildingLifecycleNode,
} from '@/shared/components/business/building-lifecycle'
import type {
  BuildingEvidenceGalleryItem,
  BuildingRiskSummaryView,
  BuildingSummaryView,
} from '@/shared/components/business/building-business'

export type BuildingDetailDomain =
  | 'COMMUNITY'
  | 'SPATIAL'
  | 'INSPECTION'
  | 'ANALYSIS'
  | 'ASSESSMENT'
  | 'REPORT'

export interface BuildingDetailWarning {
  domain: BuildingDetailDomain
  message: string
}

export interface BuildingDetailModel {
  summary: BuildingSummaryView
  lifecycle: BuildingLifecycleNode[]
  risk: BuildingRiskSummaryView
  evidence: BuildingEvidenceGalleryItem[]
  inspections: InspectionTask[]
  analyses: AiInferenceTask[]
  assessment: BuildingCurrentAssessment | null
  reports: RiskReportRow[]
  warnings: BuildingDetailWarning[]
}

export interface BuildingDetailSources {
  getBuilding: typeof getBuilding
  getCommunity: typeof getCommunity
  getBuildingBoundary: typeof getBuildingBoundary
  listInspectionTasks: typeof listInspectionTasks
  listAiInferences: typeof listAiInferences
  getCurrentBuildingAssessment: typeof getCurrentBuildingAssessment
  listRiskReports: typeof listRiskReports
}

const defaultSources: BuildingDetailSources = {
  getBuilding,
  getCommunity,
  getBuildingBoundary,
  listInspectionTasks,
  listAiInferences,
  getCurrentBuildingAssessment,
  listRiskReports,
}

interface OptionalResult<T> {
  value: T | null
  warning?: BuildingDetailWarning
}

export async function loadBuildingDetail(
  buildingId: string,
  sources: BuildingDetailSources = defaultSources,
): Promise<BuildingDetailModel> {
  const building = await sources.getBuilding(buildingId)
  const communityId = building.communityId ?? ''

  const [communityResult, boundaryResult, inspectionResult, analysisResult, assessmentResult, reportResult] = await Promise.all([
    communityId
      ? optional('COMMUNITY', () => sources.getCommunity(communityId))
      : Promise.resolve<OptionalResult<Awaited<ReturnType<typeof getCommunity>>>>({
          value: null,
          warning: { domain: 'COMMUNITY', message: '楼栋尚未关联所属小区。' },
        }),
    optional('SPATIAL', () => sources.getBuildingBoundary(buildingId)),
    optional('INSPECTION', () => sources.listInspectionTasks({ buildingId })),
    optional('ANALYSIS', () => sources.listAiInferences({ buildingId, page: 0, size: 50 })),
    optional('ASSESSMENT', () => sources.getCurrentBuildingAssessment(buildingId)),
    optional('REPORT', () => sources.listRiskReports({ buildingId, page: 0, size: 20 })),
  ])

  const community = communityResult.value
  const boundary = boundaryResult.value
  const inspections = inspectionResult.value ?? []
  const analyses = analysisResult.value?.content ?? []
  const assessment = assessmentResult.value
  const reports = reportResult.value?.content ?? []
  const communityName = community?.communityName || assessment?.communityName || '所属小区'

  const summary: BuildingSummaryView = {
    buildingId: building.id || buildingId,
    buildingCode: building.buildingCode || '未设置编码',
    buildingName: building.buildingName || building.buildingCode || '未命名楼栋',
    communityName,
    address: building.address || undefined,
    constructionYear: building.constructionYear ?? undefined,
    floorCount: building.floorCount ?? undefined,
    residentCount: building.residentCount ?? undefined,
    spatialStatus: boundary?.status ?? 'NONE',
  }

  const formalPriority = assessment?.renewalPriorities.find((item) => item.rankingScopeKey === 'ALL')
    ?? assessment?.renewalPriorities[0]
  const risk: BuildingRiskSummaryView = {
    freshness: assessment?.freshness ?? 'NO_RESULT',
    riskScore: assessment?.risk?.riskScore,
    riskLevel: assessment?.risk?.riskLevel,
    confidenceScore: assessment?.risk?.confidenceScore,
    completenessScore: assessment?.completeness?.completenessScore,
    priorityScore: formalPriority?.priorityScore,
    priorityLevel: formalPriority?.priorityLevel,
    needManualReview: assessment?.risk?.needManualReview,
    recommendations: mergeRecommendations(
      assessment?.risk?.recommendations,
      assessment?.completeness?.suggestions,
      formalPriority?.recommendations,
    ),
    assessedAt: assessment?.risk?.assessedAt,
  }

  const lifecycle = buildBuildingLifecycle({
    building: {
      id: summary.buildingId,
      code: summary.buildingCode,
      name: summary.buildingName,
      communityName: summary.communityName,
      address: summary.address,
      constructionYear: summary.constructionYear,
      floorCount: summary.floorCount,
      residentCount: summary.residentCount,
    },
    inspections: inspections.map((item) => ({ status: item.status })),
    analyses: analyses.map((item) => ({
      status: item.status,
      reviewStatus: item.reviewStatus,
      updatedAt: item.completedAt ?? item.createdAt,
    })),
    assessment: {
      freshness: risk.freshness,
      needManualReview: risk.needManualReview,
      updatedAt: risk.assessedAt,
    },
    reports: reports.map((item) => ({
      reportStatus: item.reportStatus,
      updatedAt: item.generatedAt ?? item.createdAt,
    })),
  })

  const warnings = [
    communityResult.warning,
    boundaryResult.warning,
    inspectionResult.warning,
    analysisResult.warning,
    assessmentResult.warning,
    reportResult.warning,
  ].filter((warning): warning is BuildingDetailWarning => Boolean(warning))

  return {
    summary,
    lifecycle,
    risk,
    evidence: buildEvidence(analyses),
    inspections,
    analyses,
    assessment,
    reports,
    warnings,
  }
}

async function optional<T>(
  domain: BuildingDetailDomain,
  load: () => Promise<T>,
): Promise<OptionalResult<T>> {
  try {
    return { value: await load() }
  } catch (error) {
    return {
      value: null,
      warning: {
        domain,
        message: error instanceof Error ? error.message : `${domain} 数据暂时不可用`,
      },
    }
  }
}

function buildEvidence(analyses: AiInferenceTask[]): BuildingEvidenceGalleryItem[] {
  const latestByAsset = new Map<string, AiInferenceTask>()
  for (const task of analyses) {
    if (!task.assetId) continue
    const current = latestByAsset.get(task.assetId)
    if (!current || taskTime(task) >= taskTime(current)) latestByAsset.set(task.assetId, task)
  }

  return [...latestByAsset.values()]
    .sort((left, right) => taskTime(right) - taskTime(left))
    .map((task) => ({
      id: task.assetId,
      title: task.structuredResult?.summary || task.summary?.summary || `现场证据 ${task.assetId.slice(0, 8)}`,
      sourceLabel: '现场证据 · 辅助分析',
      reviewStatus: task.reviewStatus,
      reliabilityLabel: reliabilityLabel(task.evidenceReliability),
      aiAssisted: true,
      capturedAt: task.completedAt ?? task.createdAt,
    }))
}

function taskTime(task: AiInferenceTask): number {
  const value = task.completedAt ?? task.createdAt ?? task.requestedAt
  const parsed = value ? Date.parse(value) : 0
  return Number.isFinite(parsed) ? parsed : 0
}

function reliabilityLabel(value: AiInferenceTask['evidenceReliability']): string {
  return ({
    SIMULATED: '待人工核实',
    MODEL_UNREVIEWED: '辅助分析待复核',
    PROFESSIONAL_REVIEWED: '人工已复核',
    HUMAN_REJECTED: '人工已排除',
    NOT_USABLE: '不可用于正式评分',
    UNKNOWN_SOURCE: '来源待核实',
  } as const)[value] ?? '来源待核实'
}

function mergeRecommendations(...groups: Array<string[] | undefined>): string[] | undefined {
  const merged = [...new Set(groups.flatMap((group) => group ?? []).filter(Boolean))]
  return merged.length ? merged : undefined
}
