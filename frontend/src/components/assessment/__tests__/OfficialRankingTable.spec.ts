import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import OfficialRankingTable from '../OfficialRankingTable.vue'
import type { RenewalPriorityRow } from '@/shared/api'

function row(ranking: number, buildingId: string, buildingCode: string): RenewalPriorityRow {
  return { ranking, buildingId, buildingCode, buildingName: `${buildingCode}楼`, communityId: '20000000-0000-0000-0000-000000000001', communityName: '示例社区', priorityScore: 80, priorityLevel: 'P1', riskScore: 70, riskLevel: 'HIGH', confidenceScore: 75, completenessScore: 80, completenessLevel: 'GOOD', residentCount: 300, mainReasons: [], needManualReview: true, needProfessionalInspection: false, rankingScopeKey: 'ALL', status: 'CURRENT', generatedAt: '2026-07-25T00:00:00Z', disclaimer: '测试' }
}

describe('OfficialRankingTable', () => {
  it('按服务端返回顺序展示正式 ranking，不在组件中重排', () => {
    const wrapper = mount(OfficialRankingTable, { props: { rows: [row(2, 'b-2', 'B-02'), row(1, 'b-1', 'A-01')] } })
    const renderedRows = wrapper.findAll('tbody tr')
    expect(renderedRows[0].attributes('data-building-id')).toBe('b-2')
    expect(renderedRows[0].find('.ranking-number').text()).toBe('2')
    expect(renderedRows[1].attributes('data-building-id')).toBe('b-1')
    expect(renderedRows[1].find('.ranking-number').text()).toBe('1')
  })
})
