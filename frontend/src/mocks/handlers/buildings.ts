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
]
