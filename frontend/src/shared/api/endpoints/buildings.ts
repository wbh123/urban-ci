import type { Schema } from '../schema'
import { apiGet, apiPost } from '../client'

export type CreateBuildingRequest = Schema<'CreateBuildingRequest'>
export type BuildingResponse = Schema<'BuildingResponse'>
export type BuildingListRow = Schema<'BuildingListRow'>
export type BuildingPageResponse = Schema<'BuildingPageResponse'>
export type PageMetadata = Schema<'PageMetadata'>

export interface ListBuildingsParams {
  communityId?: string
  keyword?: string
  page?: number
  size?: number
  sort?: string
}

export function createBuilding(input: CreateBuildingRequest): Promise<BuildingResponse> {
  return apiPost<BuildingResponse>('/api/v1/buildings', input)
}

export function listBuildings(params: ListBuildingsParams = {}): Promise<BuildingPageResponse> {
  return apiGet<BuildingPageResponse>('/api/v1/buildings', { ...params })
}
