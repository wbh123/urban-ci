import { describe, expect, it, vi } from 'vitest'
import type { MapRuntimeConfig } from '@/shared/api/endpoints/map'
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
  defaultZoom: 18.4,
  defaultCenter: { longitude: 114.305, latitude: 30.585 },
} as MapRuntimeConfig

function dispatchPlainLeftClick(container: HTMLElement, clientX: number, clientY: number): void {
  container.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, button: 0, clientX, clientY }))
  container.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, button: 0, clientX, clientY }))
  container.dispatchEvent(new MouseEvent('click', { bubbles: true, button: 0, clientX, clientY, detail: 1 }))
}

describe('spatial AMap visual regression contract', () => {
  it('uses the visible Buildings layer for selection highlight and animated 3D camera state', async () => {
    const buildingLayers: FakeBuildings[] = []
    const maps: FakeMap[] = []

    class FakePolygon {
      on() {}
      setMap() {}
    }

    class FakeBuildings {
      styleCalls: Array<Record<string, unknown>> = []
      opacityCalls: number[] = []
      constructor(_options: Record<string, unknown>) {
        void _options
        buildingLayers.push(this)
      }
      on() {}
      setStyle(options: Record<string, unknown>) { this.styleCalls.push(options) }
      setzIndex() {}
      setOpacity(opacity: number) { this.opacityCalls.push(opacity) }
    }

    class FakeMap {
      options: Record<string, unknown>
      zoomCalls: unknown[][] = []
      pitchCalls: unknown[][] = []
      rotationCalls: unknown[][] = []
      currentZoom = 18.4
      constructor(_container: HTMLElement, options: Record<string, unknown>) {
        void _container
        this.options = { ...options }
        maps.push(this)
      }
      add() {}
      on() {}
      getZoom() { return this.currentZoom }
      getPitch() { return 0 }
      getRotation() { return 0 }
      getCenter() { return { getLng: () => 114.305, getLat: () => 30.585 } }
      getBounds() {
        return {
          getSouthWest: () => ({ getLng: () => 114.28, getLat: () => 30.56 }),
          getNorthEast: () => ({ getLng: () => 114.34, getLat: () => 30.61 }),
        }
      }
      setZoomAndCenter(...args: unknown[]) { this.zoomCalls.push(args) }
      setPitch(...args: unknown[]) { this.pitchCalls.push(args) }
      setRotation(...args: unknown[]) { this.rotationCalls.push(args) }
      lngLatToContainer() { return { getX: () => 400, getY: () => 300 } }
      containerToLngLat() { return { getLng: () => 114.305, getLat: () => 30.585 } }
      destroy() {}
    }

    const loader: SpatialAmapLoader = vi.fn().mockResolvedValue({
      Map: FakeMap,
      Polygon: FakePolygon,
      Buildings: FakeBuildings,
      createDefaultLayer: () => ({ kind: 'default-layer' }),
    })
    const driver = createSpatialAmapDriver({ loader, theme: 'DARK', showOfficialBuildings: true })
    const container = document.createElement('div')
    Object.defineProperty(container, 'getBoundingClientRect', {
      value: () => ({ left: 0, top: 0, right: 800, bottom: 600, width: 800, height: 600, x: 0, y: 0, toJSON: () => ({}) }),
    })

    expect(await driver.mount(container, liveConfig)).toBe(true)
    expect(buildingLayers).toHaveLength(1)
    expect(maps[0]!.options.animateEnable).toBe(true)

    const input: SpatialAmapSyncInput = {
      communities: [],
      buildings: [],
      communityPoints: [],
      buildingPoints: [{
        id: 'b-1',
        longitude: 114.305,
        latitude: 30.585,
        kind: 'BUILDING',
      }],
      selectedCommunityId: null,
      selectedBuildingIds: ['b-1'],
    }
    const selected: SpatialAmapActiveBuilding = {
      source: 'SYSTEM',
      buildingId: 'b-1',
      longitude: 114.305,
      latitude: 30.585,
    }

    driver.sync(input)
    driver.setActiveBuilding(selected, input)

    const style = buildingLayers[0]!.styleCalls.at(-1) as { hideWithoutStyle?: boolean; areas?: Array<Record<string, unknown>> }
    expect(style.hideWithoutStyle).toBe(false)
    expect(style.areas).toHaveLength(1)
    expect(style.areas?.[0]?.color1).toBe('fff5fffd')
    expect(style.areas?.[0]?.color2).toBe('ff55f1d3')

    expect(driver.focusBuilding(selected)).toBe(true)
    expect(maps[0]!.zoomCalls.at(-1)?.[2]).toBe(false)
    expect(Number(maps[0]!.zoomCalls.at(-1)?.[3])).toBeGreaterThan(0)
    expect(maps[0]!.rotationCalls.at(-1)?.[0]).toBe(-30)
    expect(maps[0]!.rotationCalls.at(-1)?.[1]).toBe(false)
    expect(maps[0]!.pitchCalls.at(-1)?.[0]).toBe(60)
    expect(maps[0]!.pitchCalls.at(-1)?.[1]).toBe(false)
  })

  it('treats a confirmed empty map click as dismissal instead of an unregistered building', async () => {
    class FakePolygon {
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
      constructor(_container: HTMLElement, _options: Record<string, unknown>) {
        void _container
        void _options
      }
      add() {}
      on() {}
      getZoom() { return 18.4 }
      getPitch() { return 0 }
      getRotation() { return 0 }
      getCenter() { return { getLng: () => 114.34, getLat: () => 30.61 } }
      getBounds() {
        return {
          getSouthWest: () => ({ getLng: () => 114.28, getLat: () => 30.56 }),
          getNorthEast: () => ({ getLng: () => 114.36, getLat: () => 30.63 }),
        }
      }
      containerToLngLat() { return { getLng: () => 114.34, getLat: () => 30.61 } }
      lngLatToContainer() { return { getX: () => 20, getY: () => 20 } }
      setZoomAndCenter() {}
      setPitch() {}
      setRotation() {}
      destroy() {}
    }

    const loader: SpatialAmapLoader = vi.fn().mockResolvedValue({
      Map: FakeMap,
      Polygon: FakePolygon,
      Buildings: FakeBuildings,
      createDefaultLayer: () => ({ kind: 'default-layer' }),
    })
    const onMapBlankClick = vi.fn()
    const onBuildingModelClick = vi.fn()
    const driver = createSpatialAmapDriver({ loader, theme: 'DARK', showOfficialBuildings: true })
    const container = document.createElement('div')
    Object.defineProperty(container, 'getBoundingClientRect', {
      value: () => ({ left: 0, top: 0, right: 800, bottom: 600, width: 800, height: 600, x: 0, y: 0, toJSON: () => ({}) }),
    })

    expect(await driver.mount(container, liveConfig, { onMapBlankClick, onBuildingModelClick })).toBe(true)
    driver.sync({
      communities: [],
      buildings: [],
      communityPoints: [],
      buildingPoints: [],
      selectedCommunityId: null,
      selectedBuildingIds: [],
    })

    dispatchPlainLeftClick(container, 400, 300)

    await vi.waitFor(() => expect(onMapBlankClick).toHaveBeenCalledTimes(1), { timeout: 600 })
    expect(onBuildingModelClick).not.toHaveBeenCalled()
  })

  it('does not allow direct building selection before the 3D interaction scale is reached', async () => {
    class FakePolygon {
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
      constructor(_container: HTMLElement, _options: Record<string, unknown>) {
        void _container
        void _options
      }
      add() {}
      on() {}
      getZoom() { return 17 }
      getPitch() { return 0 }
      getRotation() { return 0 }
      getCenter() { return { getLng: () => 114.305, getLat: () => 30.585 } }
      getBounds() {
        return {
          getSouthWest: () => ({ getLng: () => 114.28, getLat: () => 30.56 }),
          getNorthEast: () => ({ getLng: () => 114.34, getLat: () => 30.61 }),
        }
      }
      containerToLngLat() { return { getLng: () => 114.305, getLat: () => 30.585 } }
      lngLatToContainer() { return { getX: () => 400, getY: () => 300 } }
      setZoomAndCenter() {}
      setPitch() {}
      setRotation() {}
      destroy() {}
    }

    const loader: SpatialAmapLoader = vi.fn().mockResolvedValue({
      Map: FakeMap,
      Polygon: FakePolygon,
      Buildings: FakeBuildings,
      createDefaultLayer: () => ({ kind: 'default-layer' }),
    })
    const onMapBlankClick = vi.fn()
    const onBuildingModelClick = vi.fn()
    const driver = createSpatialAmapDriver({ loader, theme: 'DARK', showOfficialBuildings: true })
    const container = document.createElement('div')
    Object.defineProperty(container, 'getBoundingClientRect', {
      value: () => ({ left: 0, top: 0, right: 800, bottom: 600, width: 800, height: 600, x: 0, y: 0, toJSON: () => ({}) }),
    })

    expect(await driver.mount(container, liveConfig, { onMapBlankClick, onBuildingModelClick })).toBe(true)
    driver.sync({
      communities: [],
      buildings: [],
      communityPoints: [],
      buildingPoints: [{
        id: 'b-1',
        longitude: 114.305,
        latitude: 30.585,
        kind: 'BUILDING',
      }],
      selectedCommunityId: null,
      selectedBuildingIds: [],
    })

    dispatchPlainLeftClick(container, 400, 300)

    await vi.waitFor(() => expect(onMapBlankClick).toHaveBeenCalledTimes(1), { timeout: 600 })
    expect(onBuildingModelClick).not.toHaveBeenCalled()
  })
})