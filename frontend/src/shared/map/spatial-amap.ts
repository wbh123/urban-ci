import type { MapRuntimeConfig } from '@/shared/api/endpoints/map'
import type { SpatialGeoJsonFeature, SpatialGeoJsonGeometry } from '@/shared/api/endpoints/spatial'
import type { SpatialBboxQuery } from '@/shared/api/endpoints/spatial'
import type { SpatialBuildingProjection } from '@/stores/spatial-map'

interface AmapLngLatLike {
  getLng?: () => number
  getLat?: () => number
  lng?: number
  lat?: number
}

interface AmapBoundsLike {
  getSouthWest: () => AmapLngLatLike
  getNorthEast: () => AmapLngLatLike
}

interface AmapMapLike {
  add: (overlay: AmapPolygonLike) => void
  on: (event: string, handler: () => void) => void
  getZoom: () => number
  getBounds: () => AmapBoundsLike
  destroy: () => void
}

interface AmapPolygonLike {
  on: (event: string, handler: () => void) => void
  setOptions: (options: Record<string, unknown>) => void
  setMap: (map: AmapMapLike | null) => void
}

interface AmapNamespaceLike {
  Map: new (container: HTMLElement, options: Record<string, unknown>) => AmapMapLike
  Polygon: new (options: Record<string, unknown>) => AmapPolygonLike
}

interface AmapWindow extends Window {
  AMap?: AmapNamespaceLike
  _AMapSecurityConfig?: { serviceHost?: string }
  [key: string]: unknown
}

export interface SpatialAmapLoadOptions {
  key: string
  version: string
  plugins: string[]
}

export type SpatialAmapLoader = (options: SpatialAmapLoadOptions) => Promise<AmapNamespaceLike>

export interface SpatialAmapHandlers {
  onViewportChange?: (viewport: SpatialBboxQuery) => void
  onCommunityClick?: (communityId: string) => void
  onBuildingClick?: (buildingId: string) => void
}

export interface SpatialAmapSyncInput {
  communities: SpatialGeoJsonFeature[]
  buildings: SpatialBuildingProjection[]
  selectedCommunityId: string | null
  selectedBuildingIds: string[]
}

export interface SpatialAmapDriver {
  mount: (
    container: HTMLElement,
    config: MapRuntimeConfig,
    handlers?: SpatialAmapHandlers,
  ) => Promise<boolean>
  sync: (input: SpatialAmapSyncInput) => void
  getViewport: () => SpatialBboxQuery | null
  destroy: () => void
}

type Coordinate = [number, number]
type Ring = Coordinate[]
type PolygonPath = Ring[]

const DEFAULT_PLUGINS = ['AMap.PolygonEditor', 'AMap.MouseTool']
let loaderPromise: Promise<AmapNamespaceLike> | null = null

const defaultLoader: SpatialAmapLoader = async (options) => {
  if (typeof window === 'undefined' || typeof document === 'undefined') {
    throw new Error('当前运行环境不支持高德地图')
  }
  const target = window as AmapWindow
  if (target.AMap) return target.AMap
  if (loaderPromise) return loaderPromise

  loaderPromise = new Promise<AmapNamespaceLike>((resolve, reject) => {
    const callbackName = `__urbanSafeSpatialAmapLoaded_${Date.now()}_${Math.random().toString(36).slice(2)}`
    target[callbackName] = () => {
      delete target[callbackName]
      if (target.AMap) resolve(target.AMap)
      else reject(new Error('高德地图脚本已加载，但 AMap 对象不可用'))
    }

    const params = new URLSearchParams({
      v: options.version,
      key: options.key,
      callback: callbackName,
      plugin: options.plugins.join(','),
    })
    const script = document.createElement('script')
    script.async = true
    script.src = `https://webapi.amap.com/maps?${params.toString()}`
    script.onerror = () => {
      delete target[callbackName]
      loaderPromise = null
      reject(new Error('高德地图 JavaScript API 加载失败'))
    }
    document.head.appendChild(script)
  })

  return loaderPromise
}

export function geometryToAmapPolygons(geometry: SpatialGeoJsonGeometry): PolygonPath[] {
  if (geometry.type === 'Polygon') {
    return [normalizePolygon(geometry.coordinates)]
  }
  if (geometry.type === 'MultiPolygon') {
    if (!Array.isArray(geometry.coordinates)) return []
    return geometry.coordinates.map((polygon) => normalizePolygon(polygon))
  }
  return []
}

function normalizePolygon(value: unknown): PolygonPath {
  if (!Array.isArray(value)) return []
  return value
    .filter(Array.isArray)
    .map((ring) => normalizeRing(ring))
    .filter((ring) => ring.length >= 3)
}

function normalizeRing(value: unknown): Ring {
  if (!Array.isArray(value)) return []
  const result: Ring = []
  value.forEach((coordinate) => {
    if (!Array.isArray(coordinate) || coordinate.length < 2) return
    const lng = Number(coordinate[0])
    const lat = Number(coordinate[1])
    if (Number.isFinite(lng) && Number.isFinite(lat)) result.push([lng, lat])
  })
  return result
}

function communityStyle(selected: boolean): Record<string, unknown> {
  return {
    strokeColor: selected ? '#155eef' : '#4d7cfe',
    strokeWeight: selected ? 4 : 2.4,
    strokeOpacity: selected ? 1 : 0.8,
    fillColor: selected ? '#91caff' : '#d6e4ff',
    fillOpacity: selected ? 0.22 : 0.1,
    zIndex: selected ? 40 : 10,
  }
}

function buildingStyle(riskLevel: string | undefined, selected: boolean): Record<string, unknown> {
  const palette: Record<string, string> = {
    VERY_HIGH: '#cf1322',
    HIGH: '#d46b08',
    MEDIUM: '#d4a017',
    LOW: '#389e0d',
  }
  const fillColor = palette[riskLevel ?? ''] ?? '#8c8c8c'
  return {
    strokeColor: selected ? '#111827' : fillColor,
    strokeWeight: selected ? 4 : 1.6,
    strokeOpacity: selected ? 1 : 0.9,
    fillColor,
    fillOpacity: selected ? 0.62 : 0.42,
    zIndex: selected ? 60 : 30,
  }
}

export function createSpatialAmapDriver(
  options: { loader?: SpatialAmapLoader } = {},
): SpatialAmapDriver {
  const loader = options.loader ?? defaultLoader
  let namespace: AmapNamespaceLike | null = null
  let map: AmapMapLike | null = null
  let handlers: SpatialAmapHandlers = {}
  let overlays: AmapPolygonLike[] = []

  async function mount(
    container: HTMLElement,
    config: MapRuntimeConfig,
    nextHandlers: SpatialAmapHandlers = {},
  ): Promise<boolean> {
    destroy()
    handlers = nextHandlers
    if (config.mode !== 'LIVE' || !config.jsApiKey) return false

    if (config.serviceHost && typeof window !== 'undefined') {
      const target = window as AmapWindow
      target._AMapSecurityConfig = { serviceHost: config.serviceHost }
    }

    namespace = await loader({
      key: config.jsApiKey,
      version: config.jsApiVersion || '2.0',
      plugins: DEFAULT_PLUGINS,
    })
    map = new namespace.Map(container, {
      viewMode: '2D',
      resizeEnable: true,
      zoom: config.defaultZoom,
      center: [config.defaultCenter.longitude, config.defaultCenter.latitude],
    })

    const emitViewport = () => {
      const viewport = getViewport()
      if (viewport) handlers.onViewportChange?.(viewport)
    }
    map.on('moveend', emitViewport)
    map.on('zoomend', emitViewport)
    map.on('complete', emitViewport)
    return true
  }

  function sync(input: SpatialAmapSyncInput): void {
    clearOverlays()
    if (!map || !namespace) return

    input.communities.forEach((feature) => {
      const selected = feature.id === input.selectedCommunityId
      geometryToAmapPolygons(feature.geometry).forEach((path) => {
        const polygon = new namespace!.Polygon({
          path,
          ...communityStyle(selected),
        })
        polygon.on('click', () => handlers.onCommunityClick?.(feature.id))
        map!.add(polygon)
        overlays.push(polygon)
      })
    })

    const selectedBuildingIds = new Set(input.selectedBuildingIds)
    input.buildings.forEach(({ feature, risk }) => {
      const selected = selectedBuildingIds.has(feature.id)
      geometryToAmapPolygons(feature.geometry).forEach((path) => {
        const polygon = new namespace!.Polygon({
          path,
          ...buildingStyle(risk?.riskLevel, selected),
        })
        polygon.on('click', () => handlers.onBuildingClick?.(feature.id))
        map!.add(polygon)
        overlays.push(polygon)
      })
    })
  }

  function getViewport(): SpatialBboxQuery | null {
    if (!map) return null
    const bounds = map.getBounds()
    const southWest = bounds.getSouthWest()
    const northEast = bounds.getNorthEast()
    const west = readLongitude(southWest)
    const south = readLatitude(southWest)
    const east = readLongitude(northEast)
    const north = readLatitude(northEast)
    const zoom = Math.round(map.getZoom())
    if (![west, south, east, north, zoom].every(Number.isFinite)) return null
    return { west, south, east, north, zoom }
  }

  function clearOverlays(): void {
    overlays.forEach((overlay) => overlay.setMap(null))
    overlays = []
  }

  function destroy(): void {
    clearOverlays()
    map?.destroy()
    map = null
    namespace = null
    handlers = {}
  }

  return { mount, sync, getViewport, destroy }
}

function readLongitude(value: AmapLngLatLike): number {
  if (typeof value.getLng === 'function') return value.getLng()
  return Number(value.lng)
}

function readLatitude(value: AmapLngLatLike): number {
  if (typeof value.getLat === 'function') return value.getLat()
  return Number(value.lat)
}
