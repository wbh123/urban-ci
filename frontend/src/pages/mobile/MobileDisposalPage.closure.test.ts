import { describe, expect, it } from 'vitest'
import source from './MobileDisposalPage.vue?raw'

describe('MobileDisposalPage rectification closure', () => {
  it('requires rectification evidence before moving to reinspection', () => {
    expect(source).toContain("bindingRole: 'RECTIFICATION_PHOTO'")
    expect(source).toContain('submitFeedbackRectification')
    expect(source).toContain('提交整改并进入待复验')
    expect(source).toContain('至少上传一张整改证据')
  })
  it('creates a reinspection task and only closes after completed reinspection', () => {
    expect(source).toContain('createFeedbackReinspection')
    expect(source).toContain('completeFeedbackReinspection')
    expect(source).toContain('发起复查复验')
    expect(source).toContain('复验通过并关闭')
    expect(source).toContain('复验不通过')
    expect(source).toContain('已闭环')
  })
  it('keeps formal risk outside the disposal state transition', () => {
    expect(source).toContain('复验不会直接修改正式风险评分')
  })
})
