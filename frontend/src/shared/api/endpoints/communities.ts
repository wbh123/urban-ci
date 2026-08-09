import type { Schema } from '../schema'
import { apiGet, apiPost } from '../client'

export type CreateCommunityRequest = Schema<'CreateCommunityRequest'>
export type CommunityResponse = Schema<'CommunityResponse'> & { id: string }
export type CommunityListRow = Schema<'CommunityListRow'> & { id: string }
type GeneratedCommunityPageResponse = Schema<'CommunityPageResponse'>
export type CommunityPageResponse = Omit<GeneratedCommunityPageResponse, 'content'> & {
  content?: CommunityListRow[]
}

export interface ListCommunitiesParams {
  keyword?: string
  administrativeRegion?: string
  status?: string
  page?: number
  size?: number
  sort?: string
}

/** 创建小区档案。业务层后续操作必须依赖返回的唯一标识。 */
export function createCommunity(input: CreateCommunityRequest): Promise<CommunityResponse> {
  return apiPost<CommunityResponse>('/api/v1/communities', input)
}

/** 查询小区详情；楼栋统一档案使用该接口补充所属小区业务名称。 */
export function getCommunity(communityId: string): Promise<CommunityResponse> {
  return apiGet<CommunityResponse>(`/api/v1/communities/${encodeURIComponent(communityId)}`)
}

/** 查询当前账号有权访问的小区目录；目录项必须带唯一标识才能被选择。 */
export function listCommunities(params: ListCommunitiesParams = {}): Promise<CommunityPageResponse> {
  return apiGet<CommunityPageResponse>('/api/v1/communities', { ...params })
}
