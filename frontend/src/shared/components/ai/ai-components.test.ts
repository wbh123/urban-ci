import { describe, expect, it } from 'vitest'
import statusSource from './AiStatusBadge.vue?raw'
import insightSource from './AiInsightCard.vue?raw'
import actionSource from './AiActionButton.vue?raw'
import evidenceSource from './AiEvidencePanel.vue?raw'
import progressSource from './AiAnalysisProgress.vue?raw'
import activitySource from './AiActivityFeed.vue?raw'
import runtimeSource from './AiRuntimeBadge.vue?raw'

const sources = [statusSource, insightSource, actionSource, evidenceSource, progressSource, activitySource, runtimeSource]

describe('共享 AI 前端组件', () => {
  it('统一使用 ✦ AI 视觉符号和业务化名称', () => {
    expect(insightSource).toContain('✦ AI')
    expect(actionSource).toContain('✦ AI')
    expect(runtimeSource).toContain('✦ AI')
  })

  it('普通共享组件不硬编码技术供应商、Token 或 Workflow ID', () => {
    for (const source of sources) {
      expect(source).not.toContain('Token')
      expect(source).not.toContain('Workflow ID')
      expect(source).not.toContain('AI_PROVIDER_NOT_CONFIGURED')
    }
  })

  it('证据、进度与活动流组件保持可复用的数据驱动接口', () => {
    expect(evidenceSource).toContain('items')
    expect(progressSource).toContain('status')
    expect(activitySource).toContain('items')
  })
})
