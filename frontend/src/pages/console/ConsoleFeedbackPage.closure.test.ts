import { describe, expect, it } from 'vitest'
import source from './ConsoleFeedbackPage.vue?raw'

describe('ConsoleFeedbackPage rectification closure', () => {
  it('supports the same evidence-first closure path on desktop', () => {
    expect(source).toContain("bindingRole: 'RECTIFICATION_PHOTO'")
    expect(source).toContain('submitFeedbackRectification')
    expect(source).toContain('提交整改并进入待复验')
    expect(source).toContain('至少上传一张整改证据')
  })

  it('offers reinspection actions without bypassing completed task requirement', () => {
    expect(source).toContain('createFeedbackReinspection')
    expect(source).toContain('completeFeedbackReinspection')
    expect(source).toContain('发起复查复验')
    expect(source).toContain('复验通过并关闭')
    expect(source).toContain('复验不通过')
    expect(source).toContain('复查任务已完成，等待复验结论')
  })

  it('labels RESOLVED as pending reinspection rather than final closure', () => {
    expect(source).toContain("{ value: 'RESOLVED', label: '待复验' }")
    expect(source).toContain("{ value: 'CLOSED', label: '已闭环' }")
    expect(source).toContain('复验不会直接修改正式风险评分')
  })
})
