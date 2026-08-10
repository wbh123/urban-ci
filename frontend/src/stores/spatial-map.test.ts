import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  querySpatialCommunities: vi.fn(),
  querySpatialBuildings: vi.fn(),
  getRiskMap: vi.fn(),
}))

vi.mock('@/shared/api/endpoints/spatial', () => ({
  querySpatialCommunities: mocks.querySpatialCommunities,
  querySpatialBuildings: mocks.querySpatialBuildings,
}))
vi.mock('@/shared/api/endpoints/reports', () => ({ getRiskMap: mocks.getRiskMap }))

import { useSpatialMapStore } from './spatial-map'

const viewport = { west: 113, south: 27, east: 114, north: 28, zoom: 16 }

function feature(id: string, entityType: 'COMMUNITY' | 'BUILDING', communityId?: string) {
  return {
    type: 'Feature' as const,
    id,
    geometry: {
      type: 'Polygon' as const,
      coordinates: [[[113, 27], [113.1, 27], [113.1, 27.1], [113, 27.1], [113, 27]]],
    },
    properties: {
      entityType,
      entityId: id,
      entityCode: id.toUpperCase(),
      name: `${id} name`,
      communityId,
      status: 'VERIFIED' as const,
      version: 1,
      coordinateSystem: 'GCJ02' as const,
      sourceType: 'MANUAL_DRAW' as const,
    },
  }
}

describe('spatial map store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    mocks.querySpatialCommunities.mockResolvedValue({ type: 'FeatureCollection', features: [feature('c1', 'COMMUNITY')] })
    mocks.querySpatialBuildings.mockResolvedValue({ type: 'FeatureCollection', features: [feature('b1', 'BUILDING', 'c1'), feature('b2', 'BUILDING', 'c1')] })
    mocks.getRiskMap.mockResolvedValue({
      scopeKey: 'ALL', generatedAt: '2026-08-08T10:00:00+08:00', disclaimer: 'demo',
      buildings: [
        { buildingId: 'b1', buildingCode: 'B1', buildingName: '一号楼', communityId: 'c1', communityName: '一号小区', longitude: 114.31, latitude: 30.52, riskScore: 88, riskLevel: 'HIGH', completenessScore: 82, priorityScore: 91, priorityLevel: 'P1', freshness: 'CURRENT' },
        { buildingId: 'b2', buildingCode: 'B2', buildingName: '二号楼', communityId: 'c1', communityName: '一号小区', longitude: 114.32, latitude: 30.53, riskScore: 36, riskLevel: 'LOW', completenessScore: 70, priorityScore: 42, priorityLevel: 'P3', freshness: 'CURRENT' },
      ],
    })
  })

  it('loads verified polygons by bbox and merges risk rows by building id', async () => {
    const store = useSpatialMapStore()
    await store.loadViewport(viewport)
    expect(mocks.querySpatialCommunities).toHaveBeenCalledWith(viewport)
    expect(mocks.querySpatialBuildings).toHaveBeenCalledWith(viewport)
    expect(mocks.getRiskMap).toHaveBeenCalledWith('ALL', undefined)
    expect(store.communityFeatures.map((item) => item.id)).toEqual(['c1'])
    expect(store.visibleBuildings.map((item) => [item.feature.id, item.risk?.riskLevel])).toEqual([['b1', 'HIGH'], ['b2', 'LOW']])
  })

  it('keeps business buildings visible when the current viewport has no verified polygons', async () => {
    mocks.querySpatialCommunities.mockResolvedValue({ type: 'FeatureCollection', features: [] })
    mocks.querySpatialBuildings.mockResolvedValue({ type: 'FeatureCollection', features: [] })
    const store = useSpatialMapStore()
    await store.loadViewport(viewport)
    expect(store.visibleBuildings).toEqual([])
    expect(store.visibleRiskBuildings.map((item) => item.buildingId)).toEqual(['b1', 'b2'])
    expect(store.visibleRiskBuildings[0]?.longitude).toBe(114.31)
  })

  it('selected community scopes both polygon and risk loading', async () => {
    const store = useSpatialMapStore()
    store.selectCommunity('c1')
    await store.loadViewport(viewport)
    expect(mocks.querySpatialBuildings).toHaveBeenCalledWith({ ...viewport, communityId: 'c1' })
    expect(mocks.getRiskMap).toHaveBeenCalledWith('COMMUNITY', 'c1')
  })

  it('risk and text filters affect both business rows and polygon projections without mutating source polygons', async () => {
    const store = useSpatialMapStore()
    await store.loadViewport(viewport)
    store.setRiskLevels(['HIGH'])
    expect(store.visibleBuildings.map((item) => item.feature.id)).toEqual(['b1'])
    expect(store.visibleRiskBuildings.map((item) => item.buildingId)).toEqual(['b1'])
    expect(store.buildingFeatures).toHaveLength(2)
    store.setSearchKeyword('二号')
    expect(store.visibleBuildings).toEqual([])
    expect(store.visibleRiskBuildings).toEqual([])
    store.setRiskLevels([])
    expect(store.visibleBuildings.map((item) => item.feature.id)).toEqual(['b2'])
    expect(store.visibleRiskBuildings.map((item) => item.buildingId)).toEqual(['b2'])
  })

  it('keeps multi-building selection and clears it when community changes', () => {
    const store = useSpatialMapStore()
    store.toggleBuilding('b1'); store.toggleBuilding('b2')
    expect(store.selectedBuildingIds).toEqual(['b1', 'b2'])
    store.toggleBuilding('b1')
    expect(store.selectedBuildingIds).toEqual(['b2'])
    store.selectCommunity('c2')
    expect(store.selectedCommunityId).toBe('c2')
    expect(store.selectedBuildingIds).toEqual([])
  })

  it('ignores stale viewport responses when a newer request finishes first', async () => {
    let resolveOld!: (value: unknown) => void
    mocks.querySpatialBuildings.mockImplementationOnce(() => new Promise((resolve) => { resolveOld = resolve })).mockResolvedValueOnce({ type: 'FeatureCollection', features: [feature('new', 'BUILDING', 'c1')] })
    mocks.querySpatialCommunities.mockResolvedValue({ type: 'FeatureCollection', features: [] })
    mocks.getRiskMap.mockResolvedValue({ scopeKey: 'ALL', generatedAt: '', disclaimer: '', buildings: [] })
    const store = useSpatialMapStore()
    const oldRequest = store.loadViewport(viewport)
    const newRequest = store.loadViewport({ ...viewport, west: 114, east: 115 })
    await newRequest
    resolveOld({ type: 'FeatureCollection', features: [feature('old', 'BUILDING', 'c1')] })
    await oldRequest
    expect(store.buildingFeatures.map((item) => item.id)).toEqual(['new'])
  })
})
