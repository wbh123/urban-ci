import { describe, expect, it, vi } from 'vitest'
import type { MapRuntimeConfig } from '@/shared/api/endpoints/map'
import type { SpatialGeoJsonFeature } from '@/shared/api/endpoints/spatial'
import type { SpatialBuildingProjection } from '@/stores/spatial-map'
import {
  createSpatialAmapDriver,
  geometryToAmapPolygons,
  type SpatialAmapActiveBuilding,
  type SpatialAmapLoader,
  type SpatialAmapSyncInput,
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

const liveConfig = {
  mode: 'LIVE',
  jsApiKey: 'key',
  serviceHost: '/_AMapService',
  defaultZoom: 15,
  defaultCenter: { longitude: 113.5, latitude: 27.5 },
} as MapRuntimeConfig

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

  it('uses a quiet basemap and keeps system polygon interactions', async () => {
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
      setMap(value: unknown) { this.removed = value === null }
      emit(event: string) { this.handlers.get(event)?.() }
    }

    class FakeMap {
      handlers = new Map<string, () => void>()
      destroyed = false
      options: Record<string, unknown>
      constructor(_container: HTMLElement, options: Record<string, unknown>) {
        this.options = { ...options }
        maps.push(this)
      }
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

    const loader: SpatialAmapLoader = vi.fn().mockResolvedValue({ Map: FakeMap, Polygon: FakePolygon })
    const onViewportChange = vi.fn()
    const onCommunityClick = vi.fn()
    const onBuildingClick = vi.fn()
    const driver = createSpatialAmapDriver({ loader })

    expect(await driver.mount(document.createElement('div'), liveConfig, {
      onViewportChange,
      onCommunityClick,
      onBuildingClick,
    })).toBe(true)
    const map = maps[0]!
    expect(map.options.mapStyle).toBe('amap://styles/whitesmoke')
    expect(map.options.features).toEqual(['bg', 'road'])

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

  it('keeps one Map and one visible Buildings layer while supporting outline or 3D focus', async () => {
    const buildingLayers: FakeBuildings[] = []
    const buildingLayerOptions: Array<Record<string, unknown>> = []
    const maps: FakeMap[] = []
    const polygons: FakePolygon[] = []

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
      handlers = new Map<string, (event?: unknown) => void>()
      constructor(_options: Record<string, unknown>) {
        void _options
      }
      on(event: string, handler: (event?: unknown) => void) { this.handlers.set(event, handler) }
      setMap() {}
    }

    class FakeBuildings {
      styleCalls: Array<Record<string, unknown>> = []
      opacityCalls: number[] = []
      handlers = new Map<string, (event?: unknown) => void>()
      constructor(options: Record<string, unknown>) {
        buildingLayers.push(this)
        buildingLayerOptions.push({ ...options })
      }
      on(event: string, handler: (event?: unknown) => void) { this.handlers.set(event, handler) }
      setStyle(options: Record<string, unknown>) { this.styleCalls.push(options) }
      setzIndex() {}
      setOpacity(opacity: number) { this.opacityCalls.push(opacity) }
    }

    class FakeMap {
      handlers = new Map<string, (...args: unknown[]) => void>()
      zoomCalls: unknown[][] = []
      pitchCalls: unknown[][] = []
      rotationCalls: unknown[][] = []
      options: Record<string, unknown>
      destroyed = false
      currentZoom: number
      currentPitch: number
      currentRotation: number
      currentCenter: [number, number]

      constructor(_container: HTMLElement, options: Record<string, unknown>) {
        this.options = { ...options }
        this.currentZoom = Number(options.zoom ?? 18)
        this.currentPitch = Number(options.pitch ?? 0)
        this.currentRotation = Number(options.rotation ?? 0)
        const center = options.center as [number, number]
        this.currentCenter = [center[0], center[1]]
        maps.push(this)
      }
      add() {}
      on(event: string, handler: (...args: unknown[]) => void) { this.handlers.set(event, handler) }
      getZoom() { return this.currentZoom }
      getPitch() { return this.currentPitch }
      getRotation() { return this.currentRotation }
      getCenter() { return { getLng: () => this.currentCenter[0], getLat: () => this.currentCenter[1] } }
      setZoomAndCenter(...args: unknown[]) {
        this.zoomCalls.push(args)
        this.currentZoom = Number(args[0])
        const center = args[1] as [number, number]
        this.currentCenter = [center[0], center[1]]
      }
      setPitch(...args: unknown[]) {
        this.pitchCalls.push(args)
        this.currentPitch = Number(args[0])
      }
      setRotation(...args: unknown[]) {
        this.rotationCalls.push(args)
        this.currentRotation = Number(args[0])
      }
      lngLatToContainer() { return { getX: () => 300, getY: () => 200 } }
      containerToLngLat() { return { getLng: () => 114.305, getLat: () => 30.585 } }
      getBounds() {
        return {
          getSouthWest: () => ({ getLng: () => 114.28, getLat: () => 30.56 }),
          getNorthEast: () => ({ getLng: () => 114.32, getLat: () => 30.60 }),
        }
      }
      destroy() { this.destroyed = true }
    }

    const loader: SpatialAmapLoader = vi.fn().mockResolvedValue({
      Map: FakeMap,
      Polygon: FakePolygon,
      CircleMarker: FakeCircleMarker,
      Buildings: FakeBuildings,
      createDefaultLayer: () => ({ kind: 'default-layer' }),
    })
    const driver = createSpatialAmapDriver({ loader, theme: 'DARK', showOfficialBuildings: true })
    const container = document.createElement('div')
    Object.defineProperty(container, 'getBoundingClientRect', {
      value: () => ({ left: 0, top: 0, right: 800, bottom: 600, width: 800, height: 600, x: 0, y: 0, toJSON: () => ({}) }),
    })
    expect(await driver.mount(container, liveConfig)).toBe(true)

    expect(maps).toHaveLength(1)
    expect(buildingLayers).toHaveLength(1)
    expect(buildingLayerOptions[0]?.zIndex).toBe(130)
    expect(buildingLayers[0]?.opacityCalls.at(-1)).toBe(1)
    expect(maps[0]?.options.viewMode).toBe('3D')
    expect(maps[0]?.options.showBuildingBlock).toBe(false)
    expect(maps[0]?.options.animateEnable).toBe(true)

    const verifiedBuilding = feature('verified-building', 'BUILDING', {
      type: 'Polygon',
      coordinates: [[
        [114.3000, 30.5800],
        [114.3003, 30.5800],
        [114.3003, 30.5803],
        [114.3000, 30.5800],
      ]],
    })
    const input: SpatialAmapSyncInput = {
      communities: [],
      buildings: [{
        feature: verifiedBuilding,
        risk: {
          buildingId: 'verified-building',
          buildingCode: 'V-1',
          buildingName: '已确认楼栋',
          communityId: 'c1',
          communityName: '测试小区',
          riskLevel: 'VERY_HIGH',
          freshness: 'CURRENT',
        },
      }],
      buildingPoints: [{
        id: 'verified-building',
        longitude: 114.30015,
        latitude: 30.58015,
        kind: 'BUILDING',
        riskLevel: 'VERY_HIGH',
      }],
      selectedCommunityId: null,
      selectedBuildingIds: [],
    }

    driver.sync(input)
    expect(polygons.at(-1)?.options.fillOpacity).toBe(0)
    expect(polygons.at(-1)?.options.strokeOpacity).toBe(0)

    const selected: SpatialAmapActiveBuilding = {
      source: 'SYSTEM',
      buildingId: 'verified-building',
      longitude: 114.30015,
      latitude: 30.58015,
      riskLevel: 'VERY_HIGH',
    }
    driver.setActiveBuilding(selected, input)
    driver.sync({ ...input, selectedBuildingIds: ['verified-building'] })

    const visibleBuildingsLayer = buildingLayers[0]!
    const highlightStyle = visibleBuildingsLayer.styleCalls.at(-1) as {
      hideWithoutStyle?: boolean
      areas?: Array<Record<string, unknown>>
    }
    expect(highlightStyle.hideWithoutStyle).toBe(false)
    expect(highlightStyle.areas).toHaveLength(1)
    expect(highlightStyle.areas?.[0]?.color1).toBe('fff5fffd')
    expect(highlightStyle.areas?.[0]?.color2).toBe('ff55f1d3')
    expect(polygons.at(-1)?.options.fillOpacity).toBe(0)
    expect(polygons.at(-1)?.options.strokeOpacity).toBe(0)

    expect(driver.focusBuildingOutline(selected)).toBe(true)
    expect(Number(maps[0]?.zoomCalls.at(-1)?.[0])).toBeGreaterThanOrEqual(18.4)
    expect(maps[0]?.zoomCalls.at(-1)?.[1]).toEqual([114.30015, 30.58015])
    expect(maps[0]?.zoomCalls.at(-1)?.[2]).toBe(false)
    expect(Number(maps[0]?.zoomCalls.at(-1)?.[3])).toBeGreaterThan(0)
    expect(maps[0]?.pitchCalls.at(-1)?.[0]).toBe(0)
    expect(maps[0]?.pitchCalls.at(-1)?.[1]).toBe(false)
    expect(maps[0]?.rotationCalls.at(-1)?.[0]).toBe(0)
    expect(maps[0]?.rotationCalls.at(-1)?.[1]).toBe(false)

    expect(driver.focusBuilding(selected)).toBe(true)
    expect(maps[0]?.pitchCalls.at(-1)?.[0]).toBe(60)
    expect(maps[0]?.pitchCalls.at(-1)?.[1]).toBe(false)
    expect(maps[0]?.rotationCalls.at(-1)?.[0]).toBe(-30)
    expect(maps[0]?.rotationCalls.at(-1)?.[1]).toBe(false)

    driver.setActiveBuilding(null, input)
    const clearedStyle = visibleBuildingsLayer.styleCalls.at(-1) as { areas?: Array<Record<string, unknown>> }
    expect(clearedStyle.areas).toEqual([])
    expect(driver.restoreOverview()).toBe(true)
    expect(maps[0]?.zoomCalls.at(-1)?.[0]).toBe(15)
    expect(maps[0]?.zoomCalls.at(-1)?.[1]).toEqual([113.5, 27.5])
    expect(maps[0]?.zoomCalls.at(-1)?.[2]).toBe(false)
    expect(maps[0]?.pitchCalls.at(-1)?.[0]).toBe(48)
    expect(maps[0]?.rotationCalls.at(-1)?.[0]).toBe(0)

    driver.destroy()
    expect(maps[0]?.destroyed).toBe(true)
  })
})