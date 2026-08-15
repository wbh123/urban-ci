import { describe, expect, it } from 'vitest'
import {
  canEnterClient,
  resolveDefaultEntry,
  type AuthPrincipal,
} from './access'

const principal = (roles: AuthPrincipal['roles']): AuthPrincipal => ({ roles, permissions: [] })

describe('角色化客户端入口', () => {
  it('物业巡检人员只能进入移动作业端', () => {
    const user = principal(['PROPERTY_INSPECTOR'])
    expect(canEnterClient(user, 'MOBILE')).toBe(true)
    expect(canEnterClient(user, 'CONSOLE')).toBe(false)
    expect(resolveDefaultEntry(user, 'MOBILE')).toBe('/mobile/tasks')
  })

  it('专业复核人员进入电脑管理端后先到角色工作台', () => {
    const user = principal(['EXPERT'])
    expect(canEnterClient(user, 'CONSOLE')).toBe(true)
    expect(canEnterClient(user, 'MOBILE')).toBe(false)
    expect(resolveDefaultEntry(user, 'CONSOLE')).toBe('/console')
  })

  it('社区管理人员进入电脑管理端后先到角色工作台', () => {
    const user = principal(['COMMUNITY_MANAGER'])
    expect(canEnterClient(user, 'CONSOLE')).toBe(true)
    expect(resolveDefaultEntry(user, 'CONSOLE')).toBe('/console')
  })

  it('管理员可以进入两个客户端并优先进入管理端', () => {
    const user = principal(['ADMIN'])
    expect(canEnterClient(user, 'MOBILE')).toBe(true)
    expect(canEnterClient(user, 'CONSOLE')).toBe(true)
    expect(resolveDefaultEntry(user)).toBe('/console')
  })

  it('无适配角色时返回未授权入口', () => {
    expect(resolveDefaultEntry(principal([]))).toBe('/unauthorized')
  })
})
