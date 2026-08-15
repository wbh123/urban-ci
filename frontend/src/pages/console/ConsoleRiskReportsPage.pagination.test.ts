import { describe, expect, it } from 'vitest'
import source from './ConsoleRiskReportsPage.vue?raw'

describe('ConsoleRiskReportsPage pagination contract', () => {
  it('starts the retained risk tables at 20 rows per page', () => {
    expect(source).toContain('reportSize: 20')
    expect(source).toContain('const topRiskPageSize = ref(20)')
    expect(source).not.toContain('const mapPageSize = ref(20)')
  })

  it('paginates high-risk buildings and historical reports after removing the duplicate risk-point table', () => {
    expect(source.match(/<AppTablePager/g)).toHaveLength(2)
    expect(source).toContain('const pagedTopRiskBuildings = computed')
    expect(source).not.toContain('const pagedMapBuildings = computed')
    expect(source).toContain('page: filters.reportPage - 1')
    expect(source).toContain('show-user-menu')
  })
})