import { describe, expect, it } from 'vitest'
import source from './ConsoleSpatialMapPage.vue?raw'

describe('ConsoleSpatialMapPage native map status contract', () => {
  it('passes complete status fields into native building markers', () => {
    expect(source).toContain('riskLevel: item.riskLevel')
    expect(source).toContain('priorityLevel: item.priorityLevel')
    expect(source).toContain('freshness: item.freshness')
  })

  it('uses one selection helper for list and map clicks and activates Buildings highlighting', () => {
    expect(source).toContain('function selectBuilding(buildingId: string)')
    expect(source).toContain('driver.setActiveBuilding(')
    expect(source).toContain('@click="focusBuildingFromList(item.buildingId)"')
    expect(source).toContain('function focusBuildingFromList(buildingId: string)')
    expect(source).toContain('selectBuilding(buildingId)')
    expect(source).toContain('onBuildingClick: (buildingId) => {')
    expect(source).toContain('driver.focusBuilding(selection)')
  })

  it('explains the concentric native marker semantics in the map legend', () => {
    expect(source).toContain('内圆')
    expect(source).toContain('外圈')
    expect(source).toContain('风险')
    expect(source).toContain('优先级')
  })
})
