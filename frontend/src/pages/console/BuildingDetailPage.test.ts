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

  it('exposes stable business sections without leaking raw AI payloads', () => {
    for (const label of ['基础档案', '业务进度', '巡检记录', '辅助分析', '风险与优先级', '现场证据', '风险报告']) {
      expect(source).toContain(label)
    }
    expect(source).toContain('人工智能结果仅用于辅助分析')
    expect(source).not.toContain('JSON.stringify')
    expect(source).not.toContain('structuredResult')
  })
})
