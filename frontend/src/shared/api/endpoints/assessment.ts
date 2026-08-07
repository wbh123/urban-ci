import { apiGet, apiPost } from '../client'

export type RuleType = 'COMPLETENESS' | 'RISK' | 'RENEWAL'
export type RuleStatus = 'DRAFT' | 'ACTIVE' | 'RETIRED'
export type AssessmentStatus = 'CURRENT' | 'STALE' | 'SUPERSEDED'
export type RankingScopeType = 'ALL' | 'REGION' | 'COMMUNITY'

export interface ScoreDimension { code: string; label: string; score: number; weight: number; contribution: number; status?: string; evidenceCount?: number }
export interface AssessmentFactor { factorCode: string; label: string; effect: number; direction: 'INCREASE' | 'DECREASE' | 'NEUTRAL' | 'EXCLUDED'; sourceType?: string; sourceId?: string; reliability?: number }
export interface ExcludedEvidence { reason: string; sourceId?: string; sourceType?: string }

export interface CompletenessAssessmentSummary {
  assessmentId: string; completenessScore: number; completenessLevel: 'INSUFFICIENT' | 'LIMITED' | 'GOOD' | 'EXCELLENT'; status: AssessmentStatus
  dimensionScores: ScoreDimension[]; availableItems: string[]; missingItems: string[]; suggestions: string[]
  assessedAt: string; ruleVersion: string; inputChecksum: string; engineVersion: string; triggerType: string; staleReason?: string; calculationBatchId?: string
}
export interface RiskAssessmentSummary {
  assessmentId: string; riskScore: number; riskLevel: 'LOW' | 'MEDIUM' | 'HIGH' | 'VERY_HIGH'; confidenceScore: number; confidenceLevel: 'LOW' | 'MEDIUM' | 'HIGH'; evidenceReliabilityScore: number; status: AssessmentStatus
  dimensionScores: ScoreDimension[]; topFactors: AssessmentFactor[]; excludedEvidence: ExcludedEvidence[]; missingData: string[]; recommendations: string[]
  needManualReview: boolean; needProfessionalInspection: boolean; assessedAt: string; ruleVersion: string; inputChecksum: string; engineVersion: string; triggerType: string; staleReason?: string; disclaimer: string; calculationBatchId?: string
}
export interface RenewalPrioritySummary {
  priorityId: string; priorityScore: number; priorityLevel: 'P1' | 'P2' | 'P3' | 'P4'; ranking?: number; rankingScopeKey: string; status: AssessmentStatus
  factors: ScoreDimension[]; recommendations: string[]; generatedAt: string; ruleVersion: string; inputChecksum: string; engineVersion: string; triggerType: string; staleReason?: string; disclaimer: string; calculationBatchId?: string
}
export interface BuildingCurrentAssessment {
  buildingId: string; buildingCode: string; buildingName: string; communityId: string; communityName: string; freshness: 'CURRENT' | 'STALE' | 'NO_RESULT'
  completeness?: CompletenessAssessmentSummary; risk?: RiskAssessmentSummary; renewalPriorities: RenewalPrioritySummary[]; inputSummary: Record<string, unknown>; disclaimer: string
}

export interface BuildingAssessmentSummary {
  buildingId: string; buildingCode: string; buildingName: string; communityId: string; communityName: string; freshness: 'CURRENT' | 'STALE' | 'NO_RESULT'
  completeness?: Pick<CompletenessAssessmentSummary, 'completenessScore' | 'completenessLevel' | 'missingItems' | 'suggestions'>
  risk?: Pick<RiskAssessmentSummary, 'riskScore' | 'riskLevel' | 'confidenceScore' | 'confidenceLevel' | 'needManualReview' | 'needProfessionalInspection' | 'recommendations'>
  disclaimer: string
}
export interface AssessmentHistoryItem {
  assessmentId: string; assessmentType: RuleType; score: number; secondaryScore?: number; level: string; status: AssessmentStatus; ruleVersion: string; inputChecksum: string; engineVersion: string; triggerType: string; triggeredBy?: string; calculationBatchId: string; calculatedAt: string; staleReason?: string; disclaimer: string
}
export interface AssessmentHistoryPage { content: AssessmentHistoryItem[]; page: { page: number; size: number; totalElements: number; totalPages: number } }
export interface AssessmentRule {
  ruleId: string; ruleType: RuleType; versionCode: string; ruleName: string; checksum: string; status: RuleStatus; ruleContent: Record<string, unknown>; activatedAt?: string; createdBy?: string; createdAt: string; updatedAt: string
}
export interface RenewalPriorityRow {
  ranking: number; buildingId: string; buildingCode: string; buildingName: string; communityId: string; communityName: string
  priorityScore: number; priorityLevel: 'P1' | 'P2' | 'P3' | 'P4'; riskScore: number; riskLevel: 'LOW' | 'MEDIUM' | 'HIGH' | 'VERY_HIGH'; confidenceScore: number
  completenessScore?: number; completenessLevel?: string; residentCount: number; mainReasons: string[]; needManualReview: boolean; needProfessionalInspection: boolean
  rankingScopeKey: string; status: AssessmentStatus; generatedAt: string; disclaimer: string
}
export interface RenewalPriorityPage { scopeKey: string; content: RenewalPriorityRow[]; page: { page: number; size: number; totalElements: number; totalPages: number }; disclaimer: string }


export function getBuildingAssessmentSummary(buildingId: string): Promise<BuildingAssessmentSummary> {
  return apiGet<BuildingAssessmentSummary>(`/api/v1/assessments/buildings/${buildingId}/summary`)
}
export function getCurrentBuildingAssessment(buildingId: string): Promise<BuildingCurrentAssessment> {
  return apiGet<BuildingCurrentAssessment>(`/api/v1/assessments/buildings/${buildingId}/current`)
}
export function calculateBuildingAssessment(buildingId: string, payload: { force?: boolean; rankingScopes?: RankingScopeType[] } = {}): Promise<Record<string, unknown>> {
  return apiPost<Record<string, unknown>>(`/api/v1/assessments/buildings/${buildingId}/calculate`, payload)
}
export function getBuildingAssessmentHistory(buildingId: string, params: { assessmentType?: RuleType; page?: number; size?: number } = {}): Promise<AssessmentHistoryPage> {
  return apiGet<AssessmentHistoryPage>(`/api/v1/assessments/buildings/${buildingId}/history`, params)
}
export function listRenewalPriorities(params: { scopeType: RankingScopeType; scopeId?: string; priorityLevel?: string; riskLevel?: string; page?: number; size?: number }): Promise<RenewalPriorityPage> {
  return apiGet<RenewalPriorityPage>('/api/v1/renewal-priorities', params)
}
export function listAssessmentRules(params: { ruleType?: RuleType; status?: RuleStatus } = {}): Promise<AssessmentRule[]> {
  return apiGet<{ content: AssessmentRule[] }>('/api/v1/assessment-rules', params).then((data) => data.content)
}
export function createAssessmentRule(payload: { ruleType: RuleType; versionCode: string; ruleName: string; ruleContent: Record<string, unknown> }): Promise<AssessmentRule> {
  return apiPost<AssessmentRule>('/api/v1/assessment-rules', payload)
}
export function activateAssessmentRule(ruleId: string): Promise<{ activeRule: AssessmentRule; retiredRuleId?: string; staleAssessmentCount: number }> {
  return apiPost<{ activeRule: AssessmentRule; retiredRuleId?: string; staleAssessmentCount: number }>(`/api/v1/assessment-rules/${ruleId}/activate`)
}
