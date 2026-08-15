import { describe, expect, it } from 'vitest'
import source from './ConsoleInspectionPage.vue?raw'

describe('ConsoleInspectionPage spatial selection', () => {
  it('uses the global spatial selector instead of manually loading community and building options', () => {
    expect(source).toContain("import SpatialObjectSelector from '@/shared/components/SpatialObjectSelector.vue'")
    expect(source).toContain('<SpatialObjectSelector')
    expect(source).toContain('mode="both"')
    expect(source).toContain('handleSpatialSelection')
    expect(source).not.toContain('api.listCommunities')
    expect(source).not.toContain('api.listBuildings')
  })

  it('keeps inspection task querying independent from detail image loading', () => {
    expect(source).toContain('api.listInspectionTasks')
    expect(source).toContain('openDetail')
    expect(source).toContain('api.listInspectionRecords')
    expect(source).toContain('InspectionImageGallery')
  })
})
