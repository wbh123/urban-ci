import type { Schema } from '../schema'
import { apiGet, apiPost, apiPut } from '../client'
import type { SpatialGeoJsonGeometry } from './spatial'

export type GeocodingRequest = Schema<'GeocodingRequest'>
export type CommunityLocationRequest = Schema<'CommunityLocationRequest'>
export type MapRuntimeConfig = Schema<'MapRuntimeConfig'>
export type CommunityPoint = Schema<'CommunityPoint'>
export type GeocodingResult = Schema<'GeocodingResult'>
export type CommunityLocation = Schema<'CommunityLocation'>
export type MapMode = MapRuntimeConfig['mode']

export type BoundaryCandidateReason =
  | 'DISABLED'
  | 'NO_RESULT'
  | 'AOI_UNAVAILABLE'
  | 'UPSTREAM_UNAVAILABLE'

export interface CommunityBoundaryCandidateRequest {
  communityName?: string | null
  address?: string | null
  city?: string | null
}

export interface CommunityBoundaryCandidate {
  available: boolean
  provider: 'AMAP'
  coordinateSystem?: 'GCJ02' | null
  sourceType?: 'AMAP_AOI' | null
  sourceId?: string | null
  name?: string | null
  address?: string | null
  geometry?: SpatialGeoJsonGeometry | null
  reasonCode?: BoundaryCandidateReason | null
  message?: string | null
}

export function getMapRuntimeConfig(): Promise<MapRuntimeConfig> {
  return apiGet<MapRuntimeConfig>('/api/v1/map/runtime-config')
}

export function listCommunityPoints(): Promise<CommunityPoint[]> {
  return apiGet<CommunityPoint[]>('/api/v1/map/communities')
}

export function previewGeocoding(payload: GeocodingRequest): Promise<GeocodingResult> {
  return apiPost<GeocodingResult>('/api/v1/map/geocoding/preview', payload)
}

export function previewCommunityBoundaryCandidate(
  payload: CommunityBoundaryCandidateRequest,
): Promise<CommunityBoundaryCandidate> {
  return apiPost<CommunityBoundaryCandidate>('/api/v1/map/boundary-candidates/community', payload)
}

export function saveCommunityLocation(
  communityId: string,
  payload: CommunityLocationRequest,
): Promise<CommunityLocation> {
  return apiPut<CommunityLocation>(`/api/v1/communities/${communityId}/location`, payload)
}
