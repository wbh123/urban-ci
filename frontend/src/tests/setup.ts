import { afterAll, beforeAll, vi } from 'vitest'
import { httpClient } from '@/shared/api/client'

// jsdom 在某些配置下不暴露全局 localStorage，在此提供兼容垫片。
const storage = new Map<string, string>()
vi.stubGlobal('localStorage', {
  getItem: (key: string): string | null => storage.get(key) ?? null,
  setItem: (key: string, value: string): void => {
    storage.set(key, value)
  },
  removeItem: (key: string): void => {
    storage.delete(key)
  },
  clear: (): void => {
    storage.clear()
  },
  get length(): number {
    return storage.size
  },
  key: (index: number): string | null => {
    const keys = Array.from(storage.keys())
    return keys[index] ?? null
  },
})

// Mock Service Worker server for Node tests that exercise API endpoints.
let server: ReturnType<typeof import('msw/node').setupServer> | null = null
let mockHttpClientConfigured = false
let previousApiBaseUrl = httpClient.defaults.baseURL
let previousApiAdapter = httpClient.defaults.adapter

beforeAll(() => {
  storage.clear()
})

afterAll(() => {
  if (server) {
    server.close()
    server = null
  }
  if (mockHttpClientConfigured) {
    httpClient.defaults.baseURL = previousApiBaseUrl
    httpClient.defaults.adapter = previousApiAdapter
    mockHttpClientConfigured = false
  }
})

/**
 * 懒加载并启动 Mock Service Worker server，仅在需要时使用。
 *
 * 浏览器 Mock 模式继续使用相对 `/api/...` 请求；Vitest/jsdom 中则把 Axios 固定为
 * `http://localhost` + fetch adapter。这样请求始终经过 Node 的 fetch 拦截链，避免
 * jsdom 的 XMLHttpRequest 实现或本机网络/代理差异把本应由 MSW 接管的请求送到网络。
 * 未注册的请求在测试环境中直接失败，禁止静默访问真实网络。
 */
export async function ensureMockServer() {
  if (!server) {
    const { server: s } = await import('@/mocks/server')
    server = s
    server.listen({ onUnhandledRequest: 'error' })
  }

  if (!mockHttpClientConfigured) {
    previousApiBaseUrl = httpClient.defaults.baseURL
    previousApiAdapter = httpClient.defaults.adapter
    httpClient.defaults.baseURL = 'http://localhost'
    httpClient.defaults.adapter = 'fetch'
    mockHttpClientConfigured = true
  }

  return server
}