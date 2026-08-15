import { describe, expect, it } from 'vitest'
import source from './ConsoleArchiveManagementPage.vue?raw'

describe('ConsoleArchiveManagementPage visual archive flow', () => {
  it('loads scoped community and building directories', () => {
    expect(source).toContain('listCommunities')
    expect(source).toContain('listBuildings')
    expect(source).toContain('selectedCommunityId')
    expect(source).toContain('selectedBuildingId')
  })

  it('delegates creation to dedicated archive drawers', () => {
    expect(source).toContain('CreateCommunityDrawer')
    expect(source).toContain('CreateBuildingDrawer')
    expect(source).not.toContain('createArchivePointPicker')
    expect(source).not.toContain('searchArchivePlaces')
    expect(source).not.toContain('submitCreation')
  })

  it('keeps spatial archive navigation on the directory page', () => {
    expect(source).toContain('空间档案')
    expect(source).toContain("name: 'console-spatial-archive'")
    expect(source).toContain('entityType')
    expect(source).toContain('entityId')
  })
})
