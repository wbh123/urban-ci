import { describe, expect, it } from 'vitest'
import { routes } from './routes'

function consoleChildren() {
  return routes.find((route) => route.path === '/console')?.children || []
}

function childPaths(rootPath: string): string[] {
  const root = routes.find((route) => route.path === rootPath)
  return (root?.children || []).map((route) => route.path)
}

describe('第四阶段评分路由', () => {
  it('注册管理端评分详情、排行榜和规则页面', () => {
    expect(childPaths('/console')).toEqual(expect.arrayContaining(['buildings/:buildingId/assessment', 'renewal-priorities', 'assessment-rules']))
  })
  it('移动端只注册楼栋评分摘要，不注册规则管理和排行榜', () => {
    const paths = childPaths('/mobile')
    expect(paths).toContain('buildings/:buildingId/assessment')
    expect(paths).not.toContain('assessment-rules')
    expect(paths).not.toContain('renewal-priorities')
  })
})

describe('R3 空间地图路由', () => {
  it('注册正式地图和空间档案页面', () => {
    expect(childPaths('/console')).toEqual(expect.arrayContaining(['map', 'spatial-archive']))
  })

  it('正式地图继承管理端五类读取角色并使用全宽内容区', () => {
    const mapRoute = consoleChildren().find((route) => route.path === 'map')
    expect(mapRoute?.name).toBe('console-spatial-map')
    expect(mapRoute?.meta?.allowedRoles).toEqual([
      'EXPERT',
      'PROFESSIONAL_REVIEWER',
      'COMMUNITY_MANAGER',
      'GOVERNMENT_MANAGER',
      'ADMIN',
    ])
    expect(mapRoute?.meta?.fullWidth).toBe(true)
  })

  it('空间档案只允许具备档案管理权限的角色进入', () => {
    const archiveRoute = consoleChildren().find((route) => route.path === 'spatial-archive')
    expect(archiveRoute?.name).toBe('console-spatial-archive')
    expect(archiveRoute?.meta?.allowedRoles).toEqual([
      'COMMUNITY_MANAGER',
      'GOVERNMENT_MANAGER',
      'ADMIN',
    ])
  })
})
