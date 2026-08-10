import { describe, expect, it } from 'vitest'
import { buildConsoleMenu, resolveActiveConsoleMenuPath } from './console-menu'
import type { RoleCode } from '@/shared/auth/access'

function pathsFor(...roles: RoleCode[]): string[] {
  return buildConsoleMenu(roles).flatMap((group) => group.items.map((item) => item.path))
}

describe('电脑管理端分组导航', () => {
  it('管理员菜单按建档、巡检、研判、空间治理和辅助能力顺序排列', () => {
    const groups = buildConsoleMenu(['ADMIN'])
    expect(groups.map((group) => group.label)).toEqual(['工作台', '基础建档', '巡检治理', '风险研判', '辅助决策', '系统管理'])
    expect(pathsFor('ADMIN')).toEqual([
      '/console',
      '/console/archive-management',
      '/console/spatial-archive',
      '/console/inspections',
      '/console/feedback',
      '/console/review',
      '/console/map',
      '/console/renewal-priorities',
      '/console/assessment-rules',
      '/console/knowledge',
      '/console/system-status',
    ])
    expect(pathsFor('ADMIN')).not.toContain('/console/legacy-workspace')
  })

  it('社区管理员保持权限不变但按业务使用顺序排列', () => {
    expect(pathsFor('COMMUNITY_MANAGER')).toEqual([
      '/console',
      '/console/archive-management',
      '/console/spatial-archive',
      '/console/inspections',
      '/console/feedback',
      '/console/map',
      '/console/assessment-rules',
    ])
  })

  it('专家按复核到地图再到辅助能力排列', () => {
    expect(pathsFor('EXPERT')).toEqual([
      '/console',
      '/console/review',
      '/console/map',
      '/console/assessment-rules',
      '/console/knowledge',
    ])
  })

  it('住建管理人员先看基础建档和公众反馈，再进入地图与更新优先级', () => {
    expect(pathsFor('GOVERNMENT_MANAGER')).toEqual([
      '/console',
      '/console/archive-management',
      '/console/spatial-archive',
      '/console/feedback',
      '/console/map',
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
