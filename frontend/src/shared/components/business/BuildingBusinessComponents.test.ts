import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import BuildingSummaryCard from './BuildingSummaryCard.vue'
import BuildingLifecycleTimeline from './BuildingLifecycleTimeline.vue'
import RiskSummaryPanel from './RiskSummaryPanel.vue'
import EvidenceGallery from './EvidenceGallery.vue'
import summarySource from './BuildingSummaryCard.vue?raw'
import timelineSource from './BuildingLifecycleTimeline.vue?raw'
import riskSource from './RiskSummaryPanel.vue?raw'
import evidenceSource from './EvidenceGallery.vue?raw'

const global = { plugins: [ElementPlus] }

const buildingSummary = {
  buildingId: 'b-1',
  buildingCode: 'B-001',
  buildingName: '1号楼',
  communityName: '示范小区',
  address: '示范路1号',
  constructionYear: 2008,
  floorCount: 18,
  residentCount: 360,
  spatialStatus: 'VERIFIED' as const,
}

describe('R4-2 building business components', () => {
  it('keeps all four components presentational without direct API imports', () => {
    for (const source of [summarySource, timelineSource, riskSource, evidenceSource]) {
      expect(source).not.toContain("from '@/shared/api")
      expect(source).not.toContain('apiGet(')
      expect(source).not.toContain('apiPost(')
    }
  })

  it('renders a business-friendly building summary', () => {
    const wrapper = mount(BuildingSummaryCard, {
      props: { summary: buildingSummary },
      global,
    })

    expect(wrapper.text()).toContain('1号楼')
    expect(wrapper.text()).toContain('B-001')
    expect(wrapper.text()).toContain('示范小区')
    expect(wrapper.text()).toContain('18 层')
    expect(wrapper.text()).toContain('360 人')
    expect(wrapper.text()).toContain('空间档案已确认')
  })

  it('supports a compact summary mode without repeating secondary archive fields', () => {
    const wrapper = mount(BuildingSummaryCard, {
      props: { summary: buildingSummary, compact: true },
      global,
    })

    expect(wrapper.find('.building-summary-card').classes()).toContain('is-compact')
    expect(wrapper.text()).toContain('1号楼')
    expect(wrapper.text()).toContain('B-001')
    expect(wrapper.text()).not.toContain('建成年份')
    expect(wrapper.text()).not.toContain('居民')
  })

  it('renders the fixed lifecycle stages and human-readable statuses', () => {
    const wrapper = mount(BuildingLifecycleTimeline, {
      props: {
        nodes: [
          { stage: 'ARCHIVE', label: '基础档案', status: 'COMPLETED', count: 1, description: '楼栋主档已建立。' },
          { stage: 'INSPECTION', label: '现场巡检', status: 'IN_PROGRESS', count: 2, description: '处理中' },
          { stage: 'ASSESSMENT', label: '正式评分', status: 'STALE', count: 1, description: '现有正式评分已过期。' },
        ],
      },
      global,
    })

    expect(wrapper.text()).toContain('基础档案')
    expect(wrapper.text()).toContain('已完成')
    expect(wrapper.text()).toContain('现场巡检')
    expect(wrapper.text()).toContain('进行中')
    expect(wrapper.text()).toContain('正式评分')
    expect(wrapper.text()).toContain('已过期')
  })

  it('renders formal risk data with business labels and separates AI assistance from formal conclusions', () => {
    const wrapper = mount(RiskSummaryPanel, {
      props: {
        summary: {
          freshness: 'CURRENT',
          riskScore: 72.5,
          riskLevel: 'HIGH',
          confidenceScore: 81,
          completenessScore: 88,
          priorityScore: 76,
          priorityLevel: 'P1',
          needManualReview: true,
          recommendations: ['安排专业人员复核高风险因素'],
        },
      },
      global,
    })

    expect(wrapper.text()).toContain('72.50')
    expect(wrapper.text()).toContain('高风险')
    expect(wrapper.text()).not.toMatch(/\bHIGH\b/)
    expect(wrapper.text()).toContain('一级优先')
    expect(wrapper.text()).toContain('需要人工复核')
    expect(wrapper.text()).toContain('辅助分析')
    expect(wrapper.text()).toContain('不作为正式鉴定结论')
  })

  it('renders a business empty state instead of metric placeholders when no formal assessment exists', () => {
    const wrapper = mount(RiskSummaryPanel, {
      props: { summary: { freshness: 'NO_RESULT' } },
      global,
    })

    expect(wrapper.text()).toContain('暂无正式风险评分')
    expect(wrapper.find('.risk-empty').exists()).toBe(true)
    expect(wrapper.find('.risk-metrics').exists()).toBe(false)
  })

  it('renders evidence cards and emits open without exposing raw JSON', async () => {
    const wrapper = mount(EvidenceGallery, {
      props: {
        items: [
          {
            id: 'asset-1',
            title: '外墙裂缝照片',
            previewUrl: 'blob:asset-1',
            sourceLabel: '现场巡检',
            reviewStatus: 'CONFIRMED',
            reliabilityLabel: '人工已复核',
            aiAssisted: true,
          },
          {
            id: 'asset-2',
            title: '楼梯间照片',
            sourceLabel: '现场巡检',
            reviewStatus: 'UNREVIEWED',
            reliabilityLabel: '待复核',
            aiAssisted: false,
          },
        ],
      },
      global,
    })

    expect(wrapper.text()).toContain('外墙裂缝照片')
    expect(wrapper.text()).toContain('人工已复核')
    expect(wrapper.text()).toContain('辅助分析')
    expect(wrapper.text()).not.toContain('{"')

    await wrapper.find('[data-evidence-id="asset-1"]').trigger('click')
    expect(wrapper.emitted('open')?.[0]).toEqual(['asset-1'])
  })

  it('renders an explicit evidence empty state', () => {
    const wrapper = mount(EvidenceGallery, { props: { items: [] }, global })
    expect(wrapper.text()).toContain('暂无可展示证据')
  })
})
