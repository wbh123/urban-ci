import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { describe, expect, it, vi } from 'vitest'
import ConsoleBuildingAssessmentPage from './ConsoleBuildingAssessmentPage.vue'

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { buildingId: '10000000-0000-0000-0000-000000000001' } }),
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ hasAnyRole: () => false }),
}))

const apiMocks = vi.hoisted(() => ({
  getCurrentBuildingAssessment: vi.fn(),
  getBuildingAssessmentHistory: vi.fn(),
  calculateBuildingAssessment: vi.fn(),
}))

vi.mock('@/shared/api', () => ({
  getCurrentBuildingAssessment: apiMocks.getCurrentBuildingAssessment,
  getBuildingAssessmentHistory: apiMocks.getBuildingAssessmentHistory,
  calculateBuildingAssessment: apiMocks.calculateBuildingAssessment,
}))

describe('ConsoleBuildingAssessmentStaleState', () => {
  it('展示 STALE 警示并保留过期评分可读', async () => {
    apiMocks.getCurrentBuildingAssessment.mockResolvedValue({
      buildingId: '10000000-0000-0000-0000-000000000001',
      buildingCode: 'A-01',
      buildingName: '一号楼',
      communityId: '20000000-0000-0000-0000-000000000001',
      communityName: '示例小区',
      freshness: 'STALE',
      completeness: {
        completenessScore: 82,
        completenessLevel: 'GOOD',
        dimensionScores: [],
        missingItems: ['有效专业检测资料'],
        suggestions: ['安排第三方检测'],
        ruleVersion: 'COMPLETENESS-V1',
      },
      risk: {
        riskScore: 68,
        riskLevel: 'HIGH',
        confidenceScore: 55,
        confidenceLevel: 'LOW',
        evidenceReliabilityScore: 60,
        dimensionScores: [],
        topFactors: [],
        excludedEvidence: [],
        missingData: [],
        recommendations: ['人工复核'],
        needManualReview: true,
        needProfessionalInspection: true,
        ruleVersion: 'RISK-V1',
        engineVersion: 'phase4-rule-engine-v1',
        inputChecksum: 'checksum',
      },
      renewalPriorities: [{
        priorityScore: 75,
        priorityLevel: 'P2',
        ranking: 3,
        rankingScopeKey: 'ALL',
        recommendations: ['纳入更新评估'],
        ruleVersion: 'RENEWAL-V1.1',
      }],
      inputSummary: {},
      disclaimer: '系统结果仅用于风险筛查与辅助决策。',
    })
    apiMocks.getBuildingAssessmentHistory.mockResolvedValue({ content: [], page: { page: 0, size: 50, totalElements: 0, totalPages: 0 } })

    const wrapper = mount(ConsoleBuildingAssessmentPage, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    expect(wrapper.text()).toContain('当前展示结果包含过期评分')
    expect(wrapper.text()).toContain('STALE（存在过期评分）')
    expect(wrapper.text()).toContain('68.00')
    expect(wrapper.text()).toContain('75.00')
  })
})
