import { describe, expect, it } from 'vitest'
import source from './AiWallMap.vue?raw'

describe('AI wall community status aggregation', () => {
  it('aggregates community priority and freshness from building risk rows', () => {
    expect(source).toContain('const rows = riskRows.value.filter((row) => row.communityId === item.communityId)')
    expect(source).toContain('riskLevel: strongestRiskLevel(rows.map((row) => row.riskLevel))')
    expect(source).toContain('priorityLevel: strongestPriorityLevel(rows.map((row) => row.priorityLevel))')
    expect(source).toContain('freshness: aggregateFreshness(rows.map((row) => row.freshness))')
    expect(source).toContain('function strongestPriorityLevel(')
    expect(source).toContain('function aggregateFreshness(')
  })

  it('keeps a community usable when any current assessment exists', () => {
    expect(source).toContain("if (values.includes('CURRENT')) return 'CURRENT'")
    expect(source).toContain("if (values.includes('STALE')) return 'STALE'")
    expect(source).toContain("return 'NO_RESULT'")
  })
})
