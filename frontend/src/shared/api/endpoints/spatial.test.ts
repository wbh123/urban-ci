import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  apiPut: vi.fn(),
}))

vi.mock('../client', () => mocks)

import {
  getBuildingBoundary,
  getCommunityBoundary,
  querySpatialBuildings,
  querySpatialCommunities,
  rejectBuildingBoundary,
  rejectCommunityBoundary,
  upsertBuildingBoundary,
  upsertCommunityBoundary,
  verifyBuildingBoundary,
  verifyCommunityBoundary,
} from './spatial'

describe('spatial endpoints', () => {
  beforeEach(() => vi.clearAllMocks())

  it('passes bbox and zoom to verified community GeoJSON query', async () => {
    mocks.apiGet.mockResolvedValue({ type: 'FeatureCollection', features: [] })
    await querySpatialCommunities({ west: 114, south: 30, east: 115, north: 31, zoom: 16 })
    expect(mocks.apiGet).toHaveBeenCalledWith('/api/v1/spatial/communities', {
      west: 114,
      south: 30,
      east: 115,
      north: 31,
      zoom: 16,
    })
  })

  it('supports optional community filter for building query', async () => {
    mocks.apiGet.mockResolvedValue({ type: 'FeatureCollection', features: [] })
    await querySpatialBuildings({
      west: 114,
      south: 30,
      east: 115,
      north: 31,
      zoom: 17,
      communityId: 'community-1',
    })
    expect(mocks.apiGet).toHaveBeenCalledWith('/api/v1/spatial/buildings', {
      west: 114,
      south: 30,
      east: 115,
      north: 31,
      zoom: 17,
      communityId: 'community-1',
    })
  })

  it('uses encoded entity ids for boundary reads and writes', async () => {
    mocks.apiGet.mockResolvedValue({})
    mocks.apiPut.mockResolvedValue({})
    await getCommunityBoundary('community/a')
    await getBuildingBoundary('building/a')
    await upsertCommunityBoundary('community/a', { expectedVersion: 0 } as never)
    await upsertBuildingBoundary('building/a', { expectedVersion: 0 } as never)

    expect(mocks.apiGet).toHaveBeenCalledWith('/api/v1/spatial/communities/community%2Fa/boundary')
    expect(mocks.apiGet).toHaveBeenCalledWith('/api/v1/spatial/buildings/building%2Fa/boundary')
    expect(mocks.apiPut).toHaveBeenCalledWith(
      '/api/v1/spatial/communities/community%2Fa/boundary',
      expect.objectContaining({ expectedVersion: 0 }),
    )
    expect(mocks.apiPut).toHaveBeenCalledWith(
      '/api/v1/spatial/buildings/building%2Fa/boundary',
      expect.objectContaining({ expectedVersion: 0 }),
    )
  })

  it('routes verify and reject through versioned review endpoints', async () => {
    mocks.apiPost.mockResolvedValue({})
    const review = { expectedVersion: 3, remark: 'checked' }
    await verifyCommunityBoundary('c1', review)
    await rejectCommunityBoundary('c1', review)
    await verifyBuildingBoundary('b1', review)
    await rejectBuildingBoundary('b1', review)

    expect(mocks.apiPost).toHaveBeenCalledWith('/api/v1/spatial/communities/c1/boundary/verify', review)
    expect(mocks.apiPost).toHaveBeenCalledWith('/api/v1/spatial/communities/c1/boundary/reject', review)
    expect(mocks.apiPost).toHaveBeenCalledWith('/api/v1/spatial/buildings/b1/boundary/verify', review)
    expect(mocks.apiPost).toHaveBeenCalledWith('/api/v1/spatial/buildings/b1/boundary/reject', review)
  })
})
