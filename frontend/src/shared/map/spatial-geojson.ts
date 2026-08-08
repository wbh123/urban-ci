import type { SpatialGeoJsonGeometry } from '@/shared/api/endpoints/spatial'

type JsonObject = Record<string, unknown>
type Position = [number, number]
type Ring = Position[]
type PolygonCoordinates = Ring[]
type MultiPolygonCoordinates = PolygonCoordinates[]

export function parseSpatialGeoJson(source: string): SpatialGeoJsonGeometry {
  const text = source.trim()
  if (!text) throw new Error('请输入 GeoJSON 内容')

  let parsed: unknown
  try {
    parsed = JSON.parse(text)
  } catch {
    throw new Error('GeoJSON 不是有效 JSON')
  }

  const geometry = extractGeometry(parsed)
  if (geometry.type === 'Polygon') {
    return {
      type: 'Polygon',
      coordinates: normalizePolygon(geometry.coordinates),
    }
  }
  if (geometry.type === 'MultiPolygon') {
    return {
      type: 'MultiPolygon',
      coordinates: normalizeMultiPolygon(geometry.coordinates),
    }
  }
  throw new Error('仅支持 Polygon 或 MultiPolygon')
}

function extractGeometry(value: unknown): JsonObject {
  const object = asObject(value)
  if (object.type === 'Polygon' || object.type === 'MultiPolygon') return object

  if (object.type === 'Feature') {
    const geometry = asObject(object.geometry)
    if (geometry.type === 'Polygon' || geometry.type === 'MultiPolygon') return geometry
    throw new Error('仅支持 Polygon 或 MultiPolygon')
  }

  if (object.type === 'FeatureCollection') {
    const features = Array.isArray(object.features) ? object.features : []
    if (features.length !== 1) throw new Error('FeatureCollection 必须且只能包含 1 个 Polygon 或 MultiPolygon')
    return extractGeometry(features[0])
  }

  throw new Error('仅支持 Polygon 或 MultiPolygon')
}

function normalizeMultiPolygon(value: unknown): MultiPolygonCoordinates {
  if (!Array.isArray(value) || value.length === 0) throw new Error('MultiPolygon 至少包含 1 个 Polygon')
  return value.map((polygon) => normalizePolygon(polygon))
}

function normalizePolygon(value: unknown): PolygonCoordinates {
  if (!Array.isArray(value) || value.length === 0) throw new Error('Polygon 至少包含 1 个线环')
  return value.map((ring) => normalizeRing(ring))
}

function normalizeRing(value: unknown): Ring {
  if (!Array.isArray(value) || value.length < 3) throw new Error('Polygon 线环至少 3 个有效坐标点')
  return value.map((position) => normalizePosition(position))
}

function normalizePosition(value: unknown): Position {
  if (!Array.isArray(value) || value.length < 2) throw new Error('坐标必须包含经度和纬度')
  const [longitude, latitude] = value
  if (
    typeof longitude !== 'number'
    || typeof latitude !== 'number'
    || !Number.isFinite(longitude)
    || !Number.isFinite(latitude)
  ) {
    throw new Error('坐标必须是有限数字')
  }
  if (longitude < -180 || longitude > 180 || latitude < -90 || latitude > 90) {
    throw new Error('坐标超出有效经纬度范围')
  }
  return [longitude, latitude]
}

function asObject(value: unknown): JsonObject {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('GeoJSON 根节点必须是对象')
  return value as JsonObject
}
