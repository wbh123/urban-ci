import type { Schema } from '../schema'
import { apiGet } from '../client'

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

export function listBuildings(params: ListBuildingsParams = {}): Promise<BuildingPageResponse> {
  return apiGet<BuildingPageResponse>('/api/v1/buildings', { ...params })
}
