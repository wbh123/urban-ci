import { http } from 'msw'
import { okResponse, errorResponse, requireAuth, scenarioOf } from './helpers'
import { db } from '../fixtures/data'

export const buildingHandlers = [
  http.get('/api/v1/buildings', ({ request }) => {
    const unauth = requireAuth(request)
    if (unauth) return unauth
    const sc = scenarioOf(request)
    if (sc === 'empty') {
      return okResponse({ content: [], page: { page: 0, size: 20, totalElements: 0, totalPages: 0 } })
    }
    if (sc === 'server-error') return errorResponse('INTERNAL_ERROR', '服务器内部错误。', 500)
    const url = new URL(request.url)
    const communityId = url.searchParams.get('communityId')
    const rows = communityId
      ? db.buildings.filter((b) => b.communityId === communityId)
      : db.buildings
    return okResponse({
      content: rows,
      page: {
        page: 0,
        size: 20,
        totalElements: rows.length,
        totalPages: rows.length > 0 ? 1 : 0,
      },
    })
  }),

  http.get('/api/v1/buildings/:buildingId', ({ request, params }) => {
    const unauth = requireAuth(request)
    if (unauth) return unauth
    const buildingId = params.buildingId as string
    const building = db.buildings.find((b) => b.id === buildingId)
    if (!building) return errorResponse('BUILDING_NOT_FOUND', '楼栋不存在。', 404)
    return okResponse({
      id: building.id,
      buildingCode: building.buildingCode,
      buildingName: building.buildingName,
      communityId: building.communityId,
      constructionYear: building.constructionYear,
      floorCount: building.floorCount,
      residentCount: building.residentCount,
      status: building.status,
      createdAt: building.createdAt,
    })
  }),
]
