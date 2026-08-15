import { describe, expect, it } from 'vitest'
import source from './AppTablePager.vue?raw'

describe('AppTablePager contract', () => {
  it('uses Element Plus pagination with the standard page sizes', () => {
    expect(source).toContain('<el-pagination')
    expect(source).toContain('pageSizes: () => [20, 50, 100]')
    expect(source).toContain('layout="total, sizes, prev, pager, next, jumper"')
  })

  it('emits one-based page and page-size changes', () => {
    expect(source).toContain("'update:page'")
    expect(source).toContain("'update:pageSize'")
    expect(source).toContain('@current-change="handlePageChange"')
    expect(source).toContain('@size-change="handleSizeChange"')
  })
})
