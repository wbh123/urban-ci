import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { describe, expect, it, vi } from 'vitest'
import MobileBuildingAssessmentPage from './MobileBuildingAssessmentPage.vue'

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { buildingId: '10000000-0000-0000-0000-000000000001' } }),
}))

const apiMocks = vi.hoisted(() => ({
  getBuildingAssessmentSummary: vi.fn(),
}))

vi.mock('@/shared/api', () => ({
  getBuildingAssessmentSummary: apiMocks.getBuildingAssessmentSummary,
}))

describe('MobileBuildingAssessmentStaleState', () => {
  it('展示 STALE 警示且不展示优先级', async () => {
    apiMocks.getBuildingAssessmentSummary.mockResolvedValue({
      buildingId: '10000000-0000-0000-0000-000000000001',
      buildingCode: 'A-01',
      buildingName: '一号楼',
      communityId: '20000000-0000-0000-0000-000000000001',
      communityName: '示例小区',
      freshness: 'STALE',
      completeness: { completenessScore: 82, completenessLevel: 'GOOD', missingItems: ['专业检测'], suggestions: ['补充资料'] },
      risk: { riskScore: 68, riskLevel: 'HIGH', confidenceScore: 55, confidenceLevel: 'LOW', needManualReview: true, needProfessionalInspection: true, recommendations: ['人工复核'] },
      disclaimer: '系统结果仅用于风险筛查与辅助决策。',
    })

    const wrapper = mount(MobileBuildingAssessmentPage, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    expect(wrapper.text()).toContain('评分结果已过期')
    expect(wrapper.text()).toContain('68.00')
    expect(wrapper.text()).not.toContain('城市更新优先级')
  })
})
