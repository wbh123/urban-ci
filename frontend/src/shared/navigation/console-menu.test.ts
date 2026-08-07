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
      '/console/inspections',
      '/console/feedback',
      '/console/review',
      '/console/renewal-priorities',
      '/console/assessment-rules',
      '/console/knowledge',
      '/console/system-status',
      '/console/legacy-workspace',
    ])
  })

  it('社区管理员只得到巡检、公众反馈和评分规则相关入口', () => {
    const paths = pathsFor('COMMUNITY_MANAGER')
    expect(paths).toEqual([
      '/console',
      '/console/inspections',
      '/console/feedback',
      '/console/assessment-rules',
    ])
    expect(paths).not.toContain('/console/system-status')
  })

  it('专家得到专业复核、评分规则和知识问答入口', () => {
    expect(pathsFor('EXPERT')).toEqual([
      '/console',
      '/console/review',
      '/console/assessment-rules',
      '/console/knowledge',
    ])
  })

  it('住建管理人员得到公众反馈、风险总览和评分规则入口', () => {
    expect(pathsFor('GOVERNMENT_MANAGER')).toEqual([
      '/console',
      '/console/feedback',
      '/console/renewal-priorities',
      '/console/assessment-rules',
    ])
  })

  it('详情页按最长前缀高亮所属菜单', () => {
    const groups = buildConsoleMenu(['ADMIN'])
    expect(resolveActiveConsoleMenuPath('/console/review/abc', groups)).toBe('/console/review')
    expect(resolveActiveConsoleMenuPath('/console/buildings/1/assessment', groups)).toBe('/console')
    expect(resolveActiveConsoleMenuPath('/console/renewal-priorities', groups)).toBe('/console/renewal-priorities')
  })
})
