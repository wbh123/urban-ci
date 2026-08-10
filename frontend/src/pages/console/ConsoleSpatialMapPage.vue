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
import {
  createSpatialAmapDriver,
  type SpatialAmapPointFeature,
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
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detailModel = ref<BuildingDetailModel | null>(null)
const driver = createSpatialAmapDriver({ theme: 'LIGHT', showOfficialBuildings: true })

const riskOptions: Array<{ value: MapRiskLevel; label: string }> = [
  { value: 'VERY_HIGH', label: '极高风险' }, { value: 'HIGH', label: '高风险' },
  { value: 'MEDIUM', label: '中风险' }, { value: 'LOW', label: '低风险' },
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
    if (item.communityId && !result.has(item.communityId)) {
      result.set(item.communityId, item.communityName || '未命名小区')
    }
  })
  return [...result.entries()]
    .map(([id, name]) => ({ id, name }))
    .sort((left, right) => left.name.localeCompare(right.name, 'zh-CN'))
})

const selectedCommunityName = computed(() => selectedCommunityId.value
  ? (communityOptions.value.find((item) => item.id === selectedCommunityId.value)?.name ?? '已选小区')
  : '全部小区')

const communityMapPoints = computed<SpatialAmapPointFeature[]>(() => communityPoints.value
  .filter((item) => isCoordinate(item.longitude) && isCoordinate(item.latitude))
  .map((item) => ({
    id: item.communityId,
    longitude: Number(item.longitude),
    latitude: Number(item.latitude),
    kind: 'COMMUNITY',
    label: item.communityName,
  })))

const buildingMapPoints = computed<SpatialAmapPointFeature[]>(() => visibleRiskBuildings.value
  .filter((item) => isCoordinate(item.longitude) && isCoordinate(item.latitude))
  .map((item) => ({
    id: item.buildingId,
    longitude: Number(item.longitude),
    latitude: Number(item.latitude),
    kind: 'BUILDING',
    label: item.buildingName,
    riskLevel: item.riskLevel,
  })))

const verifiedBoundaryCount = computed(() => communityFeatures.value.length + visibleBuildings.value.length)
const canOpenRiskOverview = computed(() => authStore.hasAnyRole(['GOVERNMENT_MANAGER', 'ADMIN']))

watch(searchInput, (value) => store.setSearchKeyword(value))
watch(selectedRiskLevels, (value) => store.setRiskLevels(value), { deep: true })
watch(
  [communityFeatures, visibleBuildings, visibleRiskBuildings, communityPoints, selectedCommunityId, selectedBuildingIds],
  syncMap,
  { deep: true },
)
watch(errorMessage, (message) => {
  if (message) appStore.notify(message, 'error')
})
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
      onBuildingClick: (buildingId) => {
        store.selectSingleBuilding(buildingId)
        void openBuildingDetail(buildingId)
      },
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

function syncMap(): void {
  if (!mapMounted.value) return
  driver.sync({
    communities: communityFeatures.value,
    buildings: visibleBuildings.value,
    communityPoints: communityMapPoints.value,
    buildingPoints: buildingMapPoints.value,
    selectedCommunityId: selectedCommunityId.value,
    selectedBuildingIds: selectedBuildingIds.value,
  })
}

function isCoordinate(value: unknown): boolean {
  return Number.isFinite(Number(value))
}

function toggleRiskLevel(level: MapRiskLevel): void {
  selectedRiskLevels.value = selectedRiskLevels.value.includes(level)
    ? selectedRiskLevels.value.filter((item) => item !== level) : [...selectedRiskLevels.value, level]
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
</script>

<template>
  <section class="spatial-page">
    <header class="spatial-toolbar">
      <div>
        <h1>地图展示</h1>
        <p>展示已定位小区、楼栋风险状态及已确认空间边界。</p>
      </div>
      <div class="toolbar-side">
        <div class="tag-row">
          <el-tag effect="plain" round>{{ selectedCommunityName }}</el-tag>
          <el-tag effect="plain" type="success" round>已定位小区 {{ communityMapPoints.length }}</el-tag>
          <el-tag effect="plain" type="info" round>当前楼栋 {{ visibleRiskBuildings.length }}</el-tag>
          <el-tag effect="plain" type="info" round>确认边界 {{ verifiedBoundaryCount }}</el-tag>
          <el-tag v-if="viewport" effect="plain" type="info" round>缩放 {{ viewport.zoom }}</el-tag>
        </div>
        <button v-if="canOpenRiskOverview" type="button" class="next-action" @click="openRiskOverview">查看风险总览 →</button>
      </div>
    </header>

    <div class="next-step-hint">
      <strong>下一步</strong>
      <span>可先按小区或风险等级定位楼栋，再打开详情查看风险依据与治理优先级。</span>
    </div>

    <div class="spatial-filterbar">
      <el-select class="community-filter" :model-value="selectedCommunityId ?? ''" filterable placeholder="全部小区" @change="chooseCommunity">
        <el-option label="全部小区" value="" />
        <el-option v-for="community in communityOptions" :key="community.id" :label="community.name" :value="community.id" />
      </el-select>
      <el-input v-model="searchInput" clearable placeholder="搜索楼栋名称、编号或小区" class="spatial-search" />
      <div class="tag-row" aria-label="风险等级筛选">
        <button v-for="option in riskOptions" :key="option.value" type="button" class="filter-pill" :class="{ active: selectedRiskLevels.includes(option.value) }" @click="toggleRiskLevel(option.value)">{{ option.label }}</button>
      </div>
      <button v-if="selectedCommunityId" type="button" class="filter-pill" @click="selectCommunity(null)">清除小区筛选</button>
    </div>

    <div class="spatial-workspace">
      <aside class="spatial-list-panel">
        <div class="panel-heading"><strong>楼栋清单</strong><span>{{ visibleRiskBuildings.length }} 栋</span></div>
        <el-scrollbar class="building-scroll">
          <button v-for="item in visibleRiskBuildings" :key="item.buildingId" type="button" class="building-row" :class="{ selected: selectedBuildingIds.includes(item.buildingId) }" @click="store.selectSingleBuilding(item.buildingId)">
            <div class="row-between"><strong>{{ item.buildingName }}</strong><el-tag size="small" :type="riskTagType(item.riskLevel)" round>{{ riskLabel(item.riskLevel) }}风险</el-tag></div>
            <small>{{ item.buildingCode }} · {{ item.communityName || '所属小区' }}</small>
            <div class="row-between metrics"><span>风险 {{ item.riskScore ?? '—' }}</span><span>完整度 {{ item.completenessScore ?? '—' }}</span><span>{{ item.priorityLevel ?? '—' }}</span></div>
            <span class="detail-link" @click.stop="openBuildingDetail(item.buildingId)">查看详情</span>
          </button>
          <el-empty v-if="!loading && visibleRiskBuildings.length === 0" description="当前筛选暂无楼栋数据" />
        </el-scrollbar>
      </aside>

      <main class="map-panel">
        <div ref="mapContainer" class="map-surface" aria-label="城市房屋安全空间地图" />
        <div v-if="runtimeLoading" class="map-state">正在加载地图服务</div>
        <div v-else-if="mapUnavailable" class="map-state"><strong>地图服务当前不可用</strong><span>请稍后重试或联系系统管理员检查地图服务配置。</span></div>
        <div v-else-if="loading" class="map-loading">正在加载当前视野数据…</div>
        <div v-else-if="communityMapPoints.length === 0 && verifiedBoundaryCount === 0" class="map-data-note">当前没有可定位的空间数据，请先在“小区与楼栋管理”或“空间档案”中补充位置。</div>
        <div class="map-legend"><span class="very-high">■ 极高</span><span class="high">■ 高</span><span class="medium">■ 中</span><span class="low">■ 低</span><span>● 已定位实体</span><span>▢ 已确认边界</span></div>
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
.spatial-page{display:grid;gap:var(--usp-space-4);min-height:calc(100vh - 120px)}
.spatial-toolbar,.spatial-filterbar,.row-between,.panel-heading,.tag-row{display:flex;align-items:center;justify-content:space-between;gap:var(--usp-space-3)}
.spatial-toolbar h1{margin:0}.spatial-toolbar p{margin:4px 0 0;color:var(--usp-color-text-secondary)}.toolbar-side{display:grid;justify-items:end;gap:10px}.tag-row{justify-content:flex-start;flex-wrap:wrap}
.next-action{min-height:36px;padding:0 14px;border:1px solid var(--usp-color-primary);border-radius:var(--usp-radius-md);background:var(--usp-color-primary);color:#fff;font-weight:700}.next-step-hint{display:flex;align-items:center;gap:10px;padding:10px 14px;border:1px solid #d8ebe5;border-radius:var(--usp-radius-lg);background:#f5fbf9;color:var(--usp-color-text-secondary)}.next-step-hint strong{flex:0 0 auto;color:var(--usp-color-primary)}
.spatial-filterbar{justify-content:flex-start;padding:var(--usp-space-3);border:1px solid var(--usp-color-border);border-radius:var(--usp-radius-lg);background:var(--usp-color-surface);box-shadow:var(--usp-shadow-sm)}.community-filter{width:220px}.spatial-search{width:min(340px,100%)}
.filter-pill{min-height:34px;padding:0 var(--usp-space-3);border:1px solid var(--usp-color-border);border-radius:999px;background:var(--usp-color-surface);cursor:pointer}.filter-pill.active{border-color:var(--usp-color-primary);background:var(--usp-color-primary-soft);color:var(--usp-color-primary);font-weight:700}
.spatial-workspace{display:grid;grid-template-columns:minmax(270px,330px) minmax(0,1fr);min-height:650px;overflow:hidden;border:1px solid var(--usp-color-border);border-radius:var(--usp-radius-xl);background:var(--usp-color-surface);box-shadow:var(--usp-shadow-sm)}
.spatial-list-panel{border-right:1px solid var(--usp-color-border)}.panel-heading{padding:var(--usp-space-4);border-bottom:1px solid var(--usp-color-border)}.building-scroll{height:610px}.building-row{display:grid;width:100%;gap:8px;padding:var(--usp-space-4);border:0;border-bottom:1px solid var(--usp-color-border);border-radius:0;background:transparent;text-align:left;cursor:pointer}.building-row:hover,.building-row.selected{background:var(--usp-color-primary-soft)}.building-row.selected{box-shadow:inset 3px 0 var(--usp-color-primary)}.building-row small,.metrics{color:var(--usp-color-text-secondary)}.detail-link{color:var(--usp-color-primary);font-size:12px;font-weight:700}
.map-panel{position:relative;min-height:650px;background:#eef2f6}.map-surface{position:absolute;inset:0}.map-state{position:absolute;inset:0;z-index:20;display:grid;place-content:center;justify-items:center;gap:8px;padding:32px;background:rgba(248,250,252,.94);text-align:center}.map-loading,.map-legend,.map-data-note{position:absolute;z-index:15;padding:9px 13px;border-radius:12px;background:rgba(255,255,255,.95);box-shadow:0 4px 18px rgba(15,23,42,.12)}.map-loading{top:12px;left:12px}.map-data-note{top:12px;left:12px;right:12px;text-align:center;color:var(--usp-color-text-secondary)}.map-legend{right:12px;bottom:12px;display:flex;gap:12px;flex-wrap:wrap}.very-high{color:#cf1322}.high{color:#d46b08}.medium{color:#d4a017}.low{color:#389e0d}
@media(max-width:1100px){.spatial-toolbar{align-items:flex-start}.toolbar-side{justify-items:start}.spatial-workspace{grid-template-columns:1fr}.spatial-list-panel{border-right:0;border-bottom:1px solid var(--usp-color-border)}.building-scroll{height:320px}.map-panel{min-height:560px}}@media(max-width:720px){.spatial-toolbar,.spatial-filterbar{align-items:stretch;flex-direction:column}.toolbar-side{justify-items:stretch}.community-filter,.spatial-search{width:100%}.next-step-hint{align-items:flex-start;flex-direction:column}}
</style>
