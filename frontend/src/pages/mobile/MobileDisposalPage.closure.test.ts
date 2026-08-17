import { describe, expect, it } from 'vitest'
import source from './MobileDisposalPage.vue?raw'

describe('MobileDisposalPage rectification closure', () => {
  it('requires rectification evidence before moving to reinspection or waiver closure', () => {
    expect(source).toContain("bindingRole: 'RECTIFICATION_PHOTO'")
    expect(source).toContain('submitFeedbackRectification')
    expect(source).toContain('提交整改并进入待复验')
    expect(source).toContain('至少上传一张整改证据')
  })

  it('shows a system recommendation while keeping the final reinspection decision manual', () => {
    expect(source).toContain('getFeedbackReinspectionRecommendation')
    expect(source).toContain('系统复检建议')
    expect(source).toContain('人工最终决定')
    expect(source).toContain('需要现场复检')
    expect(source).toContain('无需复检，直接闭环')
    expect(source).toContain('人工判断理由（必填）')
  })

  it('loads the recommendation before exposing either manual decision surface', () => {
    const actionBlock = source.slice(
      source.indexOf('async function openAction'),
      source.indexOf('async function uploadRectificationEvidence'),
    )
    expect(actionBlock.indexOf('await Promise.all([')).toBeGreaterThan(-1)
    expect(actionBlock.indexOf('actionVisible.value = true')).toBeGreaterThan(actionBlock.indexOf('await Promise.all(['))

    const waiverBlock = source.slice(
      source.indexOf('async function openWaiver'),
      source.indexOf('async function submitWaiver'),
    )
    expect(waiverBlock.indexOf('await loadRecommendation')).toBeGreaterThan(-1)
    expect(waiverBlock.indexOf('waiverVisible.value = true')).toBeGreaterThan(waiverBlock.indexOf('await loadRecommendation'))
  })

  it('creates a reinspection task or permits audited waiver before any active task exists', () => {
    expect(source).toContain('createFeedbackReinspection')
    expect(source).toContain('waiveFeedbackReinspection')
    expect(source).toContain('人工确认无需复检')
    expect(source).toContain('completeFeedbackReinspection')
    expect(source).toContain('发起复查复验')
    expect(source).toContain('复验通过并关闭')
    expect(source).toContain('复验不通过')
    expect(source).toContain('已闭环')
  })

  it('keeps formal risk outside either disposal closure path', () => {
    expect(source).toContain('复验不会直接修改正式风险评分')
    expect(source).toContain('免复检只关闭治理工单，不会直接修改正式风险评分')
  })
})
