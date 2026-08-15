import { describe, expect, it } from 'vitest'
import source from './BuildingDetailPage.vue?raw'

describe('R4-3 unified building detail page', () => {
  it('composes the shared business components from the unified loader model', () => {
    expect(source).toContain('loadBuildingDetail')
    expect(source).toContain('BuildingSummaryCard')
    expect(source).toContain('BuildingLifecycleTimeline')
    expect(source).toContain('RiskSummaryPanel')
    expect(source).toContain('EvidenceGallery')
    expect(source).not.toContain("from '@/shared/api")
  })

  it('keeps the mature data domains while presenting them as an AI governance cockpit', () => {
    for (const label of ['概览', 'AI 发现', '巡检', '风险研判', '证据', '档案', '治理记录']) {
      expect(source).toContain(label)
    }
    expect(source).toContain('AI 发现仅用于辅助筛查')
    expect(source).toContain('AI 不覆盖人工专业结论')
    expect(source).not.toContain('JSON.stringify')
  })
})
