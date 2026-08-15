import { describe, expect, it } from 'vitest'
import dashboardSource from './ConsoleDashboardPage.vue?raw'
import briefSource from '@/components/workbench/AiWorkbenchBrief.vue?raw'
import attentionSource from '@/components/workbench/AiWorkbenchAttention.vue?raw'

describe('AI 优先首页工作台', () => {
  it('默认仍是日常 AI 工作台并可一键进入 AI 态势大屏', () => {
    expect(dashboardSource).toContain("ref<DashboardMode>('WORKBENCH')")
    expect(dashboardSource).toContain('AI 工作台')
    expect(dashboardSource).toContain('AI 态势大屏')
  })

  it('第一屏先展示 AI 今日简报和待人工处理', () => {
    expect(dashboardSource).toContain('AiWorkbenchBrief')
    expect(dashboardSource).toContain('AiWorkbenchAttention')
    expect(briefSource).toContain('AI 今日')
    expect(attentionSource).toContain('待人工处理')
  })

  it('AI 聚合失败只做局部降级，不移除成熟工作台组件', () => {
    expect(dashboardSource).toContain('aiOverviewError')
    expect(dashboardSource).toContain('WorkbenchRiskSnapshot')
    expect(dashboardSource).toContain('WorkbenchMapPanel')
    expect(dashboardSource).toContain('WorkbenchTodoPanel')
    expect(dashboardSource).toContain('WorkbenchTrendPanel')
  })

  it('只对原本具备全局风险总览权限的工作区加载 AI 大屏聚合', () => {
    expect(dashboardSource).toContain('canLoadAiDashboard')
    expect(dashboardSource).toContain("workspace.value.role === 'ADMIN'")
    expect(dashboardSource).toContain("workspace.value.role === 'GOVERNMENT_MANAGER'")
  })
})
