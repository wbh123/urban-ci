import type { MapRuntimeConfig } from '@/shared/api/endpoints/map'
import type { SpatialGeoJsonGeometry } from '@/shared/api/endpoints/spatial'
import { loadAmap } from './amap-loader'
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

export interface BoundaryEditorHandlers { onChange?: (geometry: SpatialGeoJsonGeometry | null) => void }
export interface SpatialBoundaryEditor {
  mount: (container: HTMLElement, config: MapRuntimeConfig, handlers?: BoundaryEditorHandlers) => Promise<boolean>
  loadGeometry: (geometry: SpatialGeoJsonGeometry | null) => void
  previewGeometry: (geometry: SpatialGeoJsonGeometry | null) => void
  clearPreview: () => void
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

const CANDIDATE_POLYGON_STYLE = {
  strokeColor: '#d97706',
  strokeWeight: 3,
  strokeStyle: 'dashed',
  strokeOpacity: 0.9,
  fillColor: '#fbbf24',
  fillOpacity: 0.1,
  zIndex: 80,
} as const

const defaultEditorLoader: SpatialAmapLoader = async (options: SpatialAmapLoadOptions) => (
  await loadAmap(options) as unknown as EditorNamespace as never
)

export function createSpatialBoundaryEditor(options: { loader?: SpatialAmapLoader } = {}): SpatialBoundaryEditor {
  const loader = options.loader ?? defaultEditorLoader
  let namespace: EditorNamespace | null = null
  let map: MapLike | null = null
  let polygons: PolygonLike[] = []
  let previewPolygons: PolygonLike[] = []
  let editors: EditorLike[] = []
  let mouseTool: MouseToolLike | null = null
  let handlers: BoundaryEditorHandlers = {}

  async function mount(container: HTMLElement, config: MapRuntimeConfig, nextHandlers: BoundaryEditorHandlers = {}): Promise<boolean> {
    destroy(); handlers = nextHandlers
    if (!config.enabled || config.mode !== 'LIVE' || !config.jsApiKey) return false
    namespace = await loader({
      key: config.jsApiKey,
      version: '2.0',
      plugins: ['AMap.PolygonEditor', 'AMap.MouseTool'],
      serviceHost: config.serviceHost || undefined,
    }) as unknown as EditorNamespace
    map = new namespace.Map(container, { viewMode: '2D', resizeEnable: true, zoom: config.defaultZoom, center: [config.defaultCenter.longitude, config.defaultCenter.latitude] })
    mouseTool = new namespace.MouseTool(map)
    mouseTool.on('draw', (event) => {
      const polygon = event.obj as PolygonLike
      clearPreview()
      clearPolygons()
      polygons = [polygon]
      editors = namespace && map ? [new namespace.PolygonEditor(map, polygon)] : []
      // 高德 MouseTool.close(true) 会清除刚绘制的覆盖物；比赛演示需要草稿持续可见。
      mouseTool?.close(false)
      handlers.onChange?.(exportGeometry())
    })
    return true
  }

  function loadGeometry(geometry: SpatialGeoJsonGeometry | null): void {
    clearPreview()
    clearPolygons()
    if (!geometry || !namespace || !map) { handlers.onChange?.(null); return }
    geometryToAmapPolygons(geometry).forEach((path) => {
      const polygon = new namespace!.Polygon({ path, ...DRAFT_POLYGON_STYLE })
      map!.add(polygon); polygons.push(polygon); editors.push(new namespace!.PolygonEditor(map!, polygon))
    })
    if (polygons.length) map.setFitView(polygons as unknown[])
    handlers.onChange?.(exportGeometry())
  }

  function previewGeometry(geometry: SpatialGeoJsonGeometry | null): void {
    clearPreview()
    if (!geometry || !namespace || !map) return
    geometryToAmapPolygons(geometry).forEach((path) => {
      const polygon = new namespace!.Polygon({ path, ...CANDIDATE_POLYGON_STYLE })
      map!.add(polygon)
      previewPolygons.push(polygon)
    })
    if (previewPolygons.length) map.setFitView([...polygons, ...previewPolygons] as unknown[])
  }

  function clearPreview(): void {
    previewPolygons.forEach((polygon) => polygon.setMap(null))
    previewPolygons = []
  }

  function startEdit(): void {
    clearPreview()
    mouseTool?.close(false)
    editors.forEach((editor) => editor.open())
  }
  function startDraw(): void {
    clearPreview()
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
  function clear(): void { clearPreview(); clearPolygons(); handlers.onChange?.(null) }
  function clearPolygons(removeFromMap = true): void {
    editors.forEach((editor) => editor.close()); editors = []
    if (removeFromMap) polygons.forEach((polygon) => polygon.setMap(null))
    polygons = []
  }
  function destroy(): void {
    mouseTool?.close(true); mouseTool = null; clearPreview(); clearPolygons(); map?.destroy(); map = null; namespace = null; handlers = {}
  }
  return { mount, loadGeometry, previewGeometry, clearPreview, startEdit, startDraw, exportGeometry, clear, destroy }
}

function polygonCoordinates(polygon: PolygonLike): number[][][] {
  const raw = polygon.getPath()
  const rings = looksLikeRing(raw) ? [raw] : Array.isArray(raw) ? raw : []
  return rings
    .map((ring) => Array.isArray(ring)
      ? ring.map(readCoordinate).filter((item): item is number[] => item !== null)
      : [])
    .map(closeRing)
    .filter((ring) => ring.length >= 4)
}

function closeRing(ring: number[][]): number[][] {
  if (ring.length < 3) return []
  const first = ring[0]!
  const last = ring[ring.length - 1]!
  const closed = first[0] === last[0] && first[1] === last[1]
    ? ring
    : [...ring, [first[0]!, first[1]!]]
  return closed.length >= 4 ? closed : []
}

function looksLikeRing(value: unknown): value is unknown[] { return Array.isArray(value) && (value.length === 0 || !Array.isArray(value[0])) }
function readCoordinate(value: unknown): number[] | null {
  const point = value as LngLat
  const lng = typeof point?.getLng === 'function' ? point.getLng() : Number(point?.lng)
  const lat = typeof point?.getLat === 'function' ? point.getLat() : Number(point?.lat)
  return Number.isFinite(lng) && Number.isFinite(lat) ? [lng, lat] : null
}