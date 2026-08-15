import { describe, expect, it, vi } from 'vitest'
import type { MapRuntimeConfig } from '@/shared/api/endpoints/map'
import type { SpatialBuildingProjection } from '@/stores/spatial-map'
import {
  createSpatialAmapDriver,
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

describe('spatial AMap native building click fallback', () => {
  it('dismisses on a confirmed blank DOM click instead of inventing an unregistered building', async () => {
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
      handlers = new Map<string, (...args: unknown[]) => void>()
      destroyed = false

      constructor(_container: HTMLElement, _options: Record<string, unknown>) {
        void _container
        void _options
      }
      add() {}
      on(event: string, handler: (...args: unknown[]) => void) { this.handlers.set(event, handler) }
      getZoom() { return 18.4 }
      getPitch() { return 60 }
      getRotation() { return -30 }
      getCenter() { return { getLng: () => 114.305, getLat: () => 30.585 } }
      getBounds() {
        return {
          getSouthWest: () => ({ getLng: () => 114.28, getLat: () => 30.56 }),
          getNorthEast: () => ({ getLng: () => 114.34, getLat: () => 30.61 }),
        }
      }
      containerToLngLat(pixel: [number, number]) {
        return {
          getLng: () => 114.34 + pixel[0] * 0,
          getLat: () => 30.61 + pixel[1] * 0,
        }
      }
      lngLatToContainer() { return { getX: () => 300, getY: () => 200 } }
      setZoomAndCenter() {}
      setPitch() {}
      setRotation() {}
      destroy() { this.destroyed = true }
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
      value: () => ({ left: 10, top: 20, right: 810, bottom: 620, width: 800, height: 600, x: 10, y: 20, toJSON: () => ({}) }),
    })

    expect(await driver.mount(container, liveConfig, { onMapBlankClick, onBuildingModelClick })).toBe(true)

    const emptyInput: SpatialAmapSyncInput = {
      communities: [],
      buildings: [],
      communityPoints: [],
      buildingPoints: [],
      selectedCommunityId: null,
      selectedBuildingIds: [],
    }
    driver.sync(emptyInput)

    dispatchPlainLeftClick(container, 510, 320)

    await vi.waitFor(() => expect(onMapBlankClick).toHaveBeenCalledTimes(1), { timeout: 600 })
    expect(onBuildingModelClick).not.toHaveBeenCalled()

    onMapBlankClick.mockClear()
    driver.destroy()
    dispatchPlainLeftClick(container, 510, 320)
    await new Promise((resolve) => setTimeout(resolve, 220))
    expect(onMapBlankClick).not.toHaveBeenCalled()
  })

  it('keeps a Buildings-layer click as the explicit unregistered-building path', async () => {
    let buildingsClick: ((event?: unknown) => void) | undefined

    class FakePolygon {
      on() {}
      setMap() {}
    }

    class FakeBuildings {
      on(event: string, handler: (event?: unknown) => void) {
        if (event === 'click') buildingsClick = handler
      }
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
      getPitch() { return 60 }
      getRotation() { return -30 }
      getCenter() { return { getLng: () => 114.34, getLat: () => 30.61 } }
      getBounds() {
        return {
          getSouthWest: () => ({ getLng: () => 114.28, getLat: () => 30.56 }),
          getNorthEast: () => ({ getLng: () => 114.36, getLat: () => 30.63 }),
        }
      }
      containerToLngLat() { return { getLng: () => 114.34, getLat: () => 30.61 } }
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
    const onBuildingModelClick = vi.fn()
    const driver = createSpatialAmapDriver({ loader, theme: 'DARK', showOfficialBuildings: true })
    expect(await driver.mount(document.createElement('div'), liveConfig, { onBuildingModelClick })).toBe(true)

    driver.sync({
      communities: [],
      buildings: [],
      communityPoints: [],
      buildingPoints: [],
      selectedCommunityId: null,
      selectedBuildingIds: [],
    })

    buildingsClick?.({
      lnglat: { getLng: () => 114.34, getLat: () => 30.61 },
      pixel: { getX: () => 400, getY: () => 300 },
    })

    expect(onBuildingModelClick).toHaveBeenCalledWith(expect.objectContaining({
      source: 'AMAP_ONLY',
      longitude: 114.34,
      latitude: 30.61,
    }))
  })

  it('prefers the nearby system building for a confirmed plain-left DOM click', async () => {
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
      handlers = new Map<string, (...args: unknown[]) => void>()
      constructor(_container: HTMLElement, _options: Record<string, unknown>) {
        void _container
        void _options
      }
      add() {}
      on(event: string, handler: (...args: unknown[]) => void) { this.handlers.set(event, handler) }
      getZoom() { return 18.4 }
      getPitch() { return 60 }
      getRotation() { return -30 }
      getCenter() { return { getLng: () => 114.305, getLat: () => 30.585 } }
      getBounds() {
        return {
          getSouthWest: () => ({ getLng: () => 114.28, getLat: () => 30.56 }),
          getNorthEast: () => ({ getLng: () => 114.34, getLat: () => 30.61 }),
        }
      }
      containerToLngLat() { return { getLng: () => 114.3051, getLat: () => 30.5851 } }
      lngLatToContainer(lnglat: [number, number]) {
        return lnglat[0] < 114.31
          ? { getX: () => 500, getY: () => 300 }
          : { getX: () => 700, getY: () => 500 }
      }
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
    const onBuildingModelClick = vi.fn()
    const driver = createSpatialAmapDriver({ loader, theme: 'DARK', showOfficialBuildings: true })
    const container = document.createElement('div')
    Object.defineProperty(container, 'getBoundingClientRect', {
      value: () => ({ left: 10, top: 20, right: 810, bottom: 620, width: 800, height: 600, x: 10, y: 20, toJSON: () => ({}) }),
    })
    expect(await driver.mount(container, liveConfig, { onBuildingModelClick })).toBe(true)

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

    dispatchPlainLeftClick(container, 510, 320)

    await vi.waitFor(() => {
      expect(onBuildingModelClick).toHaveBeenCalledWith(expect.objectContaining({
        source: 'SYSTEM',
        buildingId: 'b-1',
        longitude: 114.305,
        latitude: 30.585,
      }))
    }, { timeout: 600 })
  })

  it('prefers a real system Polygon hit before any center-point proximity fallback', async () => {
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
      handlers = new Map<string, (...args: unknown[]) => void>()
      constructor(_container: HTMLElement, _options: Record<string, unknown>) {
        void _container
        void _options
      }
      add() {}
      on(event: string, handler: (...args: unknown[]) => void) { this.handlers.set(event, handler) }
      getZoom() { return 18.4 }
      getPitch() { return 0 }
      getRotation() { return 0 }
      getCenter() { return { getLng: () => 114.3002, getLat: () => 30.5802 } }
      getBounds() {
        return {
          getSouthWest: () => ({ getLng: () => 114.28, getLat: () => 30.56 }),
          getNorthEast: () => ({ getLng: () => 114.34, getLat: () => 30.61 }),
        }
      }
      containerToLngLat() { return { getLng: () => 114.3001, getLat: () => 30.5801 } }
      lngLatToContainer() { return { getX: () => 760, getY: () => 560 } }
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
    const onBuildingModelClick = vi.fn()
    const driver = createSpatialAmapDriver({ loader, theme: 'DARK', showOfficialBuildings: true })
    const container = document.createElement('div')
    Object.defineProperty(container, 'getBoundingClientRect', {
      value: () => ({ left: 10, top: 20, right: 810, bottom: 620, width: 800, height: 600, x: 10, y: 20, toJSON: () => ({}) }),
    })
    expect(await driver.mount(container, liveConfig, { onBuildingModelClick })).toBe(true)

    const building: SpatialBuildingProjection = {
      feature: {
        type: 'Feature',
        id: 'polygon-building',
        geometry: {
          type: 'Polygon',
          coordinates: [[
            [114.3000, 30.5800],
            [114.3004, 30.5800],
            [114.3004, 30.5804],
            [114.3000, 30.5804],
            [114.3000, 30.5800],
          ]],
        },
        properties: {
          entityType: 'BUILDING',
          entityId: 'polygon-building',
          entityCode: 'PB-1',
          name: '真实边界楼栋',
          status: 'VERIFIED',
          version: 1,
          coordinateSystem: 'GCJ02',
          sourceType: 'MANUAL_DRAW',
        },
      },
    }

    driver.sync({
      communities: [],
      buildings: [building],
      communityPoints: [],
      buildingPoints: [],
      selectedCommunityId: null,
      selectedBuildingIds: [],
    })

    dispatchPlainLeftClick(container, 510, 320)

    await vi.waitFor(() => {
      expect(onBuildingModelClick).toHaveBeenCalledWith(expect.objectContaining({
        source: 'SYSTEM',
        buildingId: 'polygon-building',
      }))
    }, { timeout: 600 })
  })
})