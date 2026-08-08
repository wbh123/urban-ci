<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import { getMapRuntimeConfig, type MapRuntimeConfig } from '@/shared/api/endpoints/map'
import { getCurrentBuildingAssessment, type BuildingCurrentAssessment } from '@/shared/api/endpoints/assessment'
import { listFeedbackReports, type FeedbackManagementRow } from '@/shared/api/endpoints/feedback'
import { createSpatialAmapDriver } from '@/shared/map/spatial-amap'
import { useSpatialMapStore, type MapRiskLevel, type SpatialBuildingProjection } from '@/stores/spatial-map'

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
const detailBuildingId = ref<string | null>(null)
const detailAssessment = ref<BuildingCurrentAssessment | null>(null)
const detailFeedback = ref<FeedbackManagementRow[]>([])
const activeDetailTab = ref('overview')
const driver = createSpatialAmapDriver()

const riskOptions: Array<{ value: MapRiskLevel; label: string }> = [
  { value: 'VERY_HIGH', label: '极高风险' }, { value: 'HIGH', label: '高风险' },
  { value: 'MEDIUM', label: '中风险' }, { value: 'LOW', label: '低风险' },
]
const mapUnavailable = computed(() => {
  const config = runtimeConfig.value
  return !runtimeLoading.value && (!config || !config.enabled || config.mode !== 'LIVE' || !config.jsApiKey)
})
const selectedProjection = computed<SpatialBuildingProjection | null>(() => {
  const id = detailBuildingId.value
  if (!id) return null
  return visibleBuildings.value.find((item) => item.feature.id === id)
    ?? store.buildingFeatures.map((feature) => ({ feature, risk: store.riskRows.find((row) => row.buildingId === feature.id) })).find((item) => item.feature.id === id)
    ?? null
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
  detailBuildingId.value = buildingId; detailVisible.value = true; detailLoading.value = true; activeDetailTab.value = 'overview'
  detailAssessment.value = null; detailFeedback.value = []
  const projection = visibleBuildings.value.find((item) => item.feature.id === buildingId)
  const communityId = projection?.feature.properties.communityId ?? projection?.risk?.communityId
  const [assessmentResult, feedbackResult] = await Promise.allSettled([
    getCurrentBuildingAssessment(buildingId),
    communityId ? listFeedbackReports({ communityId, page: 1, size: 100 }) : Promise.resolve({ content: [], page: { page: 1, size: 100, totalElements: 0, totalPages: 0 } }),
  ])
  if (assessmentResult.status === 'fulfilled') detailAssessment.value = assessmentResult.value
  if (feedbackResult.status === 'fulfilled') detailFeedback.value = feedbackResult.value.content.filter((item) => item.buildingId === buildingId)
  detailLoading.value = false
}
function riskLabel(level?: string): string { return ({ VERY_HIGH: '极高', HIGH: '高', MEDIUM: '中', LOW: '低' } as Record<string, string>)[level ?? ''] ?? '暂无' }
function riskTagType(level?: string): 'danger' | 'warning' | 'success' | 'info' {
  if (level === 'VERY_HIGH') return 'danger'; if (level === 'HIGH' || level === 'MEDIUM') return 'warning'; if (level === 'LOW') return 'success'; return 'info'
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

    <el-drawer v-model="detailVisible" size="520px" title="楼栋空间详情">
      <div v-if="detailLoading">正在读取楼栋业务数据…</div>
      <template v-else-if="selectedProjection">
        <div class="row-between detail-title"><div><h2>{{ selectedProjection.feature.properties.name }}</h2><small>{{ selectedProjection.feature.properties.entityCode }}</small></div><el-tag :type="riskTagType(selectedProjection.risk?.riskLevel)">{{ riskLabel(selectedProjection.risk?.riskLevel) }}风险</el-tag></div>
        <el-tabs v-model="activeDetailTab">
          <el-tab-pane label="概览" name="overview">
            <div class="metric-grid"><article><span>风险评分</span><strong>{{ selectedProjection.risk?.riskScore ?? '—' }}</strong></article><article><span>资料完整度</span><strong>{{ selectedProjection.risk?.completenessScore ?? '—' }}</strong></article><article><span>更新优先分</span><strong>{{ selectedProjection.risk?.priorityScore ?? '—' }}</strong></article><article><span>更新优先级</span><strong>{{ selectedProjection.risk?.priorityLevel ?? '—' }}</strong></article></div>
            <el-alert v-if="detailAssessment?.freshness === 'STALE'" title="当前评分已过期，请重新计算" type="warning" :closable="false" show-icon />
            <p>{{ detailAssessment?.disclaimer || '评分仅作为治理辅助信息，不替代专业检测结论。' }}</p>
          </el-tab-pane>
          <el-tab-pane label="证据" name="evidence">
            <h3>已纳入评分因素</h3><ul><li v-for="factor in detailAssessment?.risk?.topFactors || []" :key="`${factor.factorCode}-${factor.sourceId || ''}`"><strong>{{ factor.label }}</strong> · 影响 {{ factor.effect }} · 可靠度 {{ factor.reliability ?? '—' }}</li></ul>
            <h3>未纳入证据</h3><ul><li v-for="item in detailAssessment?.risk?.excludedEvidence || []" :key="`${item.sourceType}-${item.sourceId}-${item.reason}`">{{ item.sourceType || '证据' }}：{{ item.reason }}</li></ul>
          </el-tab-pane>
          <el-tab-pane label="评分明细" name="score"><div v-for="dimension in detailAssessment?.risk?.dimensionScores || []" :key="dimension.code" class="score-row"><span>{{ dimension.label }}</span><strong>{{ dimension.score }}</strong><small>权重 {{ dimension.weight }} · 贡献 {{ dimension.contribution }}</small></div><div class="tag-row"><el-tag v-for="item in detailAssessment?.completeness?.missingItems || []" :key="item" type="warning" effect="plain">{{ item }}</el-tag></div></el-tab-pane>
          <el-tab-pane label="公众反馈" name="feedback"><article v-for="item in detailFeedback" :key="item.reportId" class="feedback-card"><div class="row-between"><strong>{{ item.reportCode }}</strong><el-tag size="small">{{ item.status }}</el-tag></div><p>{{ item.description }}</p><small>{{ item.submittedAt }} · {{ item.urgency }}</small></article><el-empty v-if="detailFeedback.length === 0" description="该楼栋暂无公众反馈" /></el-tab-pane>
        </el-tabs>
        <el-button type="primary" plain @click="router.push(`/console/buildings/${selectedProjection.feature.id}/assessment`)">打开完整评分详情</el-button>
      </template>
    </el-drawer>
  </section>
</template>

<style scoped lang="scss">
.spatial-page{display:grid;gap:var(--usp-space-4);min-height:calc(100vh - 120px)}
.spatial-toolbar,.spatial-filterbar,.row-between,.panel-heading,.tag-row{display:flex;align-items:center;justify-content:space-between;gap:var(--usp-space-3)}
.spatial-toolbar h1,.detail-title h2{margin:0}.spatial-toolbar p{margin:4px 0 0;color:var(--usp-color-text-secondary)}.tag-row{justify-content:flex-start;flex-wrap:wrap}
.spatial-filterbar{justify-content:flex-start;padding:var(--usp-space-3);border:1px solid var(--usp-color-border);border-radius:var(--usp-radius-lg);background:var(--usp-color-surface)}.community-filter{width:220px}.spatial-search{width:min(340px,100%)}
.filter-pill{min-height:34px;padding:0 var(--usp-space-3);border:1px solid var(--usp-color-border);border-radius:999px;background:var(--usp-color-surface);cursor:pointer}.filter-pill.active{border-color:var(--usp-color-primary);background:var(--usp-color-primary-soft);color:var(--usp-color-primary);font-weight:700}
.spatial-workspace{display:grid;grid-template-columns:minmax(270px,330px) minmax(0,1fr);min-height:650px;overflow:hidden;border:1px solid var(--usp-color-border);border-radius:var(--usp-radius-lg);background:var(--usp-color-surface)}
.spatial-list-panel{border-right:1px solid var(--usp-color-border)}.panel-heading{padding:var(--usp-space-4);border-bottom:1px solid var(--usp-color-border)}.building-scroll{height:610px}.building-row{display:grid;width:100%;gap:8px;padding:var(--usp-space-4);border:0;border-bottom:1px solid var(--usp-color-border);background:transparent;text-align:left;cursor:pointer}.building-row:hover,.building-row.selected{background:var(--usp-color-primary-soft)}.building-row.selected{box-shadow:inset 3px 0 var(--usp-color-primary)}.building-row small,.metrics{color:var(--usp-color-text-secondary)}.detail-link{color:var(--usp-color-primary);font-size:12px;font-weight:700}
.map-panel{position:relative;min-height:650px;background:#eef2f6}.map-surface{position:absolute;inset:0}.map-state{position:absolute;inset:0;z-index:20;display:grid;place-content:center;justify-items:center;gap:8px;padding:32px;background:rgba(248,250,252,.94);text-align:center}.map-loading,.map-legend{position:absolute;z-index:15;padding:8px 12px;border-radius:8px;background:rgba(255,255,255,.95);box-shadow:0 4px 18px rgba(15,23,42,.12)}.map-loading{top:12px;left:12px}.map-legend{right:12px;bottom:12px;display:flex;gap:12px}.very-high{color:#cf1322}.high{color:#d46b08}.medium{color:#d4a017}.low{color:#389e0d}
.metric-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px;margin-bottom:16px}.metric-grid article{display:grid;gap:4px;padding:12px;border-radius:8px;background:var(--usp-color-bg)}.metric-grid strong{font-size:24px}.score-row,.feedback-card{display:grid;gap:6px;padding:12px;margin-bottom:8px;border:1px solid var(--usp-color-border);border-radius:8px}.score-row{grid-template-columns:1fr auto}.score-row small{grid-column:1/-1}
@media(max-width:1100px){.spatial-workspace{grid-template-columns:1fr}.spatial-list-panel{border-right:0;border-bottom:1px solid var(--usp-color-border)}.building-scroll{height:320px}.map-panel{min-height:560px}}@media(max-width:720px){.spatial-toolbar,.spatial-filterbar{align-items:stretch;flex-direction:column}.community-filter,.spatial-search{width:100%}.metric-grid{grid-template-columns:1fr}}
</style>
