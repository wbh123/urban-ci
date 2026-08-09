import { describe, expect, it } from 'vitest'
import { buildConsoleMenu, resolveActiveConsoleMenuPath } from './console-menu'
import type { RoleCode } from '@/shared/auth/access'

function pathsFor(...roles: RoleCode[]): string[] {
  return buildConsoleMenu(roles).flatMap((group) => group.items.map((item) => item.path))
}

describe('电脑管理端分组导航', () => {
  it('管理员拥有五个业务分组和全部现有入口', () => {
    const groups = buildConsoleMenu(['ADMIN'])
    expect(groups.map((group) => group.label)).toEqual([
      '工作台',
      '房屋治理',
      '风险研判',
      '辅助决策',
      '系统管理',
    ])
    expect(pathsFor('ADMIN')).toEqual([
      '/console',
      '/console/map',
      '/console/inspections',
      '/console/feedback',
      '/console/archive-management',
      '/console/spatial-archive',
      '/console/review',
      '/console/renewal-priorities',
      '/console/assessment-rules',
      '/console/knowledge',
      '/console/system-status',
      '/console/legacy-workspace',
    ])
  })

  it('社区管理员得到地图、巡检、公众反馈、档案管理、空间档案和评分规则入口', () => {
    const paths = pathsFor('COMMUNITY_MANAGER')
    expect(paths).toEqual([
      '/console',
      '/console/map',
      '/console/inspections',
      '/console/feedback',
      '/console/archive-management',
      '/console/spatial-archive',
      '/console/assessment-rules',
    ])
    expect(paths).not.toContain('/console/system-status')
  })

  it('专家得到正式地图、专业复核、评分规则和知识问答入口', () => {
    expect(pathsFor('EXPERT')).toEqual([
      '/console',
      '/console/map',
      '/console/review',
      '/console/assessment-rules',
      '/console/knowledge',
    ])
  })

  it('住建管理人员得到地图、公众反馈、档案管理、空间档案、风险总览和评分规则入口', () => {
    expect(pathsFor('GOVERNMENT_MANAGER')).toEqual([
      '/console',
      '/console/map',
      '/console/feedback',
      '/console/archive-management',
      '/console/spatial-archive',
      '/console/renewal-priorities',
      '/console/assessment-rules',
    ])
  })

  it('详情页按最长前缀高亮所属菜单', () => {
    const groups = buildConsoleMenu(['ADMIN'])
    expect(resolveActiveConsoleMenuPath('/console/review/abc', groups)).toBe('/console/review')
    expect(resolveActiveConsoleMenuPath('/console/buildings/1/assessment', groups)).toBe('/console')
    expect(resolveActiveConsoleMenuPath('/console/renewal-priorities', groups)).toBe('/console/renewal-priorities')
    expect(resolveActiveConsoleMenuPath('/console/map', groups)).toBe('/console/map')
    expect(resolveActiveConsoleMenuPath('/console/archive-management', groups)).toBe('/console/archive-management')
    expect(resolveActiveConsoleMenuPath('/console/spatial-archive', groups)).toBe('/console/spatial-archive')
  })
})
