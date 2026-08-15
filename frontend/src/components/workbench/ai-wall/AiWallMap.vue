<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import type { AiDashboardBuilding, AiDashboardLayerMode } from '@/shared/api/endpoints/ai-dashboard'
import {
  getMapRuntimeConfig,
  listCommunityPoints,
  type CommunityPoint,
  type MapRuntimeConfig,
} from '@/shared/api/endpoints/map'
import type { SpatialBboxQuery } from '@/shared/api/endpoints/spatial'
import { formatAiDetectionLabel } from '@/shared/ai/ai-display'
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

const props = withDefaults(defineProps<{
  aiBuildings?: AiDashboardBuilding[]
  layerMode?: AiDashboardLayerMode
  focusBuildingId?: string | null
  canManageInspection?: boolean
}>(), {
  aiBuildings: () => [],
  layerMode: 'RISK',
  focusBuildingId: null,
  canManageInspection: false,
})

const emit = defineEmits<{
  openMap: []
  openBuilding: [buildingId: string]
  openInspection: [buildingId: string]
  buildingSelected: [buildingId: string]
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

const WUHAN_CENTER_DISTRICT_ADCODES = [
  '420102', '420103', '420104', '420105', '420106', '420107', '420111',
]
const VIEWPORT_REFRESH_DEBOUNCE_MS = 160
const CAMERA_SETTLE_MS = 560
const BUILDING_INTERACTION_MIN_ZOOM = 17

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
let viewportRefreshTimer: ReturnType<typeof setTimeout> | null = null
let cameraSettleTimer: ReturnType<typeof setTimeout> | null = null
let mapSyncFrame: number | null = null
let lastExternalFocusId: string | null = null

const driver = createSpatialAmapDriver({
  theme: 'DARK',
  showOfficialBuildings: true,
  districtAdcodes: WUHAN_CENTER_DISTRICT_ADCODES,
})

const aiByBuildingId = computed(() => new Map(props.aiBuildings.map((item) => [item.buildingId, item])))
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
const popupAiBuilding = computed(() => {
  if (popup.value?.kind !== 'BUILDING' || !popup.value.id) return null
  return aiByBuildingId.value.get(popup.value.id) ?? null
})
const popupBuildingView = computed(() => {
  const state = popup.value
  if (state?.kind !== 'BUILDING') return null
  const unregistered = state.source === 'AMAP_ONLY'
  const projection = popupBuildingProjection.value
  const risk = popupBuildingRisk.value
  const ai = popupAiBuilding.value
  if (!unregistered && !projection && !risk && !ai) return null
  return {
    registered: !unregistered,
    id: state.id,
    name: unregistered ? '未建档建筑' : projection?.feature.properties.name ?? risk?.buildingName ?? ai?.buildingName ?? '楼栋',
    code: unregistered ? '未纳入系统档案' : risk?.buildingCode ?? projection?.feature.properties.entityCode ?? ai?.buildingCode ?? '—',
    communityName: unregistered ? '高德三维楼块' : risk?.communityName ?? ai?.communityName ?? '当前小区',
    riskLevel: unregistered ? undefined : risk?.riskLevel ?? ai?.riskLevel,
    riskScore: unregistered ? undefined : risk?.riskScore ?? ai?.riskScore,
    priorityLevel: unregistered ? undefined : risk?.priorityLevel ?? ai?.priorityLevel,
    aiAttentionLevel: ai?.aiAttentionLevel,
    findings: ai?.findings ?? [],
    latestAiSummary: ai?.latestAiSummary,
    pendingReviewCount: ai?.pendingReviewCount ?? 0,
    longitude: state.longitude,
    latitude: state.latitude,
  }
})
const popupStyle = computed(() => popup.value ? { left: `${popup.value.x}px`, top: `${popup.value.y}px` } : undefined)
const layerLegend = computed(() => {
  if (props.layerMode === 'AI_DEFECT') return 'AI 病害候选 · 橙色表示存在视觉候选'
  if (props.layerMode === 'AI_ATTENTION') return 'AI 关注 · 仅用于治理排序，不是正式风险'
  if (props.layerMode === 'REVIEW') return '待复核 · 突出仍需人工确认的 AI 结果'
  if (props.layerMode === 'PRIORITY') return '治理优先级 · 外环显示正式更新优先级'
  return '正式风险 · 核心点显示规则评分风险等级'
})

const communityMapPoints = computed<SpatialAmapPointFeature[]>(() => communityPoints.value
  .filter((item) => isCoordinate(item.longitude) && isCoordinate(item.latitude))
  .map((item) => {
    const rows = riskRows.value.filter((row) => row.communityId === item.communityId)
    const base: SpatialAmapPointFeature = {
      id: item.communityId,
      longitude: Number(item.longitude),
      latitude: Number(item.latitude),
      kind: 'COMMUNITY',
      label: item.communityName,
      riskLevel: strongestRiskLevel(rows.map((row) => row.riskLevel)),
      priorityLevel: strongestPriorityLevel(rows.map((row) => row.priorityLevel)),
      freshness: aggregateFreshness(rows.map((row) => row.freshness)),
    }
    return decorateCommunityPoint(base, item.communityId)
  }))

const buildingMapPoints = computed<SpatialAmapPointFeature[]>(() => {
  const result = new Map<string, SpatialAmapPointFeature>()
  const riskByBuildingId = new Map(riskRows.value.map((row) => [row.buildingId, row]))

  riskRows.value
    .filter((item) => isCoordinate(item.longitude) && isCoordinate(item.latitude))
    .forEach((item) => {
      result.set(item.buildingId, decorateBuildingPoint({
        id: item.buildingId,
        longitude: Number(item.longitude),
        latitude: Number(item.latitude),
        kind: 'BUILDING',
        label: item.buildingName,
        riskLevel: item.riskLevel,
        priorityLevel: item.priorityLevel,
        freshness: item.freshness,
      }, item.buildingId))
    })

  visibleBuildings.value.forEach(({ feature, risk }) => {
    if (result.has(feature.id)) return
    const center = resolveFeatureCenter(feature.geometry)
    if (!center) return
    const riskRow = risk ?? riskByBuildingId.get(feature.id)
    result.set(feature.id, decorateBuildingPoint({
      id: feature.id,
      longitude: center.longitude,
      latitude: center.latitude,
      kind: 'BUILDING',
      label: feature.properties.name ?? riskRow?.buildingName,
      riskLevel: riskRow?.riskLevel,
      priorityLevel: riskRow?.priorityLevel,
      freshness: riskRow?.freshness,
    }, feature.id))
  })

  props.aiBuildings.forEach((item) => {
    if (result.has(item.buildingId) || !isCoordinate(item.longitude) || !isCoordinate(item.latitude)) return
    result.set(item.buildingId, decorateBuildingPoint({
      id: item.buildingId,
      longitude: Number(item.longitude),
      latitude: Number(item.latitude),
      kind: 'BUILDING',
      label: item.buildingName,
      riskLevel: item.riskLevel ?? undefined,
      priorityLevel: item.priorityLevel ?? undefined,
      freshness: normalizeFreshness(item.freshness),
    }, item.buildingId))
  })

  return [...result.values()]
})

watch(
  [communityFeatures, visibleBuildings, riskRows, communityPoints, () => props.aiBuildings, () => props.layerMode],
  () => scheduleMapSync(),
  { deep: true },
)
watch(
  [() => props.focusBuildingId, buildingMapPoints, () => mapMounted.value],
  () => {
    const id = props.focusBuildingId
    if (!id || !mapMounted.value) {
      if (!id) lastExternalFocusId = null
      return
    }
    if (id === lastExternalFocusId && activeBuilding.value?.buildingId === id) return
    void nextTick(() => focusBuildingById(id))
  },
  { deep: true },
)

onMounted(initialiseMap)
onBeforeUnmount(() => {
  if (viewportRefreshTimer) clearTimeout(viewportRefreshTimer)
  if (cameraSettleTimer) clearTimeout(cameraSettleTimer)
  if (mapSyncFrame !== null) cancelAnimationFrame(mapSyncFrame)
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
      onMapTransform: refreshPopupPosition,
      onMapBlankClick: resetMapInteraction,
      onViewportChange: scheduleViewportRefresh,
      onCommunityClick: (communityId, context) => {
        clearBuildingFocus(false)
        store.selectCommunity(communityId)
        popup.value = createPopup('COMMUNITY', communityId, context)
      },
      onBuildingClick: (buildingId, context) => {
        const point = buildingMapPoints.value.find((item) => item.id === buildingId)
        if (!point) return
        selectBuilding({
          source: 'SYSTEM',
          buildingId,
          longitude: point.longitude,
          latitude: point.latitude,
          pixelX: context?.pixelX,
          pixelY: context?.pixelY,
        })
      },
      onBuildingModelClick: selectBuilding,
    })
    syncMap()
    if (props.focusBuildingId) focusBuildingById(props.focusBuildingId)
  } catch {
    runtimeConfig.value = null
    mapMounted.value = false
    driver.destroy()
  } finally {
    runtimeLoading.value = false
  }
}

function scheduleViewportRefresh(viewport: SpatialBboxQuery): void {
  if (viewportRefreshTimer) clearTimeout(viewportRefreshTimer)
  viewportRefreshTimer = setTimeout(() => {
    viewportRefreshTimer = null
    if (activeBuilding.value) return
    void store.loadViewport(viewport).catch(() => undefined)
  }, VIEWPORT_REFRESH_DEBOUNCE_MS)
}

function scheduleMapSync(): void {
  if (mapSyncFrame !== null) return
  mapSyncFrame = requestAnimationFrame(() => {
    mapSyncFrame = null
    syncMap()
    refreshPopupPosition()
  })
}

function scheduleCameraSettle(): void {
  if (cameraSettleTimer) clearTimeout(cameraSettleTimer)
  cameraSettleTimer = setTimeout(() => {
    cameraSettleTimer = null
    syncMap()
    refreshPopupPosition()
  }, CAMERA_SETTLE_MS)
}

function resetMapInteraction(): void {
  popup.value = null
  store.selectCommunity(null)
  clearBuildingFocus(false)
  driver.restoreOverview()
  lastExternalFocusId = null
  scheduleCameraSettle()
}

function selectBuilding(hit: SpatialAmapBuildingModelHit): void {
  const selection: SpatialAmapActiveBuilding = { ...hit }
  activeBuilding.value = selection
  if (hit.source === 'SYSTEM' && hit.buildingId) {
    store.selectSingleBuilding(hit.buildingId)
    emit('buildingSelected', hit.buildingId)
  } else {
    store.clearBuildingSelection()
  }
  popup.value = createPopup('BUILDING', hit.buildingId ?? null, {
    longitude: hit.longitude,
    latitude: hit.latitude,
    pixelX: hit.pixelX,
    pixelY: hit.pixelY,
  }, hit.source)
  driver.setActiveBuilding(selection, currentMapInput())
  if (buildingFocusMode.value === '3D') driver.focusBuilding(selection)
  else driver.focusBuildingOutline(selection)
  scheduleCameraSettle()
}

function focusBuildingById(buildingId: string): void {
  const point = buildingMapPoints.value.find((item) => item.id === buildingId)
  if (!point) return
  lastExternalFocusId = buildingId
  selectBuilding({
    source: 'SYSTEM',
    buildingId,
    longitude: point.longitude,
    latitude: point.latitude,
  })
}

function clearBuildingFocus(restoreCamera: boolean): void {
  const hadActive = Boolean(activeBuilding.value)
  activeBuilding.value = null
  store.clearBuildingSelection()
  driver.setActiveBuilding(null, currentMapInput())
  if (restoreCamera && hadActive) driver.restoreOverview()
  if (popup.value?.kind === 'BUILDING') popup.value = null
}

function setBuildingFocusMode(mode: BuildingFocusMode): void {
  buildingFocusMode.value = mode
  driver.setOverviewPresentation(mode === '3D')
  const selection = activeBuilding.value
  if (!selection) return
  if (mode === '3D') driver.focusBuilding(selection)
  else driver.focusBuildingOutline(selection)
  scheduleCameraSettle()
}

function createPopup(
  kind: PopupState['kind'],
  id: string | null,
  context?: SpatialAmapClickContext,
  source?: SpatialAmapBuildingSource,
): PopupState {
  const point = id
    ? (kind === 'BUILDING' ? buildingMapPoints.value : communityMapPoints.value).find((item) => item.id === id)
    : undefined
  const longitude = point?.longitude ?? context?.longitude
  const latitude = point?.latitude ?? context?.latitude
  const position = clampPopupPosition(
    context?.pixelX ?? (mapRoot.value?.clientWidth ?? 1200) / 2,
    context?.pixelY ?? (mapRoot.value?.clientHeight ?? 700) / 2,
  )
  return { kind, id, source, ...position, longitude, latitude }
}

function refreshPopupPosition(): void {
  const state = popup.value
  if (!state || !isCoordinate(state.longitude) || !isCoordinate(state.latitude)) return
  const projected = driver.projectToContainer(Number(state.longitude), Number(state.latitude))
  if (!projected) return
  const width = mapRoot.value?.clientWidth ?? 1200
  const height = mapRoot.value?.clientHeight ?? 700
  if (projected.x < -40 || projected.x > width + 40 || projected.y < -40 || projected.y > height + 40) {
    popup.value = null
    return
  }
  const position = clampPopupPosition(projected.x, projected.y)
  popup.value = { ...state, ...position }
}

function clampPopupPosition(anchorX: number, anchorY: number): { x: number; y: number } {
  const width = mapRoot.value?.clientWidth ?? 1200
  const height = mapRoot.value?.clientHeight ?? 700
  const popupWidth = Math.min(330, Math.max(270, width - 32))
  const popupHeight = 330
  const minX = 16
  const maxX = Math.max(minX, width - popupWidth - 16)
  const minY = 92
  const maxY = Math.max(minY, height - popupHeight - 70)
  return {
    x: Math.min(Math.max(minX, anchorX + 18), maxX),
    y: Math.min(Math.max(minY, anchorY - 34), maxY),
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

function syncMap(): void {
  if (mapMounted.value) driver.sync(currentMapInput())
}

function decorateBuildingPoint(point: SpatialAmapPointFeature, buildingId: string): SpatialAmapPointFeature {
  const ai = aiByBuildingId.value.get(buildingId)
  if (props.layerMode === 'RISK') return point
  if (props.layerMode === 'PRIORITY') return { ...point, riskLevel: undefined }
  if (props.layerMode === 'AI_DEFECT') {
    return { ...point, riskLevel: ai?.findings.length ? 'HIGH' : undefined, priorityLevel: undefined, freshness: 'CURRENT' }
  }
  if (props.layerMode === 'REVIEW') {
    return { ...point, riskLevel: (ai?.pendingReviewCount ?? 0) > 0 ? 'MEDIUM' : undefined, priorityLevel: undefined, freshness: 'CURRENT' }
  }
  return {
    ...point,
    riskLevel: attentionRiskProxy(ai?.aiAttentionLevel),
    priorityLevel: undefined,
    freshness: 'CURRENT',
  }
}

function decorateCommunityPoint(point: SpatialAmapPointFeature, communityId: string): SpatialAmapPointFeature {
  if (props.layerMode === 'RISK' || props.layerMode === 'PRIORITY') {
    return props.layerMode === 'PRIORITY' ? { ...point, riskLevel: undefined } : point
  }
  const rows = props.aiBuildings.filter((item) => item.communityId === communityId)
  if (props.layerMode === 'AI_DEFECT') {
    return { ...point, riskLevel: rows.some((item) => item.findings.length > 0) ? 'HIGH' : undefined, priorityLevel: undefined, freshness: 'CURRENT' }
  }
  if (props.layerMode === 'REVIEW') {
    return { ...point, riskLevel: rows.some((item) => item.pendingReviewCount > 0) ? 'MEDIUM' : undefined, priorityLevel: undefined, freshness: 'CURRENT' }
  }
  const level = rows.map((item) => item.aiAttentionLevel).sort((a, b) => attentionWeight(b) - attentionWeight(a))[0]
  return { ...point, riskLevel: attentionRiskProxy(level), priorityLevel: undefined, freshness: 'CURRENT' }
}

function attentionRiskProxy(level?: string): string | undefined {
  if (level === 'HIGH') return 'VERY_HIGH'
  if (level === 'MEDIUM') return 'MEDIUM'
  if (level === 'LOW') return 'LOW'
  return undefined
}

function attentionWeight(level?: string): number {
  if (level === 'HIGH') return 3
  if (level === 'MEDIUM') return 2
  if (level === 'LOW') return 1
  return 0
}

function resolveFeatureCenter(geometry: Parameters<typeof geometryToAmapPolygons>[0]): { longitude: number; latitude: number } | null {
  const ring = geometryToAmapPolygons(geometry)[0]?.[0]
  if (!ring || ring.length < 3) return null
  const distinct = ring.length > 1
    && ring[0]?.[0] === ring[ring.length - 1]?.[0]
    && ring[0]?.[1] === ring[ring.length - 1]?.[1]
    ? ring.slice(0, -1)
    : ring
  if (!distinct.length) return null
  const longitude = distinct.reduce((sum, point) => sum + point[0], 0) / distinct.length
  const latitude = distinct.reduce((sum, point) => sum + point[1], 0) / distinct.length
  return Number.isFinite(longitude) && Number.isFinite(latitude) ? { longitude, latitude } : null
}

function normalizeFreshness(value?: string | null): SpatialAmapPointFeature['freshness'] {
  if (value === 'CURRENT' || value === 'STALE' || value === 'NO_RESULT') return value
  return 'NO_RESULT'
}

function isCoordinate(value: unknown): boolean {
  return value !== null && value !== undefined && value !== '' && Number.isFinite(Number(value))
}

function strongestRiskLevel(levels: Array<string | undefined>): string | undefined {
  const weight: Record<string, number> = { VERY_HIGH: 4, HIGH: 3, MEDIUM: 2, LOW: 1 }
  return levels.filter((level): level is string => Boolean(level)).sort((a, b) => (weight[b] ?? 0) - (weight[a] ?? 0))[0]
}

function strongestPriorityLevel(levels: Array<string | undefined>): string | undefined {
  const weight: Record<string, number> = { P1: 4, P2: 3, P3: 2, P4: 1 }
  return levels.filter((level): level is string => Boolean(level)).sort((a, b) => (weight[b] ?? 0) - (weight[a] ?? 0))[0]
}

function aggregateFreshness(values: Array<string | undefined>): SpatialAmapPointFeature['freshness'] {
  if (values.includes('CURRENT')) return 'CURRENT'
  if (values.includes('STALE')) return 'STALE'
  return 'NO_RESULT'
}

function riskLabel(level?: string | null): string {
  const labels: Record<string, string> = { VERY_HIGH: '极高风险', HIGH: '高风险', MEDIUM: '中风险', LOW: '低风险' }
  return level ? labels[level] ?? level : '待评估'
}

function attentionLabel(level?: string): string {
  if (level === 'HIGH') return '高关注'
  if (level === 'MEDIUM') return '中关注'
  if (level === 'LOW') return '一般关注'
  return '常规'
}
</script>

<template>
  <section ref="mapRoot" class="wall-map-background">
    <div ref="mapContainer" class="wall-map-surface" aria-label="城市建筑安全空间态势地图" />

    <div class="building-focus-switch" role="group" aria-label="地图视角">
      <button type="button" :data-active="buildingFocusMode === 'OUTLINE'" @click.stop="setBuildingFocusMode('OUTLINE')">2D 俯视</button>
      <button type="button" :data-active="buildingFocusMode === '3D'" @click.stop="setBuildingFocusMode('3D')">3D 视角</button>
    </div>

    <div v-if="runtimeLoading" class="wall-map-state">正在加载地图服务</div>
    <div v-else-if="mapUnavailable" class="wall-map-state"><strong>地图服务当前不可用</strong><span>AI 与风险指标仍可正常查看。</span></div>
    <div v-else-if="loading" class="wall-map-loading">正在更新空间态势…</div>

    <article v-if="popup?.kind === 'COMMUNITY' && popupCommunityName" class="map-popup" :style="popupStyle">
      <button type="button" class="popup-close" aria-label="关闭小区信息" @click="popup = null">×</button>
      <span class="popup-kicker">小区空间档案</span>
      <strong class="popup-title">{{ popupCommunityName }}</strong>
      <span class="popup-code">{{ popupCommunityCode }}</span>
      <div class="popup-grid">
        <div><small>纳管楼栋</small><b>{{ popupCommunityRisks.length }}</b></div>
        <div><small>高风险楼栋</small><b>{{ popupCommunityRisks.filter((item) => ['HIGH', 'VERY_HIGH'].includes(item.riskLevel ?? '')).length }}</b></div>
        <div><small>AI 关注对象</small><b>{{ props.aiBuildings.filter((item) => item.communityId === popup?.id && item.aiAttentionLevel !== 'NONE').length }}</b></div>
        <div><small>当前风险</small><b>{{ riskLabel(strongestRiskLevel(popupCommunityRisks.map((item) => item.riskLevel))) }}</b></div>
      </div>
      <button type="button" class="popup-action" @click="emit('openMap')">进入完整空间档案 →</button>
    </article>

    <article v-else-if="popup?.kind === 'BUILDING' && popupBuildingView" class="map-popup map-popup--building" :style="popupStyle">
      <button type="button" class="popup-close" aria-label="关闭楼栋信息" @click="popup = null">×</button>
      <template v-if="popupBuildingView.registered">
        <span class="popup-kicker">楼栋 AI 治理摘要</span>
        <strong class="popup-title">{{ popupBuildingView.communityName }} · {{ popupBuildingView.name }}</strong>
        <div class="popup-status-row">
          <span>正式风险 <b>{{ riskLabel(popupBuildingView.riskLevel) }}</b></span>
          <span>AI关注 <b>{{ attentionLabel(popupBuildingView.aiAttentionLevel) }}</b></span>
          <span>优先级 <b>{{ popupBuildingView.priorityLevel || '—' }}</b></span>
        </div>
        <div class="popup-ai-block">
          <small>✦ AI 最近发现</small>
          <div v-if="popupBuildingView.findings.length" class="popup-findings">
            <span v-for="item in popupBuildingView.findings.slice(0, 4)" :key="`${item.classCode}-${item.className}`">{{ formatAiDetectionLabel(item.className, item.maxConfidence) }} ×{{ item.count }}</span>
          </div>
          <p v-else>最近一次分析没有可展示的病害候选。</p>
        </div>
        <div class="popup-ai-block">
          <small>AI 综合判断</small>
          <p>{{ popupBuildingView.latestAiSummary || '当前未生成额外综合判断，可进入楼栋查看完整证据。' }}</p>
        </div>
        <div class="popup-actions">
          <button type="button" @click="popupBuildingView.id && emit('openBuilding', popupBuildingView.id)">查看 AI 研判</button>
          <button type="button" @click="popupBuildingView.id && emit('openBuilding', popupBuildingView.id)">进入楼栋</button>
          <button v-if="canManageInspection" type="button" @click="popupBuildingView.id && emit('openInspection', popupBuildingView.id)">查看巡检</button>
        </div>
      </template>
      <template v-else>
        <span class="popup-kicker">高德建筑模型</span>
        <strong class="popup-title">该建筑尚未纳入系统档案</strong>
        <p class="unregistered-copy">当前只显示高德建筑模型，不生成虚构 AI 或风险数据。</p>
        <button type="button" class="popup-action" @click="emit('openMap')">进入空间建档 →</button>
      </template>
    </article>

    <div class="wall-map-legend"><span>{{ layerLegend }}</span><span><i />当前选中楼栋</span></div>
  </section>
</template>

<style scoped lang="scss">
.wall-map-background{position:absolute;inset:0;overflow:hidden;background:#06191c}.wall-map-surface{position:absolute;inset:0}.building-focus-switch{position:absolute;z-index:29;top:84px;left:50%;display:inline-flex;gap:2px;padding:3px;border:1px solid rgba(112,225,205,.16);border-radius:999px;background:rgba(5,27,30,.76);transform:translateX(-50%);backdrop-filter:blur(14px)}.building-focus-switch button{min-width:58px;padding:5px 9px;border:0;border-radius:999px;background:transparent;color:#87aaa5;font-size:8px;font-weight:800;cursor:pointer}.building-focus-switch button[data-active='true']{background:rgba(68,201,174,.18);color:#bcfff0}.wall-map-state{position:absolute;inset:0;z-index:30;display:grid;place-content:center;justify-items:center;gap:6px;padding:24px;background:rgba(5,25,28,.92);color:#93bbb7;text-align:center}.wall-map-state strong{color:#effcf9}.wall-map-loading{position:absolute;z-index:24;bottom:22px;left:22px;padding:6px 9px;border:1px solid rgba(114,223,205,.18);border-radius:999px;background:rgba(5,27,30,.72);color:#b7d9d3;font-size:9px}.wall-map-legend{position:absolute;right:18px;bottom:18px;z-index:24;display:flex;flex-wrap:wrap;gap:9px;padding:7px 10px;border:1px solid rgba(114,223,205,.16);border-radius:999px;background:rgba(5,27,30,.72);color:#b9d8d2;font-size:8px;backdrop-filter:blur(12px)}.wall-map-legend span{display:inline-flex;align-items:center;gap:5px}.wall-map-legend i{width:7px;height:7px;border:1px solid #c9fff5;border-radius:50%;background:#62dfca;box-shadow:0 0 7px rgba(98,223,202,.55)}.map-popup{position:absolute;z-index:40;width:min(330px,calc(100% - 32px));padding:14px;border:1px solid rgba(123,232,213,.22);border-radius:16px;background:linear-gradient(145deg,rgba(7,33,36,.94),rgba(7,27,31,.9));color:#f1fffc;box-shadow:0 18px 52px rgba(0,0,0,.34);backdrop-filter:blur(18px)}.popup-close{position:absolute;top:8px;right:9px;width:25px;height:25px;border:0;border-radius:50%;background:rgba(255,255,255,.06);color:#d9efea;font-size:15px;cursor:pointer}.popup-kicker{display:block;color:#9de8d9;font-size:8px;font-weight:900;letter-spacing:.06em}.popup-title{display:block;margin-top:4px;padding-right:24px;font-size:14px}.popup-code{display:block;margin-top:3px;color:#92b7b1;font-size:8px}.popup-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:6px;margin-top:10px}.popup-grid>div{display:grid;gap:2px;padding:7px;border-radius:9px;background:rgba(255,255,255,.045)}.popup-grid small{color:#89aaa5;font-size:7px}.popup-grid b{font-size:10px}.popup-status-row{display:grid;grid-template-columns:repeat(3,1fr);gap:5px;margin-top:10px}.popup-status-row span{display:grid;gap:2px;padding:6px;border-radius:8px;background:rgba(255,255,255,.04);color:#83a7a1;font-size:7px}.popup-status-row b{color:#e9fbf7;font-size:9px}.popup-ai-block{display:grid;gap:4px;margin-top:8px;padding:8px;border-radius:9px;background:rgba(255,255,255,.035)}.popup-ai-block small{color:#91decf;font-size:7px;font-weight:800}.popup-ai-block p{margin:0;color:#a7c2bd;font-size:8px;line-height:1.5}.popup-findings{display:flex;flex-wrap:wrap;gap:4px}.popup-findings span{padding:3px 5px;border-radius:7px;background:rgba(240,189,91,.09);color:#f0d493;font-size:7px}.popup-actions{display:flex;flex-wrap:wrap;gap:5px;margin-top:9px}.popup-actions button,.popup-action{padding:6px 8px;border:1px solid rgba(255,255,255,.13);border-radius:8px;background:rgba(255,255,255,.05);color:#e5f8f4;font-size:7px;font-weight:800;cursor:pointer}.popup-action{width:100%;margin-top:10px}.unregistered-copy{margin:8px 0 0;color:#9ab9b4;font-size:8px;line-height:1.55}@media(max-width:760px){.building-focus-switch{top:118px}.wall-map-legend{left:12px;right:12px;justify-content:center}.map-popup{width:calc(100% - 24px)}}
</style>
