import { describe, it, expect, afterEach, vi } from 'vitest'
import { getApiMode } from '@/shared/api'

describe('Mock 模式初始化', () => {
  afterEach(() => {
    vi.unstubAllEnvs()
  })

  it('未设置 VITE_API_MODE 时返回 mock', () => {
    vi.stubEnv('VITE_API_MODE', undefined)
    expect(getApiMode()).toBe('mock')
  })

  it('VITE_API_MODE=mock 返回 mock', () => {
    vi.stubEnv('VITE_API_MODE', 'mock')
    expect(getApiMode()).toBe('mock')
  })

  it('VITE_API_MODE=real 返回 real', () => {
    vi.stubEnv('VITE_API_MODE', 'real')
    expect(getApiMode()).toBe('real')
  })
})
