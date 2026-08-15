import { describe, expect, it } from 'vitest'
import source from './ConsoleRiskReportsPage.vue?raw'

describe('ConsoleRiskReportsPage selector v2', () => {
  it('removes the standalone building risk point table', () => {
    expect(source).not.toContain('楼栋风险点位')
    expect(source).not.toContain('pagedMapBuildings')
  })

  it('uses spatial selectors for community scope and single-building analysis', () => {
    expect(source).toContain('SpatialObjectSelector')
    expect(source).toContain('scopeCommunityId')
    expect(source).toContain('analysisBuildingId')
    expect(source).not.toContain('范围编号')
  })

  it('adds more risk filtering dimensions', () => {
    expect(source).toContain('riskLevelFilter')
    expect(source).toContain('priorityFilter')
    expect(source).toContain('freshnessFilter')
    expect(source).toContain('AppFilterBar')
  })

  it('keeps historical reports paged with the shared pager', () => {
    expect(source).toContain('AppTablePager')
    expect(source).toContain('reportSize')
    expect(source).toContain('20')
  })
})
