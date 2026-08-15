<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import {
  getAiDashboardActivity,
  getAiDashboardBuildings,
  type AiDashboardActivity,
  type AiDashboardBuilding,
  type AiDashboardLayerMode,
  type AiDashboardOverview,
} from '@/shared/api/endpoints/ai-dashboard'
import type { RiskOverview } from '@/shared/api/endpoints/reports'
import { useSpatialMapStore } from '@/stores/spatial-map'
import AiWallMap from './AiWallMap.vue'
import AiWallActivityFeed from './AiWallActivityFeed.vue'
import AiWallAttentionList from './AiWallAttentionList.vue'
import AiWallBuildingDrawer from './AiWallBuildingDrawer.vue'
import AiWallDiscoveryPanel from './AiWallDiscoveryPanel.vue'
import AiWallHeader from './AiWallHeader.vue'
import AiWallMapLayers from './AiWallMapLayers.vue'
import AiWallMetrics from './AiWallMetrics.vue'

const props = withDefaults(defineProps<{
  overview: RiskOverview | null
  loading: boolean
  error: boolean
  aiOverview?: AiDashboardOverview | null
  aiLoading?: boolean
  aiError?: boolean
  canReview?: boolean
  canManageInspection?: boolean
}>(), {
  aiOverview: null,
  aiLoading: false,
  aiError: false,
  canReview: false,
  canManageInspection: false,
})

const emit = defineEmits<{
  openMap: []
  openRisk: []
  openBuilding: [buildingId: string]
  openReview: []
  openInspection: [buildingId: string]
}>()

const spatialMapStore = useSpatialMapStore()
const { selectedBuildingIds } = storeToRefs(spatialMapStore)
const ACTIVITY_POLL_MS = 4_000
const BUILDINGS_RETRY_MS = 12_000
const wallRoot = ref<HTMLElement | null>(null)
const fullscreen = ref(false)
const layerMode = ref<AiDashboardLayerMode>('RISK')
const aiBuildings = ref<AiDashboardBuilding[]>([])
const aiBuildingsLoaded = ref(false)
const aiBuildingsError = ref(false)
const activity = ref<AiDashboardActivity | null>(null)
const activityLoading = ref(false)
const activityError = ref(false)
const selectedBuilding = ref<AiDashboardBuilding | null>(null)
const focusBuildingId = ref<string | null>(null)
let activityTimer: ReturnType<typeof setTimeout> | null = null
let buildingsRetryTimer: ReturnType<typeof setTimeout> | null = null
let disposed = false

const aiLayersAvailable = computed(() => (
  Boolean(props.aiOverview)
  && !props.aiError
  && aiBuildingsLoaded.value
  && !aiBuildingsError.value
))
const degraded = computed(() => props.aiError || aiBuildingsError.value)
const generatedAt = computed(() => props.aiOverview?.generatedAt ?? props.overview?.generatedAt ?? null)
const riskFallbackMetrics = computed(() => {
  const summary = props.overview?.summary
  if (!summary) return []
  const coverage = summary.buildingCount > 0
    ? Math.round((summary.assessedBuildingCount / summary.buildingCount) * 100)
    : 0
  return [
    { key: 'buildings', label: '纳管楼栋', value: summary.buildingCount, suffix: '栋' },
    { key: 'assessed', label: '已正式评分', value: summary.assessedBuildingCount, suffix: '栋' },
    { key: 'risk', label: '高风险楼栋', value: summary.highRiskCount, suffix: '栋' },
    { key: 'priority', label: '高优先级', value: summary.highPriorityCount, suffix: '栋' },
    { key: 'review', label: '评分待复核', value: summary.lowConfidenceCount, suffix: '栋' },
    { key: 'coverage', label: '正式评分覆盖率', value: coverage, suffix: '%' },
  ]
})
const riskFallbackBuildings = computed(() => props.overview?.topRiskBuildings.slice(0, 7) ?? [])

watch(aiLayersAvailable, (available) => {
  if (!available && ['AI_DEFECT', 'AI_ATTENTION', 'REVIEW'].includes(layerMode.value)) {
    layerMode.value = 'RISK'
  } else if (available && layerMode.value === 'RISK' && props.aiOverview) {
    layerMode.value = 'AI_ATTENTION'
  }
})

watch(selectedBuildingIds, (ids, previousIds) => {
  if (ids.length > 0) return
  if ((previousIds?.length ?? 0) === 0 && !selectedBuilding.value && !focusBuildingId.value) return
  selectedBuilding.value = null
  focusBuildingId.value = null
})

onMounted(() => {
  disposed = false
  document.addEventListener('fullscreenchange', syncFullscreen)
  void loadAiBuildings()
  void refreshActivity()
})

onBeforeUnmount(() => {
  disposed = true
  document.removeEventListener('fullscreenchange', syncFullscreen)
  clearActivityTimer()
  clearBuildingsRetryTimer()
})

async function loadAiBuildings(): Promise<void> {
  if (disposed) return
  clearBuildingsRetryTimer()
  aiBuildingsLoaded.value = false
  aiBuildingsError.value = false
  try {
    aiBuildings.value = await getAiDashboardBuildings()
    aiBuildingsError.value = false
    clearBuildingsRetryTimer()
  } catch {
    aiBuildings.value = []
    aiBuildingsError.value = true
    layerMode.value = 'RISK'
    if (!disposed) scheduleBuildingsRetry()
  } finally {
    aiBuildingsLoaded.value = true
  }
}

function scheduleBuildingsRetry(): void {
  if (disposed) return
  clearBuildingsRetryTimer()
  buildingsRetryTimer = setTimeout(() => {
    buildingsRetryTimer = null
    void loadAiBuildings()
  }, BUILDINGS_RETRY_MS)
}

function clearBuildingsRetryTimer(): void {
  if (buildingsRetryTimer) clearTimeout(buildingsRetryTimer)
  buildingsRetryTimer = null
}

async function refreshActivity(): Promise<void> {
  if (disposed) return
  clearActivityTimer()
  activityLoading.value = true
  activityError.value = false
  try {
    activity.value = await getAiDashboardActivity(18)
  } catch {
    activityError.value = true
  } finally {
    activityLoading.value = false
    if (!disposed) {
      activityTimer = setTimeout(() => void refreshActivity(), ACTIVITY_POLL_MS)
    }
  }
}

function clearActivityTimer(): void {
  if (activityTimer) clearTimeout(activityTimer)
  activityTimer = null
}

function focusBuilding(building: AiDashboardBuilding): void {
  selectedBuilding.value = building
  if (focusBuildingId.value !== building.buildingId) {
    focusBuildingId.value = building.buildingId
    return
  }
  focusBuildingId.value = null
  void nextTick(() => {
    focusBuildingId.value = building.buildingId
  })
}

function handleMapBuildingSelected(buildingId: string): void {
  focusBuildingId.value = buildingId
  selectedBuilding.value = aiBuildings.value.find((item) => item.buildingId === buildingId)
    ?? props.aiOverview?.attention.find((item) => item.buildingId === buildingId)
    ?? null
}

function openBuilding(building: AiDashboardBuilding): void {
  selectedBuilding.value = building
  emit('openBuilding', building.buildingId)
}

async function enterFullscreen(): Promise<void> {
  if (document.fullscreenElement || !wallRoot.value?.requestFullscreen) return
  await wallRoot.value.requestFullscreen()
}

function syncFullscreen(): void {
  fullscreen.value = document.fullscreenElement === wallRoot.value
}

function riskLabel(level?: string | null): string {
  if (level === 'VERY_HIGH') return '极高风险'
  if (level === 'HIGH') return '高风险'
  if (level === 'MEDIUM') return '中风险'
  if (level === 'LOW') return '低风险'
  return '待评估'
}
</script>

<template>
  <section ref="wallRoot" class="data-wall" :class="{ 'is-fullscreen': fullscreen }">
    <AiWallMap
      class="wall-map"
      :ai-buildings="aiBuildings"
      :layer-mode="layerMode"
      :focus-building-id="focusBuildingId"
      :can-manage-inspection="canManageInspection"
      @open-map="emit('openMap')"
      @open-building="emit('openBuilding', $event)"
      @open-inspection="emit('openInspection', $event)"
      @building-selected="handleMapBuildingSelected"
    />

    <div class="wall-ui">
      <AiWallHeader
        :generated-at="generatedAt"
        :fullscreen="fullscreen"
        :degraded="degraded"
        @fullscreen="enterFullscreen"
      />

      <AiWallMetrics v-if="aiOverview" :overview="aiOverview" />
      <section v-else-if="riskFallbackMetrics.length" class="risk-fallback-metrics" aria-label="正式风险态势指标">
        <article v-for="item in riskFallbackMetrics" :key="item.key">
          <span>{{ item.label }}</span><strong>{{ item.value.toLocaleString('zh-CN') }}<small>{{ item.suffix }}</small></strong>
        </article>
      </section>

      <div class="layer-row">
        <AiWallMapLayers v-model="layerMode" :ai-available="aiLayersAvailable" />
      </div>

      <div class="wall-overlay-grid">
        <div class="wall-side wall-side--left">
          <AiWallDiscoveryPanel v-if="aiOverview" :overview="aiOverview" />
          <section v-else class="fallback-panel">
            <header><strong>正式风险态势</strong><span>AI 聚合未加载时继续可用</span></header>
            <div v-if="overview?.riskDistribution.length" class="risk-distribution">
              <div v-for="row in overview.riskDistribution" :key="row.code">
                <span>{{ row.label }}</span><strong>{{ row.count }}</strong>
              </div>
            </div>
            <p v-else>当前暂无正式风险分布数据。</p>
          </section>
        </div>

        <div class="wall-map-clear" aria-hidden="true" />

        <div class="wall-side wall-side--right">
          <AiWallAttentionList
            v-if="aiOverview"
            :items="aiOverview.attention"
            @focus="focusBuilding"
            @open="openBuilding"
          />
          <section v-else class="fallback-panel">
            <header><strong>重点风险楼栋</strong><span>来自正式规则评分</span></header>
            <div v-if="riskFallbackBuildings.length" class="risk-list">
              <button
                v-for="(row, index) in riskFallbackBuildings"
                :key="row.buildingId"
                type="button"
                @click="emit('openBuilding', row.buildingId)"
              >
                <span>{{ String(index + 1).padStart(2, '0') }}</span>
                <strong>{{ row.communityName }} · {{ row.buildingName }}</strong>
                <b>{{ riskLabel(row.riskLevel) }}</b>
              </button>
            </div>
            <p v-else>当前暂无重点风险楼栋。</p>
          </section>
        </div>
      </div>

      <AiWallActivityFeed
        v-if="activity || activityLoading"
        :activity="activity"
        :loading="activityLoading"
      />
      <div v-else-if="activityError && aiOverview" class="wall-activity-unavailable">AI 实时研判动态暂不可用，地图和正式风险态势不受影响。</div>

      <div v-if="loading || aiLoading" class="wall-loading">正在更新城市建筑安全态势…</div>
      <div v-else-if="error && !overview && !aiOverview" class="wall-notice">正式风险总览暂不可用，地图与其他基础业务仍可继续使用。</div>
    </div>

    <AiWallBuildingDrawer
      :building="selectedBuilding"
      :can-review="canReview"
      :can-manage-inspection="canManageInspection"
      @close="selectedBuilding = null"
      @open-building="emit('openBuilding', $event)"
      @open-review="emit('openReview')"
      @open-inspection="emit('openInspection', $event)"
    />
  </section>
</template>

<style scoped lang="scss">
.data-wall{position:relative;min-height:760px;overflow:hidden;border:1px solid rgba(32,106,105,.2);border-radius:20px;background:#06191c;box-shadow:0 20px 56px rgba(21,51,48,.18)}.data-wall.is-fullscreen{width:100vw;height:100vh;min-height:100vh;border:0;border-radius:0}.wall-map{position:absolute;inset:0}.data-wall :deep(.building-focus-switch){top:154px;right:18px;left:auto;transform:none}.wall-ui{position:relative;z-index:30;display:grid;height:100%;min-height:760px;grid-template-rows:auto auto auto minmax(360px,1fr) auto;gap:8px;padding:12px;pointer-events:none}.is-fullscreen .wall-ui{min-height:100vh}.wall-ui>*{pointer-events:auto}.risk-fallback-metrics{display:grid;grid-template-columns:repeat(6,minmax(100px,1fr));gap:8px}.risk-fallback-metrics article{display:grid;gap:5px;padding:9px 11px;border:1px solid rgba(119,218,202,.13);border-radius:13px;background:rgba(5,29,32,.76);color:#eefcf9;backdrop-filter:blur(12px)}.risk-fallback-metrics span{color:#8cb2ad;font-size:9px;font-weight:800}.risk-fallback-metrics strong{font-size:19px}.risk-fallback-metrics small{margin-left:4px;color:#7da49f;font-size:8px}.layer-row{display:flex;justify-content:center;min-height:35px}.wall-overlay-grid{display:grid;min-height:0;grid-template-columns:minmax(220px,270px) minmax(260px,1fr) minmax(240px,300px);gap:10px;pointer-events:none}.wall-side{min-height:0;align-self:start;pointer-events:auto}.wall-map-clear{pointer-events:none}.fallback-panel{display:grid;gap:9px;padding:12px;border:1px solid rgba(113,224,205,.15);border-radius:14px;background:rgba(5,28,31,.8);color:#effcf9;backdrop-filter:blur(14px)}.fallback-panel header{display:grid;gap:2px}.fallback-panel header strong{font-size:12px}.fallback-panel header span,.fallback-panel p{margin:0;color:#789d97;font-size:8px}.risk-distribution{display:grid;grid-template-columns:repeat(2,1fr);gap:6px}.risk-distribution>div{display:flex;align-items:center;justify-content:space-between;gap:8px;padding:8px;border-radius:9px;background:rgba(255,255,255,.04)}.risk-distribution span{color:#8fb0ab;font-size:8px}.risk-distribution strong{font-size:12px}.risk-list{display:grid;gap:5px}.risk-list button{display:grid;grid-template-columns:24px minmax(0,1fr) auto;align-items:center;gap:6px;padding:8px;border:0;border-radius:9px;background:rgba(255,255,255,.04);color:#e7f8f4;text-align:left;cursor:pointer}.risk-list button span{color:#789d97;font-size:8px}.risk-list button strong{overflow:hidden;font-size:8px;text-overflow:ellipsis;white-space:nowrap}.risk-list button b{color:#f0ca86;font-size:7px}.wall-loading,.wall-notice,.wall-activity-unavailable{position:absolute;z-index:42;left:50%;bottom:18px;padding:6px 10px;border:1px solid rgba(125,220,204,.15);border-radius:999px;background:rgba(5,28,31,.82);color:#a7c8c2;font-size:8px;transform:translateX(-50%);backdrop-filter:blur(10px)}.wall-notice,.wall-activity-unavailable{color:#f1d59c}
@media(max-width:1100px){.risk-fallback-metrics{grid-template-columns:repeat(3,1fr)}.wall-overlay-grid{grid-template-columns:minmax(210px,250px) minmax(120px,1fr) minmax(220px,270px)}}@media(max-width:820px){.data-wall{min-height:900px}.data-wall :deep(.building-focus-switch){top:214px;right:12px;left:auto;transform:none}.wall-ui{min-height:900px;grid-template-rows:auto auto auto auto auto}.wall-overlay-grid{grid-template-columns:1fr;align-content:start}.wall-map-clear{display:none}.wall-side{max-height:none}.risk-fallback-metrics{grid-template-columns:repeat(2,1fr)}}
</style>
