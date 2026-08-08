import { describe, expect, it } from 'vitest'
import { routes } from './routes'

describe('小区与楼栋管理路由', () => {
  it('registers a protected console archive management page', () => {
    const consoleRoute = routes.find((route) => route.path === '/console')
    const child = consoleRoute?.children?.find((route) => route.path === 'archive-management')

    expect(child).toBeDefined()
    expect(child?.name).toBe('console-archive-management')
    expect(child?.meta?.title).toBe('小区与楼栋管理')
    expect(child?.meta?.allowedRoles).toEqual([
      'COMMUNITY_MANAGER',
      'GOVERNMENT_MANAGER',
      'ADMIN',
    ])
  })
})
