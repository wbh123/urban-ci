import { http } from 'msw'
import { okResponse, errorResponse, scenarioOf } from './helpers'

export const systemHandlers = [
  http.get('/api/v1/mock/health', () => okResponse({
    service: 'urban-safe-priority-mock',
    status: 'UP',
    mode: 'mock',
  })),

  http.get('/api/v1/system/health', ({ request }) => {
    const sc = scenarioOf(request)
    if (sc === 'unavailable') {
      return errorResponse('SERVICE_UNAVAILABLE', '服务或关键依赖不可用。', 503)
    }
    if (sc === 'server-error') {
      return errorResponse('INTERNAL_ERROR', '服务器内部错误。', 500)
    }
    return okResponse({
      service: 'urban-safe-priority-server',
      status: 'UP',
      version: '0.1.0',
    })
  }),
]
