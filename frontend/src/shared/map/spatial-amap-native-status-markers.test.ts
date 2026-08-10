import { describe, expect, it, vi } from 'vitest'
import type { MapRuntimeConfig } from '@/shared/api/endpoints/map'
import {
  createSpatialAmapDriver,
  type SpatialAmapLoader,
  type SpatialAmapSyncInput,
} from './spatial-amap'

const liveConfig = {
  mode: 'LIVE',
  jsApiKey: 'key',
  serviceHost: '/_AMapService',
  defaultZoom: 15,
  defaultCenter: { longitude: 114.30, latitude: 30.55 },
} as MapRuntimeConfig

describe('spatial AMap native status markers', () => {
  it('renders clickable concentric native building markers in official Buildings overview mode', async () => {
    const markers: FakeCircleMarker[] = []

    class FakePolygon {
      on() {}
      setMap() {}
    }

    class FakeCircleMarker {
      options: Record<string, unknown>
      handlers = new Map<string, (event?: unknown) => void>()
      constructor(options: Record<string, unknown>) {
        this.options = { ...options }
        markers.push(this)
      }
      on(event: string, handler: (event?: unknown) => void) { this.handlers.set(event, handler) }
      setMap() {}
      emit(event: string, payload?: unknown) { this.handlers.get(event)?.(payload) }
    }

    class FakeBuildings {
      on() {}
      setStyle() {}
      setzIndex() {}
      setOpacity() {}
    }

    class FakeMap {
      constructor(container: HTMLElement, options: Record<string, unknown>) {
        void container
        void options
      }
      add() {}
      on() {}
      getZoom() { return 15 }
      getBounds() {
        return {
          getSouthWest: () => ({ getLng: () => 114.20, getLat: () => 30.45 }),
          getNorthEast: () => ({ getLng: () => 114.40, getLat: () => 30.65 }),
        }
      }
      destroy() {}
    }

    const loader: SpatialAmapLoader = vi.fn().mockResolvedValue({
      Map: FakeMap,
      Polygon: FakePolygon,
      CircleMarker: FakeCircleMarker,
      Buildings: FakeBuildings,
      createDefaultLayer: () => ({}),
    })
    const onBuildingClick = vi.fn()
    const driver = createSpatialAmapDriver({
      loader,
      theme: 'DARK',
      showOfficialBuildings: true,
    })

    expect(await driver.mount(document.createElement('div'), liveConfig, { onBuildingClick })).toBe(true)

    const input: SpatialAmapSyncInput = {
      communities: [],
      buildings: [],
      buildingPoints: [{
        id: 'building-1',
        longitude: 114.30,
        latitude: 30.55,
        kind: 'BUILDING',
        riskLevel: 'VERY_HIGH',
        priorityLevel: 'P1',
        freshness: 'CURRENT',
      }],
      selectedCommunityId: null,
      selectedBuildingIds: [],
    }

    driver.sync(input)

    expect(markers).toHaveLength(2)
    expect(markers[0]?.options.center).toEqual([114.30, 30.55])
    expect(markers[0]?.options.fillOpacity).toBe(0)
    expect(markers[0]?.options.strokeColor).toBe('#d85cff')
    expect(markers[1]?.options.fillColor).toBe('#ff4d5d')
    expect(markers[1]?.options.cursor).toBe('pointer')

    markers[1]?.emit('click', {
      lnglat: { getLng: () => 114.30, getLat: () => 30.55 },
      pixel: { getX: () => 320, getY: () => 240 },
    })
    expect(onBuildingClick).toHaveBeenCalledWith('building-1', {
      longitude: 114.30,
      latitude: 30.55,
      pixelX: 320,
      pixelY: 240,
    })
  })
})
