import { http } from 'msw'
import { okResponse, errorResponse, requireAuth } from './helpers'
import { BUILDING_ID, COMMUNITY_ID } from '../fixtures/data'
import type { AssessmentRule, BuildingCurrentAssessment, BuildingAssessmentSummary, AssessmentHistoryPage, RenewalPriorityRow } from '@/shared/api'

const now = (): string => new Date().toISOString()
const disclaimer = '系统结果仅用于风险筛查与辅助决策，不作为正式房屋安全鉴定结论。'

const priorityRows: RenewalPriorityRow[] = [
  {
    ranking: 1,
    buildingId: BUILDING_ID,
    buildingCode: 'B-001',
    buildingName: '1号楼',
    communityId: COMMUNITY_ID,
    communityName: '示范小区',
    priorityScore: 86.5,
    priorityLevel: 'P1',
    riskScore: 78.2,
    riskLevel: 'HIGH',
    confidenceScore: 72.4,
    completenessScore: 84.1,
    completenessLevel: 'GOOD',
    residentCount: 24,
    mainReasons: ['风险特征明显', '居民影响较高', '资料完整度可支持复核'],
    needManualReview: true,
    needProfessionalInspection: true,
    rankingScopeKey: 'ALL',
    status: 'CURRENT',
    generatedAt: now(),
    disclaimer,
  },
  {
    ranking: 2,
    buildingId: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
    buildingCode: 'B-002',
    buildingName: '2号楼',
    communityId: COMMUNITY_ID,
    communityName: '示范小区',
    priorityScore: 61.8,
    priorityLevel: 'P3',
    riskScore: 45.5,
    riskLevel: 'MEDIUM',
    confidenceScore: 68.0,
    completenessScore: 76.2,
    completenessLevel: 'GOOD',
    residentCount: 18,
    mainReasons: ['中等风险', '持续巡检'],
    needManualReview: false,
    needProfessionalInspection: false,
    rankingScopeKey: 'ALL',
    status: 'CURRENT',
    generatedAt: now(),
    disclaimer,
  },
]



const currentAssessment: BuildingCurrentAssessment = {
  buildingId: BUILDING_ID,
  buildingCode: 'B-001',
  buildingName: '1号楼',
  communityId: COMMUNITY_ID,
  communityName: '示范小区',
  freshness: 'CURRENT',
  completeness: {
    assessmentId: '51000000-0000-0000-0000-000000000001',
    completenessScore: 84.1,
    completenessLevel: 'GOOD',
    status: 'CURRENT',
    dimensionScores: [],
    availableItems: ['基础档案', '巡检记录', '现场图片'],
    missingItems: ['专业检测报告'],
    suggestions: ['补充第三方专业检测资料'],
    assessedAt: now(),
    ruleVersion: 'COMPLETENESS-V1.1',
    inputChecksum: 'mock-completeness-checksum',
    engineVersion: 'phase4-rule-engine-v1',
    triggerType: 'MOCK',
  },
  risk: {
    assessmentId: '52000000-0000-0000-0000-000000000001',
    riskScore: 78.2,
    riskLevel: 'HIGH',
    confidenceScore: 72.4,
    confidenceLevel: 'MEDIUM',
    evidenceReliabilityScore: 60,
    status: 'CURRENT',
    dimensionScores: [],
    topFactors: [{ factorCode: 'VISUAL_DEFECT', label: '表观病害明显', effect: 18, direction: 'INCREASE' }],
    excludedEvidence: [{ reason: 'MOCK AI 结果需人工复核后进入正式评分。' }],
    missingData: ['专业检测报告'],
    recommendations: ['安排人工复核', '补充专业检测'],
    needManualReview: true,
    needProfessionalInspection: true,
    assessedAt: now(),
    ruleVersion: 'RISK-V1',
    inputChecksum: 'mock-risk-checksum',
    engineVersion: 'phase4-rule-engine-v1',
    triggerType: 'MOCK',
    disclaimer,
  },
  renewalPriorities: [{
    priorityId: '53000000-0000-0000-0000-000000000001',
    priorityScore: 86.5,
    priorityLevel: 'P1',
    ranking: 1,
    rankingScopeKey: 'ALL',
    status: 'CURRENT',
    factors: [],
    recommendations: ['纳入近期城市更新评估清单'],
    generatedAt: now(),
    ruleVersion: 'RENEWAL-V1.1',
    inputChecksum: 'mock-renewal-checksum',
    engineVersion: 'phase4-rule-engine-v1',
    triggerType: 'MOCK',
    disclaimer,
  }],
  inputSummary: { source: 'mock' },
  disclaimer,
}

const currentSummary: BuildingAssessmentSummary = {
  buildingId: currentAssessment.buildingId,
  buildingCode: currentAssessment.buildingCode,
  buildingName: currentAssessment.buildingName,
  communityId: currentAssessment.communityId,
  communityName: currentAssessment.communityName,
  freshness: currentAssessment.freshness,
  completeness: {
    completenessScore: 84.1,
    completenessLevel: 'GOOD',
    missingItems: ['专业检测报告'],
    suggestions: ['补充第三方专业检测资料'],
  },
  risk: {
    riskScore: 78.2,
    riskLevel: 'HIGH',
    confidenceScore: 72.4,
    confidenceLevel: 'MEDIUM',
    needManualReview: true,
    needProfessionalInspection: true,
    recommendations: ['安排人工复核', '补充专业检测'],
  },
  disclaimer,
}

const historyPage: AssessmentHistoryPage = {
  content: [{
    assessmentId: '52000000-0000-0000-0000-000000000001',
    assessmentType: 'RISK',
    score: 78.2,
    secondaryScore: 72.4,
    level: 'HIGH',
    status: 'CURRENT',
    ruleVersion: 'RISK-V1',
    inputChecksum: 'mock-risk-checksum',
    engineVersion: 'phase4-rule-engine-v1',
    triggerType: 'MOCK',
    calculationBatchId: '54000000-0000-0000-0000-000000000001',
    calculatedAt: now(),
    disclaimer,
  }],
  page: { page: 0, size: 50, totalElements: 1, totalPages: 1 },
}

const rules: AssessmentRule[] = [
  {
    ruleId: '41000000-0000-0000-0000-000000000004',
    ruleType: 'COMPLETENESS',
    versionCode: 'COMPLETENESS-V1.1',
    ruleName: '资料完整度评分规则 V1.1',
    checksum: 'cc528dc0e116ebbcde636db751834be4173d13a31911648c176de841751b4a2a',
    status: 'ACTIVE',
    ruleContent: {},
    activatedAt: now(),
    createdAt: now(),
    updatedAt: now(),
  },
  {
    ruleId: '42000000-0000-0000-0000-000000000002',
    ruleType: 'RISK',
    versionCode: 'RISK-V1',
    ruleName: '风险筛查评分规则 V1',
    checksum: '47e2b83e321f954c8de2739e5edee4666a533ff57259ba8d31a3da9c9a2da7e9',
    status: 'ACTIVE',
    ruleContent: {},
    activatedAt: now(),
    createdAt: now(),
    updatedAt: now(),
  },
  {
    ruleId: '43000000-0000-0000-0000-000000000003',
    ruleType: 'RENEWAL',
    versionCode: 'RENEWAL-V1.1',
    ruleName: '城市更新优先级规则 V1.1',
    checksum: '424e4f3ef9626d6c9a5b65cc241c703f485ce6d863ab9972a2504cdbe4997ddd',
    status: 'ACTIVE',
    ruleContent: {},
    activatedAt: now(),
    createdAt: now(),
    updatedAt: now(),
  },
]

export const assessmentHandlers = [

  http.get('/api/v1/assessments/buildings/:buildingId/current', ({ request, params }) => {
    const unauth = requireAuth(request)
    if (unauth) return unauth
    if (!params.buildingId) return errorResponse('BUILDING_NOT_FOUND', '楼栋不存在。', 404)
    return okResponse(currentAssessment)
  }),

  http.get('/api/v1/assessments/buildings/:buildingId/summary', ({ request, params }) => {
    const unauth = requireAuth(request)
    if (unauth) return unauth
    if (!params.buildingId) return errorResponse('BUILDING_NOT_FOUND', '楼栋不存在。', 404)
    return okResponse(currentSummary)
  }),

  http.get('/api/v1/assessments/buildings/:buildingId/history', ({ request, params }) => {
    const unauth = requireAuth(request)
    if (unauth) return unauth
    if (!params.buildingId) return errorResponse('BUILDING_NOT_FOUND', '楼栋不存在。', 404)
    return okResponse(historyPage)
  }),

  http.post('/api/v1/assessments/buildings/:buildingId/calculate', ({ request, params }) => {
    const unauth = requireAuth(request)
    if (unauth) return unauth
    if (!params.buildingId) return errorResponse('BUILDING_NOT_FOUND', '楼栋不存在。', 404)
    return okResponse({ reused: true, calculationBatchId: '54000000-0000-0000-0000-000000000001' })
  }),

  http.post('/api/v1/assessment-rules/:ruleId/activate', ({ request, params }) => {
    const unauth = requireAuth(request)
    if (unauth) return unauth
    const rule = rules.find((item) => item.ruleId === String(params.ruleId))
    if (!rule) return errorResponse('ASSESSMENT_RULE_NOT_FOUND', '评分规则不存在。', 404)
    return okResponse({ activeRule: rule, staleAssessmentCount: 0 })
  }),

  http.get('/api/v1/renewal-priorities', ({ request }) => {
    const unauth = requireAuth(request)
    if (unauth) return unauth
    const url = new URL(request.url)
    const page = Number(url.searchParams.get('page') ?? '0')
    const size = Number(url.searchParams.get('size') ?? '20')
    const priorityLevel = url.searchParams.get('priorityLevel')
    const riskLevel = url.searchParams.get('riskLevel')
    const filtered = priorityRows.filter((row) => {
      if (priorityLevel && row.priorityLevel !== priorityLevel) return false
      if (riskLevel && row.riskLevel !== riskLevel) return false
      return true
    })
    const safePage = Number.isFinite(page) ? page : 0
    const safeSize = Number.isFinite(size) && size > 0 ? size : 20
    return okResponse({
      scopeKey: url.searchParams.get('scopeType') ?? 'ALL',
      content: filtered.slice(safePage * safeSize, safePage * safeSize + safeSize),
      page: {
        page: safePage,
        size: safeSize,
        totalElements: filtered.length,
        totalPages: Math.ceil(filtered.length / safeSize),
      },
      disclaimer,
    })
  }),

  http.get('/api/v1/assessment-rules', ({ request }) => {
    const unauth = requireAuth(request)
    if (unauth) return unauth
    const url = new URL(request.url)
    const ruleType = url.searchParams.get('ruleType')
    const status = url.searchParams.get('status')
    return okResponse({
      content: rules.filter((rule) => {
        if (ruleType && rule.ruleType !== ruleType) return false
        if (status && rule.status !== status) return false
        return true
      }),
    })
  }),

  http.post('/api/v1/assessment-rules', ({ request }) => {
    const unauth = requireAuth(request)
    if (unauth) return unauth
    return errorResponse('MOCK_READ_ONLY', 'Mock 环境不创建评分规则。', 409)
  }),
]
