import { describe, expect, it, vi } from 'vitest'
import type { MapRuntimeConfig } from '@/shared/api/endpoints/map'
import type { SpatialGeoJsonFeature } from '@/shared/api/endpoints/spatial'
import type { SpatialBuildingProjection } from '@/stores/spatial-map'
import {
  createSpatialAmapDriver,
  geometryToAmapPolygons,
  type SpatialAmapLoader,
} from './spatial-amap'

function feature(
  id: string,
  entityType: 'COMMUNITY' | 'BUILDING',
  geometry: SpatialGeoJsonFeature['geometry'],
): SpatialGeoJsonFeature {
  return {
    type: 'Feature',
    id,
    geometry,
    properties: {
      entityType,
      entityId: id,
      entityCode: id.toUpperCase(),
      name: id,
      status: 'VERIFIED',
      version: 1,
      coordinateSystem: 'GCJ02',
      sourceType: 'MANUAL_DRAW',
    },
  }
}

describe('spatial AMap driver', () => {
  it('normalizes Polygon and MultiPolygon into independent AMap polygon paths', () => {
    const polygon = geometryToAmapPolygons({
      type: 'Polygon',
      coordinates: [[[113, 27], [114, 27], [114, 28], [113, 27]]],
    })
    expect(polygon).toEqual([[[[113, 27], [114, 27], [114, 28], [113, 27]]]])

    const multi = geometryToAmapPolygons({
      type: 'MultiPolygon',
      coordinates: [
        [[[113, 27], [114, 27], [113, 27]]],
        [[[115, 29], [116, 29], [115, 29]]],
      ],
    })
    expect(multi).toHaveLength(2)
  })

  it('renders community/building polygons, links clicks and emits viewport changes', async () => {
    const polygons: FakePolygon[] = []
    const maps: FakeMap[] = []

    class FakePolygon {
      options: Record<string, unknown>
      handlers = new Map<string, () => void>()
      removed = false

      constructor(options: Record<string, unknown>) {
        this.options = { ...options }
        polygons.push(this)
      }

      on(event: string, handler: () => void) { this.handlers.set(event, handler) }
      setOptions(options: Record<string, unknown>) { this.options = { ...this.options, ...options } }
      setMap(value: unknown) { this.removed = value === null }
      emit(event: string) { this.handlers.get(event)?.() }
    }

    class FakeMap {
      handlers = new Map<string, () => void>()
      destroyed = false

      constructor() { maps.push(this) }
      add() {}
      on(event: string, handler: () => void) { this.handlers.set(event, handler) }
      getZoom() { return 16 }
      getBounds() {
        return {
          getSouthWest: () => ({ getLng: () => 113, getLat: () => 27 }),
          getNorthEast: () => ({ getLng: () => 114, getLat: () => 28 }),
        }
      }
      emit(event: string) { this.handlers.get(event)?.() }
      destroy() { this.destroyed = true }
    }

    const loader: SpatialAmapLoader = vi.fn().mockResolvedValue({
      Map: FakeMap,
      Polygon: FakePolygon,
    })
    const onViewportChange = vi.fn()
    const onCommunityClick = vi.fn()
    const onBuildingClick = vi.fn()
    const driver = createSpatialAmapDriver({ loader })
    const config = {
      mode: 'LIVE',
      jsApiKey: 'key',
      serviceHost: '/_AMapService',
      defaultZoom: 15,
      defaultCenter: { longitude: 113.5, latitude: 27.5 },
    } as MapRuntimeConfig

    expect(await driver.mount(document.createElement('div'), config, {
      onViewportChange,
      onCommunityClick,
      onBuildingClick,
    })).toBe(true)
    const map = maps[0]!

    const community = feature('c1', 'COMMUNITY', {
      type: 'Polygon',
      coordinates: [[[113, 27], [114, 27], [114, 28], [113, 27]]],
    })
    const buildingFeature = feature('b1', 'BUILDING', {
      type: 'Polygon',
      coordinates: [[[113.1, 27.1], [113.2, 27.1], [113.2, 27.2], [113.1, 27.1]]],
    })
    const building: SpatialBuildingProjection = {
      feature: buildingFeature,
      risk: {
        buildingId: 'b1',
        buildingCode: 'B1',
        buildingName: '一号楼',
        communityId: 'c1',
        communityName: '一号小区',
        riskScore: 88,
        riskLevel: 'HIGH',
        freshness: 'CURRENT',
      },
    }

    driver.sync({
      communities: [community],
      buildings: [building],
      selectedCommunityId: 'c1',
      selectedBuildingIds: ['b1'],
    })

    expect(polygons).toHaveLength(2)
    expect(polygons[0]?.options.strokeWeight).toBeGreaterThan(2)
    expect(polygons[1]?.options.strokeWeight).toBeGreaterThan(2)
    polygons[0]?.emit('click')
    polygons[1]?.emit('click')
    expect(onCommunityClick).toHaveBeenCalledWith('c1')
    expect(onBuildingClick).toHaveBeenCalledWith('b1')

    map.emit('moveend')
    expect(onViewportChange).toHaveBeenCalledWith({ west: 113, south: 27, east: 114, north: 28, zoom: 16 })

    driver.destroy()
    expect(map.destroyed).toBe(true)
    expect(polygons.every((polygon) => polygon.removed)).toBe(true)
  })
})
