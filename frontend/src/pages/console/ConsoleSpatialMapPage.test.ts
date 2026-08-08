import { describe, expect, it } from 'vitest'
import source from './ConsoleSpatialMapPage.vue?raw'

describe('ConsoleSpatialMapPage R3 integration', () => {
  it('loads runtime config and drives verified bbox data through the spatial store', () => {
    expect(source).toContain('getMapRuntimeConfig')
    expect(source).toContain('createSpatialAmapDriver')
    expect(source).toContain('useSpatialMapStore')
    expect(source).toContain('store.loadViewport')
    expect(source).toContain('driver.sync')
  })

  it('keeps search, risk filters, community selection and multi-building selection connected to store actions', () => {
    expect(source).toContain('store.setSearchKeyword')
    expect(source).toContain('store.setRiskLevels')
    expect(source).toContain('store.selectCommunity')
    expect(source).toContain('store.toggleBuilding')
  })

  it('uses real assessment and feedback data in the selected-building drawer', () => {
    expect(source).toContain('getCurrentBuildingAssessment')
    expect(source).toContain('listFeedbackReports')
    expect(source).toContain('概览')
    expect(source).toContain('证据')
    expect(source).toContain('评分明细')
    expect(source).toContain('公众反馈')
  })

  it('has an explicit unavailable-state instead of fabricating polygons', () => {
    expect(source).toContain("runtimeConfig.mode !== 'LIVE'")
    expect(source).toContain('地图服务当前不可用')
    expect(source).not.toContain('syntheticBoundary')
  })
})
