import { describe, expect, it } from 'vitest'
import source from './ConsoleArchiveManagementPage.vue?raw'

describe('ConsoleArchiveManagementPage query flow', () => {
  it('keeps community-then-building workflow while placing AI archive hints beside community query', () => {
    expect(source).not.toContain('directory-grid')
    expect(source).toContain('community-ai-row')
    expect(source).toContain('community-ai-row__directory')
    expect(source).toContain('community-ai-row__ai')
    expect(source).toContain('selected-community-context')
    expect(source).toContain('building-query-section')
  })

  it('supports server-backed community filtering and building keyword search', () => {
    expect(source).toContain('communityKeyword')
    expect(source).toContain('communityStatus')
    expect(source).toContain('buildingKeyword')
    expect(source).toContain('AppFilterBar')
    expect(source).toContain('AppQueryField')
  })

  it('does not auto-select the first community or first building', () => {
    expect(source).not.toContain('communities.value[0]?.id')
    expect(source).not.toContain('buildings.value[0]?.id')
  })

  it('resets building selection and pagination when the community changes', () => {
    expect(source).toContain("selectedBuildingId.value = ''")
    expect(source).toContain('buildingPage.value = 1')
    expect(source).toContain('loadBuildings')
  })
})
