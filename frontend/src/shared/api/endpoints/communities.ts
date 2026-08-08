import type { Schema } from '../schema'
import { apiGet, apiPost } from '../client'

export type CreateCommunityRequest = Schema<'CreateCommunityRequest'>
export type CommunityResponse = Schema<'CommunityResponse'>
export type CommunityListRow = Schema<'CommunityListRow'>
export type CommunityPageResponse = Schema<'CommunityPageResponse'>

export interface ListCommunitiesParams {
  keyword?: string
  administrativeRegion?: string
  status?: string
  page?: number
  size?: number
  sort?: string
}

/** 创建小区档案。 */
export function createCommunity(input: CreateCommunityRequest): Promise<CommunityResponse> {
  return apiPost<CommunityResponse>('/api/v1/communities', input)
}

/** 查询当前账号有权访问的小区目录。 */
export function listCommunities(params: ListCommunitiesParams = {}): Promise<CommunityPageResponse> {
  return apiGet<CommunityPageResponse>('/api/v1/communities', { ...params })
}
