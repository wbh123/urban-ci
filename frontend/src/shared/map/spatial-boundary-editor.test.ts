import { describe, expect, it, vi } from 'vitest'
import type { MapRuntimeConfig } from '@/shared/api/endpoints/map'
import type { SpatialAmapLoader } from './spatial-amap'
import { createSpatialBoundaryEditor } from './spatial-boundary-editor'

const config = {
  enabled: true,
  mode: 'LIVE',
  provider: 'AMAP',
  jsApiKey: 'key',
  serviceHost: '/_AMapService',
  securityJsCodeExposed: false,
  defaultZoom: 16,
  defaultCenter: { longitude: 113.5, latitude: 27.5 },
} as MapRuntimeConfig

describe('spatial boundary editor driver', () => {
  it('loads an existing polygon, opens PolygonEditor and exports current GeoJSON', async () => {
    const paths = [[[113, 27], [114, 27], [114, 28], [113, 27]]]
    const maps: FakeMap[] = []
    const polygons: FakePolygon[] = []
    const editors: FakeEditor[] = []

    class FakeMap {
      add = vi.fn()
      setFitView = vi.fn()
      destroy = vi.fn()
      constructor() { maps.push(this) }
    }
    class FakePolygon {
      setMap = vi.fn()
      constructor() { polygons.push(this) }
      getPath() {
        return paths[0]!.map(([lng, lat]) => ({ getLng: () => lng, getLat: () => lat }))
      }
    }
    class FakeEditor {
      open = vi.fn()
      close = vi.fn()
      constructor() { editors.push(this) }
    }
    class FakeMouseTool {
      polygon = vi.fn()
      close = vi.fn()
      on = vi.fn()
    }

    const loader: SpatialAmapLoader = vi.fn().mockResolvedValue({
      Map: FakeMap,
      Polygon: FakePolygon,
      PolygonEditor: FakeEditor,
      MouseTool: FakeMouseTool,
    } as never)
    const driver = createSpatialBoundaryEditor({ loader })

    await driver.mount(document.createElement('div'), config)
    driver.loadGeometry({ type: 'Polygon', coordinates: paths })
    driver.startEdit()

    expect(editors[0]?.open).toHaveBeenCalled()
    expect(driver.exportGeometry()).toEqual({
      type: 'Polygon',
      coordinates: [[[113, 27], [114, 27], [114, 28], [113, 27]]],
    })
    driver.destroy()
    expect(editors[0]?.close).toHaveBeenCalled()
    expect(polygons[0]?.setMap).toHaveBeenCalledWith(null)
    expect(maps[0]?.destroy).toHaveBeenCalled()
  })

  it('accepts MouseTool polygon drawing as the new editable geometry', async () => {
    let drawHandler: ((event: { obj: unknown }) => void) | undefined
    const mouseTools: FakeMouseTool[] = []
    const drawnPolygon = {
      getPath: () => [
        { getLng: () => 113, getLat: () => 27 },
        { getLng: () => 114, getLat: () => 27 },
        { getLng: () => 113, getLat: () => 27 },
      ],
      setMap: vi.fn(),
    }

    class FakeMap {
      add = vi.fn()
      setFitView = vi.fn()
      destroy = vi.fn()
    }
    class FakePolygon {
      getPath = vi.fn(() => [])
      setMap = vi.fn()
    }
    class FakeEditor {
      open = vi.fn()
      close = vi.fn()
    }
    class FakeMouseTool {
      polygon = vi.fn()
      close = vi.fn()
      constructor() { mouseTools.push(this) }
      on = vi.fn((event: string, handler: (value: { obj: unknown }) => void) => {
        if (event === 'draw') drawHandler = handler
      })
    }

    const loader: SpatialAmapLoader = vi.fn().mockResolvedValue({
      Map: FakeMap,
      Polygon: FakePolygon,
      PolygonEditor: FakeEditor,
      MouseTool: FakeMouseTool,
    } as never)
    const onChange = vi.fn()
    const driver = createSpatialBoundaryEditor({ loader })
    await driver.mount(document.createElement('div'), config, { onChange })
    driver.startDraw()
    drawHandler?.({ obj: drawnPolygon })

    expect(mouseTools[0]?.polygon).toHaveBeenCalled()
    expect(onChange).toHaveBeenCalled()
    expect(driver.exportGeometry()?.type).toBe('Polygon')
  })
})
