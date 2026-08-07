import { describe, it, expect, vi, beforeAll, afterAll, afterEach } from 'vitest'
import { http } from 'msw'
import { setupServer } from 'msw/node'
import { configureInterceptors, resetInterceptors, toAppError } from '@/shared/api'
import { request, httpClient } from '@/shared/api/client'

// 仅用于本次测试的临时 MSW 服务器，返回受控的 401 / 200。
const server = setupServer()

describe('401 会话清理', () => {
  beforeAll(() => server.listen({ onUnhandledRequest: 'bypass' }))
  afterEach(() => {
    resetInterceptors()
    server.resetHandlers()
  })
  afterAll(() => server.close())

  it('401 响应时调用 onUnauthorized', async () => {
    server.use(
      http.get('/test-unauth', () => {
        return Response.json(
          {
            success: false,
            data: null,
            error: { code: 'UNAUTHORIZED', message: '未认证', fieldErrors: [] },
            requestId: 'r1',
            timestamp: 't1',
          },
          { status: 401 },
        )
      }),
    )

    const onUnauthorized = vi.fn()
    configureInterceptors({ onUnauthorized })

    // 使用 fetch 直接请求，MSW server 返回 401
    // 但 client 模块使用相对 URL（mock 模式），httpClient.baseURL 为空。
    // 临时设置 baseURL 以便在 node 中解析 URL。
    httpClient.defaults.baseURL = 'http://localhost'

    try {
      await request({ method: 'get', url: '/test-unauth' })
    } catch {
      // 预期抛出 AppError
    }

    expect(onUnauthorized).toHaveBeenCalledOnce()
    httpClient.defaults.baseURL = ''
  })

  it('200 成功时不调用 onUnauthorized', async () => {
    server.use(
      http.get('/test-success', () => {
        return Response.json(
          { success: true, data: { ok: true }, error: null, requestId: 'r2', timestamp: 't2' },
          { status: 200 },
        )
      }),
    )

    const onUnauthorized = vi.fn()
    configureInterceptors({ onUnauthorized })
    httpClient.defaults.baseURL = 'http://localhost'

    const res = await request({ method: 'get', url: '/test-success' })
    expect(res).toEqual({ ok: true })
    expect(onUnauthorized).not.toHaveBeenCalled()
    httpClient.defaults.baseURL = ''
  })

  it('toAppError 正确识别 401', () => {
    const err = toAppError(createAxiosErrorLike(401))
    expect(err.status).toBe(401)
    expect(err.isUnauthorized).toBe(true)
    expect(err.code).toBe('UNAUTHORIZED')
  })
})

function createAxiosErrorLike(status: number) {
  return {
    isAxiosError: true,
    response: {
      status,
      data: {
        success: false,
        data: null,
        error: { code: 'UNAUTHORIZED', message: '未登录', fieldErrors: [] },
        requestId: 'r',
        timestamp: 't',
      },
      headers: {},
      config: {} as never,
    },
    request: {},
  }
}
