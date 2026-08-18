import type { SpatialGeoJsonFeature } from '@/shared/api/endpoints/spatial'
import type { SpatialAmapPointFeature } from '@/shared/map/spatial-amap'
import type { SpatialBuildingProjection } from '@/stores/spatial-map'

export type ScopedSpatialAmapPointFeature = SpatialAmapPointFeature & {
  communityId?: string
}

export interface SpatialMapCollections {
  communities: SpatialGeoJsonFeature[]
  buildings: SpatialBuildingProjection[]
  communityPoints: ScopedSpatialAmapPointFeature[]
  buildingPoints: ScopedSpatialAmapPointFeature[]
}

export function filterSpatialMapCollectionsForCommunity(
  collections: SpatialMapCollections,
  communityId: string | null,
): SpatialMapCollections {
  if (!communityId) return collections
  return {
    communities: collections.communities.filter((item) => item.id === communityId),
    communityPoints: collections.communityPoints.filter((item) => item.id === communityId),
    buildings: collections.buildings.filter(({ feature, risk }) => (
      feature.properties.communityId === communityId || risk?.communityId === communityId
    )),
    buildingPoints: collections.buildingPoints.filter((item) => item.communityId === communityId),
  }
}
