<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import BuildingDetailDrawer from '@/features/building-detail/BuildingDetailDrawer.vue'
import { loadBuildingDetail, type BuildingDetailModel } from '@/features/building-detail/building-detail-loader'
import {
  getMapRuntimeConfig,
  listCommunityPoints,
  type CommunityPoint,
  type MapRuntimeConfig,
} from '@/shared/api/endpoints/map'
import AppFilterBar from '@/shared/components/AppFilterBar.vue'
import AppFilterField from '@/shared/components/AppFilterField.vue'
import AppQueryField from '@/shared/components/AppQueryField.vue'
import AppPageHeader from '@/shared/components/layout/AppPageHeader.vue'
import {
  createSpatialAmapDriver,
  geometryToAmapPolygons,
  type SpatialAmapActiveBuilding,
  type SpatialAmapPointFeature,
  type SpatialAmapSyncInput,
} from '@/shared/map/spatial-amap'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'
import { useSpatialMapStore, type MapRiskLevel } from '@/stores/spatial-map'

const router = useRouter()
const appStore = useAppStore()
const authStore = useAuthStore()
const store = useSpatialMapStore()
const {
  communityFeatures,
  visibleBuildings,
  visibleRiskBuildings,
  riskRows,
  selectedCommunityId,
  selectedBuildingIds,
  viewport,
  loading,
  errorMessage,
} = storeToRefs(store)
const mapContainer = ref<HTMLElement | null>(null)
const runtimeConfig = ref<MapRuntimeConfig | null>(null)
const communityPoints = ref<CommunityPoint[]>([])
const runtimeLoading = ref(true)
const mapMounted = ref(false)
const searchInput = ref('')
const selectedRiskLevels = ref<MapRiskLevel[]>([])
const priorityFilter = ref('')
const freshnessFilter = ref('')
const resultFilter = ref('ALL')
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detailModel = ref<BuildingDetailModel | null>(null)
const driver = createSpatialAmapDriver({ theme: 'LIGHT', showOfficialBuildings: true })

const riskOptions: Array<{ value: MapRiskLevel; label: string }> = [
  { value: 'VERY_HIGH', label: '极高风险' },
  { value: 'HIGH', label: '高风险' },
  { value: 'MEDIUM', label: '中风险' },
  { value: 'LOW', label: '低风险' },
]

const mapUnavailable = computed(() => {
  const config = runtimeConfig.value
  return !runtimeLoading.value && (!config || !config.enabled || config.mode !== 'LIVE' || !config.jsApiKey)
})

const communityOptions = computed(() => {
  const result = new Map<string, string>()
  communityPoints.value.forEach((item) => {
    if (item.communityId) result.set(item.communityId, item.communityName || '未命名小区')
  })
  communityFeatures.value.forEach((item) => {
    if (!result.has(item.id)) result.set(item.id, item.properties.name || '未命名小区')
  })
  riskRows.value.forEach((item) => {
    if (item.communityId && !result.has(item.communityId)) result.set(item.communityId, item.communityName || '未命名小区')
  })
  return [...result.entries()].map(([id, name]) => ({ id, name })).sort((a, b) => a.name.localeCompare(b.name, 'zh-CN'))
})

const selectedCommunityName = computed(() => selectedCommunityId.value
  ? (communityOptions.value.find((item) => item.id === selectedCommunityId.value)?.name ?? '已选小区')
  : '全部小区')

const displayRiskBuildings = computed(() => visibleRiskBuildings.value.filter((item) => {
  if (priorityFilter.value && item.priorityLevel !== priorityFilter.value) return false
  if (freshnessFilter.value && item.freshness !== freshnessFilter.value) return false
  if (resultFilter.value === 'HAS_RESULT' && !item.riskLevel) return false
  if (resultFilter.value === 'NO_RESULT' && item.riskLevel) return false
  return true
}))

const selectedMapBuilding = computed(() => {
  const buildingId = selectedBuildingIds.value[0]
  if (!buildingId) return null
  return displayRiskBuildings.value.find((item) => item.buildingId === buildingId)
    ?? riskRows.value.find((item) => item.buildingId === buildingId)
    ?? null
})

const communityMapPoints = computed<SpatialAmapPointFeature[]>(() => communityPoints.value
  .filter((item) => isCoordinate(item.longitude) && isCoordinate(item.latitude))
  .map((item) => ({
    id: item.communityId,
    longitude: Number(item.longitude),
    latitude: Number(item.latitude),
    kind: 'COMMUNITY',
    label: item.communityName,
  })))

const buildingMapPoints = computed<SpatialAmapPointFeature[]>(() => displayRiskBuildings.value
  .filter((item) => isCoordinate(item.longitude) && isCoordinate(item.latitude))
  .map((item) => ({
    id: item.buildingId,
    longitude: Number(item.longitude),
    latitude: Number(item.latitude),
    kind: 'BUILDING',
    label: item.buildingName,
    riskLevel: item.riskLevel,
    priorityLevel: item.priorityLevel,
    freshness: item.freshness,
  })))

const verifiedBoundaryCount = computed(() => communityFeatures.value.length + visibleBuildings.value.length)
const canOpenRiskOverview = computed(() => authStore.hasAnyRole(['GOVERNMENT_MANAGER', 'ADMIN']))

watch(searchInput, (value) => store.setSearchKeyword(value))
watch(selectedRiskLevels, (value) => store.setRiskLevels(value), { deep: true })
watch(
  [communityFeatures, visibleBuildings, displayRiskBuildings, communityPoints, selectedCommunityId, selectedBuildingIds],
  syncMap,
  { deep: true },
)
watch(errorMessage, (message) => { if (message) appStore.notify(message, 'error') })
onMounted(initialiseMap)
onBeforeUnmount(() => driver.destroy())

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
      onViewportChange: (nextViewport) => { void loadViewport(nextViewport) },
      onCommunityClick: (communityId) => { void selectCommunity(communityId) },
      onBuildingClick: (buildingId) => { focusBuildingFromMap(buildingId) },
      onBuildingModelClick: (hit) => {
        if (hit.source !== 'SYSTEM' || !hit.buildingId) return
        focusBuildingFromMap(hit.buildingId)
      },
      onMapBlankClick: () => clearMapBuildingSelection(),
    })
    if (!mapMounted.value) return
    const initialViewport = driver.getViewport()
    if (initialViewport) await store.loadViewport(initialViewport)
    syncMap()
  } catch (error) {
    appStore.notify(error instanceof Error ? error.message : String(error), 'error')
  } finally {
    runtimeLoading.value = false
  }
}

async function loadViewport(nextViewport: { west: number; south: number; east: number; north: number; zoom: number }): Promise<void> {
  try { await store.loadViewport(nextViewport); syncMap() } catch { /* store 已保留稳定错误 */ }
}

async function selectCommunity(communityId: string | null): Promise<void> {
  store.selectCommunity(selectedCommunityId.value === communityId ? null : communityId)
  if (viewport.value) await loadViewport(viewport.value)
}

async function chooseCommunity(communityId: string): Promise<void> {
  const nextCommunityId = communityId || null
  if (selectedCommunityId.value === nextCommunityId) return
  store.selectCommunity(nextCommunityId)
  if (viewport.value) await loadViewport(viewport.value)
}

function currentMapInput(): SpatialAmapSyncInput {
  return {
    communities: communityFeatures.value,
    buildings: visibleBuildings.value,
    communityPoints: communityMapPoints.value,
    buildingPoints: buildingMapPoints.value,
    selectedCommunityId: selectedCommunityId.value,
    selectedBuildingIds: selectedBuildingIds.value,
  }
}

function resolveBuildingSelection(buildingId: string): SpatialAmapActiveBuilding | null {
  const point = buildingMapPoints.value.find((item) => item.id === buildingId)
  if (point) {
    return {
      source: 'SYSTEM',
      buildingId: point.id,
      longitude: point.longitude,
      latitude: point.latitude,
      riskLevel: point.riskLevel,
    }
  }

  const projection = visibleBuildings.value.find((item) => item.feature.id === buildingId)
  if (!projection) return null
  const polygon = geometryToAmapPolygons(projection.feature.geometry)[0]?.[0]
  if (!polygon?.length) return null
  const usable = polygon.length > 1
    && polygon[0]?.[0] === polygon[polygon.length - 1]?.[0]
    && polygon[0]?.[1] === polygon[polygon.length - 1]?.[1]
    ? polygon.slice(0, -1)
    : polygon
  if (!usable.length) return null
  const center = usable.reduce(
    (sum, coordinate) => [sum[0] + coordinate[0], sum[1] + coordinate[1]] as [number, number],
    [0, 0] as [number, number],
  )
  return {
    source: 'SYSTEM',
    buildingId,
    longitude: center[0] / usable.length,
    latitude: center[1] / usable.length,
    riskLevel: projection.risk?.riskLevel,
  }
}

function activeSelection(buildingId: string): SpatialAmapActiveBuilding | null {
  return resolveBuildingSelection(buildingId)
}

function syncMap(): void {
  if (!mapMounted.value) return
  const input = currentMapInput()
  driver.sync(input)
  const buildingId = selectedBuildingIds.value[0]
  driver.setActiveBuilding(buildingId ? activeSelection(buildingId) : null, input)
}

function selectBuilding(buildingId: string): void {
  store.selectSingleBuilding(buildingId)
  syncMap()
}

function focusBuildingFromMap(buildingId: string): void {
  selectBuilding(buildingId)
  const selection = resolveBuildingSelection(buildingId)
  if (!selection || !mapMounted.value) return
  const input = currentMapInput()
  driver.setActiveBuilding(selection, input)
}

function focusBuildingFromList(buildingId: string): void {
  selectBuilding(buildingId)
  const input = currentMapInput()
  const selection = resolveBuildingSelection(buildingId)
  if (selection && mapMounted.value) {
    driver.setActiveBuilding(selection, input)
    driver.focusBuilding(selection)
  }
}

function clearMapBuildingSelection(): void {
  store.clearBuildingSelection()
  if (mapMounted.value) driver.setActiveBuilding(null, currentMapInput())
}

function isCoordinate(value: unknown): boolean {
  return value !== null && value !== undefined && value !== '' && Number.isFinite(Number(value))
}

function toggleRiskLevel(level: MapRiskLevel): void {
  selectedRiskLevels.value = selectedRiskLevels.value.includes(level)
    ? selectedRiskLevels.value.filter((item) => item !== level)
    : [...selectedRiskLevels.value, level]
}

function resetFilters(): void {
  searchInput.value = ''
  selectedRiskLevels.value = []
  priorityFilter.value = ''
  freshnessFilter.value = ''
  resultFilter.value = 'ALL'
  store.selectCommunity(null)
  clearMapBuildingSelection()
}

async function openBuildingDetail(buildingId: string): Promise<void> {
  detailVisible.value = true
  detailLoading.value = true
  detailError.value = ''
  detailModel.value = null
  try {
    detailModel.value = await loadBuildingDetail(buildingId)
  } catch (error) {
    detailError.value = error instanceof Error ? error.message : String(error)
  } finally {
    detailLoading.value = false
  }
}

function openSelectedBuildingDetail(): void {
  if (!selectedMapBuilding.value) return
  void openBuildingDetail(selectedMapBuilding.value.buildingId)
}

function openFullDetail(buildingId: string): void {
  detailVisible.value = false
  void router.push({ name: 'console-building-detail', params: { buildingId } })
}

function openRiskOverview(): void {
  void router.push('/console/renewal-priorities')
}

function riskLabel(level?: string): string {
  return ({ VERY_HIGH: '极高', HIGH: '高', MEDIUM: '中', LOW: '低' } as Record<string, string>)[level ?? ''] ?? '暂无'
}

function riskTagType(level?: string): 'danger' | 'warning' | 'success' | 'info' {
  if (level === 'VERY_HIGH') return 'danger'
  if (level === 'HIGH' || level === 'MEDIUM') return 'warning'
  if (level === 'LOW') return 'success'
  return 'info'
}

function freshnessLabel(value?: string): string {
  if (value === 'CURRENT') return '当前有效'
  if (value === 'STALE') return '结果过期'
  if (value === 'NO_RESULT') return '暂无评分'
  return value || '未知'
}
</script>

<template>
  <section class="spatial-page">
    <AppPageHeader
      eyebrow="空间治理"
      title="城市地图"
      description="按小区、风险、优先级和数据状态筛选楼栋；点击楼栋先在地图聚焦并查看简略信息，再按需进入完整治理详情。"
    >
      <template #actions>
        <el-button v-if="canOpenRiskOverview" type="primary" plain @click="openRiskOverview">查看风险总览</el-button>
      </template>
    </AppPageHeader>

    <section class="map-status-strip" aria-label="地图运行状态">
      <el-tag effect="plain" round>{{ selectedCommunityName }}</el-tag>
      <el-tag effect="plain" type="success" round>已定位小区 {{ communityMapPoints.length }}</el-tag>
      <el-tag effect="plain" type="info" round>当前楼栋 {{ displayRiskBuildings.length }}</el-tag>
      <el-tag effect="plain" type="info" round>确认边界 {{ verifiedBoundaryCount }}</el-tag>
    </section>

    <AppFilterBar :show-reset="true" @query="syncMap" @reset="resetFilters">
      <AppFilterField kind="community">
        <el-select class="community-filter" :model-value="selectedCommunityId ?? ''" filterable placeholder="全部小区" @change="chooseCommunity">
          <el-option label="全部小区" value="" />
          <el-option v-for="community in communityOptions" :key="community.id" :label="community.name" :value="community.id" />
        </el-select>
      </AppFilterField>
      <AppFilterField kind="keyword" width="250px">
        <AppQueryField v-model="searchInput" placeholder="搜索楼栋名称、编号或小区" width="100%" @query="syncMap" />
      </AppFilterField>
      <AppFilterField kind="priority">
        <el-select v-model="priorityFilter" clearable placeholder="全部优先级">
          <el-option v-for="value in ['P1', 'P2', 'P3', 'P4']" :key="value" :label="value" :value="value" />
        </el-select>
      </AppFilterField>
      <AppFilterField kind="status">
        <el-select v-model="freshnessFilter" clearable placeholder="全部数据状态">
          <el-option label="当前有效" value="CURRENT" />
          <el-option label="结果过期" value="STALE" />
          <el-option label="暂无结果" value="NO_RESULT" />
        </el-select>
      </AppFilterField>
      <AppFilterField kind="status">
        <el-select v-model="resultFilter" placeholder="评分结果">
          <el-option label="全部" value="ALL" />
          <el-option label="已有评分" value="HAS_RESULT" />
          <el-option label="暂无评分" value="NO_RESULT" />
        </el-select>
      </AppFilterField>
      <div class="risk-filter-pills" aria-label="风险等级筛选">
        <button v-for="option in riskOptions" :key="option.value" type="button" class="filter-pill" :class="{ active: selectedRiskLevels.includes(option.value) }" @click="toggleRiskLevel(option.value)">{{ option.label }}</button>
      </div>
    </AppFilterBar>

    <div class="spatial-workspace">
      <aside class="spatial-list-panel">
        <div class="panel-heading"><strong>楼栋清单</strong><span>{{ displayRiskBuildings.length }} 栋</span></div>
        <el-scrollbar class="building-scroll">
          <button
            v-for="item in displayRiskBuildings"
            :key="item.buildingId"
            type="button"
            class="compact-building-row"
            :class="{ selected: selectedBuildingIds.includes(item.buildingId) }"
            @click="focusBuildingFromList(item.buildingId)"
          >
            <div class="row-between">
              <strong>{{ item.buildingName }}</strong>
              <el-tag size="small" :type="riskTagType(item.riskLevel)" round>{{ riskLabel(item.riskLevel) }}</el-tag>
            </div>
            <small>{{ item.buildingCode }} · {{ item.communityName || '所属小区' }}</small>
            <div class="compact-meta"><span>{{ item.priorityLevel ?? '—' }}</span><span>{{ freshnessLabel(item.freshness) }}</span></div>
          </button>
          <el-empty v-if="!loading && displayRiskBuildings.length === 0" description="当前筛选暂无楼栋数据" />
        </el-scrollbar>
      </aside>

      <main class="map-panel">
        <div ref="mapContainer" class="map-surface" aria-label="城市房屋安全空间地图" />
        <div v-if="runtimeLoading" class="map-state">正在加载地图服务</div>
        <div v-else-if="mapUnavailable" class="map-state"><strong>地图服务当前不可用</strong><span>请稍后重试或联系系统管理员检查地图服务配置。</span></div>
        <div v-else-if="loading" class="map-loading">正在加载当前视野数据…</div>
        <div v-else-if="communityMapPoints.length === 0 && verifiedBoundaryCount === 0" class="map-data-note">当前没有可定位的空间数据，请先补充位置或空间边界。</div>

        <section v-if="selectedMapBuilding" class="map-building-summary" aria-label="已选楼栋简略信息">
          <div class="map-building-summary__head">
            <div>
              <small>{{ selectedMapBuilding.communityName || '所属小区' }}</small>
              <strong>{{ selectedMapBuilding.buildingName }}</strong>
              <span>{{ selectedMapBuilding.buildingCode || '未设置楼栋编号' }}</span>
            </div>
            <el-tag :type="riskTagType(selectedMapBuilding.riskLevel)" round>{{ riskLabel(selectedMapBuilding.riskLevel) }}风险</el-tag>
          </div>
          <div class="map-building-summary__metrics">
            <span><b>{{ selectedMapBuilding.riskScore ?? '—' }}</b><small>风险分</small></span>
            <span><b>{{ selectedMapBuilding.priorityLevel ?? '—' }}</b><small>优先级</small></span>
            <span><b>{{ selectedMapBuilding.priorityScore ?? '—' }}</b><small>优先分</small></span>
            <span><b>{{ freshnessLabel(selectedMapBuilding.freshness) }}</b><small>数据状态</small></span>
          </div>
          <div class="map-building-summary__actions">
            <el-button size="small" @click="clearMapBuildingSelection">取消选择</el-button>
            <el-button size="small" type="primary" @click="openSelectedBuildingDetail">查看治理详情</el-button>
          </div>
        </section>

        <div class="map-legend">
          <span><b class="legend-core" />内圆 · 风险</span>
          <span><b class="legend-ring" />外圈 · 优先级</span>
          <span>◇ 小区位置</span><span>▢ 已确认边界</span>
        </div>
      </main>
    </div>

    <BuildingDetailDrawer
      v-model="detailVisible"
      :model="detailModel"
      :loading="detailLoading"
      :error="detailError"
      @open-full="openFullDetail"
    />
  </section>
</template>

<style scoped lang="scss">
.spatial-page { display: grid; gap: var(--usp-space-4); min-height: calc(100vh - 120px); }
.map-status-strip,
.row-between,
.panel-heading,
.tag-row { display: flex; align-items: center; justify-content: space-between; gap: var(--usp-space-3); }
.map-status-strip { justify-content: flex-end; flex-wrap: wrap; padding: 0 2px; }
.tag-row { flex-wrap: wrap; justify-content: flex-start; }
.community-filter { width: 100%; }
.risk-filter-pills { display: flex; flex-wrap: wrap; gap: 6px; }
.filter-pill { min-height: 32px; padding: 0 10px; border: 1px solid var(--usp-color-border); border-radius: 999px; background: var(--usp-color-surface); color: var(--usp-color-text-secondary); cursor: pointer; }
.filter-pill.active { border-color: var(--usp-color-primary); background: var(--usp-color-primary-soft); color: var(--usp-color-primary-strong); }
.spatial-workspace { display: grid; grid-template-columns: minmax(230px, 300px) minmax(0, 1fr); min-height: 650px; gap: 12px; }
.spatial-list-panel,
.map-panel { min-width: 0; border: 1px solid var(--usp-color-border); border-radius: var(--usp-radius-xl); background: var(--usp-color-surface); box-shadow: var(--usp-shadow-sm); overflow: hidden; }
.spatial-list-panel { display: grid; grid-template-rows: auto minmax(0, 1fr); }
.panel-heading { padding: 12px 14px; border-bottom: 1px solid var(--usp-color-border); }
.panel-heading span { color: var(--usp-color-text-secondary); font-size: 12px; }
.building-scroll { height: 610px; }
.compact-building-row { display: grid; width: calc(100% - 16px); gap: 4px; margin: 8px; padding: 9px 10px; border: 1px solid var(--usp-color-border); border-radius: var(--usp-radius-lg); background: var(--usp-color-surface); color: inherit; cursor: pointer; text-align: left; transition: .16s ease; }
.compact-building-row:hover { border-color: var(--usp-color-primary); transform: translateY(-1px); }
.compact-building-row.selected { border-color: var(--usp-color-primary); background: var(--usp-color-primary-soft); }
.compact-building-row small { overflow: hidden; color: var(--usp-color-text-secondary); text-overflow: ellipsis; white-space: nowrap; }
.compact-meta { display: flex; gap: 8px; color: var(--usp-color-text-secondary); font-size: 11px; }
.map-panel { position: relative; min-height: 650px; }
.map-surface { position: absolute; inset: 0; }
.map-state,
.map-loading,
.map-data-note { position: absolute; z-index: 5; left: 50%; top: 50%; display: grid; transform: translate(-50%, -50%); gap: 5px; padding: 14px 18px; border-radius: var(--usp-radius-xl); background: rgba(255,255,255,.94); box-shadow: var(--usp-shadow-sm); text-align: center; }
.map-building-summary { position: absolute; z-index: 7; top: 14px; right: 14px; display: grid; width: min(360px, calc(100% - 28px)); gap: 11px; padding: 13px 14px; border: 1px solid rgba(255,255,255,.72); border-radius: var(--usp-radius-xl); background: rgba(255,255,255,.94); box-shadow: 0 16px 38px rgba(15,23,42,.18); backdrop-filter: blur(10px); }
.map-building-summary__head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.map-building-summary__head > div { display: grid; min-width: 0; gap: 2px; }
.map-building-summary__head small,.map-building-summary__head span { color: var(--usp-color-text-secondary); font-size: 10px; }
.map-building-summary__head strong { overflow: hidden; font-size: 16px; text-overflow: ellipsis; white-space: nowrap; }
.map-building-summary__metrics { display: grid; grid-template-columns: repeat(4,minmax(0,1fr)); gap: 6px; }
.map-building-summary__metrics > span { display: grid; min-width: 0; gap: 2px; padding: 8px; border-radius: var(--usp-radius-lg); background: var(--usp-color-surface-muted); }
.map-building-summary__metrics b { overflow: hidden; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.map-building-summary__metrics small { color: var(--usp-color-text-secondary); font-size: 9px; }
.map-building-summary__actions { display: flex; justify-content: flex-end; gap: 8px; }
.map-legend { position: absolute; z-index: 6; right: 14px; bottom: 14px; display: flex; flex-wrap: wrap; gap: 8px 12px; padding: 9px 12px; border-radius: var(--usp-radius-xl); background: rgba(255,255,255,.92); box-shadow: var(--usp-shadow-sm); font-size: 11px; }
.legend-core,.legend-ring { display: inline-block; width: 9px; height: 9px; margin-right: 4px; border-radius: 50%; background: #d92d20; }
.legend-ring { border: 2px solid #7f56d9; background: transparent; }
.spatial-page :deep(.el-input__wrapper),
.spatial-page :deep(.el-select__wrapper),
.spatial-page :deep(.el-button) { border-radius: var(--usp-radius-lg); }
@media (max-width: 960px) {
  .map-status-strip { justify-content: flex-start; }
  .spatial-workspace { grid-template-columns: 1fr; }
  .building-scroll { height: 320px; }
  .map-panel { min-height: 560px; }
}
@media (max-width: 620px) {
  .map-building-summary__metrics { grid-template-columns: 1fr 1fr; }
}
</style>
