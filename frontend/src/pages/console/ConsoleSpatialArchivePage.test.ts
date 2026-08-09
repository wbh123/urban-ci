import { describe, expect, it } from 'vitest'
import source from './ConsoleSpatialArchivePage.vue?raw'

describe('ConsoleSpatialArchivePage R3/R4 lifecycle', () => {
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

  it('previews AMap candidates without dirtying the draft until explicit adoption', () => {
    expect(source).toContain('previewCommunityBoundaryCandidate')
    expect(source).toContain('editor.previewGeometry(result.geometry)')
    expect(source).toContain('采用候选作为草稿')
    expect(source).toContain('candidateAdopted.value = true')
    expect(source).toContain('editor.loadGeometry(candidate.value.geometry)')
    expect(source).toContain("sourceType: candidateAdopted.value ? 'AMAP_AOI'")
    expect(source).toContain("sourceProvider: candidateAdopted.value ? 'AMAP'")
    expect(source).toContain('保存后只会进入“待确认”')
  })

  it('does not allow clearing candidate provenance after adoption', () => {
    expect(source).toContain('v-if="!candidateAdopted" @click="cancelAmapCandidate"')
    expect(source).toContain('采用后如需撤销，请使用“取消本次修改”')
  })

  it('keeps manual drawing and GeoJSON available when AMap candidates fail', () => {
    expect(source).toContain('人工绘制和 GeoJSON 导入仍可继续使用')
    expect(source).toContain('绘制新边界')
    expect(source).toContain('导入 GCJ-02 GeoJSON')
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

  it('consumes archive management deep-link query and preselects the requested object', () => {
    expect(source).toContain('useRoute')
    expect(source).toContain('route.query.entityType')
    expect(source).toContain('route.query.entityId')
    expect(source).toContain('route.query.communityId')
    expect(source).toContain('preferredEntityId')
    expect(source).toContain('preferredCommunityId')
    expect(source).toContain("entityType.value = 'BUILDING'")
  })
})
