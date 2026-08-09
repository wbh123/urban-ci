import { describe, expect, it } from 'vitest'
import source from './ConsoleSpatialMapPage.vue?raw'

describe('R4-3 spatial map business drawer', () => {
  it('reuses the shared building summary and risk components instead of maintaining duplicate metric markup', () => {
    expect(source).toContain('BuildingSummaryCard')
    expect(source).toContain('RiskSummaryPanel')
    expect(source).toContain('<BuildingSummaryCard')
    expect(source).toContain('<RiskSummaryPanel')
    expect(source).not.toContain('<div class="metric-grid">')
  })
})
