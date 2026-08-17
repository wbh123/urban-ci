import { describe, expect, it } from 'vitest'
import source from './ConsoleFeedbackPage.vue?raw'

describe('ConsoleFeedbackPage reinspection human decision', () => {
  it('loads a system recommendation but keeps the final decision editable by staff', () => {
    expect(source).toContain('getFeedbackReinspectionRecommendation')
    expect(source).toContain('系统复检建议')
    expect(source).toContain('人工最终决定')
    expect(source).toContain('需要现场复检')
    expect(source).toContain('无需复检，直接闭环')
  })

  it('waits for the recommendation before exposing the editable rectification drawer', () => {
    const recommendationLoad = source.indexOf('await Promise.all([')
    const drawerOpen = source.indexOf('statusDrawerVisible.value = true')
    expect(recommendationLoad).toBeGreaterThan(-1)
    expect(drawerOpen).toBeGreaterThan(recommendationLoad)
  })

  it('waits for the recommendation before exposing the standalone waiver dialog', () => {
    const waiverBlock = source.slice(
      source.indexOf('async function openWaiver'),
      source.indexOf('async function submitWaiver'),
    )
    expect(waiverBlock.indexOf('await loadRecommendation')).toBeGreaterThan(-1)
    expect(waiverBlock.indexOf('waiverVisible.value = true')).toBeGreaterThan(waiverBlock.indexOf('await loadRecommendation'))
  })

  it('requires an auditable reason for waiver or overriding the recommendation', () => {
    expect(source).toContain('decisionNeedsReason')
    expect(source).toContain('人工判断理由（必填）')
    expect(source).toContain('当前人工决定与系统建议不同')
    expect(source).toContain('覆盖理由已留痕')
  })

  it('allows pending reinspection tickets to be waived only before an active task exists', () => {
    expect(source).toContain('waiveFeedbackReinspection')
    expect(source).toContain("!scope.row.reinspectionTaskId || scope.row.reinspectionStatus === 'CANCELLED'")
    expect(source).toContain('人工确认无需复检')
    expect(source).toContain('已经开始或等待结论的复检任务不能绕过')
  })
})
