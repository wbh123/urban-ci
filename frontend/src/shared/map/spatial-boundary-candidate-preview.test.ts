import { describe, expect, it, vi } from 'vitest'
import type { MapRuntimeConfig } from '@/shared/api/endpoints/map'
import type { SpatialGeoJsonGeometry } from '@/shared/api/endpoints/spatial'
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

describe('spatial boundary candidate preview layer', () => {
  it('previews candidate geometry without changing draft geometry or firing onChange', async () => {
    const polygons: FakePolygon[] = []
    const onChange = vi.fn()

    class FakeMap {
      add = vi.fn()
      setFitView = vi.fn()
      destroy = vi.fn()
    }
    class FakePolygon {
      options: Record<string, unknown>
      setMap = vi.fn()
      constructor(options: Record<string, unknown>) {
        this.options = options
        polygons.push(this)
      }
      getPath() {
        const path = this.options.path as number[][][]
        const ring = path[0] ?? []
        return ring.map(([lng, lat]) => ({ getLng: () => lng, getLat: () => lat }))
      }
    }
    class FakeEditor { open = vi.fn(); close = vi.fn() }
    class FakeMouseTool { polygon = vi.fn(); close = vi.fn(); on = vi.fn() }

    const loader: SpatialAmapLoader = vi.fn().mockResolvedValue({
      Map: FakeMap,
      Polygon: FakePolygon,
      PolygonEditor: FakeEditor,
      MouseTool: FakeMouseTool,
    } as never)
    const driver = createSpatialBoundaryEditor({ loader })
    await driver.mount(document.createElement('div'), config, { onChange })

    const draft: SpatialGeoJsonGeometry = {
      type: 'Polygon',
      coordinates: [[[113, 27], [114, 27], [114, 28], [113, 27]]],
    }
    const candidate: SpatialGeoJsonGeometry = {
      type: 'Polygon',
      coordinates: [[[113.2, 27.2], [113.3, 27.2], [113.3, 27.3], [113.2, 27.2]]],
    }
    driver.loadGeometry(draft)
    onChange.mockClear()

    driver.previewGeometry(candidate)

    expect(onChange).not.toHaveBeenCalled()
    expect(driver.exportGeometry()).toEqual(draft)
    expect(polygons).toHaveLength(2)
    expect(polygons[1]?.options.strokeStyle).toBe('dashed')
    expect(polygons[1]?.options.fillOpacity).toBeLessThan(0.2)

    driver.clearPreview()
    expect(polygons[1]?.setMap).toHaveBeenCalledWith(null)
    expect(driver.exportGeometry()).toEqual(draft)
    expect(onChange).not.toHaveBeenCalled()
  })
})
