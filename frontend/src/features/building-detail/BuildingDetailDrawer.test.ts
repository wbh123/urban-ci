import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import BuildingDetailDrawer from './BuildingDetailDrawer.vue'
import drawerSource from './BuildingDetailDrawer.vue?raw'
import type { BuildingDetailModel } from './building-detail-loader'

const global = {
  plugins: [ElementPlus],
  stubs: {
    ElDrawer: {
      props: ['modelValue'],
      template: '<section class="drawer-stub"><slot /></section>',
    },
  },
}

const model: BuildingDetailModel = {
  summary: {
    buildingId: 'b-1',
    buildingCode: 'B-001',
    buildingName: '1号楼',
    communityName: '示范小区',
    address: '示范路1号',
    spatialStatus: 'VERIFIED',
  },
  lifecycle: [],
  risk: {
    freshness: 'CURRENT',
    riskScore: 72,
    riskLevel: 'HIGH',
    completenessScore: 88,
    priorityLevel: 'P1',
  },
  evidence: [],
  inspections: [],
  analyses: [],
  assessment: null,
  reports: [],
  warnings: [],
}

describe('R4-3 BuildingDetailDrawer', () => {
  it('remains presentational and does not issue API requests itself', () => {
    expect(drawerSource).not.toContain("from '@/shared/api")
    expect(drawerSource).not.toContain('loadBuildingDetail(')
    expect(drawerSource).not.toContain('apiGet(')
  })

  it('renders compact building and risk summaries and opens the unified detail page through an event', async () => {
    const wrapper = mount(BuildingDetailDrawer, {
      props: { model, modelValue: true },
      global,
    })

    expect(wrapper.text()).toContain('1号楼')
    expect(wrapper.text()).toContain('高风险')
    expect(wrapper.text()).toContain('一级优先')
    expect(wrapper.text()).toContain('查看完整档案')

    await wrapper.get('[data-action="open-full-detail"]').trigger('click')
    expect(wrapper.emitted('open-full')?.[0]).toEqual(['b-1'])
  })
})
