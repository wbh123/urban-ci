import { HttpResponse } from 'msw'
import { generateRequestId } from '@/shared/api'

export type Scenario =
  | 'unauthorized'
  | 'forbidden'
  | 'not-found'
  | 'conflict'
  | 'server-error'
  | 'unavailable'
  | 'delay'
  | 'empty'
  | null

/** 开发态 Mock 控制头，用于触发错误/边界场景，非后端业务字段。 */
export function scenarioOf(request: Request): Scenario {
  const value = request.headers.get('x-mock-scenario')
  if (!value) return null
  return value as Scenario
}

function envelope(data: unknown) {
  return {
    success: true,
    data,
    error: null,
    requestId: generateRequestId(),
    timestamp: new Date().toISOString(),
  }
}

function errorEnvelope(code: string, message: string, fieldErrors: { field: string; message: string }[] = []) {
  return {
    success: false,
    data: null,
    error: { code, message, fieldErrors },
    requestId: generateRequestId(),
    timestamp: new Date().toISOString(),
  }
}

export function okResponse(data: unknown, status = 200) {
  return HttpResponse.json(envelope(data), { status })
}

export function errorResponse(
  code: string,
  message: string,
  status: number,
  fieldErrors: { field: string; message: string }[] = [],
) {
  return HttpResponse.json(errorEnvelope(code, message, fieldErrors), { status })
}

/** 校验 Bearer 令牌；通过返回 null，否则返回 401 响应。 */
export function requireAuth(request: Request): Response | null {
  const auth = request.headers.get('authorization') ?? ''
  const token = auth.startsWith('Bearer ') ? auth.slice(7).trim() : ''
  if (!token) {
    return errorResponse('UNAUTHORIZED', '未登录或登录已过期。', 401)
  }
  return null
}
