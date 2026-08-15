import { describe, expect, it } from 'vitest'
import source from './ConsoleReviewQueuePage.vue?raw'

describe('ConsoleReviewQueuePage pagination contract', () => {
  it('loads the review queue with server pagination starting at 20 rows', () => {
    expect(source).toContain('const page = ref(1)')
    expect(source).toContain('const pageSize = ref(20)')
    expect(source).toContain('page: page.value - 1')
    expect(source).toContain('size: pageSize.value')
    expect(source).toContain('total.value = response.page.totalElements')
  })

  it('resets page on status changes and renders the shared pager', () => {
    expect(source).toContain('page.value = 1')
    expect(source).toContain('<AppTablePager')
    expect(source).toContain('show-user-menu')
  })
})
