import type { components } from '../generated/spatial-schema'
import { apiGet, apiPost, apiPut } from '../client'

type SpatialSchema<K extends keyof components['schemas']> = components['schemas'][K]

export type SpatialGeometry = SpatialSchema<'SpatialGeometry'>
export type SpatialBoundary = SpatialSchema<'SpatialBoundaryResponse'>
export type SpatialBoundaryUpsertRequest = SpatialSchema<'SpatialBoundaryUpsertRequest'>
export type SpatialBoundaryVerifyRequest = SpatialSchema<'SpatialBoundaryVerifyRequest'>
export type SpatialBoundaryRevision = SpatialSchema<'SpatialBoundaryRevisionResponse'>
export type SpatialFeatureCollection = SpatialSchema<'SpatialFeatureCollection'>

export interface SpatialViewport {
  west: number
  south: number
  east: number
  north: number
  zoom: number
}

function viewportQuery(viewport: SpatialViewport, communityId?: string): string {
  const params = new URLSearchParams({
    west: String(viewport.west),
    south: String(viewport.south),
    east: String(viewport.east),
    north: String(viewport.north),
    zoom: String(viewport.zoom),
  })
  if (communityId) params.set('communityId', communityId)
  return params.toString()
}

export function getCommunityBoundary(communityId: string): Promise<SpatialBoundary> {
  return apiGet<SpatialBoundary>(`/api/v1/communities/${communityId}/boundary`)
}

export function saveCommunityBoundary(
  communityId: string,
  payload: SpatialBoundaryUpsertRequest,
): Promise<SpatialBoundary> {
  return apiPut<SpatialBoundary>(`/api/v1/communities/${communityId}/boundary`, payload)
}

export function verifyCommunityBoundary(
  communityId: string,
  payload: SpatialBoundaryVerifyRequest,
): Promise<SpatialBoundary> {
  return apiPost<SpatialBoundary>(
    `/api/v1/communities/${communityId}/boundary/verify`,
    payload,
  )
}

export function listCommunityBoundaryRevisions(
  communityId: string,
): Promise<SpatialBoundaryRevision[]> {
  return apiGet<SpatialBoundaryRevision[]>(
    `/api/v1/communities/${communityId}/boundary/revisions`,
  )
}

export function getBuildingBoundary(buildingId: string): Promise<SpatialBoundary> {
  return apiGet<SpatialBoundary>(`/api/v1/buildings/${buildingId}/boundary`)
}

export function saveBuildingBoundary(
  buildingId: string,
  payload: SpatialBoundaryUpsertRequest,
): Promise<SpatialBoundary> {
  return apiPut<SpatialBoundary>(`/api/v1/buildings/${buildingId}/boundary`, payload)
}

export function verifyBuildingBoundary(
  buildingId: string,
  payload: SpatialBoundaryVerifyRequest,
): Promise<SpatialBoundary> {
  return apiPost<SpatialBoundary>(
    `/api/v1/buildings/${buildingId}/boundary/verify`,
    payload,
  )
}

export function listBuildingBoundaryRevisions(
  buildingId: string,
): Promise<SpatialBoundaryRevision[]> {
  return apiGet<SpatialBoundaryRevision[]>(
    `/api/v1/buildings/${buildingId}/boundary/revisions`,
  )
}

export function listCommunityFeatures(
  viewport: SpatialViewport,
): Promise<SpatialFeatureCollection> {
  return apiGet<SpatialFeatureCollection>(
    `/api/v1/spatial/communities?${viewportQuery(viewport)}`,
  )
}

export function listBuildingFeatures(
  viewport: SpatialViewport,
  communityId?: string,
): Promise<SpatialFeatureCollection> {
  return apiGet<SpatialFeatureCollection>(
    `/api/v1/spatial/buildings?${viewportQuery(viewport, communityId)}`,
  )
}
