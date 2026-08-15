import { describe, expect, it } from 'vitest'
import source from './ConsoleFeedbackPage.vue?raw'

describe('ConsoleFeedbackPage management v2', () => {
  it('keeps ticket code searchable without showing it as a table column', () => {
    expect(source).toContain('feedbackKeyword')
    expect(source).toContain('工单编号')
    expect(source).not.toContain('prop="reportCode" label="工单编号"')
  })

  it('formats submitted time through the shared datetime component', () => {
    expect(source).toContain('AppDateTime')
    expect(source).toContain(':value="scope.row.submittedAt"')
  })

  it('uses shared filters, pager and a real action button', () => {
    expect(source).toContain('AppFilterBar')
    expect(source).toContain('AppQueryField')
    expect(source).toContain('AppTablePager')
    expect(source).toContain('AppActionButton')
  })

  it('handles status updates in a drawer instead of the old dialog', () => {
    expect(source).toContain('<el-drawer')
    expect(source).toContain('处理公众反馈')
    expect(source).not.toContain('v-model="dialogVisible"')
  })
})
