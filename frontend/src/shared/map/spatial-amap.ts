import type { MapRuntimeConfig } from '@/shared/api/endpoints/map'
import type { SpatialGeoJsonFeature, SpatialGeoJsonGeometry } from '@/shared/api/endpoints/spatial'
import type { SpatialBboxQuery } from '@/shared/api/endpoints/spatial'
import type { SpatialBuildingProjection } from '@/stores/spatial-map'
import { loadAmap, type AmapLoadOptions } from './amap-loader'

interface AmapLngLatLike {
  getLng?: () => number
  getLat?: () => number
  lng?: number
  lat?: number
}

interface AmapPixelLike {
  getX?: () => number
  getY?: () => number
  x?: number
  y?: number
}

interface AmapMouseEventLike {
  lnglat?: AmapLngLatLike
  pixel?: AmapPixelLike
}

interface AmapBoundsLike {
  getSouthWest: () => AmapLngLatLike
  getNorthEast: () => AmapLngLatLike
}

interface AmapMapLike {
  add: (overlay: unknown) => void
  on: (event: string, handler: (...args: unknown[]) => void) => void
  getZoom: () => number
  getCenter?: () => AmapLngLatLike
  getBounds: () => AmapBoundsLike
  getPitch?: () => number
  getRotation?: () => number
  setZoomAndCenter?: (
    zoom: number,
    center: [number, number],
    immediately?: boolean,
    duration?: number,
  ) => void
  setPitch?: (pitch: number, immediately?: boolean, duration?: number) => void
  setRotation?: (rotation: number, immediately?: boolean, duration?: number) => void
  lngLatToContainer?: (lnglat: [number, number]) => AmapPixelLike
  containerToLngLat?: (pixel: [number, number]) => AmapLngLatLike
  destroy: () => void
}

interface AmapOverlayLike {
  on: (event: string, handler: (event?: AmapMouseEventLike) => void) => void
  setMap: (map: AmapMapLike | null) => void
}

interface AmapBuildingsLike {
  on?: (event: string, handler: (event?: AmapMouseEventLike) => void) => void
  setStyle: (options: Record<string, unknown>) => void
  setzIndex?: (zIndex: number) => void
  setOpacity?: (opacity: number) => void
}

interface AmapDistrictResultLike {
  districtList?: Array<{
    boundaries?: unknown[]
  }>
}

interface AmapDistrictSearchLike {
  search: (
    keyword: string,
    callback: (status: string, result?: AmapDistrictResultLike) => void,
  ) => void
}

interface AmapNamespaceLike {
  Map: new (container: HTMLElement, options: Record<string, unknown>) => AmapMapLike
  Polygon: new (options: Record<string, unknown>) => AmapOverlayLike
  CircleMarker?: new (options: Record<string, unknown>) => AmapOverlayLike
  Buildings?: new (options: Record<string, unknown>) => AmapBuildingsLike
  DistrictSearch?: new (options: Record<string, unknown>) => AmapDistrictSearchLike
  createDefaultLayer?: () => unknown
}

export type SpatialAmapLoadOptions = AmapLoadOptions
export type SpatialAmapLoader = (options: SpatialAmapLoadOptions) => Promise<AmapNamespaceLike>
export type SpatialAmapTheme = 'LIGHT' | 'DARK'
export type SpatialAmapBuildingSource = 'SYSTEM' | 'AMAP_ONLY'

export interface SpatialAmapClickContext {
  longitude?: number
  latitude?: number
  pixelX?: number
  pixelY?: number
}

export interface SpatialAmapContainerPoint {
  x: number
  y: number
}

export interface SpatialAmapPointFeature {
  id: string
  longitude: number
  latitude: number
  kind: 'COMMUNITY' | 'BUILDING'
  label?: string
  riskLevel?: string
  priorityLevel?: string
  freshness?: 'CURRENT' | 'STALE' | 'NO_RESULT'
}

export interface SpatialAmapBuildingModelHit {
  source: SpatialAmapBuildingSource
  buildingId?: string
  longitude: number
  latitude: number
  pixelX?: number
  pixelY?: number
  riskLevel?: string
}

export type SpatialAmapActiveBuilding = SpatialAmapBuildingModelHit

export interface SpatialAmapHandlers {
  onViewportChange?: (viewport: SpatialBboxQuery) => void
  onMapTransform?: () => void
  onMapBlankClick?: () => void
  onCommunityClick?: (communityId: string, context?: SpatialAmapClickContext) => void
  onBuildingClick?: (buildingId: string, context?: SpatialAmapClickContext) => void
  onBuildingModelClick?: (hit: SpatialAmapBuildingModelHit) => void
}

export interface SpatialAmapSyncInput {
  communities: SpatialGeoJsonFeature[]
  buildings: SpatialBuildingProjection[]
  communityPoints?: SpatialAmapPointFeature[]
  buildingPoints?: SpatialAmapPointFeature[]
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
  setActiveBuilding: (
    selection: SpatialAmapActiveBuilding | null,
    input: SpatialAmapSyncInput,
  ) => void
  focusBuilding: (selection: SpatialAmapActiveBuilding) => boolean
  focusBuildingOutline: (selection: SpatialAmapActiveBuilding) => boolean
  setOverviewPresentation: (threeDimensional: boolean) => boolean
  restoreOverview: () => boolean
  setBuildingPresentationView: (
    buildingId: string,
    input: SpatialAmapSyncInput,
    threeDimensional: boolean,
  ) => boolean
  projectToContainer: (longitude: number, latitude: number) => SpatialAmapContainerPoint | null
  getViewport: () => SpatialBboxQuery | null
  destroy: () => void
}

type Coordinate = [number, number]
type Ring = Coordinate[]
type PolygonPath = Ring[]

interface PresentationState {
  center: Coordinate
  zoom: number
  pitch: number
  rotation: number
}

interface MouseGestureState {
  startX: number
  startY: number
  dragged: boolean
}

interface PlainLeftGesture {
  clientX: number
  clientY: number
  at: number
}

const DEFAULT_PLUGINS = ['AMap.PolygonEditor', 'AMap.MouseTool']
const POI_HIGHLIGHT_HALF_SIZE_METERS = 24
const BUILDING_CLICK_RADIUS_METERS = 60
const BUILDING_SCREEN_CLICK_RADIUS_PX = 58
const BUILDING_MODEL_MIN_ZOOM = 17.2
const COMMUNITY_POINT_FOCUS_ZOOM = BUILDING_MODEL_MIN_ZOOM
const PLAIN_LEFT_DRAG_THRESHOLD_PX = 6
const PLAIN_LEFT_CLICK_POSITION_TOLERANCE_PX = 10
const PLAIN_LEFT_GESTURE_MAX_AGE_MS = 320
const PLAIN_LEFT_CONFIRM_DELAY_MS = 180
const BUILDING_PRESENTATION_ZOOM = 18.4
const BUILDING_PRESENTATION_PITCH = 60
const BUILDING_PRESENTATION_ROTATION = -30
const OVERVIEW_3D_PITCH = 48
const OVERVIEW_3D_ROTATION = 0
const MAP_CAMERA_MOVE_DURATION_MS = 520
const MAP_CAMERA_ROTATE_DURATION_MS = 420

const RISK_COLORS: Record<string, string> = {
  LOW: '#42d392',
  MEDIUM: '#f2c94c',
  HIGH: '#ff941f',
  VERY_HIGH: '#ff4d5d',
}
const PRIORITY_COLORS: Record<string, string> = {
  P1: '#d85cff',
  P2: '#4f8cff',
  P3: '#22d3d8',
  P4: '#94a3b8',
}
const STATUS_UNKNOWN_COLOR = '#64748b'

const EMPTY_SYNC_INPUT: SpatialAmapSyncInput = {
  communities: [],
  buildings: [],
  communityPoints: [],
  buildingPoints: [],
  selectedCommunityId: null,
  selectedBuildingIds: [],
}

const defaultLoader: SpatialAmapLoader = async (options) => (
  await loadAmap(options) as unknown as AmapNamespaceLike
)

export function geometryToAmapPolygons(geometry: SpatialGeoJsonGeometry): PolygonPath[] {
  if (geometry.type === 'Polygon') return [normalizePolygon(geometry.coordinates)]
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

function communityStyle(selected: boolean, theme: SpatialAmapTheme): Record<string, unknown> {
  if (theme === 'DARK') {
    return {
      strokeColor: selected ? '#c8fff5' : '#57d6c2',
      strokeWeight: selected ? 3.4 : 2,
      strokeOpacity: selected ? 1 : 0.88,
      fillColor: selected ? '#52e0c4' : '#2fb9a5',
      fillOpacity: selected ? 0.22 : 0.08,
      zIndex: selected ? 40 : 10,
      bubble: false,
    }
  }
  return {
    strokeColor: selected ? '#155eef' : '#6688c4',
    strokeWeight: selected ? 3.2 : 1.8,
    strokeOpacity: selected ? 1 : 0.72,
    fillColor: selected ? '#91caff' : '#dbe8f7',
    fillOpacity: selected ? 0.14 : 0.05,
    zIndex: selected ? 40 : 10,
    bubble: false,
  }
}

function buildingStyle(
  selected: boolean,
  theme: SpatialAmapTheme,
  officialBuildings: boolean,
): Record<string, unknown> {
  const accent = theme === 'DARK' ? '#8ff5df' : '#155eef'
  const neutral = theme === 'DARK' ? '#5b8f8a' : '#8ca0bd'
  if (officialBuildings) {
    return {
      strokeColor: neutral,
      strokeWeight: 0,
      strokeOpacity: 0,
      fillColor: accent,
      fillOpacity: 0,
      zIndex: selected ? 190 : 125,
      bubble: false,
    }
  }
  return {
    strokeColor: selected ? '#ffffff' : neutral,
    strokeWeight: selected ? 4 : 1.8,
    strokeOpacity: selected ? 1 : 0.72,
    fillColor: selected ? accent : neutral,
    fillOpacity: selected ? 0.28 : 0.06,
    zIndex: selected ? 90 : 35,
    bubble: false,
  }
}

function pointStyle(
  point: SpatialAmapPointFeature,
  selected: boolean,
  theme: SpatialAmapTheme,
): Record<string, unknown> {
  const color = point.kind === 'BUILDING'
    ? (theme === 'DARK' ? '#77ddca' : '#64748b')
    : (theme === 'DARK' ? '#57d6c2' : '#155eef')
  return {
    center: [point.longitude, point.latitude],
    radius: selected ? (point.kind === 'BUILDING' ? 9 : 10) : (point.kind === 'BUILDING' ? 5.5 : 7),
    strokeColor: selected ? '#ffffff' : color,
    strokeWeight: selected ? 3.5 : 1.5,
    strokeOpacity: 1,
    fillColor: color,
    fillOpacity: selected ? 1 : (point.kind === 'BUILDING' ? 0.8 : 0.72),
    bubble: false,
    cursor: 'pointer',
    zIndex: selected ? 180 : (point.kind === 'BUILDING' ? 58 : 48),
  }
}

function statusFreshnessOpacity(point: SpatialAmapPointFeature): number {
  return point.freshness === 'STALE' ? 0.48 : 1
}

function statusRiskColor(point: SpatialAmapPointFeature): string {
  if (point.freshness === 'NO_RESULT') return STATUS_UNKNOWN_COLOR
  return RISK_COLORS[point.riskLevel ?? ''] ?? STATUS_UNKNOWN_COLOR
}

function statusPriorityColor(point: SpatialAmapPointFeature): string {
  if (point.freshness === 'NO_RESULT') return STATUS_UNKNOWN_COLOR
  return PRIORITY_COLORS[point.priorityLevel ?? ''] ?? STATUS_UNKNOWN_COLOR
}

function nativeStatusPriorityStyle(
  point: SpatialAmapPointFeature,
  selected: boolean,
): Record<string, unknown> {
  const opacity = statusFreshnessOpacity(point)
  return {
    center: [point.longitude, point.latitude],
    radius: selected ? 12 : 10,
    strokeColor: selected ? '#ffffff' : statusPriorityColor(point),
    strokeWeight: selected ? 5 : 4,
    strokeOpacity: opacity,
    fillColor: statusPriorityColor(point),
    fillOpacity: 0,
    bubble: false,
    cursor: 'pointer',
    zIndex: selected ? 205 : 165,
  }
}

function nativeStatusRiskStyle(
  point: SpatialAmapPointFeature,
  selected: boolean,
): Record<string, unknown> {
  const opacity = statusFreshnessOpacity(point)
  return {
    center: [point.longitude, point.latitude],
    radius: selected ? 7 : 6,
    strokeColor: selected ? '#ffffff' : 'rgba(255,255,255,.7)',
    strokeWeight: selected ? 2.5 : 1.2,
    strokeOpacity: opacity,
    fillColor: statusRiskColor(point),
    fillOpacity: point.freshness === 'NO_RESULT' ? 0.58 : 0.94 * opacity,
    bubble: false,
    cursor: 'pointer',
    zIndex: selected ? 206 : 166,
  }
}

function selectedBuildingBlockColors(): { roof: string; wall: string } {
  return {
    roof: 'fff5fffd',
    wall: 'ff55f1d3',
  }
}

function pointFence(point: Pick<SpatialAmapPointFeature, 'longitude' | 'latitude'>): Ring {
  return coordinateFence(point.longitude, point.latitude)
}

function coordinateFence(longitude: number, latitude: number, halfSizeMeters = POI_HIGHLIGHT_HALF_SIZE_METERS): Ring {
  const latDelta = halfSizeMeters / 111_320
  const cosine = Math.max(0.25, Math.cos((latitude * Math.PI) / 180))
  const lngDelta = halfSizeMeters / (111_320 * cosine)
  return [
    [longitude - lngDelta, latitude - latDelta],
    [longitude + lngDelta, latitude - latDelta],
    [longitude + lngDelta, latitude + latDelta],
    [longitude - lngDelta, latitude + latDelta],
    [longitude - lngDelta, latitude - latDelta],
  ]
}

function clickContext(event?: AmapMouseEventLike): SpatialAmapClickContext | undefined {
  const context: SpatialAmapClickContext = {}
  if (event?.lnglat) {
    const longitude = readLongitude(event.lnglat)
    const latitude = readLatitude(event.lnglat)
    if (Number.isFinite(longitude)) context.longitude = longitude
    if (Number.isFinite(latitude)) context.latitude = latitude
  }
  if (event?.pixel) {
    const pixelX = readPixelX(event.pixel)
    const pixelY = readPixelY(event.pixel)
    if (Number.isFinite(pixelX)) context.pixelX = pixelX
    if (Number.isFinite(pixelY)) context.pixelY = pixelY
  }
  return Object.keys(context).length > 0 ? context : undefined
}

function hasMouseModifier(event: MouseEvent): boolean {
  return event.ctrlKey || event.shiftKey || event.altKey || event.metaKey
}

export function createSpatialAmapDriver(
  options: {
    loader?: SpatialAmapLoader
    theme?: SpatialAmapTheme
    mapStyle?: string
    features?: string[]
    showOfficialBuildings?: boolean
    districtAdcodes?: string[]
  } = {},
): SpatialAmapDriver {
  const loader = options.loader ?? defaultLoader
  const theme = options.theme ?? 'LIGHT'
  const mapStyle = options.mapStyle ?? (theme === 'DARK' ? 'amap://styles/darkblue' : 'amap://styles/whitesmoke')
  const configuredFeatures = options.features ?? ['bg', 'road']
  const districtAdcodes = options.districtAdcodes ?? []
  const showOfficialBuildings = options.showOfficialBuildings === true

  let namespace: AmapNamespaceLike | null = null
  let map: AmapMapLike | null = null
  let mapContainer: HTMLElement | null = null
  let nativeClickHandler: ((event: MouseEvent) => void) | null = null
  let nativeMouseDownHandler: ((event: MouseEvent) => void) | null = null
  let nativeMouseMoveHandler: ((event: MouseEvent) => void) | null = null
  let nativeMouseUpHandler: ((event: MouseEvent) => void) | null = null
  let nativeClickTimer: ReturnType<typeof setTimeout> | null = null
  let activeMouseGesture: MouseGestureState | null = null
  let pendingPlainLeftGesture: PlainLeftGesture | null = null
  let baseBuildingsLayer: AmapBuildingsLike | null = null
  let handlers: SpatialAmapHandlers = {}
  let overlays: AmapOverlayLike[] = []
  let baseOverlays: AmapOverlayLike[] = []
  let latestSyncInput: SpatialAmapSyncInput = EMPTY_SYNC_INPUT
  let activeBuilding: SpatialAmapActiveBuilding | null = null
  let overviewState: PresentationState | null = null
  let overviewThreeDimensional = showOfficialBuildings
  let mountSequence = 0
  let mapGeneration = 0
  let highlightSignature = ''

  async function mount(
    container: HTMLElement,
    config: MapRuntimeConfig,
    nextHandlers: SpatialAmapHandlers = {},
  ): Promise<boolean> {
    destroy()
    const currentMount = ++mountSequence
    handlers = nextHandlers
    if (config.mode !== 'LIVE' || !config.jsApiKey) return false

    namespace = await loader({
      key: config.jsApiKey,
      version: '2.0',
      plugins: [
        ...DEFAULT_PLUGINS,
        ...(districtAdcodes.length > 0 ? ['AMap.DistrictSearch'] : []),
      ],
      serviceHost: config.serviceHost || undefined,
    })
    if (currentMount !== mountSequence || !namespace) return false

    const generation = ++mapGeneration
    let initialLayers: unknown[] | undefined
    if (showOfficialBuildings && namespace.Buildings) {
      baseBuildingsLayer = new namespace.Buildings({
        zooms: [15, 20],
        zIndex: 130,
        opacity: 1,
        heightFactor: 2.2,
        wallColor: 'rgba(22,73,78,1)',
        roofColor: 'rgba(40,108,109,1)',
        styleOpts: { hideWithoutStyle: false, areas: [] },
      })
      baseBuildingsLayer.setStyle({ hideWithoutStyle: false, areas: [] })
      highlightSignature = 'NONE'
      if (namespace.createDefaultLayer) {
        initialLayers = [
          namespace.createDefaultLayer(),
          baseBuildingsLayer,
        ]
      }
    }

    overviewThreeDimensional = showOfficialBuildings
    const mapOptions: Record<string, unknown> = {
      viewMode: showOfficialBuildings ? '3D' : '2D',
      pitch: showOfficialBuildings ? OVERVIEW_3D_PITCH : 0,
      rotation: showOfficialBuildings ? OVERVIEW_3D_ROTATION : 0,
      pitchEnable: showOfficialBuildings,
      rotateEnable: showOfficialBuildings,
      keyboardEnable: true,
      resizeEnable: true,
      animateEnable: showOfficialBuildings,
      buildingAnimation: false,
      zooms: [2, 20],
      zoom: config.defaultZoom,
      center: [config.defaultCenter.longitude, config.defaultCenter.latitude],
      mapStyle,
      features: showOfficialBuildings ? ['bg', 'point', 'road'] : configuredFeatures,
      showBuildingBlock: !showOfficialBuildings,
      skyColor: theme === 'DARK' ? '#07191b' : '#dfeef7',
    }
    if (initialLayers) mapOptions.layers = initialLayers

    map = new namespace.Map(container, mapOptions)
    mapContainer = container
    if (!initialLayers && baseBuildingsLayer) map.add(baseBuildingsLayer)
    baseBuildingsLayer?.setzIndex?.(130)
    baseBuildingsLayer?.setOpacity?.(1)

    bindMapEvents(generation)
    bindNativeContainerClick(container, generation)
    drawOfficialDistricts(generation)
    sync(latestSyncInput)
    return true
  }

  function bindMapEvents(generation: number): void {
    if (!map) return
    const emitViewport = () => {
      if (generation !== mapGeneration) return
      const nextViewport = getViewport()
      if (nextViewport) handlers.onViewportChange?.(nextViewport)
      handlers.onMapTransform?.()
    }
    const emitTransform = () => {
      if (generation === mapGeneration) handlers.onMapTransform?.()
    }
    const dispatchBuildingLayerHit = (event?: AmapMouseEventLike) => {
      if (generation !== mapGeneration || !map || map.getZoom() < BUILDING_MODEL_MIN_ZOOM) return
      cancelPendingBuildingSelection()
      const context = clickContext(event)
      if (!context || !Number.isFinite(context.longitude) || !Number.isFinite(context.latitude)) return
      handlers.onBuildingModelClick?.(buildingHitFromContext(context, latestSyncInput))
    }

    map.on('mapmove', emitTransform)
    map.on('zoomchange', emitTransform)
    map.on('moveend', emitViewport)
    map.on('zoomend', emitViewport)
    map.on('resize', emitTransform)
    map.on('complete', emitViewport)
    baseBuildingsLayer?.on?.('click', dispatchBuildingLayerHit)
  }

  function bindNativeContainerClick(container: HTMLElement, generation: number): void {
    unbindNativeContainerClick()
    mapContainer = container

    nativeMouseDownHandler = (event: MouseEvent) => {
      cancelPendingBuildingSelection()
      if (event.button !== 0 || hasMouseModifier(event)) {
        activeMouseGesture = null
        return
      }
      activeMouseGesture = {
        startX: event.clientX,
        startY: event.clientY,
        dragged: false,
      }
    }

    nativeMouseMoveHandler = (event: MouseEvent) => {
      if (!activeMouseGesture) return
      if (
        Math.hypot(
          event.clientX - activeMouseGesture.startX,
          event.clientY - activeMouseGesture.startY,
        ) > PLAIN_LEFT_DRAG_THRESHOLD_PX
      ) {
        activeMouseGesture.dragged = true
      }
    }

    nativeMouseUpHandler = (event: MouseEvent) => {
      const gesture = activeMouseGesture
      activeMouseGesture = null
      if (!gesture) {
        pendingPlainLeftGesture = null
        return
      }
      const distance = Math.hypot(
        event.clientX - gesture.startX,
        event.clientY - gesture.startY,
      )
      if (
        event.button !== 0
        || hasMouseModifier(event)
        || gesture.dragged
        || distance > PLAIN_LEFT_DRAG_THRESHOLD_PX
      ) {
        pendingPlainLeftGesture = null
        return
      }
      pendingPlainLeftGesture = {
        clientX: event.clientX,
        clientY: event.clientY,
        at: Date.now(),
      }
    }

    nativeClickHandler = (event: MouseEvent) => {
      if (
        generation !== mapGeneration
        || !showOfficialBuildings
        || !map
        || !map.containerToLngLat
      ) return

      if (event.button !== 0 || hasMouseModifier(event)) {
        cancelPendingBuildingSelection()
        return
      }
      if (event.detail > 1) {
        cancelPendingBuildingSelection()
        return
      }

      const gesture = pendingPlainLeftGesture
      pendingPlainLeftGesture = null
      if (!gesture || Date.now() - gesture.at > PLAIN_LEFT_GESTURE_MAX_AGE_MS) return
      if (
        Math.hypot(event.clientX - gesture.clientX, event.clientY - gesture.clientY)
        > PLAIN_LEFT_CLICK_POSITION_TOLERANCE_PX
      ) return

      const target = event.target
      if (
        target instanceof Element
        && target.closest('.amap-control, .amap-controls, .amap-toolbar, .amap-info, .amap-menu')
      ) return

      const rect = container.getBoundingClientRect()
      const pixelX = event.clientX - rect.left
      const pixelY = event.clientY - rect.top
      if (
        !Number.isFinite(pixelX)
        || !Number.isFinite(pixelY)
        || pixelX < 0
        || pixelY < 0
        || pixelX > rect.width
        || pixelY > rect.height
      ) return

      cancelNativeClickTimer()
      nativeClickTimer = setTimeout(() => {
        nativeClickTimer = null
        if (
          generation !== mapGeneration
          || !map
          || !map.containerToLngLat
        ) return

        if (map.getZoom() < BUILDING_MODEL_MIN_ZOOM) {
          handlers.onMapBlankClick?.()
          return
        }

        const lnglat = map.containerToLngLat([pixelX, pixelY])
        const longitude = readLongitude(lnglat)
        const latitude = readLatitude(lnglat)
        if (!Number.isFinite(longitude) || !Number.isFinite(latitude)) return

        const context: SpatialAmapClickContext = {
          longitude,
          latitude,
          pixelX,
          pixelY,
        }
        const hit = buildingHitFromContext(context, latestSyncInput)
        if (hit.source === 'SYSTEM') {
          handlers.onBuildingModelClick?.(hit)
          return
        }
        handlers.onMapBlankClick?.()
      }, PLAIN_LEFT_CONFIRM_DELAY_MS)
    }

    container.addEventListener('mousedown', nativeMouseDownHandler, true)
    container.addEventListener('mousemove', nativeMouseMoveHandler, true)
    container.addEventListener('mouseup', nativeMouseUpHandler, true)
    container.addEventListener('click', nativeClickHandler, true)
  }

  function cancelNativeClickTimer(): void {
    if (nativeClickTimer !== null) {
      clearTimeout(nativeClickTimer)
      nativeClickTimer = null
    }
  }

  function cancelPendingBuildingSelection(): void {
    cancelNativeClickTimer()
    pendingPlainLeftGesture = null
  }

  function unbindNativeContainerClick(): void {
    cancelPendingBuildingSelection()
    activeMouseGesture = null
    if (mapContainer && nativeMouseDownHandler) {
      mapContainer.removeEventListener('mousedown', nativeMouseDownHandler, true)
    }
    if (mapContainer && nativeMouseMoveHandler) {
      mapContainer.removeEventListener('mousemove', nativeMouseMoveHandler, true)
    }
    if (mapContainer && nativeMouseUpHandler) {
      mapContainer.removeEventListener('mouseup', nativeMouseUpHandler, true)
    }
    if (mapContainer && nativeClickHandler) {
      mapContainer.removeEventListener('click', nativeClickHandler, true)
    }
    nativeMouseDownHandler = null
    nativeMouseMoveHandler = null
    nativeMouseUpHandler = null
    nativeClickHandler = null
    mapContainer = null
  }

  function buildingHitFromContext(
    context: SpatialAmapClickContext,
    input: SpatialAmapSyncInput,
  ): SpatialAmapBuildingModelHit {
    const polygonBuilding = buildingContainingPoint(context, input)
    if (polygonBuilding) {
      const center = resolveBuildingCenter(polygonBuilding.feature.id, input)
      return {
        source: 'SYSTEM',
        buildingId: polygonBuilding.feature.id,
        longitude: center?.[0] ?? Number(context.longitude),
        latitude: center?.[1] ?? Number(context.latitude),
        pixelX: context.pixelX,
        pixelY: context.pixelY,
        riskLevel: polygonBuilding.risk?.riskLevel,
      }
    }

    const point = nearestBuildingPointForClick(context, input)
    return point
      ? {
          source: 'SYSTEM',
          buildingId: point.id,
          longitude: point.longitude,
          latitude: point.latitude,
          pixelX: context.pixelX,
          pixelY: context.pixelY,
          riskLevel: point.riskLevel,
        }
      : {
          source: 'AMAP_ONLY',
          longitude: Number(context.longitude),
          latitude: Number(context.latitude),
          pixelX: context.pixelX,
          pixelY: context.pixelY,
        }
  }

  function nearestBuildingPointForClick(
    context: SpatialAmapClickContext,
    input: SpatialAmapSyncInput,
  ): SpatialAmapPointFeature | null {
    const screenPoint = nearestBuildingPointByPixel(context, input, map)
    return screenPoint ?? nearestBuildingPoint(context, input)
  }

  function drawOfficialDistricts(generation: number): void {
    if (!map || !namespace?.DistrictSearch || districtAdcodes.length === 0) return
    districtAdcodes.forEach((adcode) => {
      if (!namespace?.DistrictSearch) return
      const search = new namespace.DistrictSearch({
        subdistrict: 0,
        extensions: 'all',
        level: 'district',
      })
      search.search(adcode, (status, result) => {
        if (status !== 'complete' || generation !== mapGeneration || !map || !namespace) return
        const boundaries = result?.districtList?.[0]?.boundaries ?? []
        boundaries.forEach((path) => {
          const polygon = new namespace!.Polygon({
            path,
            strokeColor: theme === 'DARK' ? '#6dd8ca' : '#7a9ccc',
            strokeWeight: theme === 'DARK' ? 1.4 : 1.2,
            strokeOpacity: theme === 'DARK' ? 0.34 : 0.28,
            fillColor: theme === 'DARK' ? '#0d4b4c' : '#dbeafe',
            fillOpacity: theme === 'DARK' ? 0.018 : 0.025,
            zIndex: 3,
            bubble: false,
          })
          map!.add(polygon)
          baseOverlays.push(polygon)
        })
      })
    })
  }

  function sync(input: SpatialAmapSyncInput): void {
    latestSyncInput = input
    clearOverlays()
    if (!map || !namespace) return

    const communityPolygonIds = new Set<string>()
    input.communities.forEach((feature) => {
      const selected = feature.id === input.selectedCommunityId
      const polygons = geometryToAmapPolygons(feature.geometry)
      if (polygons.length > 0) communityPolygonIds.add(feature.id)
      polygons.forEach((path) => {
        const polygon = new namespace!.Polygon({ path, ...communityStyle(selected, theme) })
        polygon.on('click', (event) => {
          cancelPendingBuildingSelection()
          const context = clickContext(event)
          if (context) handlers.onCommunityClick?.(feature.id, context)
          else handlers.onCommunityClick?.(feature.id)
        })
        map!.add(polygon)
        overlays.push(polygon)
      })
    })

    const selectedBuildingIds = new Set(input.selectedBuildingIds)
    const buildingPolygonIds = new Set<string>()
    input.buildings.forEach(({ feature }) => {
      const selected = selectedBuildingIds.has(feature.id)
      const polygons = geometryToAmapPolygons(feature.geometry)
      if (polygons.length > 0) buildingPolygonIds.add(feature.id)
      polygons.forEach((path) => {
        const polygon = new namespace!.Polygon({
          path,
          ...buildingStyle(selected, theme, showOfficialBuildings),
        })
        if (!showOfficialBuildings) {
          polygon.on('click', (event) => {
            const context = clickContext(event)
            if (context) handlers.onBuildingClick?.(feature.id, context)
            else handlers.onBuildingClick?.(feature.id)
          })
        }
        map!.add(polygon)
        overlays.push(polygon)
      })
    })

    const CircleMarker = namespace.CircleMarker
    if (CircleMarker) {
      input.communityPoints
        ?.filter((point) => !communityPolygonIds.has(point.id))
        .filter(validPoint)
        .forEach((point) => {
          const marker = new CircleMarker({ ...pointStyle(point, point.id === input.selectedCommunityId, theme) })
          marker.on('click', (event) => {
            cancelPendingBuildingSelection()
            const currentMap = map
            if (currentMap?.setZoomAndCenter) {
              if (!overviewState) overviewState = capturePresentationState()
              currentMap.setZoomAndCenter(
                Math.max(currentMap.getZoom(), COMMUNITY_POINT_FOCUS_ZOOM),
                [point.longitude, point.latitude],
                false,
                MAP_CAMERA_MOVE_DURATION_MS,
              )
              handlers.onMapTransform?.()
            }
            const context = clickContext(event)
            if (context) handlers.onCommunityClick?.(point.id, context)
            else handlers.onCommunityClick?.(point.id)
          })
          map!.add(marker)
          overlays.push(marker)
        })

      input.buildingPoints
        ?.filter(validPoint)
        .filter((point) => showOfficialBuildings || !buildingPolygonIds.has(point.id))
        .forEach((point) => {
          const selected = selectedBuildingIds.has(point.id)
          const handleClick = (event?: AmapMouseEventLike) => {
            cancelPendingBuildingSelection()
            const context = clickContext(event)
            if (context) handlers.onBuildingClick?.(point.id, context)
            else handlers.onBuildingClick?.(point.id)
          }
          if (showOfficialBuildings) {
            const priorityRing = new CircleMarker({ ...nativeStatusPriorityStyle(point, selected) })
            const riskCore = new CircleMarker({ ...nativeStatusRiskStyle(point, selected) })
            priorityRing.on('click', handleClick)
            riskCore.on('click', handleClick)
            map!.add(priorityRing)
            map!.add(riskCore)
            overlays.push(priorityRing, riskCore)
          } else {
            const marker = new CircleMarker({ ...pointStyle(point, selected, theme) })
            marker.on('click', handleClick)
            map!.add(marker)
            overlays.push(marker)
          }
        })
    }

    syncActiveBuildingHighlight(activeBuilding, input)
  }

  function setActiveBuilding(
    selection: SpatialAmapActiveBuilding | null,
    input: SpatialAmapSyncInput,
  ): void {
    activeBuilding = selection
    latestSyncInput = input
    syncActiveBuildingHighlight(selection, input)
  }

  function syncActiveBuildingHighlight(
    selection: SpatialAmapActiveBuilding | null,
    input: SpatialAmapSyncInput,
  ): void {
    if (!baseBuildingsLayer) return
    const signature = activeBuildingSignature(selection, input)
    if (signature === highlightSignature) return
    highlightSignature = signature

    if (!selection) {
      baseBuildingsLayer.setStyle({ hideWithoutStyle: false, areas: [] })
      return
    }

    const path = selection.source === 'SYSTEM' && selection.buildingId
      ? resolveBuildingPath(selection.buildingId, input) ?? coordinateFence(selection.longitude, selection.latitude)
      : coordinateFence(selection.longitude, selection.latitude)
    const colors = selectedBuildingBlockColors()
    baseBuildingsLayer.setStyle({
      hideWithoutStyle: false,
      areas: [{
        rejectTexture: true,
        visible: true,
        path,
        color1: colors.roof,
        color2: colors.wall,
      }],
    })
  }

  function activeBuildingSignature(
    selection: SpatialAmapActiveBuilding | null,
    input: SpatialAmapSyncInput,
  ): string {
    if (!selection) return 'NONE'
    const version = selection.buildingId
      ? input.buildings.find((item) => item.feature.id === selection.buildingId)?.feature.properties.version ?? ''
      : ''
    return [
      selection.source,
      selection.buildingId ?? '',
      selection.longitude.toFixed(6),
      selection.latitude.toFixed(6),
      version,
    ].join(':')
  }

  function focusBuilding(selection: SpatialAmapActiveBuilding): boolean {
    if (
      !showOfficialBuildings
      || !map
      || !map.setZoomAndCenter
      || !map.setPitch
      || !map.setRotation
    ) return false

    if (!overviewState) overviewState = capturePresentationState()
    map.setZoomAndCenter(
      Math.max(map.getZoom(), BUILDING_PRESENTATION_ZOOM),
      [selection.longitude, selection.latitude],
      false,
      MAP_CAMERA_MOVE_DURATION_MS,
    )
    map.setRotation(BUILDING_PRESENTATION_ROTATION, false, MAP_CAMERA_ROTATE_DURATION_MS)
    map.setPitch(BUILDING_PRESENTATION_PITCH, false, MAP_CAMERA_MOVE_DURATION_MS)
    handlers.onMapTransform?.()
    return true
  }

  function focusBuildingOutline(selection: SpatialAmapActiveBuilding): boolean {
    if (
      !showOfficialBuildings
      || !map
      || !map.setZoomAndCenter
      || !map.setPitch
      || !map.setRotation
    ) return false

    if (!overviewState) overviewState = capturePresentationState()
    map.setRotation(0, false, MAP_CAMERA_ROTATE_DURATION_MS)
    map.setPitch(0, false, MAP_CAMERA_ROTATE_DURATION_MS)
    map.setZoomAndCenter(
      Math.max(map.getZoom(), BUILDING_PRESENTATION_ZOOM),
      [selection.longitude, selection.latitude],
      false,
      MAP_CAMERA_MOVE_DURATION_MS,
    )
    handlers.onMapTransform?.()
    return true
  }

  function setOverviewPresentation(threeDimensional: boolean): boolean {
    if (!showOfficialBuildings || !map || !map.setPitch || !map.setRotation) return false
    overviewThreeDimensional = threeDimensional
    const pitch = threeDimensional ? OVERVIEW_3D_PITCH : 0
    const rotation = threeDimensional ? OVERVIEW_3D_ROTATION : 0
    if (overviewState) {
      overviewState = { ...overviewState, pitch, rotation }
      return true
    }
    map.setRotation(rotation, false, MAP_CAMERA_ROTATE_DURATION_MS)
    map.setPitch(pitch, false, MAP_CAMERA_MOVE_DURATION_MS)
    handlers.onMapTransform?.()
    return true
  }

  function restoreOverview(): boolean {
    if (
      !map
      || !overviewState
      || !map.setZoomAndCenter
      || !map.setPitch
      || !map.setRotation
    ) return false

    const target = {
      ...overviewState,
      pitch: overviewThreeDimensional ? OVERVIEW_3D_PITCH : 0,
      rotation: overviewThreeDimensional ? OVERVIEW_3D_ROTATION : 0,
    }
    overviewState = null
    map.setZoomAndCenter(target.zoom, target.center, false, MAP_CAMERA_MOVE_DURATION_MS)
    map.setRotation(target.rotation, false, MAP_CAMERA_ROTATE_DURATION_MS)
    map.setPitch(target.pitch, false, MAP_CAMERA_MOVE_DURATION_MS)
    handlers.onMapTransform?.()
    return true
  }

  function capturePresentationState(): PresentationState {
    const center = readMapCenter(map)
    return {
      center: center ?? resolveBoundsCenter(map?.getBounds()) ?? [0, 0],
      zoom: map?.getZoom() ?? 13,
      pitch: map?.getPitch?.() ?? 0,
      rotation: map?.getRotation?.() ?? 0,
    }
  }

  function setBuildingPresentationView(
    buildingId: string,
    input: SpatialAmapSyncInput,
    threeDimensional: boolean,
  ): boolean {
    const point = input.buildingPoints?.find((item) => item.id === buildingId && validPoint(item))
    const center = point
      ? [point.longitude, point.latitude] as Coordinate
      : resolveBuildingCenter(buildingId, input)
    if (!center) return false
    const selection: SpatialAmapActiveBuilding = {
      source: 'SYSTEM',
      buildingId,
      longitude: center[0],
      latitude: center[1],
      riskLevel: point?.riskLevel,
    }
    setOverviewPresentation(threeDimensional)
    setActiveBuilding(selection, input)
    return threeDimensional ? focusBuilding(selection) : focusBuildingOutline(selection)
  }

  function resolveBuildingPath(buildingId: string, input: SpatialAmapSyncInput): Ring | null {
    const projection = input.buildings.find((item) => item.feature.id === buildingId)
    const polygonPath = projection
      ? geometryToAmapPolygons(projection.feature.geometry)[0]?.[0]
      : undefined
    if (polygonPath && polygonPath.length >= 3) return polygonPath
    const point = input.buildingPoints?.find((item) => item.id === buildingId && validPoint(item))
    return point ? pointFence(point) : null
  }

  function projectToContainer(longitude: number, latitude: number): SpatialAmapContainerPoint | null {
    if (!map?.lngLatToContainer || !Number.isFinite(longitude) || !Number.isFinite(latitude)) return null
    const pixel = map.lngLatToContainer([longitude, latitude])
    const x = readPixelX(pixel)
    const y = readPixelY(pixel)
    if (!Number.isFinite(x) || !Number.isFinite(y)) return null
    return { x, y }
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

  function clearBaseOverlays(): void {
    baseOverlays.forEach((overlay) => overlay.setMap(null))
    baseOverlays = []
  }

  function destroy(): void {
    mountSequence += 1
    mapGeneration += 1
    unbindNativeContainerClick()
    clearOverlays()
    clearBaseOverlays()
    baseBuildingsLayer = null
    latestSyncInput = EMPTY_SYNC_INPUT
    activeBuilding = null
    overviewState = null
    overviewThreeDimensional = showOfficialBuildings
    highlightSignature = ''
    map?.destroy()
    map = null
    namespace = null
    handlers = {}
  }

  return {
    mount,
    sync,
    setActiveBuilding,
    focusBuilding,
    focusBuildingOutline,
    setOverviewPresentation,
    restoreOverview,
    setBuildingPresentationView,
    projectToContainer,
    getViewport,
    destroy,
  }
}

function buildingContainingPoint(
  context: SpatialAmapClickContext,
  input: SpatialAmapSyncInput,
): SpatialBuildingProjection | null {
  if (!Number.isFinite(context.longitude) || !Number.isFinite(context.latitude)) return null
  const point: Coordinate = [Number(context.longitude), Number(context.latitude)]
  for (const projection of input.buildings) {
    const polygons = geometryToAmapPolygons(projection.feature.geometry)
    if (polygons.some((polygon) => polygon[0] && pointInRing(point, polygon[0]))) return projection
  }
  return null
}

function pointInRing(point: Coordinate, ring: Ring): boolean {
  if (ring.length < 3) return false
  let inside = false
  for (let index = 0, previous = ring.length - 1; index < ring.length; previous = index++) {
    const [xi, yi] = ring[index]!
    const [xj, yj] = ring[previous]!
    const intersects = ((yi > point[1]) !== (yj > point[1]))
      && (point[0] < ((xj - xi) * (point[1] - yi)) / ((yj - yi) || Number.EPSILON) + xi)
    if (intersects) inside = !inside
  }
  return inside
}

function nearestBuildingPointByPixel(
  context: SpatialAmapClickContext,
  input: SpatialAmapSyncInput,
  map: AmapMapLike | null,
): SpatialAmapPointFeature | null {
  if (
    !map?.lngLatToContainer
    || !Number.isFinite(context.pixelX)
    || !Number.isFinite(context.pixelY)
  ) return null

  const pixelX = Number(context.pixelX)
  const pixelY = Number(context.pixelY)
  let nearest: SpatialAmapPointFeature | null = null
  let nearestDistance = Number.POSITIVE_INFINITY

  input.buildingPoints?.filter(validPoint).forEach((point) => {
    const projected = map.lngLatToContainer?.([point.longitude, point.latitude])
    if (!projected) return
    const x = readPixelX(projected)
    const y = readPixelY(projected)
    if (!Number.isFinite(x) || !Number.isFinite(y)) return
    const distance = Math.hypot(pixelX - x, pixelY - y)
    if (distance < nearestDistance) {
      nearestDistance = distance
      nearest = point
    }
  })

  return nearestDistance <= BUILDING_SCREEN_CLICK_RADIUS_PX ? nearest : null
}

function nearestBuildingPoint(
  context: SpatialAmapClickContext,
  input: SpatialAmapSyncInput,
): SpatialAmapPointFeature | null {
  if (!Number.isFinite(context.longitude) || !Number.isFinite(context.latitude)) return null
  const longitude = Number(context.longitude)
  const latitude = Number(context.latitude)
  let nearest: SpatialAmapPointFeature | null = null
  let nearestDistance = Number.POSITIVE_INFINITY

  input.buildingPoints?.filter(validPoint).forEach((point) => {
    const distance = approximateDistanceMeters(longitude, latitude, point.longitude, point.latitude)
    if (distance < nearestDistance) {
      nearestDistance = distance
      nearest = point
    }
  })
  return nearestDistance <= BUILDING_CLICK_RADIUS_METERS ? nearest : null
}

function approximateDistanceMeters(
  longitudeA: number,
  latitudeA: number,
  longitudeB: number,
  latitudeB: number,
): number {
  const meanLatitude = ((latitudeA + latitudeB) / 2) * Math.PI / 180
  const dx = (longitudeA - longitudeB) * 111_320 * Math.cos(meanLatitude)
  const dy = (latitudeA - latitudeB) * 111_320
  return Math.hypot(dx, dy)
}

function resolveBuildingCenter(
  buildingId: string,
  input: SpatialAmapSyncInput,
): Coordinate | null {
  const point = input.buildingPoints?.find((item) => item.id === buildingId && validPoint(item))
  if (point) return [point.longitude, point.latitude]

  const projection = input.buildings.find((item) => item.feature.id === buildingId)
  if (!projection) return null
  const ring = geometryToAmapPolygons(projection.feature.geometry)[0]?.[0]
  if (!ring || ring.length < 3) return null
  const distinct = ring.length > 1
    && ring[0]?.[0] === ring[ring.length - 1]?.[0]
    && ring[0]?.[1] === ring[ring.length - 1]?.[1]
    ? ring.slice(0, -1)
    : ring
  if (distinct.length === 0) return null
  const longitude = distinct.reduce((sum, item) => sum + item[0], 0) / distinct.length
  const latitude = distinct.reduce((sum, item) => sum + item[1], 0) / distinct.length
  return Number.isFinite(longitude) && Number.isFinite(latitude) ? [longitude, latitude] : null
}

function readMapCenter(map: AmapMapLike | null): Coordinate | null {
  const center = map?.getCenter?.()
  if (!center) return null
  const longitude = readLongitude(center)
  const latitude = readLatitude(center)
  return Number.isFinite(longitude) && Number.isFinite(latitude) ? [longitude, latitude] : null
}

function resolveBoundsCenter(bounds: AmapBoundsLike | undefined): Coordinate | null {
  if (!bounds) return null
  const southWest = bounds.getSouthWest()
  const northEast = bounds.getNorthEast()
  const west = readLongitude(southWest)
  const south = readLatitude(southWest)
  const east = readLongitude(northEast)
  const north = readLatitude(northEast)
  if (![west, south, east, north].every(Number.isFinite)) return null
  return [(west + east) / 2, (south + north) / 2]
}

function validPoint(point: SpatialAmapPointFeature): boolean {
  return Number.isFinite(point.longitude) && Number.isFinite(point.latitude)
}

function readLongitude(value: AmapLngLatLike): number {
  if (typeof value.getLng === 'function') return value.getLng()
  return Number(value.lng)
}

function readLatitude(value: AmapLngLatLike): number {
  if (typeof value.getLat === 'function') return value.getLat()
  return Number(value.lat)
}

function readPixelX(value: AmapPixelLike): number {
  if (typeof value.getX === 'function') return value.getX()
  return Number(value.x)
}

function readPixelY(value: AmapPixelLike): number {
  if (typeof value.getY === 'function') return value.getY()
  return Number(value.y)
}