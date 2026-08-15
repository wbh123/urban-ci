import { describe, expect, it } from 'vitest'
import source from './ConsoleArchiveManagementPage.vue?raw'

describe('ConsoleArchiveManagementPage AI archive hints', () => {
  it('shows explainable archive hints without pretending they are model-generated facts', () => {
    expect(source).toContain('AI 档案提示')
    expect(source).toContain('AiInsightCard')
    expect(source).toContain('buildArchiveHints')
    expect(source).toContain('档案完整性与治理规则')
    expect(source).toContain('archiveHints')
  })

  it('keeps existing archive actions available', () => {
    expect(source).toContain('新增小区')
    expect(source).toContain('新增楼栋')
    expect(source).toContain("openSpatialArchive('COMMUNITY')")
    expect(source).toContain("openSpatialArchive('BUILDING')")
  })
})
