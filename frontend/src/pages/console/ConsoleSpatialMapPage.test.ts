import { describe, expect, it } from 'vitest'
import source from './ConsoleSpatialMapPage.vue?raw'

describe('ConsoleSpatialMapPage R4-3 integration', () => {
  it('loads runtime config and drives verified bbox data through the spatial store', () => {
    expect(source).toContain('getMapRuntimeConfig')
    expect(source).toContain('createSpatialAmapDriver')
    expect(source).toContain('useSpatialMapStore')
    expect(source).toContain('store.loadViewport')
    expect(source).toContain('driver.sync')
  })

  it('keeps search, risk filters, explicit community selection and multi-building selection connected to store actions', () => {
    expect(source).toContain('store.setSearchKeyword')
    expect(source).toContain('store.setRiskLevels')
    expect(source).toContain('store.selectCommunity')
    expect(source).toContain('store.toggleBuilding')
    expect(source).toContain('community-filter')
    expect(source).toContain('全部小区')
  })

  it('uses the unified building-detail loader and presentational drawer instead of duplicating business requests', () => {
    expect(source).toContain('loadBuildingDetail')
    expect(source).toContain('BuildingDetailDrawer')
    expect(source).toContain("name: 'console-building-detail'")
    expect(source).not.toContain('getCurrentBuildingAssessment')
    expect(source).not.toContain('listFeedbackReports')
    expect(source).not.toContain('评分明细')
    expect(source).not.toContain('公众反馈')
  })

  it('has an explicit unavailable-state instead of fabricating polygons', () => {
    expect(source).toContain("mode !== 'LIVE'")
    expect(source).toContain('地图服务当前不可用')
    expect(source).not.toContain('syntheticBoundary')
  })
})
