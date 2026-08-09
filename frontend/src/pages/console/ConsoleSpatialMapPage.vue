<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import BuildingDetailDrawer from '@/features/building-detail/BuildingDetailDrawer.vue'
import { loadBuildingDetail, type BuildingDetailModel } from '@/features/building-detail/building-detail-loader'
import { getMapRuntimeConfig, type MapRuntimeConfig } from '@/shared/api/endpoints/map'
import { createSpatialAmapDriver } from '@/shared/map/spatial-amap'
import { useSpatialMapStore, type MapRiskLevel } from '@/stores/spatial-map'

const router = useRouter()
const store = useSpatialMapStore()
const { communityFeatures, visibleBuildings, selectedCommunityId, selectedBuildingIds, viewport, loading, errorMessage } = storeToRefs(store)
const mapContainer = ref<HTMLElement | null>(null)
const runtimeConfig = ref<MapRuntimeConfig | null>(null)
const runtimeLoading = ref(true)
const runtimeError = ref('')
const mapMounted = ref(false)
const searchInput = ref('')
const selectedRiskLevels = ref<MapRiskLevel[]>([])
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detailModel = ref<BuildingDetailModel | null>(null)
const driver = createSpatialAmapDriver()

const riskOptions: Array<{ value: MapRiskLevel; label: string }> = [
  { value: 'VERY_HIGH', label: '极高风险' }, { value: 'HIGH', label: '高风险' },
  { value: 'MEDIUM', label: '中风险' }, { value: 'LOW', label: '低风险' },
]
const mapUnavailable = computed(() => {
  const config = runtimeConfig.value
  return !runtimeLoading.value && (!config || !config.enabled || config.mode !== 'LIVE' || !config.jsApiKey)
})
const selectedCommunityName = computed(() => selectedCommunityId.value
  ? (communityFeatures.value.find((item) => item.id === selectedCommunityId.value)?.properties.name ?? '已选小区')
  : '全部小区')

watch(searchInput, (value) => store.setSearchKeyword(value))
watch(selectedRiskLevels, (value) => store.setRiskLevels(value), { deep: true })
watch([communityFeatures, visibleBuildings, selectedCommunityId, selectedBuildingIds], syncMap, { deep: true })
onMounted(initialiseMap)
onBeforeUnmount(() => driver.destroy())

async function initialiseMap(): Promise<void> {
  runtimeLoading.value = true
  try {
    runtimeConfig.value = await getMapRuntimeConfig()
    await nextTick()
    const config = runtimeConfig.value
    if (!mapContainer.value || !config || !config.enabled || config.mode !== 'LIVE' || !config.jsApiKey) return
    mapMounted.value = await driver.mount(mapContainer.value, config, {
      onViewportChange: (nextViewport) => { void loadViewport(nextViewport) },
      onCommunityClick: (communityId) => { void selectCommunity(communityId) },
      onBuildingClick: (buildingId) => { store.toggleBuilding(buildingId); void openBuildingDetail(buildingId) },
    })
    syncMap()
  } catch (error) {
    runtimeError.value = error instanceof Error ? error.message : String(error)
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
  driver.sync({ communities: communityFeatures.value, buildings: visibleBuildings.value, selectedCommunityId: selectedCommunityId.value, selectedBuildingIds: selectedBuildingIds.value })
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
      <div><h1>地图展示</h1><p>仅展示已经人工确认的空间边界，地图颜色来自当前楼栋风险评分。</p></div>
      <div class="tag-row"><el-tag effect="plain">{{ selectedCommunityName }}</el-tag><el-tag v-if="viewport" effect="plain" type="info">缩放 {{ viewport.zoom }}</el-tag><el-tag v-if="loading" type="warning">空间数据加载中</el-tag></div>
    </header>

    <div class="spatial-filterbar">
      <el-select class="community-filter" :model-value="selectedCommunityId ?? ''" filterable placeholder="全部小区" @change="chooseCommunity">
        <el-option label="全部小区" value="" />
        <el-option v-for="community in communityFeatures" :key="community.id" :label="community.properties.name" :value="community.id" />
      </el-select>
      <el-input v-model="searchInput" clearable placeholder="搜索楼栋名称、编号或小区" class="spatial-search" />
      <div class="tag-row" aria-label="风险等级筛选"><button v-for="option in riskOptions" :key="option.value" type="button" class="filter-pill" :class="{ active: selectedRiskLevels.includes(option.value) }" @click="toggleRiskLevel(option.value)">{{ option.label }}</button></div>
      <button v-if="selectedCommunityId" type="button" class="filter-pill" @click="selectCommunity(null)">清除小区筛选</button>
    </div>
    <el-alert v-if="runtimeError || errorMessage" :title="runtimeError || errorMessage" type="error" :closable="false" show-icon />

    <div class="spatial-workspace">
      <aside class="spatial-list-panel">
        <div class="panel-heading"><strong>楼栋清单</strong><span>{{ visibleBuildings.length }} 栋</span></div>
        <el-scrollbar class="building-scroll">
          <button v-for="item in visibleBuildings" :key="item.feature.id" type="button" class="building-row" :class="{ selected: selectedBuildingIds.includes(item.feature.id) }" @click="store.toggleBuilding(item.feature.id)">
            <div class="row-between"><strong>{{ item.feature.properties.name }}</strong><el-tag size="small" :type="riskTagType(item.risk?.riskLevel)">{{ riskLabel(item.risk?.riskLevel) }}风险</el-tag></div>
            <small>{{ item.feature.properties.entityCode }} · {{ item.risk?.communityName || '所属小区' }}</small>
            <div class="row-between metrics"><span>风险 {{ item.risk?.riskScore ?? '—' }}</span><span>完整度 {{ item.risk?.completenessScore ?? '—' }}</span><span>{{ item.risk?.priorityLevel ?? '—' }}</span></div>
            <span class="detail-link" @click.stop="openBuildingDetail(item.feature.id)">查看详情</span>
          </button>
          <el-empty v-if="!loading && visibleBuildings.length === 0" description="当前范围暂无匹配楼栋" />
        </el-scrollbar>
      </aside>

      <main class="map-panel">
        <div ref="mapContainer" class="map-surface" aria-label="城市房屋安全空间地图" />
        <div v-if="runtimeLoading" class="map-state">正在加载地图运行配置</div>
        <div v-else-if="mapUnavailable" class="map-state"><strong>地图服务当前不可用</strong><span>系统不会使用模拟边界替代正式空间数据，请检查地图运行配置。</span></div>
        <div v-else-if="loading" class="map-loading">正在按当前视野加载边界…</div>
        <div class="map-legend"><span class="very-high">■ 极高</span><span class="high">■ 高</span><span class="medium">■ 中</span><span class="low">■ 低</span><span>▢ 小区边界</span></div>
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
.spatial-toolbar h1{margin:0}.spatial-toolbar p{margin:4px 0 0;color:var(--usp-color-text-secondary)}.tag-row{justify-content:flex-start;flex-wrap:wrap}
.spatial-filterbar{justify-content:flex-start;padding:var(--usp-space-3);border:1px solid var(--usp-color-border);border-radius:var(--usp-radius-lg);background:var(--usp-color-surface)}.community-filter{width:220px}.spatial-search{width:min(340px,100%)}
.filter-pill{min-height:34px;padding:0 var(--usp-space-3);border:1px solid var(--usp-color-border);border-radius:999px;background:var(--usp-color-surface);cursor:pointer}.filter-pill.active{border-color:var(--usp-color-primary);background:var(--usp-color-primary-soft);color:var(--usp-color-primary);font-weight:700}
.spatial-workspace{display:grid;grid-template-columns:minmax(270px,330px) minmax(0,1fr);min-height:650px;overflow:hidden;border:1px solid var(--usp-color-border);border-radius:var(--usp-radius-lg);background:var(--usp-color-surface)}
.spatial-list-panel{border-right:1px solid var(--usp-color-border)}.panel-heading{padding:var(--usp-space-4);border-bottom:1px solid var(--usp-color-border)}.building-scroll{height:610px}.building-row{display:grid;width:100%;gap:8px;padding:var(--usp-space-4);border:0;border-bottom:1px solid var(--usp-color-border);background:transparent;text-align:left;cursor:pointer}.building-row:hover,.building-row.selected{background:var(--usp-color-primary-soft)}.building-row.selected{box-shadow:inset 3px 0 var(--usp-color-primary)}.building-row small,.metrics{color:var(--usp-color-text-secondary)}.detail-link{color:var(--usp-color-primary);font-size:12px;font-weight:700}
.map-panel{position:relative;min-height:650px;background:#eef2f6}.map-surface{position:absolute;inset:0}.map-state{position:absolute;inset:0;z-index:20;display:grid;place-content:center;justify-items:center;gap:8px;padding:32px;background:rgba(248,250,252,.94);text-align:center}.map-loading,.map-legend{position:absolute;z-index:15;padding:8px 12px;border-radius:8px;background:rgba(255,255,255,.95);box-shadow:0 4px 18px rgba(15,23,42,.12)}.map-loading{top:12px;left:12px}.map-legend{right:12px;bottom:12px;display:flex;gap:12px}.very-high{color:#cf1322}.high{color:#d46b08}.medium{color:#d4a017}.low{color:#389e0d}
@media(max-width:1100px){.spatial-workspace{grid-template-columns:1fr}.spatial-list-panel{border-right:0;border-bottom:1px solid var(--usp-color-border)}.building-scroll{height:320px}.map-panel{min-height:560px}}@media(max-width:720px){.spatial-toolbar,.spatial-filterbar{align-items:stretch;flex-direction:column}.community-filter,.spatial-search{width:100%}}
</style>
