import { describe, expect, it } from 'vitest'
import { parseSpatialGeoJson } from './spatial-geojson'

describe('parseSpatialGeoJson', () => {
  it('accepts Polygon geometry and Feature wrappers', () => {
    const polygon = parseSpatialGeoJson(JSON.stringify({
      type: 'Polygon',
      coordinates: [[[113, 27], [114, 27], [114, 28], [113, 27]]],
    }))
    expect(polygon.type).toBe('Polygon')

    const feature = parseSpatialGeoJson(JSON.stringify({
      type: 'Feature',
      properties: { name: 'demo' },
      geometry: {
        type: 'MultiPolygon',
        coordinates: [[[[113, 27], [114, 27], [113, 27]]]],
      },
    }))
    expect(feature.type).toBe('MultiPolygon')
  })

  it('rejects unsupported geometry without mutating the source text', () => {
    const source = JSON.stringify({ type: 'Point', coordinates: [113, 27] })
    expect(() => parseSpatialGeoJson(source)).toThrow('仅支持 Polygon 或 MultiPolygon')
    expect(source).toContain('Point')
  })

  it('rejects malformed rings and non-finite coordinates', () => {
    expect(() => parseSpatialGeoJson(JSON.stringify({
      type: 'Polygon',
      coordinates: [[[113, 27], [114, 27]]],
    }))).toThrow('至少 3 个有效坐标点')

    expect(() => parseSpatialGeoJson('{"type":"Polygon","coordinates":[[[113,27],["x",28],[113,27]]]}')).toThrow('坐标必须是有限数字')
  })
})
