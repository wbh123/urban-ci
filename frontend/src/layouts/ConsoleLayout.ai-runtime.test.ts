import { describe, expect, it } from 'vitest'
import layoutSource from './ConsoleLayout.vue?raw'
import pageHeaderSource from '@/shared/components/layout/AppPageHeader.vue?raw'
import runtimeSource from '@/shared/components/ai/AiRuntimeBadge.vue?raw'

describe('管理端 AI Runtime', () => {
  it('由 ConsoleLayout 获取一次运行摘要并通过上下文提供给真实页面标题栏', () => {
    expect(layoutSource).toContain('getAiRuntimeSummary')
    expect(layoutSource).toContain('AI_RUNTIME_CONTEXT_KEY')
    expect(layoutSource).toContain('<RouterView')
    expect(layoutSource).not.toContain('<el-header')
    expect(pageHeaderSource).toContain('AiRuntimeBadge')
  })

  it('只消费业务化运行摘要，不直接调用管理员治理接口', () => {
    expect(layoutSource).toContain('runtimeServices.value = summary.services')
    expect(layoutSource).toContain('runtimePolicy.value = summary.policy')
    expect(layoutSource).not.toContain('getAiGovernanceStatus')
    expect(runtimeSource).not.toContain('AI_PROVIDER_NOT_CONFIGURED')
  })

  it('状态接口失败不会替换或阻断主业务 RouterView', () => {
    expect(layoutSource).toContain("runtimeState.value = 'UNKNOWN'")
    expect(layoutSource).toContain('AI 状态暂不可用')
  })
})
