import type { Schema } from '../schema'
import { apiGet, apiPost, apiPut } from '../client'

// 以下请求类型由 OpenAPI 生成。
export type GeocodingRequest = Schema<'GeocodingRequest'>
export type CommunityLocationRequest = Schema<'CommunityLocationRequest'>

// 适配类型：phase2 地图接口的「响应」在 openapi-phase2.yaml 中仅有 example，尚无正式 schema。
// 以下类型依据响应示例定义，作为适配层隔离暂时性差异；待后端补齐响应 schema 后由生成类型替换。
export type MapMode = 'MOCK' | 'LIVE'

export interface MapRuntimeConfig {
  enabled: boolean
  mode: MapMode
  provider: string
  jsApiKey: string
  serviceHost: string
  securityJsCodeExposed: boolean
  defaultCenter: { longitude: number; latitude: number }
  defaultZoom: number
}

export interface CommunityPoint {
  communityId: string
  communityName: string
  address?: string
  formattedAddress?: string
  longitude?: number
  latitude?: number
  provider?: string
  matchLevel?: string
}

export interface GeocodingResult {
  formattedAddress: string
  longitude: number
  latitude: number
  provider: string
  matchLevel: string
  mock: boolean
}

export interface CommunityLocation {
  communityId: string
  longitude: number
  latitude: number
  formattedAddress: string
  provider: string
  coordinateSystem: string
  matchLevel: string
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

export function saveCommunityLocation(
  communityId: string,
  payload: CommunityLocationRequest,
): Promise<CommunityLocation> {
  return apiPut<CommunityLocation>(`/api/v1/communities/${communityId}/location`, payload)
}
