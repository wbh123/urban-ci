import { describe, expect, it } from 'vitest'
import source from './ConsoleInspectionPage.vue?raw'

describe('inspection AI summary', () => {
  it('keeps the compact visual-only AI inspection summary', () => {
    expect(source).toContain('AI 巡检摘要')
    expect(source).toContain('AiInspectionSummary')
    expect(source).toContain('疑似病害')
    expect(source).toContain('建议')
  })

  it('combines inspector records with an already completed visual result', () => {
    expect(source).toContain('getInspectionAiCombinedSummary')
    expect(source).toContain('AiInspectionCombinedSummary')
    expect(source).toContain('detailRecords.length')
    expect(source).toContain("detailInference.value.status === 'SUCCEEDED'")
    expect(source).toContain('AI 巡检综合总结')
  })

  it('keeps model route details out of the normal inspection flow', () => {
    expect(source).not.toContain('show-technical-route')
    expect(source).toContain('AI 视觉识别')
  })
})
