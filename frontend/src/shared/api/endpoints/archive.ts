import { apiGet, apiPost, apiPut } from '../client'

export type ArchiveProvider = 'AMAP' | 'MANUAL' | 'IMPORT' | 'MOCK'
export type ArchiveCoordinateSystem = 'GCJ02' | 'WGS84' | 'BD09' | 'UNKNOWN'

export interface PlaceSearchRequest {
  keyword: string
  region?: string | null
  cityLimit?: boolean
  pageSize?: number
}

export interface MapPlaceCandidate {
  providerObjectId?: string | null
  name: string
  formattedAddress?: string | null
  province?: string | null
  city?: string | null
  district?: string | null
  adcode?: string | null
  citycode?: string | null
  longitude: number
  latitude: number
  provider: 'AMAP' | 'MOCK'
  coordinateSystem: 'GCJ02' | 'UNKNOWN'
  mock: boolean
}

export interface ReverseGeocodingRequest {
  longitude: number
  latitude: number
}

export interface ReverseGeocodingResult {
  formattedAddress?: string | null
  province?: string | null
  city?: string | null
  district?: string | null
  adcode?: string | null
  citycode?: string | null
  longitude: number
  latitude: number
  provider: 'AMAP' | 'MOCK'
  coordinateSystem: 'GCJ02' | 'UNKNOWN'
  nearestPoiId?: string | null
  nearestPoiName?: string | null
  mock: boolean
}

export interface BuildingLocationRequest {
  longitude: number
  latitude: number
  formattedAddress?: string | null
  provider?: ArchiveProvider
  matchLevel?: string | null
  mock?: boolean | null
  metadata?: Record<string, unknown> | null
}

export interface BuildingLocation {
  buildingId: string
  longitude: number
  latitude: number
  formattedAddress?: string | null
  provider: ArchiveProvider
  coordinateSystem: ArchiveCoordinateSystem
  matchLevel?: string | null
  metadata?: Record<string, unknown> | null
  updatedAt?: string | null
}

export function searchArchivePlaces(input: PlaceSearchRequest): Promise<MapPlaceCandidate[]> {
  return apiPost<MapPlaceCandidate[]>('/api/v1/map/places/search', input)
}

export function previewArchiveReverseGeocoding(
  input: ReverseGeocodingRequest,
): Promise<ReverseGeocodingResult> {
  return apiPost<ReverseGeocodingResult>('/api/v1/map/reverse-geocoding/preview', input)
}

export function getArchiveBuildingLocation(buildingId: string): Promise<BuildingLocation> {
  return apiGet<BuildingLocation>(
    `/api/v1/buildings/${encodeURIComponent(buildingId)}/location`,
  )
}

export function saveArchiveBuildingLocation(
  buildingId: string,
  input: BuildingLocationRequest,
): Promise<BuildingLocation> {
  return apiPut<BuildingLocation>(
    `/api/v1/buildings/${encodeURIComponent(buildingId)}/location`,
    input,
  )
}
