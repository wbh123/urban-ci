import { describe, expect, it } from 'vitest'
import source from './ConsoleRiskReportsPage.vue?raw'

describe('risk report automatic download', () => {
  it('enables automatic download by default and persists the user choice locally', () => {
    expect(source).toContain("const AUTO_DOWNLOAD_KEY = 'urban-safe:risk-report:auto-download'")
    expect(source).toContain('readAutoDownloadPreference')
    expect(source).toContain("return stored == null ? true : stored !== 'false'")
    expect(source).toContain('生成后自动下载')
    expect(source).toContain('localStorage.setItem(AUTO_DOWNLOAD_KEY')
  })

  it('downloads the generated or reused report after refreshing the real report list', () => {
    expect(source).toContain('autoDownloadAfterGenerate.value')
    expect(source).toContain('findGeneratedReport')
    expect(source).toContain('await download(generatedReport)')
    expect(source).toContain('报告已生成，但自动下载失败')
    expect(source).toContain('可从历史报告手动下载')
  })
})
