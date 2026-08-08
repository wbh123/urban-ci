import type { MapRuntimeConfig } from '@/shared/api/endpoints/map'
import type { SpatialGeoJsonGeometry } from '@/shared/api/endpoints/spatial'
import { geometryToAmapPolygons, type SpatialAmapLoadOptions, type SpatialAmapLoader } from './spatial-amap'

type LngLat = { getLng?: () => number; getLat?: () => number; lng?: number; lat?: number }
type PolygonLike = { getPath: () => unknown; setMap: (map: unknown) => void }
type EditorLike = { open: () => void; close: () => void }
type MouseToolLike = { polygon: (options?: Record<string, unknown>) => void; close: (keepOverlay?: boolean) => void; on: (event: string, handler: (value: { obj: unknown }) => void) => void }
type MapLike = { add: (value: unknown) => void; setFitView: (values?: unknown[]) => void; destroy: () => void }
type EditorNamespace = {
  Map: new (container: HTMLElement, options: Record<string, unknown>) => MapLike
  Polygon: new (options: Record<string, unknown>) => PolygonLike
  PolygonEditor: new (map: MapLike, polygon: PolygonLike) => EditorLike
  MouseTool: new (map: MapLike) => MouseToolLike
}

interface EditorWindow extends Window { AMap?: EditorNamespace; _AMapSecurityConfig?: { serviceHost?: string } }

export interface BoundaryEditorHandlers { onChange?: (geometry: SpatialGeoJsonGeometry | null) => void }
export interface SpatialBoundaryEditor {
  mount: (container: HTMLElement, config: MapRuntimeConfig, handlers?: BoundaryEditorHandlers) => Promise<boolean>
  loadGeometry: (geometry: SpatialGeoJsonGeometry | null) => void
  startEdit: () => void
  startDraw: () => void
  exportGeometry: () => SpatialGeoJsonGeometry | null
  clear: () => void
  destroy: () => void
}

const DRAFT_POLYGON_STYLE = {
  strokeColor: '#155eef',
  strokeWeight: 3,
  strokeStyle: 'dashed',
  fillColor: '#91caff',
  fillOpacity: 0.22,
} as const

let editorLoaderPromise: Promise<EditorNamespace> | null = null

const defaultEditorLoader: SpatialAmapLoader = async (options: SpatialAmapLoadOptions) => {
  if (typeof window === 'undefined' || typeof document === 'undefined') throw new Error('当前运行环境不支持高德地图')
  const target = window as unknown as EditorWindow
  const callbacks = target as unknown as Record<string, unknown>
  if (target.AMap) return target.AMap as never
  if (editorLoaderPromise) return editorLoaderPromise as never
  editorLoaderPromise = new Promise<EditorNamespace>((resolve, reject) => {
    const callbackName = `__urbanSafeBoundaryAmapLoaded_${Date.now()}_${Math.random().toString(36).slice(2)}`
    callbacks[callbackName] = () => {
      delete callbacks[callbackName]
      if (target.AMap) resolve(target.AMap)
      else reject(new Error('高德地图脚本已加载，但 AMap 对象不可用'))
    }
    const params = new URLSearchParams({ v: options.version, key: options.key, callback: callbackName, plugin: options.plugins.join(',') })
    const script = document.createElement('script')
    script.async = true
    script.src = `https://webapi.amap.com/maps?${params.toString()}`
    script.onerror = () => { delete callbacks[callbackName]; editorLoaderPromise = null; reject(new Error('高德地图 JavaScript API 加载失败')) }
    document.head.appendChild(script)
  })
  return editorLoaderPromise as never
}

export function createSpatialBoundaryEditor(options: { loader?: SpatialAmapLoader } = {}): SpatialBoundaryEditor {
  const loader = options.loader ?? defaultEditorLoader
  let namespace: EditorNamespace | null = null
  let map: MapLike | null = null
  let polygons: PolygonLike[] = []
  let editors: EditorLike[] = []
  let mouseTool: MouseToolLike | null = null
  let handlers: BoundaryEditorHandlers = {}

  async function mount(container: HTMLElement, config: MapRuntimeConfig, nextHandlers: BoundaryEditorHandlers = {}): Promise<boolean> {
    destroy(); handlers = nextHandlers
    if (!config.enabled || config.mode !== 'LIVE' || !config.jsApiKey) return false
    if (config.serviceHost && typeof window !== 'undefined') (window as unknown as EditorWindow)._AMapSecurityConfig = { serviceHost: config.serviceHost }
    namespace = await loader({ key: config.jsApiKey, version: '2.0', plugins: ['AMap.PolygonEditor', 'AMap.MouseTool'] }) as unknown as EditorNamespace
    map = new namespace.Map(container, { viewMode: '2D', resizeEnable: true, zoom: config.defaultZoom, center: [config.defaultCenter.longitude, config.defaultCenter.latitude] })
    mouseTool = new namespace.MouseTool(map)
    mouseTool.on('draw', (event) => {
      const polygon = event.obj as PolygonLike
      clearPolygons()
      polygons = [polygon]
      editors = namespace && map ? [new namespace.PolygonEditor(map, polygon)] : []
      mouseTool?.close(true)
      handlers.onChange?.(exportGeometry())
    })
    return true
  }

  function loadGeometry(geometry: SpatialGeoJsonGeometry | null): void {
    clearPolygons()
    if (!geometry || !namespace || !map) { handlers.onChange?.(null); return }
    geometryToAmapPolygons(geometry).forEach((path) => {
      const polygon = new namespace!.Polygon({ path, ...DRAFT_POLYGON_STYLE })
      map!.add(polygon); polygons.push(polygon); editors.push(new namespace!.PolygonEditor(map!, polygon))
    })
    if (polygons.length) map.setFitView(polygons as unknown[])
    handlers.onChange?.(exportGeometry())
  }

  function startEdit(): void { mouseTool?.close(true); editors.forEach((editor) => editor.open()) }
  function startDraw(): void {
    editors.forEach((editor) => editor.close())
    mouseTool?.polygon({ ...DRAFT_POLYGON_STYLE })
  }
  function exportGeometry(): SpatialGeoJsonGeometry | null {
    const coordinatePolygons = polygons.map((polygon) => polygonCoordinates(polygon)).filter((rings) => rings.length > 0)
    if (!coordinatePolygons.length) return null
    return coordinatePolygons.length === 1
      ? { type: 'Polygon', coordinates: coordinatePolygons[0]! }
      : { type: 'MultiPolygon', coordinates: coordinatePolygons }
  }
  function clear(): void { clearPolygons(); handlers.onChange?.(null) }
  function clearPolygons(removeFromMap = true): void {
    editors.forEach((editor) => editor.close()); editors = []
    if (removeFromMap) polygons.forEach((polygon) => polygon.setMap(null))
    polygons = []
  }
  function destroy(): void {
    mouseTool?.close(true); mouseTool = null; clearPolygons(); map?.destroy(); map = null; namespace = null; handlers = {}
  }
  return { mount, loadGeometry, startEdit, startDraw, exportGeometry, clear, destroy }
}

function polygonCoordinates(polygon: PolygonLike): number[][][] {
  const raw = polygon.getPath()
  const rings = looksLikeRing(raw) ? [raw] : Array.isArray(raw) ? raw : []
  return rings.map((ring) => Array.isArray(ring) ? ring.map(readCoordinate).filter((item): item is number[] => item !== null) : []).filter((ring) => ring.length >= 3)
}
function looksLikeRing(value: unknown): value is unknown[] { return Array.isArray(value) && (value.length === 0 || !Array.isArray(value[0])) }
function readCoordinate(value: unknown): number[] | null {
  const point = value as LngLat
  const lng = typeof point?.getLng === 'function' ? point.getLng() : Number(point?.lng)
  const lat = typeof point?.getLat === 'function' ? point.getLat() : Number(point?.lat)
  return Number.isFinite(lng) && Number.isFinite(lat) ? [lng, lat] : null
}
