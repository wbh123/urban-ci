import { describe, expect, it } from 'vitest'
import {
  canEnterClient,
  permissionsForRoles,
  resolveDefaultEntry,
  type AuthPrincipal,
  type RoleCode,
} from '@/shared/auth/access'

function principal(...roles: RoleCode[]): AuthPrincipal {
  return { roles, permissions: permissionsForRoles(roles) }
}

describe('角色客户端访问基线', () => {
  it('管理员同时可以进入电脑端和移动端', () => {
    const user = principal('ADMIN')
    expect(canEnterClient(user, 'CONSOLE')).toBe(true)
    expect(canEnterClient(user, 'MOBILE')).toBe(true)
    expect(resolveDefaultEntry(user, 'CONSOLE')).toBe('/console')
    expect(resolveDefaultEntry(user, 'MOBILE')).toBe('/mobile')
  })

  it('社区管理员默认进入电脑端巡检组织管理', () => {
    const user = principal('COMMUNITY_MANAGER')
    expect(canEnterClient(user, 'CONSOLE')).toBe(true)
    expect(canEnterClient(user, 'MOBILE')).toBe(false)
    expect(resolveDefaultEntry(user, 'CONSOLE')).toBe('/console/inspections')
  })

  it('专家默认进入专业复核且不能进入移动端', () => {
    const user = principal('EXPERT')
    expect(resolveDefaultEntry(user, 'CONSOLE')).toBe('/console/review')
    expect(resolveDefaultEntry(user, 'MOBILE')).toBe('/client-mismatch?expected=MOBILE')
  })

  it('住建管理人员进入电脑端但不进入移动端', () => {
    const user = principal('GOVERNMENT_MANAGER')
    expect(resolveDefaultEntry(user, 'CONSOLE')).toBe('/console')
    expect(resolveDefaultEntry(user, 'MOBILE')).toBe('/client-mismatch?expected=MOBILE')
  })

  it('巡检人员默认进入移动端巡检任务', () => {
    const user = principal('PROPERTY_INSPECTOR')
    expect(resolveDefaultEntry(user, 'MOBILE')).toBe('/mobile/tasks')
    expect(resolveDefaultEntry(user, 'CONSOLE')).toBe('/client-mismatch?expected=CONSOLE')
  })

  it('处置人员默认进入移动端问题处置', () => {
    const user = principal('DISPOSAL_OPERATOR')
    expect(resolveDefaultEntry(user, 'MOBILE')).toBe('/mobile/disposal')
    expect(resolveDefaultEntry(user, 'CONSOLE')).toBe('/client-mismatch?expected=CONSOLE')
  })

  it('公众访问不依赖内部角色', () => {
    expect(canEnterClient(null, 'PUBLIC')).toBe(true)
  })
})
