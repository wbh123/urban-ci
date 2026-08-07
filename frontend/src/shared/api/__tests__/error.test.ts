import { describe, it, expect } from 'vitest'
import { toAppError, AppError, mapStatusToCode, mapStatusToMessage } from '@/shared/api'

describe('toAppError', () => {
  it('直接透传 AppError', () => {
    const original = new AppError({ message: 'test', code: 'X', status: 400 })
    expect(toAppError(original)).toBe(original)
  })

  it('AxiosError(401) 转为 AppError(401)', () => {
    const err = mockAxiosError(401, {
      success: false,
      data: null,
      error: { code: 'UNAUTHORIZED', message: '未认证', fieldErrors: [] },
      requestId: 'r1',
      timestamp: 't1',
    })
    const result = toAppError(err)
    expect(result).toBeInstanceOf(AppError)
    expect(result.status).toBe(401)
    expect(result.isUnauthorized).toBe(true)
    expect(result.code).toBe('UNAUTHORIZED')
    expect(result.requestId).toBe('r1')
  })

  it('AxiosError(0) 转为网络错误', () => {
    const err = mockAxiosError(0, undefined, false)
    const result = toAppError(err)
    expect(result.isNetwork).toBe(true)
    expect(result.code).toBe('NETWORK_ERROR')
  })

  it('AxiosError(503) 转为服务不可用错误', () => {
    const err = mockAxiosError(503, {
      success: false,
      data: null,
      error: { code: 'SERVICE_UNAVAILABLE', message: '服务暂不可用', fieldErrors: [] },
    })
    const result = toAppError(err)
    expect(result.status).toBe(503)
    expect(result.code).toBe('SERVICE_UNAVAILABLE')
  })

  it('普通 Error 转为 AppError', () => {
    const err = new Error('boom')
    const result = toAppError(err)
    expect(result).toBeInstanceOf(AppError)
    expect(result.message).toBe('boom')
    expect(result.status).toBe(0)
  })

  it('未知类型转为通用 AppError', () => {
    const result = toAppError('just a string')
    expect(result).toBeInstanceOf(AppError)
    expect(result.code).toBe('UNKNOWN')
  })
})

describe('mapStatusToCode / mapStatusToMessage', () => {
  it('映射已知状态码', () => {
    expect(mapStatusToCode(503)).toBe('SERVICE_UNAVAILABLE')
    expect(mapStatusToCode(404)).toBe('NOT_FOUND')
    expect(mapStatusToCode(409)).toBe('CONFLICT')
  })

  it('未知状态码映射为 HTTP_ERROR', () => {
    expect(mapStatusToCode(418)).toBe('HTTP_ERROR')
    expect(mapStatusToMessage(418)).toContain('418')
  })
})

function mockAxiosError(status: number, data?: unknown, hasResponse = true) {
  return {
    isAxiosError: true,
    name: 'AxiosError',
    message: 'mock error',
    response: hasResponse
      ? { status, data, headers: {} as Record<string, string>, config: {} }
      : undefined,
    request: { path: '/test' },
  }
}
