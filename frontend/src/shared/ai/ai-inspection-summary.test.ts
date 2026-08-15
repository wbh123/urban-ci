import { describe, expect, it } from 'vitest'
import { buildAiInspectionSummary } from './ai-inspection-summary'

describe('buildAiInspectionSummary', () => {
  it('groups visible detections without inventing findings', () => {
    const summary = buildAiInspectionSummary([
      { className: '裂缝', confidence: 0.82 },
      { className: '裂缝', confidence: 0.31 },
      { className: '表面剥落', confidence: 0.64 },
    ])

    expect(summary.total).toBe(3)
    expect(summary.findings).toEqual([
      { name: '裂缝', count: 2 },
      { name: '表面剥落', count: 1 },
    ])
    expect(summary.suggestion).toContain('人工复核')
  })

  it('returns a neutral message when no AI finding exists', () => {
    const summary = buildAiInspectionSummary([])
    expect(summary.total).toBe(0)
    expect(summary.findings).toEqual([])
    expect(summary.suggestion).toContain('未发现')
  })
})
