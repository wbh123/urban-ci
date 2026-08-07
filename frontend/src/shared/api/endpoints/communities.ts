import type { Schema } from '../schema'
import { apiGet } from '../client'

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

/** 查询当前账号有权访问的小区目录。 */
export function listCommunities(params: ListCommunitiesParams = {}): Promise<CommunityPageResponse> {
  return apiGet<CommunityPageResponse>('/api/v1/communities', { ...params })
}
