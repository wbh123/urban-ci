import { describe, expect, it } from 'vitest'
import source from './CitizenReportPage.vue?raw'

describe('CitizenReportPage credentials', () => {
  it('shows both report code and tracking secret after creation', () => {
    expect(source).toContain('查询编号')
    expect(source).toContain('查询凭证')
    expect(source).toContain('{{ createdResult.reportCode }}')
    expect(source).toContain('{{ createdResult.trackingSecret }}')
  })

  it('does not automatically navigate away after a successful submit', () => {
    expect(source).not.toContain("await router.push(`/citizen/reports/${encodeURIComponent(result.reportCode)}`)")
  })
})
