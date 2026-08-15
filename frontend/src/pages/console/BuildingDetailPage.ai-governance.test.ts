import { describe, expect, it } from 'vitest'
import source from './BuildingDetailPage.vue?raw'

describe('P2 building AI governance cockpit', () => {
  it('presents one building as an AI-assisted governance cockpit', () => {
    expect(source).toContain('单栋建筑 AI 治理驾驶舱')
    expect(source).toContain('AI 综合研判')
    expect(source).toContain('主要发现')
    expect(source).toContain('分析依据')
    expect(source).toContain('AI关注')
  })

  it('uses governance-oriented tabs while preserving the unified loader', () => {
    expect(source).toContain('loadBuildingDetail')
    for (const label of ['概览', 'AI 发现', '巡检', '风险研判', '证据', '档案', '治理记录']) {
      expect(source).toContain(label)
    }
    expect(source).not.toContain("from '@/shared/api")
  })
})
