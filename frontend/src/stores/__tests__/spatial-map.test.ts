import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/shared/api/endpoints/spatial', () => ({
  querySpatialCommunities: vi.fn(),
  querySpatialBuildings: vi.fn(),
}))
vi.mock('@/shared/api/endpoints/reports', () => ({
  getRiskMap: vi.fn(),
}))

import { querySpatialBuildings, querySpatialCommunities } from '@/shared/api/endpoints/spatial'
import { getRiskMap } from '@/shared/api/endpoints/reports'
import { useSpatialMapStore } from '@/stores/spatial-map'

const viewport = { west: 113, south: 27, east: 114, north: 28, zoom: 16 }
const community = {
  type: 'Feature',
  id: 'community-1',
  geometry: { type: 'Polygon', coordinates: [[[113, 27], [114, 27], [114, 28], [113, 27]]] },
  properties: {
    entityType: 'COMMUNITY', entityId: 'community-1', entityCode: 'C-1', name: '演示小区',
    communityId: 'community-1', status: 'VERIFIED', version: 1, coordinateSystem: 'GCJ02', sourceType: 'MANUAL_DRAW',
  },
}
const building = {
  type: 'Feature',
  id: 'building-1',
  geometry: { type: 'Polygon', coordinates: [[[113.1, 27.1], [113.2, 27.1], [113.2, 27.2], [113.1, 27.1]]] },
  properties: {
    entityType: 'BUILDING', entityId: 'building-1', entityCode: 'B-1', name: '1号楼',
    communityId: 'community-1', status: 'VERIFIED', version: 1, coordinateSystem: 'GCJ02', sourceType: 'MANUAL_DRAW',
  },
}

describe('spatial map store demo behavior', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('keeps verified spatial features when risk enhancement fails', async () => {
    vi.mocked(querySpatialCommunities).mockResolvedValue({ type: 'FeatureCollection', features: [community] } as never)
    vi.mocked(querySpatialBuildings).mockResolvedValue({ type: 'FeatureCollection', features: [building] } as never)
    vi.mocked(getRiskMap).mockRejectedValue(new Error('risk service unavailable'))

    const store = useSpatialMapStore()
    await expect(store.loadViewport(viewport)).resolves.toBeUndefined()

    expect(store.communityFeatures).toHaveLength(1)
    expect(store.buildingFeatures).toHaveLength(1)
    expect(store.visibleBuildings).toHaveLength(1)
    expect(store.visibleBuildings[0]?.risk).toBeUndefined()
    expect(store.riskRows).toEqual([])
    expect(store.errorMessage).toBe('')
  })

  it('single selection replaces the previous building', () => {
    const store = useSpatialMapStore()
    store.selectSingleBuilding('building-a')
    store.selectSingleBuilding('building-b')
    expect(store.selectedBuildingIds).toEqual(['building-b'])
  })
})