import { describe, expect, it, vi } from 'vitest'
import type { MapRuntimeConfig } from '@/shared/api/endpoints/map'
import { createSpatialAmapDriver, type SpatialAmapLoader } from './spatial-amap'

const liveConfig = {
  mode: 'LIVE',
  jsApiKey: 'key',
  serviceHost: '/_AMapService',
  defaultZoom: 15,
  defaultCenter: { longitude: 114.30, latitude: 30.55 },
} as MapRuntimeConfig

describe('spatial AMap overview presentation', () => {
  it('switches overview camera between true 2D and pitched 3D without requiring a selected building', async () => {
    const maps: FakeMap[] = []
    class FakePolygon { on() {}; setMap() {} }
    class FakeBuildings { on() {}; setStyle() {}; setzIndex() {}; setOpacity() {} }
    class FakeMap {
      pitchCalls: number[] = []
      rotationCalls: number[] = []
      constructor(_container: HTMLElement, _options: Record<string, unknown>) { maps.push(this) }
      add() {}
      on() {}
      getZoom() { return 15 }
      getPitch() { return this.pitchCalls.at(-1) ?? 0 }
      getRotation() { return this.rotationCalls.at(-1) ?? 0 }
      getCenter() { return { getLng: () => 114.30, getLat: () => 30.55 } }
      getBounds() { return { getSouthWest: () => ({ getLng: () => 114.20, getLat: () => 30.45 }), getNorthEast: () => ({ getLng: () => 114.40, getLat: () => 30.65 }) } }
      setPitch(value: number) { this.pitchCalls.push(value) }
      setRotation(value: number) { this.rotationCalls.push(value) }
      setZoomAndCenter() {}
      destroy() {}
    }
    const loader: SpatialAmapLoader = vi.fn().mockResolvedValue({ Map: FakeMap, Polygon: FakePolygon, Buildings: FakeBuildings, createDefaultLayer: () => ({}) })
    const driver = createSpatialAmapDriver({ loader, showOfficialBuildings: true })
    expect(await driver.mount(document.createElement('div'), liveConfig)).toBe(true)
    expect(driver.setOverviewPresentation(true)).toBe(true)
    expect(maps[0]?.pitchCalls.at(-1)).toBeGreaterThan(0)
    expect(driver.setOverviewPresentation(false)).toBe(true)
    expect(maps[0]?.pitchCalls.at(-1)).toBe(0)
    expect(maps[0]?.rotationCalls.at(-1)).toBe(0)
  })
})
