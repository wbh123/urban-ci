import { beforeEach, describe, expect, it, vi } from 'vitest'

const workerMocks = vi.hoisted(() => ({
  start: vi.fn(),
}))

vi.mock('@/mocks/browser', () => ({
  worker: {
    start: workerMocks.start,
  },
}))

import { enableMockWorker, handleMockUnhandledRequest, renderBootstrapError } from '@/app/bootstrap'

describe('MockBootstrapTest', () => {
  beforeEach(() => {
    workerMocks.start.mockReset()
    vi.unstubAllGlobals()
    document.body.innerHTML = '<div id="app"></div>'
  })

  it('启动 MSW 后校验 mock health', async () => {
    workerMocks.start.mockResolvedValue(undefined)
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      success: true,
      data: { status: 'UP' },
    }), { status: 200, headers: { 'Content-Type': 'application/json' } })))

    await enableMockWorker()

    expect(workerMocks.start).toHaveBeenCalledWith(expect.objectContaining({
      onUnhandledRequest: handleMockUnhandledRequest,
    }))
    expect(fetch).toHaveBeenCalledWith('/api/v1/mock/health')
  })

  it('mock 启动失败时抛出可读错误', async () => {
    workerMocks.start.mockRejectedValue(new Error('service worker missing'))

    await expect(enableMockWorker()).rejects.toThrow('Mock 服务启动失败：service worker missing')
  })

  it('未处理的 api 请求报错，静态资源旁路', () => {
    const warning = vi.fn()

    expect(() => handleMockUnhandledRequest(
      new Request('http://localhost/api/v1/not-implemented'),
      { warning },
    )).toThrow('未配置 API mock 处理器')

    handleMockUnhandledRequest(new Request('http://localhost/assets/app.js'), { warning })
    expect(warning).toHaveBeenCalledTimes(1)
  })

  it('启动失败会渲染到根节点', () => {
    renderBootstrapError(new Error('Mock 服务启动失败：health check failed'))

    expect(document.body.textContent).toContain('应用启动失败')
    expect(document.body.textContent).toContain('Mock 服务启动失败：health check failed')
  })
})
