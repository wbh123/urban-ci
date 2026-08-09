import { http } from 'msw'
import { okResponse, errorResponse, requireAuth, scenarioOf } from './helpers'
import { db, mapRuntimeConfig } from '../fixtures/data'
import type { CommunityLocationRequest } from '@/shared/api'

export const mapHandlers = [
  http.get('/api/v1/map/runtime-config', ({ request }) => {
    const unauth = requireAuth(request)
    if (unauth) return unauth
    const sc = scenarioOf(request)
    if (sc === 'server-error') return errorResponse('INTERNAL_ERROR', '服务器内部错误。', 500)
    if (sc === 'unavailable') return errorResponse('SERVICE_UNAVAILABLE', '服务暂不可用。', 503)
    return okResponse(mapRuntimeConfig)
  }),

  http.get('/api/v1/map/communities', ({ request }) => {
    const unauth = requireAuth(request)
    if (unauth) return unauth
    const sc = scenarioOf(request)
    if (sc === 'empty') return okResponse([])
    if (sc === 'server-error') return errorResponse('INTERNAL_ERROR', '服务器内部错误。', 500)
    return okResponse(db.communities)
  }),

  http.post('/api/v1/map/geocoding/preview', async ({ request }) => {
    const unauth = requireAuth(request)
    if (unauth) return unauth
    const body = (await request.json().catch(() => ({}))) as { address?: string }
    if (!body.address) {
      return errorResponse('BAD_REQUEST', '地址不能为空。', 400, [{ field: 'address', message: '不能为空' }])
    }
    return okResponse({
      formattedAddress: body.address,
      longitude: 113.13396,
      latitude: 27.82767,
      provider: 'MOCK',
      matchLevel: 'MOCK_PREVIEW',
      mock: true,
    })
  }),

  http.put('/api/v1/communities/:communityId/location', async ({ request, params }) => {
    const unauth = requireAuth(request)
    if (unauth) return unauth
    const communityId = params.communityId as string
    const body = (await request.json().catch(() => ({}))) as Partial<CommunityLocationRequest>
    const community = db.communities.find((c) => c.communityId === communityId)
    if (!community) return errorResponse('COMMUNITY_NOT_FOUND', '小区不存在。', 404)
    const longitude = body.longitude ?? 0
    const latitude = body.latitude ?? 0
    const provider = body.provider ?? 'MOCK'
    const coordinateSystem = body.coordinateSystem ?? (provider === 'AMAP' ? 'GCJ02' : 'UNKNOWN')
    community.longitude = longitude
    community.latitude = latitude
    if (body.formattedAddress) community.formattedAddress = body.formattedAddress
    return okResponse({
      communityId,
      longitude,
      latitude,
      formattedAddress: body.formattedAddress ?? community.formattedAddress ?? '',
      provider,
      coordinateSystem,
      matchLevel: body.matchLevel ?? 'MOCK_PREVIEW',
    })
  }),
]
