import type { Schema } from '../schema'
import { apiGet, apiPost, apiPut } from '../client'

export type GeocodingRequest = Schema<'GeocodingRequest'>
type GeneratedCommunityLocationRequest = Schema<'CommunityLocationRequest'>
export type CommunityLocationRequest = GeneratedCommunityLocationRequest & {
  coordinateSystem?: 'GCJ02' | 'WGS84' | 'BD09' | 'UNKNOWN'
}
export type MapRuntimeConfig = Schema<'MapRuntimeConfig'>
export type CommunityPoint = Schema<'CommunityPoint'>
export type GeocodingResult = Schema<'GeocodingResult'>
export type CommunityLocation = Schema<'CommunityLocation'>
export type MapMode = MapRuntimeConfig['mode']

export function getMapRuntimeConfig(): Promise<MapRuntimeConfig> {
  return apiGet<MapRuntimeConfig>('/api/v1/map/runtime-config')
}

export function listCommunityPoints(): Promise<CommunityPoint[]> {
  return apiGet<CommunityPoint[]>('/api/v1/map/communities')
}

export function previewGeocoding(payload: GeocodingRequest): Promise<GeocodingResult> {
  return apiPost<GeocodingResult>('/api/v1/map/geocoding/preview', payload)
}

export function saveCommunityLocation(
  communityId: string,
  payload: CommunityLocationRequest,
): Promise<CommunityLocation> {
  return apiPut<CommunityLocation>(`/api/v1/communities/${communityId}/location`, payload)
}
