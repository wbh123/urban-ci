import { describe, expect, it } from 'vitest'
import type { SpatialAmapPointFeature } from '@/shared/map/spatial-amap'
import type { SpatialBuildingProjection } from '@/stores/spatial-map'
import type { SpatialGeoJsonFeature } from '@/shared/api/endpoints/spatial'
import {
  filterSpatialMapCollectionsForCommunity,
  type ScopedSpatialAmapPointFeature,
} from './spatialMapCommunityScope'

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

function point(
  id: string,
  kind: SpatialAmapPointFeature['kind'],
  communityId?: string,
): ScopedSpatialAmapPointFeature {
  return { id, kind, communityId, longitude: 114.3, latitude: 30.5 }
}

describe('filterSpatialMapCollectionsForCommunity', () => {
  const collections = {
    communities: [community('community-a'), community('community-b')],
    buildings: [building('building-a', 'community-a'), building('building-b', 'community-b')],
    communityPoints: [point('community-a', 'COMMUNITY'), point('community-b', 'COMMUNITY')],
    buildingPoints: [point('building-a', 'BUILDING', 'community-a'), point('building-b', 'BUILDING', 'community-b')],
  }

  it('keeps only selected community collections', () => {
    const result = filterSpatialMapCollectionsForCommunity(collections, 'community-a')
    expect(result.communities.map((item) => item.id)).toEqual(['community-a'])
    expect(result.communityPoints.map((item) => item.id)).toEqual(['community-a'])
    expect(result.buildings.map((item) => item.feature.id)).toEqual(['building-a'])
    expect(result.buildingPoints.map((item) => item.id)).toEqual(['building-a'])
  })

  it('keeps city-level collections without a selected community', () => {
    const result = filterSpatialMapCollectionsForCommunity(collections, null)
    expect(result.communities).toHaveLength(2)
    expect(result.communityPoints).toHaveLength(2)
    expect(result.buildings).toHaveLength(2)
    expect(result.buildingPoints).toHaveLength(2)
  })
})
