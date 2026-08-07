import { describe, expect, it } from 'vitest'
import source from './ConsoleInspectionPage.vue?raw'

describe('ConsoleInspectionPage building cascade', () => {
  it('loads scoped communities instead of the map point directory', () => {
    expect(source).toContain("api.listCommunities({ status: 'ACTIVE', size: 100 })")
    expect(source).not.toContain('api.listCommunityPoints()')
  })

  it('clears stale tasks and loads tasks after selecting the first building', () => {
    expect(source).toContain('tasks.value = []')
    expect(source).toContain("selectedBuilding.value = buildings.value[0]?.id ?? ''")
    expect(source).toContain('await loadTasks()')
  })
})
