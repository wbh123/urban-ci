import { describe, expect, it, vi } from 'vitest'
import type { MapRuntimeConfig } from '@/shared/api/endpoints/map'
import { createArchivePointPicker } from './archive-point-picker'

const liveConfig: MapRuntimeConfig = {
  enabled: true,
  mode: 'LIVE',
  provider: 'AMAP',
  jsApiKey: 'test-key',
  serviceHost: '/_AMapService',
  securityJsCodeExposed: false,
  defaultCenter: { longitude: 113.13, latitude: 27.82 },
  defaultZoom: 15,
}

const mockConfig: MapRuntimeConfig = {
  ...liveConfig,
  enabled: false,
  mode: 'MOCK',
  jsApiKey: '',
}

describe('archive point picker', () => {
  it('does not load AMap when runtime config is mock', async () => {
    const loader = vi.fn()
    const picker = createArchivePointPicker({ loader })

    const mounted = await picker.mount(document.createElement('div'), mockConfig)

    expect(mounted).toBe(false)
    expect(loader).not.toHaveBeenCalled()
  })

  it('emits map click coordinates and moves a single marker', async () => {
    let clickHandler: ((event: unknown) => void) | undefined
    const markerSetPosition = vi.fn()
    const mapSetCenter = vi.fn()
    const mapDestroy = vi.fn()

    class FakeMap {
      on(event: string, handler: (event: unknown) => void): void {
        if (event === 'click') clickHandler = handler
      }
      setCenter = mapSetCenter
      destroy = mapDestroy
    }

    class FakeMarker {
      constructor() {}
      setPosition = markerSetPosition
      setMap = vi.fn()
    }

    const loader = vi.fn().mockResolvedValue({ Map: FakeMap, Marker: FakeMarker })
    const onSelect = vi.fn()
    const picker = createArchivePointPicker({ loader })

    const mounted = await picker.mount(document.createElement('div'), liveConfig, { onSelect })
    expect(mounted).toBe(true)

    clickHandler?.({
      lnglat: {
        getLng: () => 113.12345,
        getLat: () => 27.87654,
      },
    })

    expect(onSelect).toHaveBeenCalledWith({ longitude: 113.12345, latitude: 27.87654 })
    expect(markerSetPosition).toHaveBeenCalledWith([113.12345, 27.87654])

    picker.setPoint({ longitude: 113.2, latitude: 27.9 })
    expect(markerSetPosition).toHaveBeenCalledWith([113.2, 27.9])
    expect(mapSetCenter).toHaveBeenCalledWith([113.2, 27.9])

    picker.destroy()
    expect(mapDestroy).toHaveBeenCalled()
  })
})
