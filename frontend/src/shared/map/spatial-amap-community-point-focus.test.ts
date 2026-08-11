import { describe, expect, it, vi } from 'vitest'
import type { MapRuntimeConfig } from '@/shared/api/endpoints/map'
import {
  createSpatialAmapDriver,
  type SpatialAmapLoader,
} from './spatial-amap'

const liveConfig = {
  mode: 'LIVE',
  jsApiKey: 'key',
  serviceHost: '/_AMapService',
  defaultZoom: 15,
  defaultCenter: { longitude: 114.30, latitude: 30.58 },
} as MapRuntimeConfig

describe('spatial AMap community point focus', () => {
  it('animates from the native community diamond into building interaction scale and restores overview', async () => {
    const polygons: FakePolygon[] = []
    const maps: FakeMap[] = []

    class FakePolygon {
      handlers = new Map<string, (event?: unknown) => void>()
      constructor(options: Record<string, unknown>) {
        void options
        polygons.push(this)
      }
      on(event: string, handler: (event?: unknown) => void) { this.handlers.set(event, handler) }
      setMap() {}
      emit(event: string) {
        this.handlers.get(event)?.({
          lnglat: { getLng: () => 114.305, getLat: () => 30.585 },
        })
      }
    }

    class FakeMap {
      currentZoom = 15
      currentCenter: [number, number] = [114.30, 30.58]
      zoomCalls: unknown[][] = []
      pitchCalls: unknown[][] = []
      rotationCalls: unknown[][] = []
      constructor(container: HTMLElement, options: Record<string, unknown>) {
        void container
        void options
        maps.push(this)
      }
      add() {}
      on() {}
      getZoom() { return this.currentZoom }
      getPitch() { return 0 }
      getRotation() { return 0 }
      getCenter() { return { getLng: () => this.currentCenter[0], getLat: () => this.currentCenter[1] } }
      setZoomAndCenter(...args: unknown[]) {
        this.zoomCalls.push(args)
        this.currentZoom = Number(args[0])
        this.currentCenter = [...args[1] as [number, number]]
      }
      setPitch(...args: unknown[]) { this.pitchCalls.push(args) }
      setRotation(...args: unknown[]) { this.rotationCalls.push(args) }
      getBounds() {
        return {
          getSouthWest: () => ({ getLng: () => 114.28, getLat: () => 30.56 }),
          getNorthEast: () => ({ getLng: () => 114.32, getLat: () => 30.60 }),
        }
      }
      destroy() {}
    }

    const loader: SpatialAmapLoader = vi.fn().mockResolvedValue({
      Map: FakeMap,
      Polygon: FakePolygon,
    })
    const driver = createSpatialAmapDriver({ loader, theme: 'DARK', showOfficialBuildings: true })

    expect(await driver.mount(document.createElement('div'), liveConfig)).toBe(true)
    driver.sync({
      communities: [],
      buildings: [],
      communityPoints: [{
        id: 'community-1',
        longitude: 114.305,
        latitude: 30.585,
        kind: 'COMMUNITY',
        label: '测试小区',
      }],
      buildingPoints: [],
      selectedCommunityId: null,
      selectedBuildingIds: [],
    })

    expect(polygons).toHaveLength(1)
    polygons[0]!.emit('click')

    expect(maps[0]!.zoomCalls).toHaveLength(1)
    expect(Number(maps[0]!.zoomCalls[0]![0])).toBeGreaterThanOrEqual(17.2)
    expect(maps[0]!.zoomCalls[0]![1]).toEqual([114.305, 30.585])
    expect(maps[0]!.zoomCalls[0]![2]).toBe(false)
    expect(Number(maps[0]!.zoomCalls[0]![3])).toBeGreaterThan(0)

    expect(driver.restoreOverview()).toBe(true)
    expect(maps[0]!.zoomCalls.at(-1)?.[0]).toBe(15)
    expect(maps[0]!.zoomCalls.at(-1)?.[1]).toEqual([114.30, 30.58])
    expect(maps[0]!.zoomCalls.at(-1)?.[2]).toBe(false)
  })
})
