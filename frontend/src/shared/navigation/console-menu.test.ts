import { describe, expect, it } from 'vitest'
import { buildConsoleMenu, resolveActiveConsoleMenuPath } from './console-menu'
import type { RoleCode } from '@/shared/auth/access'

function pathsFor(...roles: RoleCode[]): string[] {
  return buildConsoleMenu(roles).flatMap((group) => group.items.map((item) => item.path))
}

describe('AI 优先电脑管理端分组导航', () => {
  it('管理员按 AI 工作台、空间、巡检、风险、智能服务和系统顺序排列', () => {
    const groups = buildConsoleMenu(['ADMIN'])
    expect(groups.map((group) => group.label)).toEqual([
      'AI 工作台',
      '空间治理',
      '巡检治理',
      '风险治理',
      '智能服务',
      '系统管理',
    ])
    expect(pathsFor('ADMIN')).toEqual([
      '/console',
      '/console/map',
      '/console/archive-management',
      '/console/spatial-archive',
      '/console/inspections',
      '/console/feedback',
      '/console/review',
      '/console/renewal-priorities',
      '/console/assessment-rules',
      '/console/knowledge',
      '/console/system-status',
    ])
    expect(pathsFor('ADMIN')).not.toContain('/console/legacy-workspace')
  })

  it('社区管理员保持权限不变但按 AI 优先业务顺序排列', () => {
    expect(pathsFor('COMMUNITY_MANAGER')).toEqual([
      '/console',
      '/console/map',
      '/console/archive-management',
      '/console/spatial-archive',
      '/console/inspections',
      '/console/feedback',
      '/console/assessment-rules',
    ])
  })

  it('专家保持原权限并按新的固定分组顺序排列', () => {
    expect(pathsFor('EXPERT')).toEqual([
      '/console',
      '/console/map',
      '/console/review',
      '/console/assessment-rules',
      '/console/knowledge',
    ])
  })

  it('住建管理人员保持原权限并优先进入空间治理', () => {
    expect(pathsFor('GOVERNMENT_MANAGER')).toEqual([
      '/console',
      '/console/map',
      '/console/archive-management',
      '/console/spatial-archive',
      '/console/feedback',
      '/console/renewal-priorities',
      '/console/assessment-rules',
    ])
  })

  it('使用统一业务名称而不是创建独立的巨大 AI 功能菜单', () => {
    const groups = buildConsoleMenu(['ADMIN'])
    const labels = groups.flatMap((group) => group.items.map((item) => item.label))
    expect(labels).toEqual(expect.arrayContaining([
      '城市地图',
      '小区与楼栋',
      '巡检管理',
      '公众反馈',
      'AI 人工复核',
      '风险总览与报告',
      '评分规则',
      '知识助手',
      'AI 运行状态',
    ]))
    expect(groups.map((group) => group.label)).not.toContain('AI 功能')
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
