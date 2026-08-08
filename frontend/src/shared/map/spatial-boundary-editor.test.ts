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
    const polygon = { getPath: vi.fn(() => paths[0]!.map(([lng, lat]) => ({ getLng: () => lng, getLat: () => lat }))), setMap: vi.fn() }
    const editor = { open: vi.fn(), close: vi.fn() }
    const map = { add: vi.fn(), setFitView: vi.fn(), destroy: vi.fn() }
    const loader: SpatialAmapLoader = vi.fn().mockResolvedValue({
      Map: vi.fn(() => map),
      Polygon: vi.fn(() => polygon),
      PolygonEditor: vi.fn(() => editor),
      MouseTool: vi.fn(),
    } as never)
    const driver = createSpatialBoundaryEditor({ loader })

    await driver.mount(document.createElement('div'), config)
    driver.loadGeometry({ type: 'Polygon', coordinates: paths })
    driver.startEdit()

    expect(editor.open).toHaveBeenCalled()
    expect(driver.exportGeometry()).toEqual({
      type: 'Polygon',
      coordinates: [[[113, 27], [114, 27], [114, 28], [113, 27]]],
    })
    driver.destroy()
    expect(editor.close).toHaveBeenCalled()
    expect(polygon.setMap).toHaveBeenCalledWith(null)
    expect(map.destroy).toHaveBeenCalled()
  })

  it('accepts MouseTool polygon drawing as the new editable geometry', async () => {
    let drawHandler: ((event: { obj: unknown }) => void) | undefined
    const drawnPolygon = {
      getPath: () => [
        { getLng: () => 113, getLat: () => 27 },
        { getLng: () => 114, getLat: () => 27 },
        { getLng: () => 113, getLat: () => 27 },
      ],
      setMap: vi.fn(),
    }
    const mouseTool = {
      polygon: vi.fn(), close: vi.fn(),
      on: vi.fn((event: string, handler: (value: { obj: unknown }) => void) => { if (event === 'draw') drawHandler = handler }),
    }
    const loader: SpatialAmapLoader = vi.fn().mockResolvedValue({
      Map: vi.fn(() => ({ add: vi.fn(), setFitView: vi.fn(), destroy: vi.fn() })),
      Polygon: vi.fn(),
      PolygonEditor: vi.fn(() => ({ open: vi.fn(), close: vi.fn() })),
      MouseTool: vi.fn(() => mouseTool),
    } as never)
    const onChange = vi.fn()
    const driver = createSpatialBoundaryEditor({ loader })
    await driver.mount(document.createElement('div'), config, { onChange })
    driver.startDraw()
    drawHandler?.({ obj: drawnPolygon })

    expect(mouseTool.polygon).toHaveBeenCalled()
    expect(onChange).toHaveBeenCalled()
    expect(driver.exportGeometry()?.type).toBe('Polygon')
  })
})
