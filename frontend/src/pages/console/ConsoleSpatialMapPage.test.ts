import { describe, expect, it } from 'vitest'
import source from './ConsoleSpatialMapPage.vue?raw'

describe('ConsoleSpatialMapPage production integration', () => {
  it('loads runtime config, community locations and drives bbox data through the spatial store', () => {
    expect(source).toContain('getMapRuntimeConfig')
    expect(source).toContain('listCommunityPoints')
    expect(source).toContain('createSpatialAmapDriver')
    expect(source).toContain('useSpatialMapStore')
    expect(source).toContain('store.loadViewport')
    expect(source).toContain('driver.sync')
  })

  it('passes real community and building point projections to the existing AMap driver', () => {
    expect(source).toContain('communityMapPoints')
    expect(source).toContain('buildingMapPoints')
    expect(source).toContain('communityPoints: communityMapPoints.value')
    expect(source).toContain('buildingPoints: buildingMapPoints.value')
    expect(source).toContain('visibleRiskBuildings')
  })

  it('does an explicit initial viewport load after mounting the map', () => {
    expect(source).toContain('const initialViewport = driver.getViewport()')
    expect(source).toContain('await store.loadViewport(initialViewport)')
  })

  it('rejects null and empty coordinates instead of mapping them to zero', () => {
    expect(source).toContain("value !== null && value !== undefined && value !== ''")
    expect(source).toContain('Number.isFinite(Number(value))')
  })

  it('keeps search, risk filters and explicit community selection connected to store actions', () => {
    expect(source).toContain('store.setSearchKeyword')
    expect(source).toContain('store.setRiskLevels')
    expect(source).toContain('store.selectCommunity')
    expect(source).toContain('community-filter')
    expect(source).toContain('全部小区')
  })

  it('uses risk rows for the business list even when a building has no verified polygon', () => {
    expect(source).toContain('v-for="item in visibleRiskBuildings"')
    expect(source).toContain('store.selectSingleBuilding(item.buildingId)')
    expect(source).not.toContain('v-for="item in visibleBuildings"')
  })

  it('uses the unified building-detail loader and presentational drawer instead of duplicating business requests', () => {
    expect(source).toContain('loadBuildingDetail')
    expect(source).toContain('BuildingDetailDrawer')
    expect(source).toContain("name: 'console-building-detail'")
    expect(source).not.toContain('getCurrentBuildingAssessment')
    expect(source).not.toContain('listFeedbackReports')
    expect(source).not.toContain('评分明细')
  })

  it('has an explicit unavailable-state instead of fabricating polygons', () => {
    expect(source).toContain("mode !== 'LIVE'")
    expect(source).toContain('地图服务当前不可用')
    expect(source).not.toContain('syntheticBoundary')
  })

  it('removes the large workflow card and keeps a lightweight next action', () => {
    expect(source).not.toContain('GovernanceJourney')
    expect(source).not.toContain('业务闭环')
    expect(source).toContain('查看风险总览')
  })
})
