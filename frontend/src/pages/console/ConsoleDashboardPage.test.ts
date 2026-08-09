import { describe, expect, it } from 'vitest'
import dashboardSource from './ConsoleDashboardPage.vue?raw'
import { resolveWorkspaceConfig } from './workbench-config'

describe('R4-4 role dashboard workspace config', () => {
  it('gives administrators a system-wide workspace with risk and operational queues', () => {
    const config = resolveWorkspaceConfig(['ADMIN'], ['*'])

    expect(config.title).toBe('系统管理工作台')
    expect(config.enableRisk).toBe(true)
    expect(config.metrics.map((item) => item.key)).toEqual(expect.arrayContaining(['buildings', 'inspections', 'reviews', 'risk']))
    expect(config.todos).toEqual(expect.arrayContaining([
      expect.objectContaining({ path: '/console/feedback?status=PENDING' }),
      expect.objectContaining({ path: '/console/review?status=PENDING' }),
    ]))
    expect(config.mapMode).toBe('GLOBAL')
  })

  it('keeps community managers focused on inspections and feedback without loading risk fields', () => {
    const config = resolveWorkspaceConfig(
      ['COMMUNITY_MANAGER'],
      ['community:manage', 'building:manage', 'inspection:manage'],
    )

    expect(config.title).toBe('社区巡检工作台')
    expect(config.enableRisk).toBe(false)
    expect(config.metrics.map((item) => item.key)).not.toContain('risk')
    expect(config.todos).toEqual(expect.arrayContaining([
      expect.objectContaining({ path: '/console/inspections?status=IN_PROGRESS' }),
      expect.objectContaining({ path: '/console/feedback?status=PENDING' }),
    ]))
    expect(config.mapMode).toBe('AREA')
  })

  it('gives government managers a regional risk and report workspace', () => {
    const config = resolveWorkspaceConfig(
      ['GOVERNMENT_MANAGER'],
      ['community:read', 'building:read', 'risk:read', 'report:read'],
    )

    expect(config.title).toBe('区域风险工作台')
    expect(config.enableRisk).toBe(true)
    expect(config.metrics.map((item) => item.key)).toContain('risk')
    expect(config.todos).toEqual(expect.arrayContaining([
      expect.objectContaining({ path: '/console/renewal-priorities' }),
    ]))
    expect(config.mapMode).toBe('GLOBAL')
  })

  it('gives expert reviewers a review-first workspace and only enables risk through review permission', () => {
    const config = resolveWorkspaceConfig(
      ['EXPERT'],
      ['inference:review', 'risk:review'],
    )

    expect(config.title).toBe('专业复核工作台')
    expect(config.enableRisk).toBe(true)
    expect(config.todos[0]).toEqual(expect.objectContaining({ path: '/console/review?status=PENDING' }))
    expect(config.mapMode).toBe('REVIEW')
  })

  it('never enables risk merely because a role can enter the console', () => {
    const config = resolveWorkspaceConfig(['GOVERNMENT_MANAGER'], ['community:read', 'building:read'])

    expect(config.enableRisk).toBe(false)
    expect(config.metrics.map((item) => item.key)).not.toContain('risk')
  })
})

describe('R4-4 role dashboard composition', () => {
  it('composes the dashboard from reusable workbench panels', () => {
    expect(dashboardSource).toContain('WorkbenchMetricCard')
    expect(dashboardSource).toContain('WorkbenchTodoPanel')
    expect(dashboardSource).toContain('WorkbenchMapPanel')
    expect(dashboardSource).toContain('WorkbenchTrendPanel')
  })

  it('derives content from the authenticated roles and permissions', () => {
    expect(dashboardSource).toContain('resolveWorkspaceConfig')
    expect(dashboardSource).toContain('authStore.user?.roles')
    expect(dashboardSource).toContain('authStore.user?.permissions')
    expect(dashboardSource).not.toContain("hasRole('ADMIN')")
  })

  it('keeps spatial map and deep-link navigation as explicit workbench actions', () => {
    expect(dashboardSource).toContain("router.push('/console/map')")
    expect(dashboardSource).toContain('router.push(todo.path)')
  })
})
