import { describe, expect, it, vi } from 'vitest'
import type { MapRuntimeConfig } from '@/shared/api/endpoints/map'
import { createSpatialAmapDriver, type SpatialAmapLoader } from './spatial-amap'

const liveConfig = {
  mode: 'LIVE',
  jsApiKey: 'key',
  serviceHost: '/_AMapService',
  defaultZoom: 15,
  defaultCenter: { longitude: 114.30, latitude: 30.58 },
} as MapRuntimeConfig

describe('spatial AMap community marker shape', () => {
  it('uses a native hollow polygon marker instead of a single circle in official Buildings mode', async () => {
    const polygons: FakePolygon[] = []
    const circles: FakeCircleMarker[] = []

    class FakePolygon {
      options: Record<string, unknown>
      handlers = new Map<string, (event?: unknown) => void>()
      constructor(options: Record<string, unknown>) {
        this.options = { ...options }
        polygons.push(this)
      }
      on(event: string, handler: (event?: unknown) => void) { this.handlers.set(event, handler) }
      setMap() {}
    }

    class FakeCircleMarker {
      constructor(_options: Record<string, unknown>) { circles.push(this) }
      on() {}
      setMap() {}
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
      getCenter() { return { getLng: () => 114.30, getLat: () => 30.58 } }
      getBounds() {
        return {
          getSouthWest: () => ({ getLng: () => 114.28, getLat: () => 30.56 }),
          getNorthEast: () => ({ getLng: () => 114.32, getLat: () => 30.60 }),
        }
      }
      setZoomAndCenter() {}
      destroy() {}
    }

    const loader: SpatialAmapLoader = vi.fn().mockResolvedValue({
      Map: FakeMap,
      Polygon: FakePolygon,
      CircleMarker: FakeCircleMarker,
      Buildings: FakeBuildings,
      createDefaultLayer: () => ({}),
    })
    const driver = createSpatialAmapDriver({ loader, showOfficialBuildings: true })
    expect(await driver.mount(document.createElement('div'), liveConfig)).toBe(true)

    driver.sync({
      communities: [],
      buildings: [],
      communityPoints: [{
        id: 'community-1',
        longitude: 114.305,
        latitude: 30.585,
        kind: 'COMMUNITY',
      }],
      buildingPoints: [],
      selectedCommunityId: null,
      selectedBuildingIds: [],
    })

    expect(circles).toHaveLength(0)
    expect(polygons).toHaveLength(1)
    expect(polygons[0]?.options.fillOpacity).toBe(0)
    expect(polygons[0]?.options.cursor).toBe('pointer')
    expect(polygons[0]?.options.path).toHaveLength(5)
  })
})
