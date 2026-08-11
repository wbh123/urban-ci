<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import {
  getMapRuntimeConfig,
  listCommunityPoints,
  type CommunityPoint,
  type MapRuntimeConfig,
} from '@/shared/api/endpoints/map'
import type { SpatialBboxQuery } from '@/shared/api/endpoints/spatial'
import {
  createSpatialAmapDriver,
  geometryToAmapPolygons,
  type SpatialAmapActiveBuilding,
  type SpatialAmapBuildingModelHit,
  type SpatialAmapBuildingSource,
  type SpatialAmapClickContext,
  type SpatialAmapPointFeature,
  type SpatialAmapSyncInput,
} from '@/shared/map/spatial-amap'
import { useSpatialMapStore } from '@/stores/spatial-map'

const emit = defineEmits<{
  openMap: []
}>()

type BuildingFocusMode = 'OUTLINE' | '3D'

interface PopupState {
  kind: 'COMMUNITY' | 'BUILDING'
  id: string | null
  source?: SpatialAmapBuildingSource
  x: number
  y: number
  longitude?: number
  latitude?: number
}

interface BuildingInteractionSnapshot {
  at: number
  hit: SpatialAmapBuildingModelHit
}

interface SelectionHaloState {
  x: number
  y: number
}

const WUHAN_CENTER_DISTRICT_ADCODES = [
  '420102',
  '420103',
  '420104',
  '420105',
  '420106',
  '420107',
  '420111',
]
const AMAP_ONLY_SAME_BUILDING_DISTANCE_METERS = 45
const BUILDING_INTERACTION_DUPLICATE_WINDOW_MS = 320
const BUILDING_INTERACTION_DUPLICATE_PIXEL_RADIUS_PX = 14
const BUILDING_INTERACTION_DUPLICATE_GEO_RADIUS_METERS = 8
const BUILDING_INTERACTION_MIN_ZOOM = 17
const VIEWPORT_REFRESH_DEBOUNCE_MS = 140
const CAMERA_SETTLE_SYNC_MS = 640

const store = useSpatialMapStore()
const {
  communityFeatures,
  visibleBuildings,
  riskRows,
  selectedCommunityId,
  selectedBuildingIds,
  loading,
} = storeToRefs(store)

const mapRoot = ref<HTMLElement | null>(null)
const mapContainer = ref<HTMLElement | null>(null)
const runtimeConfig = ref<MapRuntimeConfig | null>(null)
const communityPoints = ref<CommunityPoint[]>([])
const runtimeLoading = ref(true)
const mapMounted = ref(false)
const popup = ref<PopupState | null>(null)
const activeBuilding = ref<SpatialAmapActiveBuilding | null>(null)
const buildingFocusMode = ref<BuildingFocusMode>('3D')
const selectionHalo = ref<SelectionHaloState | null>(null)
const selectionOutlinePath = ref<string | null>(null)
let lastBuildingInteraction: BuildingInteractionSnapshot | null = null
let viewportRefreshTimer: ReturnType<typeof setTimeout> | null = null
let pendingViewport: SpatialBboxQuery | null = null
let cameraSettleSyncTimer: ReturnType<typeof setTimeout> | null = null
let mapSyncFrame: number | null = null
let anchoredUiFrame: number | null = null

const driver = createSpatialAmapDriver({
  theme: 'DARK',
  showOfficialBuildings: true,
  districtAdcodes: WUHAN_CENTER_DISTRICT_ADCODES,
})

const mapUnavailable = computed(() => {
  const config = runtimeConfig.value
  return !runtimeLoading.value && (!config || !config.enabled || config.mode !== 'LIVE' || !config.jsApiKey)
})

const popupCommunityFeature = computed(() => {
  if (popup.value?.kind !== 'COMMUNITY' || !popup.value.id) return null
  return communityFeatures.value.find((item) => item.id === popup.value?.id) ?? null
})

const popupCommunityPoint = computed(() => {
  if (popup.value?.kind !== 'COMMUNITY' || !popup.value.id) return null
  return communityPoints.value.find((item) => item.communityId === popup.value?.id) ?? null
})

const popupCommunityRisks = computed(() => {
  if (popup.value?.kind !== 'COMMUNITY' || !popup.value.id) return []
  return riskRows.value.filter((item) => item.communityId === popup.value?.id)
})

const popupCommunityName = computed(() => (
  popupCommunityFeature.value?.properties.name
  ?? popupCommunityPoint.value?.communityName
  ?? popupCommunityRisks.value[0]?.communityName
  ?? ''
))

const popupCommunityCode = computed(() => (
  popupCommunityFeature.value?.properties.entityCode
  ?? popupCommunityPoint.value?.address
  ?? '已建立空间位置'
))

const popupBuildingProjection = computed(() => {
  if (popup.value?.kind !== 'BUILDING' || !popup.value.id) return null
  return visibleBuildings.value.find((item) => item.feature.id === popup.value?.id) ?? null
})

const popupBuildingRisk = computed(() => {
  if (popup.value?.kind !== 'BUILDING' || !popup.value.id) return null
  return popupBuildingProjection.value?.risk
    ?? riskRows.value.find((item) => item.buildingId === popup.value?.id)
    ?? null
})

const popupBuildingView = computed(() => {
  const state = popup.value
  if (state?.kind !== 'BUILDING') return null
  const unregistered = state.source === 'AMAP_ONLY'
  const projection = popupBuildingProjection.value
  const risk = popupBuildingRisk.value
  if (!unregistered && !projection && !risk) return null

  return {
    registered: !unregistered,
    name: unregistered
      ? '未建档建筑'
      : projection?.feature.properties.name ?? risk?.buildingName ?? '楼栋',
    code: unregistered ? '未纳入系统档案' : risk?.buildingCode ?? projection?.feature.properties.entityCode ?? '—',
    communityName: unregistered ? '高德三维楼块' : risk?.communityName ?? '当前小区',
    riskLevel: unregistered ? undefined : risk?.riskLevel,
    riskScore: unregistered ? undefined : risk?.riskScore,
    confidenceScore: unregistered ? undefined : risk?.confidenceScore,
    priorityLevel: unregistered ? undefined : risk?.priorityLevel,
    longitude: state.longitude,
    latitude: state.latitude,
    threeDimensional: buildingFocusMode.value === '3D' && popupMatchesActiveBuilding(state),
  }
})

const popupRiskTone = computed(() => {
  if (popup.value?.kind === 'BUILDING') return riskTone(popupBuildingView.value?.riskLevel)
  if (popup.value?.kind === 'COMMUNITY') {
    return riskTone(strongestRiskLevel(popupCommunityRisks.value.map((item) => item.riskLevel)))
  }
  return 'unknown'
})

const popupStyle = computed(() => {
  const state = popup.value
  if (!state) return undefined
  return { left: `${state.x}px`, top: `${state.y}px` }
})

const selectionHaloStyle = computed(() => {
  const state = selectionHalo.value
  if (!state) return undefined
  return { left: `${state.x}px`, top: `${state.y}px` }
})

const communityMapPoints = computed<SpatialAmapPointFeature[]>(() => communityPoints.value
  .filter((item) => isCoordinate(item.longitude) && isCoordinate(item.latitude))
  .map((item) => {
    const rows = riskRows.value.filter((row) => row.communityId === item.communityId)
    return {
      id: item.communityId,
      longitude: Number(item.longitude),
      latitude: Number(item.latitude),
      kind: 'COMMUNITY',
      label: item.communityName,
      riskLevel: strongestRiskLevel(rows.map((row) => row.riskLevel)),
      priorityLevel: strongestPriorityLevel(rows.map((row) => row.priorityLevel)),
      freshness: aggregateFreshness(rows.map((row) => row.freshness)),
    }
  }))

const buildingMapPoints = computed<SpatialAmapPointFeature[]>(() => {
  const result = new Map<string, SpatialAmapPointFeature>()
  const riskByBuildingId = new Map(riskRows.value.map((row) => [row.buildingId, row]))

  riskRows.value
    .filter((item) => isCoordinate(item.longitude) && isCoordinate(item.latitude))
    .forEach((item) => {
      result.set(item.buildingId, {
        id: item.buildingId,
        longitude: Number(item.longitude),
        latitude: Number(item.latitude),
        kind: 'BUILDING',
        label: item.buildingName,
        riskLevel: item.riskLevel,
        priorityLevel: item.priorityLevel,
        freshness: item.freshness,
      })
    })

  visibleBuildings.value.forEach(({ feature, risk }) => {
    if (result.has(feature.id)) return
    const center = resolveFeatureCenter(feature.geometry)
    if (!center) return
    const riskRow = risk ?? riskByBuildingId.get(feature.id)
    result.set(feature.id, {
      id: feature.id,
      longitude: center.longitude,
      latitude: center.latitude,
      kind: 'BUILDING',
      label: feature.properties.name ?? riskRow?.buildingName,
      riskLevel: riskRow?.riskLevel,
      priorityLevel: riskRow?.priorityLevel,
      freshness: riskRow?.freshness,
    })
  })

  return [...result.values()]
})

watch(
  [communityFeatures, visibleBuildings, riskRows, communityPoints],
  () => scheduleMapSync(),
  { deep: true },
)

onMounted(initialiseMap)
onBeforeUnmount(() => {
  if (viewportRefreshTimer) clearTimeout(viewportRefreshTimer)
  if (cameraSettleSyncTimer) clearTimeout(cameraSettleSyncTimer)
  if (mapSyncFrame !== null) cancelAnimationFrame(mapSyncFrame)
  if (anchoredUiFrame !== null) cancelAnimationFrame(anchoredUiFrame)
  driver.destroy()
})

async function initialiseMap(): Promise<void> {
  runtimeLoading.value = true
  try {
    const [config, points] = await Promise.all([
      getMapRuntimeConfig(),
      listCommunityPoints().catch(() => []),
    ])
    runtimeConfig.value = config
    communityPoints.value = points
    await nextTick()
    if (!mapContainer.value || !config.enabled || config.mode !== 'LIVE' || !config.jsApiKey) return
    mapMounted.value = await driver.mount(mapContainer.value, config, {
      onMapTransform: scheduleAnchoredUiRefresh,
      onMapBlankClick: resetMapInteraction,
      onViewportChange: scheduleViewportRefresh,
      onCommunityClick: (communityId, context) => {
        clearBuildingFocus(true)
        store.selectCommunity(selectedCommunityId.value === communityId ? null : communityId)
        popup.value = createPopup('COMMUNITY', communityId, context)
        scheduleAnchoredUiRefresh()
      },
      onBuildingClick: (buildingId, context) => {
        const point = buildingMapPoints.value.find((item) => item.id === buildingId)
        const longitude = point?.longitude ?? context?.longitude
        const latitude = point?.latitude ?? context?.latitude
        if (!isCoordinate(longitude) || !isCoordinate(latitude)) return
        handleBuildingSelection({
          source: 'SYSTEM',
          buildingId,
          longitude: Number(longitude),
          latitude: Number(latitude),
          pixelX: context?.pixelX,
          pixelY: context?.pixelY,
        })
      },
      onBuildingModelClick: handleBuildingSelection,
    })
    syncMap()
  } catch {
    runtimeConfig.value = null
    mapMounted.value = false
    driver.destroy()
  } finally {
    runtimeLoading.value = false
  }
}

function scheduleViewportRefresh(viewport: SpatialBboxQuery): void {
  pendingViewport = { ...viewport }
  if (viewportRefreshTimer) clearTimeout(viewportRefreshTimer)
  viewportRefreshTimer = setTimeout(() => {
    viewportRefreshTimer = null
    const target = pendingViewport
    pendingViewport = null
    if (!target || activeBuilding.value) return
    void store.loadViewport(target)
      .then(() => scheduleAnchoredUiRefresh())
      .catch(() => undefined)
  }, VIEWPORT_REFRESH_DEBOUNCE_MS)
}

function scheduleMapSync(): void {
  if (cameraSettleSyncTimer) {
    clearTimeout(cameraSettleSyncTimer)
    cameraSettleSyncTimer = null
  }
  if (mapSyncFrame !== null) return
  mapSyncFrame = requestAnimationFrame(() => {
    mapSyncFrame = null
    syncMap()
    scheduleAnchoredUiRefresh()
  })
}

function scheduleSettledMapSync(): void {
  if (cameraSettleSyncTimer) clearTimeout(cameraSettleSyncTimer)
  cameraSettleSyncTimer = setTimeout(() => {
    cameraSettleSyncTimer = null
    scheduleMapSync()
  }, CAMERA_SETTLE_SYNC_MS)
}

function scheduleAnchoredUiRefresh(): void {
  if (anchoredUiFrame !== null) return
  anchoredUiFrame = requestAnimationFrame(() => {
    anchoredUiFrame = null
    refreshAnchoredUi()
  })
}

function resetMapInteraction(): void {
  popup.value = null
  clearBuildingFocus(false)
  driver.restoreOverview()
  scheduleSettledMapSync()
  scheduleAnchoredUiRefresh()
}

function handleBuildingSelection(next: SpatialAmapBuildingModelHit): void {
  if (isDuplicatePhysicalBuildingInteraction(next)) return

  if (activeBuilding.value && sameBuildingSelection(activeBuilding.value, next)) {
    clearBuildingFocus(true)
    return
  }

  const selection: SpatialAmapActiveBuilding = { ...next }
  activeBuilding.value = selection
  if (next.source === 'SYSTEM' && next.buildingId) {
    store.selectSingleBuilding(next.buildingId)
  } else {
    store.clearBuildingSelection()
  }

  popup.value = createPopup(
    'BUILDING',
    next.buildingId ?? null,
    {
      longitude: next.longitude,
      latitude: next.latitude,
      pixelX: next.pixelX,
      pixelY: next.pixelY,
    },
    next.source,
  )

  driver.setActiveBuilding(selection, currentMapInput())
  if (buildingFocusMode.value === '3D') {
    driver.focusBuilding(selection)
  } else {
    driver.focusBuildingOutline(selection)
  }
  scheduleSettledMapSync()
  scheduleAnchoredUiRefresh()
}

function setBuildingFocusMode(mode: BuildingFocusMode): void {
  if (buildingFocusMode.value === mode) return
  buildingFocusMode.value = mode
  driver.setOverviewPresentation(mode === '3D')
  const current = activeBuilding.value
  if (!current) {
    scheduleAnchoredUiRefresh()
    return
  }

  if (mode === '3D') {
    driver.focusBuilding(current)
  } else {
    driver.focusBuildingOutline(current)
  }
  scheduleSettledMapSync()
  scheduleAnchoredUiRefresh()
}

function isDuplicatePhysicalBuildingInteraction(next: SpatialAmapBuildingModelHit): boolean {
  const now = typeof performance !== 'undefined' ? performance.now() : Date.now()
  const previous = lastBuildingInteraction
  lastBuildingInteraction = { at: now, hit: { ...next } }
  if (!previous || now - previous.at > BUILDING_INTERACTION_DUPLICATE_WINDOW_MS) return false

  if (
    previous.hit.source === 'SYSTEM'
    && next.source === 'SYSTEM'
    && previous.hit.buildingId
    && previous.hit.buildingId === next.buildingId
  ) return true

  if (
    isCoordinate(previous.hit.pixelX)
    && isCoordinate(previous.hit.pixelY)
    && isCoordinate(next.pixelX)
    && isCoordinate(next.pixelY)
  ) {
    const pixelDistance = Math.hypot(
      Number(previous.hit.pixelX) - Number(next.pixelX),
      Number(previous.hit.pixelY) - Number(next.pixelY),
    )
    if (pixelDistance <= BUILDING_INTERACTION_DUPLICATE_PIXEL_RADIUS_PX) return true
  }

  return approximateDistanceMeters(
    previous.hit.longitude,
    previous.hit.latitude,
    next.longitude,
    next.latitude,
  ) <= BUILDING_INTERACTION_DUPLICATE_GEO_RADIUS_METERS
}

function clearBuildingFocus(restoreCamera: boolean): void {
  const hadActiveBuilding = Boolean(activeBuilding.value)
  activeBuilding.value = null
  selectionHalo.value = null
  selectionOutlinePath.value = null
  store.clearBuildingSelection()
  driver.setActiveBuilding(null, currentMapInput())
  if (restoreCamera && hadActiveBuilding) {
    driver.restoreOverview()
    scheduleSettledMapSync()
  }
  if (popup.value?.kind === 'BUILDING') popup.value = null
}

function sameBuildingSelection(
  current: SpatialAmapActiveBuilding,
  next: SpatialAmapBuildingModelHit,
): boolean {
  if (current.source !== next.source) return false
  if (current.source === 'SYSTEM') return Boolean(current.buildingId && current.buildingId === next.buildingId)
  return approximateDistanceMeters(
    current.longitude,
    current.latitude,
    next.longitude,
    next.latitude,
  ) <= AMAP_ONLY_SAME_BUILDING_DISTANCE_METERS
}

function popupMatchesActiveBuilding(state: PopupState): boolean {
  const current = activeBuilding.value
  if (!current || state.kind !== 'BUILDING' || !isCoordinate(state.longitude) || !isCoordinate(state.latitude)) return false
  if (current.source === 'SYSTEM') return state.source === 'SYSTEM' && Boolean(state.id && state.id === current.buildingId)
  return state.source === 'AMAP_ONLY'
    && approximateDistanceMeters(
      current.longitude,
      current.latitude,
      Number(state.longitude),
      Number(state.latitude),
    ) <= AMAP_ONLY_SAME_BUILDING_DISTANCE_METERS
}

function createPopup(
  kind: PopupState['kind'],
  id: string | null,
  context?: SpatialAmapClickContext,
  source?: SpatialAmapBuildingSource,
): PopupState {
  const anchor = resolvePopupAnchor(kind, id, context)
  const initialX = context?.pixelX ?? (mapRoot.value?.clientWidth ?? 1200) / 2
  const initialY = context?.pixelY ?? (mapRoot.value?.clientHeight ?? 700) / 2
  const position = clampPopupPosition(initialX, initialY)
  return {
    kind,
    id,
    source,
    ...position,
    longitude: anchor?.longitude,
    latitude: anchor?.latitude,
  }
}

function resolvePopupAnchor(
  kind: PopupState['kind'],
  id: string | null,
  context?: SpatialAmapClickContext,
): { longitude: number; latitude: number } | null {
  const point = id
    ? kind === 'BUILDING'
      ? buildingMapPoints.value.find((item) => item.id === id)
      : communityMapPoints.value.find((item) => item.id === id)
    : null
  if (point && isCoordinate(point.longitude) && isCoordinate(point.latitude)) {
    return { longitude: point.longitude, latitude: point.latitude }
  }
  if (isCoordinate(context?.longitude) && isCoordinate(context?.latitude)) {
    return { longitude: Number(context?.longitude), latitude: Number(context?.latitude) }
  }
  return null
}

function refreshAnchoredUi(): void {
  refreshSelectionOutline()
  refreshSelectionHaloPosition()
  refreshPopupPosition()
}

function refreshSelectionOutline(): void {
  const current = activeBuilding.value
  if (buildingFocusMode.value !== 'OUTLINE' || !current || current.source !== 'SYSTEM' || !current.buildingId) {
    selectionOutlinePath.value = null
    return
  }

  const projection = visibleBuildings.value.find((item) => item.feature.id === current.buildingId)
  const ring = projection ? geometryToAmapPolygons(projection.feature.geometry)[0]?.[0] : undefined
  if (!ring || ring.length < 3) {
    selectionOutlinePath.value = null
    return
  }

  const projected = ring
    .map(([longitude, latitude]) => driver.projectToContainer(longitude, latitude))
    .filter((point): point is { x: number; y: number } => Boolean(point))
  if (projected.length < 3) {
    selectionOutlinePath.value = null
    return
  }

  selectionOutlinePath.value = `${projected
    .map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x.toFixed(1)} ${point.y.toFixed(1)}`)
    .join(' ')} Z`
}

function refreshSelectionHaloPosition(): void {
  const current = activeBuilding.value
  if (buildingFocusMode.value !== 'OUTLINE' || !current || selectionOutlinePath.value) {
    selectionHalo.value = null
    return
  }
  const projected = driver.projectToContainer(current.longitude, current.latitude)
  if (!projected) {
    selectionHalo.value = null
    return
  }
  const width = mapRoot.value?.clientWidth ?? 1200
  const height = mapRoot.value?.clientHeight ?? 700
  if (projected.x < -40 || projected.x > width + 40 || projected.y < -40 || projected.y > height + 40) {
    selectionHalo.value = null
    return
  }
  selectionHalo.value = { x: projected.x, y: projected.y }
}

function refreshPopupPosition(): void {
  const state = popup.value
  if (!state || !isCoordinate(state.longitude) || !isCoordinate(state.latitude)) return
  const projected = driver.projectToContainer(Number(state.longitude), Number(state.latitude))
  if (!projected) return

  const width = mapRoot.value?.clientWidth ?? 1200
  const height = mapRoot.value?.clientHeight ?? 700
  if (projected.x < -24 || projected.x > width + 24 || projected.y < -24 || projected.y > height + 24) {
    popup.value = null
    return
  }

  const position = clampPopupPosition(projected.x, projected.y)
  if (state.x === position.x && state.y === position.y) return
  popup.value = { ...state, ...position }
}

function clampPopupPosition(anchorX: number, anchorY: number): { x: number; y: number } {
  const width = mapRoot.value?.clientWidth ?? 1200
  const height = mapRoot.value?.clientHeight ?? 700
  const popupWidth = Math.min(300, Math.max(260, width - 32))
  const popupHeight = 248
  const sideSafe = width <= 820
    ? Math.min(200, Math.max(150, width * 0.22))
    : Math.min(285, Math.max(190, width * 0.2))
  const topSafe = width <= 820 ? 250 : 142
  const bottomSafe = 60
  const minX = Math.min(sideSafe + 12, Math.max(16, width - popupWidth - 16))
  const maxX = Math.max(minX, width - sideSafe - popupWidth - 12)
  const minY = Math.min(topSafe, Math.max(16, height - popupHeight - bottomSafe))
  const maxY = Math.max(minY, height - popupHeight - bottomSafe)
  return {
    x: Math.min(Math.max(minX, anchorX + 18), maxX),
    y: Math.min(Math.max(minY, anchorY - 36), maxY),
  }
}

function currentMapInput(): SpatialAmapSyncInput {
  const zoom = driver.getViewport()?.zoom ?? runtimeConfig.value?.defaultZoom ?? 13
  const buildingInteractionMode = zoom >= BUILDING_INTERACTION_MIN_ZOOM
  return {
    communities: buildingInteractionMode ? [] : communityFeatures.value,
    buildings: visibleBuildings.value,
    communityPoints: buildingInteractionMode ? [] : communityMapPoints.value,
    buildingPoints: buildingMapPoints.value,
    selectedCommunityId: selectedCommunityId.value,
    selectedBuildingIds: selectedBuildingIds.value,
  }
}

function resolveFeatureCenter(geometry: Parameters<typeof geometryToAmapPolygons>[0]): { longitude: number; latitude: number } | null {
  const ring = geometryToAmapPolygons(geometry)[0]?.[0]
  if (!ring || ring.length < 3) return null
  const distinct = ring.length > 1
    && ring[0]?.[0] === ring[ring.length - 1]?.[0]
    && ring[0]?.[1] === ring[ring.length - 1]?.[1]
    ? ring.slice(0, -1)
    : ring
  if (distinct.length === 0) return null
  const longitude = distinct.reduce((sum, point) => sum + point[0], 0) / distinct.length
  const latitude = distinct.reduce((sum, point) => sum + point[1], 0) / distinct.length
  return Number.isFinite(longitude) && Number.isFinite(latitude) ? { longitude, latitude } : null
}

function syncMap(): void {
  if (!mapMounted.value) return
  driver.sync(currentMapInput())
}

function isCoordinate(value: unknown): boolean {
  return value !== null && value !== undefined && value !== '' && Number.isFinite(Number(value))
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

function strongestRiskLevel(levels: Array<string | undefined>): string | undefined {
  const weight: Record<string, number> = { VERY_HIGH: 4, HIGH: 3, MEDIUM: 2, LOW: 1 }
  return levels
    .filter((level): level is string => Boolean(level))
    .sort((left, right) => (weight[right] ?? 0) - (weight[left] ?? 0))[0]
}

function strongestPriorityLevel(levels: Array<string | undefined>): string | undefined {
  const weight: Record<string, number> = { P1: 4, P2: 3, P3: 2, P4: 1 }
  return levels
    .filter((level): level is string => Boolean(level))
    .sort((left, right) => (weight[right] ?? 0) - (weight[left] ?? 0))[0]
}

function aggregateFreshness(freshness: Array<string | undefined>): SpatialAmapPointFeature['freshness'] {
  if (freshness.includes('CURRENT')) return 'CURRENT'
  if (freshness.includes('STALE')) return 'STALE'
  return 'NO_RESULT'
}

function riskLabel(level?: string): string {
  const labels: Record<string, string> = {
    VERY_HIGH: '极高风险',
    HIGH: '高风险',
    MEDIUM: '中风险',
    LOW: '低风险',
  }
  return level ? labels[level] ?? level : '待评估'
}

function riskTone(level?: string): string {
  if (level === 'VERY_HIGH') return 'very-high'
  if (level === 'HIGH') return 'high'
  if (level === 'MEDIUM') return 'medium'
  if (level === 'LOW') return 'low'
  return 'unknown'
}
</script>

<template>
  <section ref="mapRoot" class="wall-map-background">
    <div ref="mapContainer" class="wall-map-surface" aria-label="城市建筑安全空间态势地图" />

    <div class="building-focus-switch" role="group" aria-label="地图展示模式">
      <button type="button" :data-active="buildingFocusMode === 'OUTLINE'" @click.stop="setBuildingFocusMode('OUTLINE')">2D 俯视</button>
      <button type="button" :data-active="buildingFocusMode === '3D'" @click.stop="setBuildingFocusMode('3D')">3D 视角</button>
    </div>

    <svg v-if="buildingFocusMode === 'OUTLINE' && selectionOutlinePath" class="selected-building-outline-layer" aria-hidden="true"><path :d="selectionOutlinePath" /></svg>
    <div v-if="buildingFocusMode === 'OUTLINE' && selectionHalo" class="selected-building-halo" :style="selectionHaloStyle" aria-hidden="true"><span /></div>

    <div v-if="runtimeLoading" class="wall-map-state">正在加载地图服务</div>
    <div v-else-if="mapUnavailable" class="wall-map-state"><strong>地图服务当前不可用</strong><span>风险指标仍可正常查看。</span></div>
    <div v-else-if="loading" class="wall-map-loading">正在更新空间态势…</div>

    <article v-if="popup?.kind === 'COMMUNITY' && popupCommunityName" class="map-popup" :data-tone="popupRiskTone" :style="popupStyle">
      <button type="button" class="popup-close" aria-label="关闭小区信息" @click="popup = null">×</button>
      <span class="popup-kicker">小区空间档案</span>
      <strong class="popup-title">{{ popupCommunityName }}</strong>
      <span class="popup-code">{{ popupCommunityCode }}</span>
      <div class="popup-grid">
        <div><small>纳入统计楼栋</small><b>{{ popupCommunityRisks.length }}</b></div>
        <div><small>高风险楼栋</small><b data-tone="danger">{{ popupCommunityRisks.filter((item) => ['HIGH', 'VERY_HIGH'].includes(item.riskLevel ?? '')).length }}</b></div>
        <div><small>空间信息</small><b>{{ popupCommunityFeature ? '已确认边界' : '高德点位' }}</b></div>
        <div><small>当前风险</small><b>{{ riskLabel(strongestRiskLevel(popupCommunityRisks.map((item) => item.riskLevel))) }}</b></div>
      </div>
      <button type="button" class="popup-action" @click="emit('openMap')">进入完整空间档案 →</button>
    </article>

    <article v-else-if="popup?.kind === 'BUILDING' && popupBuildingView" class="map-popup" :data-tone="popupRiskTone" :style="popupStyle">
      <button type="button" class="popup-close" aria-label="关闭楼栋信息" @click="popup = null">×</button>
      <div class="popup-kicker-line"><span class="popup-kicker">{{ popupBuildingView.registered ? '楼栋风险信息' : '高德建筑模型' }}</span><span class="view-state" :data-active="popupBuildingView.threeDimensional">{{ popupBuildingView.threeDimensional ? '3D' : '轮廓' }}</span></div>
      <template v-if="popupBuildingView.registered">
        <div class="popup-title-line"><strong class="popup-title">{{ popupBuildingView.name }}</strong><span class="risk-pill" :data-tone="riskTone(popupBuildingView.riskLevel)">{{ riskLabel(popupBuildingView.riskLevel) }}</span></div>
        <span class="popup-code">{{ popupBuildingView.communityName }}</span>
        <div class="popup-grid">
          <div><small>楼栋编码</small><b>{{ popupBuildingView.code }}</b></div>
          <div><small>风险分值</small><b data-tone="danger">{{ popupBuildingView.riskScore == null ? '—' : popupBuildingView.riskScore.toFixed(1) }}</b></div>
          <div><small>置信度</small><b>{{ popupBuildingView.confidenceScore == null ? '—' : `${Math.round(popupBuildingView.confidenceScore * 100)}%` }}</b></div>
          <div><small>更新优先级</small><b>{{ popupBuildingView.priorityLevel || '—' }}</b></div>
        </div>
        <button type="button" class="popup-action" @click="emit('openMap')">查看楼栋完整信息 →</button>
      </template>
      <template v-else>
        <strong class="popup-title">该建筑尚未纳入系统档案</strong>
        <span class="popup-code">当前仅识别为高德三维建筑模型，不生成虚构业务数据。</span>
        <div class="popup-grid">
          <div><small>档案状态</small><b>未建档</b></div><div><small>风险评估</small><b>—</b></div>
          <div><small>空间来源</small><b>高德楼块</b></div><div><small>点击坐标</small><b>{{ popupBuildingView.longitude?.toFixed(5) }}, {{ popupBuildingView.latitude?.toFixed(5) }}</b></div>
        </div>
        <button type="button" class="popup-action" @click="emit('openMap')">进入空间建档 →</button>
      </template>
    </article>

    <div class="wall-map-legend" aria-label="地图交互图例"><span><i data-kind="selected" />当前选中楼栋</span><span><i data-kind="community" />小区点位/已确认边界</span><span><i data-kind="official" />未建档高德楼块</span></div>
  </section>
</template>

<style scoped lang="scss">
.wall-map-background { position:absolute; inset:0; overflow:hidden; background:#06191c; }
.wall-map-surface { position:absolute; inset:0; }
.building-focus-switch { position:absolute; z-index:29; top:72px; left:50%; display:inline-flex; gap:2px; padding:3px; border:1px solid rgba(112,225,205,.18); border-radius:999px; background:rgba(5,27,30,.72); transform:translateX(-50%); backdrop-filter:blur(14px); }
.building-focus-switch button { min-width:58px; padding:5px 9px; border:0; border-radius:999px; background:transparent; color:#87aaa5; font-size:9px; font-weight:800; cursor:pointer; transition:background .16s ease,color .16s ease; }
.building-focus-switch button[data-active='true'] { background:rgba(68,201,174,.18); color:#bcfff0; box-shadow:inset 0 0 0 1px rgba(116,235,213,.16); }
.selected-building-outline-layer { position:absolute; inset:0; z-index:27; width:100%; height:100%; overflow:visible; pointer-events:none; }
.selected-building-outline-layer path { fill:rgba(93,236,207,.14); stroke:#a9fff0; stroke-width:4; vector-effect:non-scaling-stroke; filter:drop-shadow(0 0 8px rgba(82,238,207,.92)); }
.selected-building-halo { position:absolute; z-index:28; width:56px; height:56px; pointer-events:none; transform:translate(-50%,-50%); }
.selected-building-halo::before,.selected-building-halo::after,.selected-building-halo span { position:absolute; inset:0; border:2px solid #9affea; border-radius:14px; content:''; transform:rotate(45deg); box-shadow:0 0 0 1px rgba(255,255,255,.2),0 0 22px rgba(90,239,207,.52); }
.selected-building-halo::after { inset:8px; border-width:1px; opacity:.72; }
.selected-building-halo span { inset:-5px; border-style:dashed; border-width:1px; opacity:.42; animation:building-halo-pulse 1.8s ease-in-out infinite; }
@keyframes building-halo-pulse { 0%,100% { opacity:.24; transform:rotate(45deg) scale(.94); } 50% { opacity:.68; transform:rotate(45deg) scale(1.08); } }
.wall-map-state { position:absolute; inset:0; z-index:30; display:grid; place-content:center; justify-items:center; gap:6px; padding:24px; background:rgba(5,25,28,.92); color:#93bbb7; text-align:center; }
.wall-map-state strong { color:#effcf9; }
.wall-map-loading { position:absolute; z-index:24; bottom:24px; left:24px; padding:7px 11px; border:1px solid rgba(114,223,205,.2); border-radius:999px; background:rgba(5,27,30,.72); color:#b7d9d3; font-size:11px; backdrop-filter:blur(12px); }
.wall-map-legend { position:absolute; right:22px; bottom:20px; z-index:24; display:flex; flex-wrap:wrap; gap:10px; padding:8px 11px; border:1px solid rgba(114,223,205,.18); border-radius:999px; background:rgba(5,27,30,.68); color:#d9eee9; font-size:10px; backdrop-filter:blur(14px); }
.wall-map-legend span { display:inline-flex; align-items:center; gap:5px; }.wall-map-legend i { width:7px; height:7px; border-radius:50%; background:#7aa7b7; }.wall-map-legend i[data-kind='selected'] { border:1px solid #b8fff3; background:#66e7d0; box-shadow:0 0 7px rgba(102,231,208,.7); }.wall-map-legend i[data-kind='community'] { border:1px solid #57d6c2; background:transparent; }.wall-map-legend i[data-kind='official'] { border-radius:2px; background:rgba(91,190,175,.78); }
.map-popup { --popup-border:rgba(123,232,213,.24); --popup-bg-start:rgba(7,33,36,.93); --popup-bg-end:rgba(7,27,31,.86); --popup-pointer-bg:rgba(7,31,34,.92); position:absolute; z-index:40; width:min(300px,calc(100% - 32px)); padding:16px; border:1px solid var(--popup-border); border-radius:18px; background:linear-gradient(145deg,var(--popup-bg-start),var(--popup-bg-end)); color:#f1fffc; box-shadow:0 18px 52px rgba(0,0,0,.34); backdrop-filter:blur(18px) saturate(135%); transition:left .06s linear,top .06s linear,background .16s ease,border-color .16s ease; }
.map-popup[data-tone='very-high'] { --popup-border:rgba(255,111,121,.5); --popup-bg-start:rgba(91,22,31,.95); --popup-bg-end:rgba(54,14,22,.91); --popup-pointer-bg:rgba(76,18,27,.94); }
.map-popup[data-tone='high'] { --popup-border:rgba(255,174,91,.48); --popup-bg-start:rgba(92,49,18,.95); --popup-bg-end:rgba(57,30,12,.91); --popup-pointer-bg:rgba(77,40,15,.94); }
.map-popup[data-tone='medium'] { --popup-border:rgba(241,207,92,.44); --popup-bg-start:rgba(78,67,22,.95); --popup-bg-end:rgba(48,41,14,.91); --popup-pointer-bg:rgba(66,56,18,.94); }
.map-popup[data-tone='low'] { --popup-border:rgba(93,223,157,.44); --popup-bg-start:rgba(19,75,54,.95); --popup-bg-end:rgba(12,48,36,.91); --popup-pointer-bg:rgba(16,63,46,.94); }
.map-popup::before { position:absolute; top:26px; left:-7px; width:12px; height:12px; border-bottom:1px solid var(--popup-border); border-left:1px solid var(--popup-border); background:var(--popup-pointer-bg); content:''; transform:rotate(45deg); }
.popup-close { display:none; position:absolute; top:8px; right:10px; width:28px; height:28px; border:0; border-radius:50%; background:rgba(255,255,255,.08); color:#fff; font-size:17px; }
.popup-kicker-line { display:flex; align-items:center; gap:8px; }.popup-kicker { color:#d2fff5; font-size:10px; font-weight:900; letter-spacing:.08em; }.view-state { padding:2px 6px; border:1px solid rgba(255,255,255,.16); border-radius:999px; color:#c7ded9; font-size:8px; font-weight:900; }.view-state[data-active='true'] { border-color:rgba(183,255,239,.4); background:rgba(255,255,255,.1); color:#effffb; }
.popup-title { display:block; margin-top:4px; font-size:17px; }.popup-title-line { display:flex; align-items:center; justify-content:space-between; gap:8px; margin-top:4px; }.popup-title-line .popup-title { min-width:0; margin:0; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }.popup-code { display:block; margin-top:3px; color:#d0e4df; font-size:10px; line-height:1.45; }.popup-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:8px; margin-top:13px; }.popup-grid > div { display:grid; gap:3px; padding:8px 9px; border-radius:11px; background:rgba(255,255,255,.055); }.popup-grid small { color:#c5d9d5; font-size:9px; }.popup-grid b { overflow:hidden; color:#fff; font-size:12px; text-overflow:ellipsis; white-space:nowrap; }.popup-grid b[data-tone='danger'] { color:#ffd4d7; }.popup-action { width:100%; margin-top:11px; padding:8px 10px; border:1px solid rgba(255,255,255,.16); border-radius:10px; background:rgba(255,255,255,.07); color:#f1fffc; font-size:10px; font-weight:800; }.risk-pill { flex:0 0 auto; padding:3px 7px; border-radius:999px; background:rgba(255,255,255,.1); color:#fff; font-size:9px; font-weight:900; }.risk-pill[data-tone='very-high'] { background:rgba(255,126,135,.2); color:#ffe0e3; }.risk-pill[data-tone='high'] { background:rgba(255,179,99,.2); color:#ffe4c3; }.risk-pill[data-tone='medium'] { background:rgba(246,213,104,.2); color:#fff0b5; }.risk-pill[data-tone='low'] { background:rgba(94,224,158,.2); color:#c9ffe3; }
@media (max-width:760px) { .building-focus-switch { top:118px; }.wall-map-legend { left:16px; right:16px; justify-content:center; } }
</style>