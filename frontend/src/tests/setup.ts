import { afterAll, beforeAll, vi } from 'vitest'

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

beforeAll(() => {
  storage.clear()
})

afterAll(() => {
  if (server) {
    server.close()
    server = null
  }
})

/** 懒加载并启动 MSW server，仅在需要时使用。 */
export async function ensureMockServer() {
  if (!server) {
    const { server: s } = await import('@/mocks/server')
    server = s
    server.listen({ onUnhandledRequest: 'bypass' })
  }
  return server
}
