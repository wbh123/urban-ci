import { describe, expect, it } from 'vitest'
import source from './ConsoleArchiveManagementPage.vue?raw'

describe('ConsoleArchiveManagementPage pagination contract', () => {
  it('uses server pagination for community and building directories', () => {
    expect(source).toContain('const communityPage = ref(1)')
    expect(source).toContain('const communityPageSize = ref(20)')
    expect(source).toContain('const buildingPage = ref(1)')
    expect(source).toContain('const buildingPageSize = ref(20)')
    expect(source).toContain('page: communityPage.value - 1')
    expect(source).toContain('page: buildingPage.value - 1')
  })

  it('renders a pager below both directory tables and resets buildings on community change', () => {
    expect(source.match(/<AppTablePager/g)).toHaveLength(2)
    expect(source).toContain('buildingPage.value = 1')
    expect(source).toContain('show-user-menu')
  })
})
