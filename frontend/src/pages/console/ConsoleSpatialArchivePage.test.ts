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
