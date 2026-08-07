import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAuthStore } from '@/stores/auth'

vi.mock('@/shared/api', () => ({
  login: vi.fn(),
  getCurrentUser: vi.fn(),
  logout: vi.fn(),
}))

import * as api from '@/shared/api'

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('初始状态未认证', () => {
    const store = useAuthStore()
    expect(store.isAuthenticated).toBe(false)
    expect(store.accessToken).toBe('')
    expect(store.user).toBeNull()
  })

  it('setToken 持久化', () => {
    const store = useAuthStore()
    store.setToken('my-token')
    expect(store.accessToken).toBe('my-token')
    expect(localStorage.getItem('urban-safe-token')).toBe('my-token')
  })

  it('clearSession 清空令牌和用户并清除 localStorage', () => {
    const store = useAuthStore()
    store.setToken('my-token')
    store.clearSession()
    expect(store.accessToken).toBe('')
    expect(store.user).toBeNull()
    expect(store.isAuthenticated).toBe(false)
    expect(localStorage.getItem('urban-safe-token')).toBeNull()
  })

  it('login 后保存令牌与用户', async () => {
    vi.mocked(api.login).mockResolvedValue({
      accessToken: 'login-token',
      tokenType: 'Bearer',
      expiresInSeconds: 7200,
      user: { id: 'u1', username: 'admin', realName: '管理员', roles: ['ADMIN'] },
    })
    const store = useAuthStore()
    await store.login('admin', 'pass')
    expect(store.accessToken).toBe('login-token')
    expect(store.user?.username).toBe('admin')
    expect(store.isAuthenticated).toBe(true)
    expect(localStorage.getItem('urban-safe-token')).toBe('login-token')
  })

  it('logout 后清除会话', async () => {
    vi.mocked(api.logout).mockResolvedValue({ message: '退出成功' })
    const store = useAuthStore()
    store.setToken('t')
    expect(store.isAuthenticated).toBe(true)
    await store.logout()
    expect(store.isAuthenticated).toBe(false)
    expect(localStorage.getItem('urban-safe-token')).toBeNull()
  })

  it('logout 接口异常时仍清除本地会话', async () => {
    vi.mocked(api.logout).mockRejectedValue(new Error('server down'))
    const store = useAuthStore()
    store.setToken('t')
    await store.logout()
    expect(store.isAuthenticated).toBe(false)
  })
})
