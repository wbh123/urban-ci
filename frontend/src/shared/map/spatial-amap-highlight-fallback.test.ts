import { describe, expect, it, vi } from 'vitest'
import type { MapRuntimeConfig } from '@/shared/api/endpoints/map'
import type { SpatialBuildingProjection } from '@/stores/spatial-map'
import {
  createSpatialAmapDriver,
  type SpatialAmapActiveBuilding,
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

describe('spatial AMap Buildings highlight fallback', () => {
  it('highlights with both verified geometry and a point fence when both are available', async () => {
    const buildingLayers: FakeBuildings[] = []

    class FakePolygon {
      on() {}
      setMap() {}
    }

    class FakeCircleMarker {
      on() {}
      setMap() {}
    }

    class FakeBuildings {
      styleCalls: Array<Record<string, unknown>> = []
      constructor() { buildingLayers.push(this) }
      on() {}
      setStyle(options: Record<string, unknown>) { this.styleCalls.push(options) }
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
      getZoom() { return 17 }
      getCenter() { return { getLng: () => 114.30, getLat: () => 30.55 } }
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
    const driver = createSpatialAmapDriver({ loader, showOfficialBuildings: true })
    expect(await driver.mount(document.createElement('div'), liveConfig)).toBe(true)

    const projection: SpatialBuildingProjection = {
      feature: {
        type: 'Feature',
        id: 'building-1',
        geometry: {
          type: 'Polygon',
          coordinates: [[
            [114.2998, 30.5498],
            [114.3002, 30.5498],
            [114.3002, 30.5502],
            [114.2998, 30.5498],
          ]],
        },
        properties: {
          entityType: 'BUILDING',
          entityId: 'building-1',
          entityCode: 'B-1',
          name: '一号楼',
          status: 'VERIFIED',
          version: 1,
          coordinateSystem: 'GCJ02',
          sourceType: 'MANUAL_DRAW',
        },
      },
      risk: {
        buildingId: 'building-1',
        buildingCode: 'B-1',
        buildingName: '一号楼',
        communityId: 'community-1',
        communityName: '测试小区',
        riskLevel: 'HIGH',
        freshness: 'CURRENT',
      },
    }
    const input: SpatialAmapSyncInput = {
      communities: [],
      buildings: [projection],
      buildingPoints: [{
        id: 'building-1',
        longitude: 114.30,
        latitude: 30.55,
        kind: 'BUILDING',
        riskLevel: 'HIGH',
        priorityLevel: 'P1',
        freshness: 'CURRENT',
      }],
      selectedCommunityId: null,
      selectedBuildingIds: ['building-1'],
    }
    driver.sync(input)

    const selection: SpatialAmapActiveBuilding = {
      source: 'SYSTEM',
      buildingId: 'building-1',
      longitude: 114.30,
      latitude: 30.55,
      riskLevel: 'HIGH',
    }
    driver.setActiveBuilding(selection, input)

    const style = buildingLayers[0]?.styleCalls.at(-1) as { areas?: Array<{ path?: unknown }> }
    expect(style.areas).toHaveLength(2)
    expect(style.areas?.[0]?.path).not.toEqual(style.areas?.[1]?.path)
  })
})
