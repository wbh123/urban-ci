<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getBuilding } from '@/shared/api/endpoints/buildings'
import { getCommunity } from '@/shared/api/endpoints/communities'
import { getBuildingBoundary } from '@/shared/api/endpoints/spatial'
import { listInspectionTasks } from '@/shared/api/endpoints/inspection'
import { listAiInferences } from '@/shared/api/endpoints/ai-inference'
import { getCurrentBuildingAssessment } from '@/shared/api/endpoints/assessment'
import { listRiskReports } from '@/shared/api/endpoints/reports'
import { toAppError } from '@/shared/api'
import AppLoading from '@/shared/components/AppLoading.vue'
import AppError from '@/shared/components/AppError.vue'
import {
  BuildingLifecycleTimeline,
  BuildingSummaryCard,
  EvidenceGallery,
  RiskSummaryPanel,
} from '@/shared/components/business'
import {
  loadBuildingDetail,
  type BuildingDetailModel,
  type BuildingDetailSources,
  type BuildingDetailWarningDomain,
} from '@/features/building-detail/building-detail-loader'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const errorMessage = ref('')
const model = ref<BuildingDetailModel | null>(null)
const activeTab = ref(normaliseTab(route.query.tab))

const buildingId = computed(() => String(route.params.buildingId ?? ''))

const sources: BuildingDetailSources = {
  getBuilding,
  getCommunity,
  getBuildingBoundary,
  listInspectionTasks,
  listAiInferences,
  getCurrentBuildingAssessment,
  listRiskReports,
}

const warningLabels: Record<BuildingDetailWarningDomain, string> = {
  COMMUNITY: '所属小区',
  SPATIAL: '空间档案',
  INSPECTION: '现场巡检',
  ANALYSIS: '辅助分析',
  ASSESSMENT: '正式评分',
  REPORT: '报告归档',
}

watch(buildingId, () => { void load() })
watch(() => route.query.tab, (value) => { activeTab.value = normaliseTab(value) })

onMounted(load)

async function load(): Promise<void> {
  if (!buildingId.value) {
    model.value = null
    errorMessage.value = '缺少楼栋标识。'
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    model.value = await loadBuildingDetail(buildingId.value, sources)
  } catch (error) {
    model.value = null
    errorMessage.value = toAppError(error).message
  } finally {
    loading.value = false
  }
}

function normaliseTab(value: unknown): 'overview' | 'lifecycle' | 'evidence' | 'assessment' | 'reports' {
  if (value === 'lifecycle' || value === 'evidence' || value === 'assessment' || value === 'reports') return value
  return 'overview'
}

function changeTab(tabName: string | number): void {
  const tab = normaliseTab(tabName)
  activeTab.value = tab
  void router.replace({ query: tab === 'overview' ? {} : { tab } })
}
</script>

<template>
  <section class="building-detail-page">
    <header class="detail-toolbar">
      <div>
        <el-button link type="primary" @click="router.push('/console/map')">‹ 返回地图</el-button>
        <h1>楼栋统一详情</h1>
        <p>在同一页面查看基础档案、现场巡检、辅助分析、人工复核、正式评分与报告状态。</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </header>

    <AppLoading :visible="loading" inline text="正在汇总楼栋业务数据…" />
    <AppError v-if="errorMessage" :message="errorMessage" @retry="load" />

    <template v-if="model && !loading">
      <BuildingSummaryCard :summary="model.summary" />

      <div v-if="model.warnings.length" class="domain-warnings">
        <el-alert
          v-for="warning in model.warnings"
          :key="warning.domain"
          :title="`${warningLabels[warning.domain]}暂时无法读取`"
          :description="warning.message"
          type="warning"
          :closable="false"
          show-icon
        />
      </div>

      <el-tabs :model-value="activeTab" class="detail-tabs" @tab-change="changeTab">
        <el-tab-pane label="业务概览" name="overview">
          <div class="overview-grid">
            <RiskSummaryPanel :summary="model.risk" />
            <BuildingLifecycleTimeline :nodes="model.lifecycle" />
          </div>
        </el-tab-pane>

        <el-tab-pane label="生命周期" name="lifecycle">
          <BuildingLifecycleTimeline :nodes="model.lifecycle" />
        </el-tab-pane>

        <el-tab-pane label="证据资料" name="evidence">
          <EvidenceGallery :items="model.evidence" />
          <el-alert
            class="section-note"
            title="辅助分析结果需要结合人工复核"
            description="人工智能识别和建议仅作为辅助信息，不作为正式鉴定结论。"
            type="info"
            :closable="false"
            show-icon
          />
        </el-tab-pane>

        <el-tab-pane label="正式评分" name="assessment">
          <RiskSummaryPanel :summary="model.risk" />
          <el-alert
            class="section-note"
            title="正式评分与辅助分析分层展示"
            description="正式风险评分来自系统规则和经审核证据；辅助分析结果不会自动替代人工专业判断。"
            type="info"
            :closable="false"
            show-icon
          />
        </el-tab-pane>

        <el-tab-pane label="报告归档" name="reports">
          <BuildingLifecycleTimeline :nodes="model.lifecycle.filter((item) => item.stage === 'REPORT')" />
          <p class="muted">报告下载、重新生成等操作继续由风险总览与楼栋报告页面承担；本页负责统一展示当前归档状态。</p>
          <el-button plain @click="router.push('/console/renewal-priorities')">前往风险总览与楼栋报告</el-button>
        </el-tab-pane>
      </el-tabs>
    </template>
  </section>
</template>

<style scoped lang="scss">
.building-detail-page{display:grid;gap:var(--usp-space-4);max-width:1440px;margin:0 auto}.detail-toolbar{display:flex;align-items:flex-end;justify-content:space-between;gap:var(--usp-space-4)}.detail-toolbar h1{margin:8px 0 4px;font-size:30px}.detail-toolbar p{margin:0;color:var(--usp-color-text-secondary);line-height:1.6}.domain-warnings{display:grid;gap:var(--usp-space-2)}.detail-tabs{min-width:0}.overview-grid{display:grid;grid-template-columns:minmax(0,1fr) minmax(360px,.8fr);gap:var(--usp-space-4);align-items:start}.section-note{margin-top:var(--usp-space-3)}.muted{color:var(--usp-color-text-secondary);line-height:1.7}@media(max-width:1000px){.overview-grid{grid-template-columns:1fr}}@media(max-width:640px){.detail-toolbar{align-items:flex-start;flex-direction:column}.detail-toolbar h1{font-size:26px}}
</style>
