import { describe, expect, it } from 'vitest'
import source from './MobileDisposalPage.vue?raw'

describe('MobileDisposalPage', () => {
  it('loads feedback work orders and submits status transitions', () => {
    expect(source).toContain('listFeedbackReports')
    expect(source).toContain('updateFeedbackStatus')
    expect(source).toContain("'ACCEPTED'")
    expect(source).toContain("'PROCESSING'")
    expect(source).toContain("'RESOLVED'")
    expect(source).not.toContain('处置工单接口正在建设')
  })
})
