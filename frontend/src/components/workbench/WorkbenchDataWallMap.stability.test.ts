import { describe, expect, it } from 'vitest'
import source from './WorkbenchDataWallMap.vue?raw'

describe('WorkbenchDataWallMap marker stability contract', () => {
  it('coalesces final viewport refreshes instead of reloading and rebuilding markers for every map-end event', () => {
    expect(source).toContain('viewportRefreshTimer')
    expect(source).toContain('scheduleViewportRefresh')
    expect(source).toContain('clearTimeout(viewportRefreshTimer)')
    expect(source).not.toContain('void store.loadViewport(viewport).then(() => {\n          syncMap()')
  })

  it('does not rebuild all map overlays while the overview camera is starting its restore animation', () => {
    const start = source.indexOf('function resetMapInteraction(): void')
    const end = source.indexOf('function handleBuildingSelection', start)
    const resetBlock = source.slice(start, end)
    expect(resetBlock).toContain('driver.restoreOverview()')
    expect(resetBlock).not.toContain('syncMap()')
  })

  it('does not let selection-only Pinia mutations trigger the deep data overlay watcher', () => {
    const start = source.indexOf('watch(\n  [')
    const end = source.indexOf('onMounted(initialiseMap)', start)
    const watchBlock = source.slice(start, end)
    expect(watchBlock).toContain('communityFeatures')
    expect(watchBlock).toContain('visibleBuildings')
    expect(watchBlock).toContain('riskRows')
    expect(watchBlock).toContain('communityPoints')
    expect(watchBlock).not.toContain('selectedCommunityId')
    expect(watchBlock).not.toContain('selectedBuildingIds')
  })
})
