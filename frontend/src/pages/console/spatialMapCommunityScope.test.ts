import { describe, expect, it } from 'vitest'
import type { SpatialAmapPointFeature } from '@/shared/map/spatial-amap'
import type { SpatialBuildingProjection } from '@/stores/spatial-map'
import type { SpatialGeoJsonFeature } from '@/shared/api/endpoints/spatial'
import { filterSpatialMapCollectionsForCommunity } from './spatialMapCommunityScope'

function community(id: string): SpatialGeoJsonFeature {
  return {
    type: 'Feature', id, geometry: { type: 'Polygon', coordinates: [] },
    properties: { entityType: 'COMMUNITY', entityId: id, name: id },
  } as SpatialGeoJsonFeature
}

function building(id: string, communityId: string): SpatialBuildingProjection {
  return {
    feature: {
      type: 'Feature', id, geometry: { type: 'Polygon', coordinates: [] },
      properties: { entityType: 'BUILDING', entityId: id, communityId, name: id },
    } as SpatialGeoJsonFeature,
    risk: { buildingId: id, communityId } as SpatialBuildingProjection['risk'],
  }
}

function point(id: string, kind: SpatialAmapPointFeature['kind'], communityId?: string): SpatialAmapPointFeature {
  return { id, kind, communityId, longitude: 114.3, latitude: 30.5 }
}

describe('filterSpatialMapCollectionsForCommunity', () => {
  it('keeps only selected community collections', () => {
    const result = filterSpatialMapCollectionsForCommunity({
      communities: [community('community-a'), community('community-b')],
      buildings: [building('building-a', 'community-a'), building('building-b', 'community-b')],
      communityPoints: [point('community-a', 'COMMUNITY'), point('community-b', 'COMMUNITY')],
      buildingPoints: [point('building-a', 'BUILDING', 'community-a'), point('building-b', 'BUILDING', 'community-b')],
    }, 'community-a')

    expect(result.communities.map((item) => item.id)).toEqual(['community-a'])
    expect(result.communityPoints.map((item) => item.id)).toEqual(['community-a'])
    expect(result.buildings.map((item) => item.feature.id)).toEqual(['building-a'])
    expect(result.buildingPoints.map((item) => item.id)).toEqual(['building-a'])
  })
})
