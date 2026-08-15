import { describe, expect, it } from 'vitest'
import source from './ConsoleSpatialArchivePage.vue?raw'

describe('ConsoleSpatialArchivePage layout contract', () => {
  it('uses the shared page header with inline user menu', () => {
    expect(source).toContain("import AppPageHeader from '@/shared/components/layout/AppPageHeader.vue'")
    expect(source).toContain('title="空间档案"')
    expect(source).toContain('show-user-menu')
  })

  it('uses the global selector in the object section', () => {
    expect(source).toContain("import SpatialObjectSelector from '@/shared/components/SpatialObjectSelector.vue'")
    expect(source).toContain('<SpatialObjectSelector')
    expect(source).toContain('mode="both"')
  })

  it('separates status, source assistance, editing and review areas', () => {
    expect(source).toContain('class="archive-summary"')
    expect(source).toContain('control-section--source')
    expect(source).toContain('control-section--edit')
    expect(source).toContain('control-section--review')
  })
})
