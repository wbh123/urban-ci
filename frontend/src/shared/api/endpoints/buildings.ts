import type { Schema } from '../schema'
import { apiGet, apiPost } from '../client'

export type CreateBuildingRequest = Schema<'CreateBuildingRequest'>
export type BuildingResponse = Schema<'BuildingResponse'> & { id: string }
export type BuildingListRow = Schema<'BuildingListRow'> & { id: string }
type GeneratedBuildingPageResponse = Schema<'BuildingPageResponse'>
export type BuildingPageResponse = Omit<GeneratedBuildingPageResponse, 'content'> & {
  content?: BuildingListRow[]
}
export type PageMetadata = Schema<'PageMetadata'>

export interface ListBuildingsParams {
  communityId?: string
  keyword?: string
  page?: number
  size?: number
  sort?: string
}

/** 创建楼栋档案。业务层后续定位与空间档案操作必须依赖返回的唯一标识。 */
export function createBuilding(input: CreateBuildingRequest): Promise<BuildingResponse> {
  return apiPost<BuildingResponse>('/api/v1/buildings', input)
}

/** 查询楼栋详情；统一楼栋档案必须使用详情接口而不是从目录行反推字段。 */
export function getBuilding(buildingId: string): Promise<BuildingResponse> {
  return apiGet<BuildingResponse>(`/api/v1/buildings/${encodeURIComponent(buildingId)}`)
}

/** 查询楼栋目录；目录项必须带唯一标识才能参与选择与对象范围操作。 */
export function listBuildings(params: ListBuildingsParams = {}): Promise<BuildingPageResponse> {
  return apiGet<BuildingPageResponse>('/api/v1/buildings', { ...params })
}
