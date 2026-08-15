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
  defaultZoom: 18,
  defaultCenter: { longitude: 114.3, latitude: 30.58 },
} as MapRuntimeConfig

describe('spatial AMap plain-left selection gesture', () => {
  it('selects only on a plain left single click', async () => {
    vi.useFakeTimers()
    const buildings: FakeBuildings[] = []

    class FakeBuildings {
      constructor(_options: Record<string, unknown>) {
        void _options
        buildings.push(this)
      }
      setStyle() {}
      setzIndex() {}
      setOpacity() {}
    }

    class FakePolygon {
      on() {}
      setMap() {}
    }

    class FakeMap {
      handlers = new Map<string, (...args: unknown[]) => void>()
      constructor(_container: HTMLElement, _options: Record<string, unknown>) {
        void _container
        void _options
      }
      add() {}
      on(event: string, handler: (...args: unknown[]) => void) { this.handlers.set(event, handler) }
      getZoom() { return 18 }
      getPitch() { return 0 }
      getRotation() { return 0 }
      getCenter() { return { getLng: () => 114.3, getLat: () => 30.58 } }
      getBounds() {
        return {
          getSouthWest: () => ({ getLng: () => 114.28, getLat: () => 30.56 }),
          getNorthEast: () => ({ getLng: () => 114.32, getLat: () => 30.60 }),
        }
      }
      containerToLngLat() { return { getLng: () => 114.305, getLat: () => 30.585 } }
      lngLatToContainer() { return { getX: () => 120, getY: () => 100 } }
      setZoomAndCenter() {}
      setPitch() {}
      setRotation() {}
      destroy() {}
    }

    const loader: SpatialAmapLoader = vi.fn().mockResolvedValue({
      Map: FakeMap,
      Polygon: FakePolygon,
      Buildings: FakeBuildings,
      createDefaultLayer: () => ({ kind: 'default' }),
    })
    const onBuildingModelClick = vi.fn()
    const driver = createSpatialAmapDriver({ loader, theme: 'DARK', showOfficialBuildings: true })
    const container = document.createElement('div')
    Object.defineProperty(container, 'getBoundingClientRect', {
      value: () => ({ left: 0, top: 0, right: 800, bottom: 600, width: 800, height: 600, x: 0, y: 0, toJSON: () => ({}) }),
    })

    expect(await driver.mount(container, liveConfig, { onBuildingModelClick })).toBe(true)
    expect(buildings).toHaveLength(1)

    const input: SpatialAmapSyncInput = {
      communities: [],
      buildings: [],
      buildingPoints: [{
        id: 'building-1',
        longitude: 114.305,
        latitude: 30.585,
        kind: 'BUILDING',
      }],
      selectedCommunityId: null,
      selectedBuildingIds: [],
    }
    driver.sync(input)

    container.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, button: 0, clientX: 120, clientY: 100 }))
    container.dispatchEvent(new MouseEvent('mousemove', { bubbles: true, buttons: 1, clientX: 170, clientY: 140 }))
    container.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, button: 0, clientX: 170, clientY: 140 }))
    container.dispatchEvent(new MouseEvent('click', { bubbles: true, button: 0, clientX: 170, clientY: 140, detail: 1 }))
    await vi.advanceTimersByTimeAsync(220)
    expect(onBuildingModelClick).not.toHaveBeenCalled()

    container.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, button: 0, clientX: 120, clientY: 100, ctrlKey: true }))
    container.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, button: 0, clientX: 120, clientY: 100, ctrlKey: true }))
    container.dispatchEvent(new MouseEvent('click', { bubbles: true, button: 0, clientX: 120, clientY: 100, ctrlKey: true, detail: 1 }))
    await vi.advanceTimersByTimeAsync(220)
    expect(onBuildingModelClick).not.toHaveBeenCalled()

    container.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, button: 0, clientX: 120, clientY: 100 }))
    container.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, button: 0, clientX: 120, clientY: 100 }))
    container.dispatchEvent(new MouseEvent('click', { bubbles: true, button: 0, clientX: 120, clientY: 100, detail: 1 }))
    await vi.advanceTimersByTimeAsync(220)
    expect(onBuildingModelClick).toHaveBeenCalledTimes(1)
    expect(onBuildingModelClick).toHaveBeenCalledWith(expect.objectContaining({
      source: 'SYSTEM',
      buildingId: 'building-1',
    }))

    vi.useRealTimers()
  })
})