import { describe, expect, it } from 'vitest'
import source from './AiWallMap.vue?raw'

describe('AiWallMap marker stability contract', () => {
  it('coalesces viewport refreshes instead of reloading for every map-end event', () => {
    expect(source).toContain('viewportRefreshTimer')
    expect(source).toContain('scheduleViewportRefresh')
    expect(source).toContain('clearTimeout(viewportRefreshTimer)')
    expect(source).toContain('VIEWPORT_REFRESH_DEBOUNCE_MS')
  })

  it('restores the overview without directly rebuilding overlays in the same interaction frame', () => {
    const start = source.indexOf('function resetMapInteraction(): void')
    const end = source.indexOf('function selectBuilding', start)
    const resetBlock = source.slice(start, end)
    expect(resetBlock).toContain('driver.restoreOverview()')
    expect(resetBlock).toContain('scheduleCameraSettle()')
    expect(resetBlock).not.toContain('syncMap()')
  })

  it('clears the community risk scope before restoring the overview viewport', () => {
    const start = source.indexOf('function resetMapInteraction(): void')
    const end = source.indexOf('function selectBuilding', start)
    const resetBlock = source.slice(start, end)
    const clearCommunityIndex = resetBlock.indexOf('store.selectCommunity(null)')
    const restoreOverviewIndex = resetBlock.indexOf('driver.restoreOverview()')

    expect(clearCommunityIndex).toBeGreaterThanOrEqual(0)
    expect(restoreOverviewIndex).toBeGreaterThan(clearCommunityIndex)
  })

  it('keeps selection-only Pinia mutations outside the deep data overlay watcher', () => {
    const start = source.indexOf('watch(\n  [communityFeatures')
    const end = source.indexOf('watch(\n  [() => props.focusBuildingId', start)
    const watchBlock = source.slice(start, end)
    expect(watchBlock).toContain('communityFeatures')
    expect(watchBlock).toContain('visibleBuildings')
    expect(watchBlock).toContain('riskRows')
    expect(watchBlock).toContain('communityPoints')
    expect(watchBlock).not.toContain('selectedCommunityId')
    expect(watchBlock).not.toContain('selectedBuildingIds')
  })
})
