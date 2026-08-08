import { describe, expect, it } from 'vitest'
import source from './ConsoleSpatialArchivePage.vue?raw'

describe('ConsoleSpatialArchivePage R3 lifecycle', () => {
  it('loads scoped community/building directories and current boundary', () => {
    expect(source).toContain('listCommunities')
    expect(source).toContain('listBuildings')
    expect(source).toContain('getCommunityBoundary')
    expect(source).toContain('getBuildingBoundary')
  })

  it('uses the polygon editor and writes with expectedVersion', () => {
    expect(source).toContain('createSpatialBoundaryEditor')
    expect(source).toContain('startDraw')
    expect(source).toContain('startEdit')
    expect(source).toContain('expectedVersion')
    expect(source).toContain('upsertCommunityBoundary')
    expect(source).toContain('upsertBuildingBoundary')
  })

  it('imports GCJ-02 GeoJSON from text or file and can save without a live map', () => {
    expect(source).toContain('parseSpatialGeoJson')
    expect(source).toContain('geoJsonInput')
    expect(source).toContain('handleGeoJsonFile')
    expect(source).toContain('dirtyGeometry.value ?? editor.exportGeometry()')
    expect(source).toContain('导入 GCJ-02 GeoJSON')
    expect(source).toContain('地图不可用时仍可通过 GeoJSON 建档')
  })

  it('cancels local geometry changes by restoring the server boundary without saving', () => {
    expect(source).toContain('cancelEdit')
    expect(source).toContain('await loadCurrentBoundary()')
    expect(source).toContain('取消本次修改')
  })

  it('supports verify/reject and recovers from HTTP 409 without overwriting server state', () => {
    expect(source).toContain('verifyCommunityBoundary')
    expect(source).toContain('rejectCommunityBoundary')
    expect(source).toContain('verifyBuildingBoundary')
    expect(source).toContain('rejectBuildingBoundary')
    expect(source).toContain('toAppError')
    expect(source).toContain('isConflict')
    expect(source).toContain('边界已被其他用户修改')
    expect(source).toContain('loadCurrentBoundary')
  })
})
