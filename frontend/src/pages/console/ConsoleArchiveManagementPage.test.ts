import { describe, expect, it } from 'vitest'
import source from './ConsoleArchiveManagementPage.vue?raw'

describe('ConsoleArchiveManagementPage visual archive flow', () => {
  it('loads scoped community and building directories', () => {
    expect(source).toContain('listCommunities')
    expect(source).toContain('listBuildings')
    expect(source).toContain('selectedCommunityId')
    expect(source).toContain('selectedBuildingId')
  })

  it('offers community and building creation drawers with map-assisted discovery', () => {
    expect(source).toContain('新增小区')
    expect(source).toContain('新增楼栋')
    expect(source).toContain('searchArchivePlaces')
    expect(source).toContain('previewArchiveReverseGeocoding')
    expect(source).toContain('createArchivePointPicker')
    expect(source).toContain('地图点选')
  })

  it('creates business objects first and persists optional center points afterwards', () => {
    expect(source).toContain('createCommunity')
    expect(source).toContain('saveCommunityLocation')
    expect(source).toContain('createBuilding')
    expect(source).toContain('saveArchiveBuildingLocation')
    expect(source).toContain('档案已创建，但地图位置保存失败，可稍后补录')
  })

  it('can continue to spatial archive for the selected object', () => {
    expect(source).toContain('进入空间档案')
    expect(source).toContain("name: 'console-spatial-archive'")
    expect(source).toContain('entityType')
    expect(source).toContain('entityId')
  })
})
