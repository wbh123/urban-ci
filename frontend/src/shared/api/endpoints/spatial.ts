import { apiGet, apiPost, apiPut } from '../client'

export type SpatialBoundaryStatus = 'UNVERIFIED' | 'VERIFIED' | 'REJECTED'
export type SpatialBoundaryEntityType = 'COMMUNITY' | 'BUILDING'
export type SpatialCoordinateSystem = 'GCJ02' | 'WGS84' | 'BD09'
export type SpatialBoundarySourceType = 'AMAP_AOI' | 'MANUAL_EDIT' | 'MANUAL_DRAW' | 'GEOJSON_IMPORT'

export interface SpatialGeoJsonGeometry {
  type: 'Polygon' | 'MultiPolygon'
  coordinates: unknown[]
  [key: string]: unknown
}

export interface SpatialBoundaryWriteInput {
  expectedVersion: number
  sourceType: SpatialBoundarySourceType
  sourceProvider?: string | null
  sourceObjectId?: string | null
  sourceCoordinateSystem: SpatialCoordinateSystem
  sourceGeometry: SpatialGeoJsonGeometry
  displayCoordinateSystem: SpatialCoordinateSystem
  displayGeometry: SpatialGeoJsonGeometry
  remark?: string | null
}

export interface SpatialBoundaryReviewInput {
  expectedVersion: number
  remark?: string | null
}

export interface SpatialBoundaryView {
  id: string
  entityType: SpatialBoundaryEntityType
  entityId: string
  sourceType: SpatialBoundarySourceType
  sourceProvider?: string | null
  sourceObjectId?: string | null
  sourceCoordinateSystem: SpatialCoordinateSystem
  sourceGeometry: SpatialGeoJsonGeometry
  displayCoordinateSystem: SpatialCoordinateSystem
  displayGeometry: SpatialGeoJsonGeometry
  status: SpatialBoundaryStatus
  version: number
  verifiedBy?: string | null
  verifiedAt?: string | null
  remark?: string | null
  createdAt: string
  updatedAt: string
}

export interface SpatialFeatureProperties {
  entityType: SpatialBoundaryEntityType
  entityId: string
  entityCode: string
  name: string
  communityId?: string | null
  status: 'VERIFIED'
  version: number
  coordinateSystem: SpatialCoordinateSystem
  sourceType: SpatialBoundarySourceType
}

export interface SpatialGeoJsonFeature {
  type: 'Feature'
  id: string
  geometry: SpatialGeoJsonGeometry
  properties: SpatialFeatureProperties
}

export interface SpatialGeoJsonFeatureCollection {
  type: 'FeatureCollection'
  features: SpatialGeoJsonFeature[]
}

export interface SpatialBboxQuery {
  west: number
  south: number
  east: number
  north: number
  zoom: number
}

export interface SpatialBuildingBboxQuery extends SpatialBboxQuery {
  communityId?: string
}

export function querySpatialCommunities(
  query: SpatialBboxQuery,
): Promise<SpatialGeoJsonFeatureCollection> {
  return apiGet<SpatialGeoJsonFeatureCollection>(
    '/api/v1/spatial/communities',
    query as unknown as Record<string, unknown>,
  )
}

export function querySpatialBuildings(
  query: SpatialBuildingBboxQuery,
): Promise<SpatialGeoJsonFeatureCollection> {
  return apiGet<SpatialGeoJsonFeatureCollection>(
    '/api/v1/spatial/buildings',
    query as unknown as Record<string, unknown>,
  )
}

export function getCommunityBoundary(communityId: string): Promise<SpatialBoundaryView> {
  return apiGet<SpatialBoundaryView>(
    `/api/v1/spatial/communities/${encodeURIComponent(communityId)}/boundary`,
  )
}

export function upsertCommunityBoundary(
  communityId: string,
  input: SpatialBoundaryWriteInput,
): Promise<SpatialBoundaryView> {
  return apiPut<SpatialBoundaryView>(
    `/api/v1/spatial/communities/${encodeURIComponent(communityId)}/boundary`,
    input,
  )
}

export function verifyCommunityBoundary(
  communityId: string,
  input: SpatialBoundaryReviewInput,
): Promise<SpatialBoundaryView> {
  return apiPost<SpatialBoundaryView>(
    `/api/v1/spatial/communities/${encodeURIComponent(communityId)}/boundary/verify`,
    input,
  )
}

export function rejectCommunityBoundary(
  communityId: string,
  input: SpatialBoundaryReviewInput,
): Promise<SpatialBoundaryView> {
  return apiPost<SpatialBoundaryView>(
    `/api/v1/spatial/communities/${encodeURIComponent(communityId)}/boundary/reject`,
    input,
  )
}

export function getBuildingBoundary(buildingId: string): Promise<SpatialBoundaryView> {
  return apiGet<SpatialBoundaryView>(
    `/api/v1/spatial/buildings/${encodeURIComponent(buildingId)}/boundary`,
  )
}

export function upsertBuildingBoundary(
  buildingId: string,
  input: SpatialBoundaryWriteInput,
): Promise<SpatialBoundaryView> {
  return apiPut<SpatialBoundaryView>(
    `/api/v1/spatial/buildings/${encodeURIComponent(buildingId)}/boundary`,
    input,
  )
}

export function verifyBuildingBoundary(
  buildingId: string,
  input: SpatialBoundaryReviewInput,
): Promise<SpatialBoundaryView> {
  return apiPost<SpatialBoundaryView>(
    `/api/v1/spatial/buildings/${encodeURIComponent(buildingId)}/boundary/verify`,
    input,
  )
}

export function rejectBuildingBoundary(
  buildingId: string,
  input: SpatialBoundaryReviewInput,
): Promise<SpatialBoundaryView> {
  return apiPost<SpatialBoundaryView>(
    `/api/v1/spatial/buildings/${encodeURIComponent(buildingId)}/boundary/reject`,
    input,
  )
}
