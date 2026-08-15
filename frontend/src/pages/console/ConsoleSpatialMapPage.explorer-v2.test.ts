import { describe, expect, it } from 'vitest'
import source from './ConsoleSpatialMapPage.vue?raw'

describe('ConsoleSpatialMapPage explorer v2', () => {
  it('uses shared business filters and compact building rows', () => {
    expect(source).toContain('AppFilterBar')
    expect(source).toContain('AppQueryField')
    expect(source).toContain('compact-building-row')
    expect(source).toContain('priorityFilter')
    expect(source).toContain('freshnessFilter')
  })

  it('focuses and highlights a building when it is selected from the list', () => {
    expect(source).toContain('focusBuildingFromList')
    expect(source).toContain('driver.setActiveBuilding')
    expect(source).toContain('driver.focusBuilding')
  })

  it('keeps the map as the dominant workspace area', () => {
    expect(source).toContain('grid-template-columns: minmax(230px, 300px) minmax(0, 1fr)')
  })
})
