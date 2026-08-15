<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  downloadRiskReport,
  generateBuildingRiskReport,
  getRiskMap,
  getRiskOverview,
  listRiskReports,
  previewBuildingRiskReport,
  runIntelligentAnalysis,
  type AiIntelligentAnalysisResult,
  type DashboardBuilding,
  type RiskOverview,
  type RiskReportPreview,
  type RiskReportRow,
} from '@/shared/api'
import { toAppError } from '@/shared/api'
import { useAppStore } from '@/stores/app'
import AppActionButton from '@/shared/components/AppActionButton.vue'
import AppEmpty from '@/shared/components/AppEmpty.vue'
import AppError from '@/shared/components/AppError.vue'
import AppFilterBar from '@/shared/components/AppFilterBar.vue'
import AppFilterField from '@/shared/components/AppFilterField.vue'
import AppStatusTag from '@/shared/components/AppStatusTag.vue'
import AppTablePager from '@/shared/components/AppTablePager.vue'
import AppPageHeader from '@/shared/components/layout/AppPageHeader.vue'
import AiInsightCard from '@/shared/components/ai/AiInsightCard.vue'
import AiPageBrief from '@/shared/components/ai/AiPageBrief.vue'
import SpatialObjectSelector from '@/shared/components/SpatialObjectSelector.vue'
import type { SpatialObjectSelection } from '@/shared/composables/useSpatialObjectSelector'

const AUTO_DOWNLOAD_KEY = 'urban-safe:risk-report:auto-download'
const appStore = useAppStore()
const loading = ref(false)
const overview = ref<RiskOverview | null>(null)
const mapBuildings = ref<DashboardBuilding[]>([])
const reports = ref<RiskReportRow[]>([])
const reportTotal = ref(0)
const errorMessage = ref('')
const previewVisible = ref(false)
const previewLoading = ref(false)
const preview = ref<RiskReportPreview | null>(null)
const generatingBuildingId = ref('')
const topRiskPage = ref(1)
const topRiskPageSize = ref(20)
const scopeCommunityId = ref('')
const scopeSelectorRevision = ref(0)
const analysisCommunityId = ref('')
const analysisBuildingId = ref('')
const analysisSelectorRevision = ref(0)
const riskLevelFilter = ref('')
const priorityFilter = ref('')
const freshnessFilter = ref('')
const riskInterpretation = ref<AiIntelligentAnalysisResult | null>(null)
const riskInterpretationLoading = ref(false)
const riskInterpretationError = ref('')
const autoDownloadAfterGenerate = ref(readAutoDownloadPreference())

const filters = reactive({
  reportStatus: '',
  reportPage: 1,
  reportSize: 20,
})

const filteredScopeBuildings = computed(() => mapBuildings.value.filter((item) => {
  if (riskLevelFilter.value && item.riskLevel !== riskLevelFilter.value) return false
  if (priorityFilter.value && item.priorityLevel !== priorityFilter.value) return false
  if (freshnessFilter.value && item.freshness !== freshnessFilter.value) return false
  return true
}))

const extraFiltersActive = computed(() => Boolean(riskLevelFilter.value || priorityFilter.value || freshnessFilter.value))
const summaryCards = computed(() => {
  const summary = overview.value?.summary
  const buildings = filteredScopeBuildings.value
  if (extraFiltersActive.value) {
    return [
      { label: '筛选楼栋', value: buildings.length, tone: 'normal' },
      { label: '已评分楼栋', value: buildings.filter((item) => Boolean(item.riskLevel)).length, tone: 'normal' },
      { label: '高风险楼栋', value: buildings.filter((item) => ['VERY_HIGH', 'HIGH'].includes(item.riskLevel || '')).length, tone: 'danger' },
      { label: '高优先级', value: buildings.filter((item) => ['P1', 'P2'].includes(item.priorityLevel || '')).length, tone: 'warning' },
      { label: '待人工复核', value: summary?.lowConfidenceCount ?? 0, tone: 'warning' },
      { label: '无评分结果', value: buildings.filter((item) => !item.riskLevel).length, tone: 'muted' },
    ]
  }
  return [
    { label: '覆盖楼栋', value: summary?.buildingCount ?? 0, tone: 'normal' },
    { label: '已评分楼栋', value: summary?.assessedBuildingCount ?? 0, tone: 'normal' },
    { label: '高风险楼栋', value: summary?.highRiskCount ?? 0, tone: 'danger' },
    { label: '高优先级', value: summary?.highPriorityCount ?? 0, tone: 'warning' },
    { label: '待人工复核', value: summary?.lowConfidenceCount ?? 0, tone: 'warning' },
    { label: '无评分结果', value: summary?.noResultCount ?? 0, tone: 'muted' },
  ]
})

const filteredTopRiskBuildings = computed(() => (overview.value?.topRiskBuildings ?? []).filter((item) => {
  if (riskLevelFilter.value && item.riskLevel !== riskLevelFilter.value) return false
  if (priorityFilter.value && item.priorityLevel !== priorityFilter.value) return false
  if (freshnessFilter.value && item.freshness !== freshnessFilter.value) return false
  return true
}))
const pagedTopRiskBuildings = computed(() => {
  const start = (topRiskPage.value - 1) * topRiskPageSize.value
  return filteredTopRiskBuildings.value.slice(start, start + topRiskPageSize.value)
})
const selectedAnalysisBuilding = computed(() => mapBuildings.value.find((item) => item.buildingId === analysisBuildingId.value) ?? null)
const interpretationSummary = computed(() => riskInterpretation.value?.answer || '选择楼栋后可让 AI 对现有正式评分进行自然语言解读。')

watch(autoDownloadAfterGenerate, (enabled) => {
  if (typeof window !== 'undefined') {
    window.localStorage.setItem(AUTO_DOWNLOAD_KEY, String(enabled))
  }
})

function readAutoDownloadPreference(): boolean {
  if (typeof window === 'undefined') return true
  const stored = window.localStorage.getItem(AUTO_DOWNLOAD_KEY)
  return stored == null ? true : stored !== 'false'
}

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const scopeType = scopeCommunityId.value ? 'COMMUNITY' : 'ALL'
    const scopeId = scopeCommunityId.value || undefined
    const [overviewData, mapData, reportPage] = await Promise.all([
      getRiskOverview(scopeType, scopeId),
      getRiskMap(scopeType, scopeId),
      listRiskReports({
        status: filters.reportStatus || undefined,
        page: filters.reportPage - 1,
        size: filters.reportSize,
      }),
    ])
    overview.value = overviewData
    mapBuildings.value = mapData.buildings ?? []
    reports.value = reportPage.content ?? []
    reportTotal.value = reportPage.page.totalElements
  } catch (error) {
    errorMessage.value = toAppError(error).message
  } finally {
    loading.value = false
  }
}

async function runQuery(): Promise<void> {
  topRiskPage.value = 1
  filters.reportPage = 1
  await load()
}

async function resetScopeFilters(): Promise<void> {
  scopeCommunityId.value = ''
  riskLevelFilter.value = ''
  priorityFilter.value = ''
  freshnessFilter.value = ''
  scopeSelectorRevision.value += 1
  topRiskPage.value = 1
  await load()
}

async function handleScopeSelection(selection: SpatialObjectSelection): Promise<void> {
  scopeCommunityId.value = selection.communityId
  await runQuery()
}

function handleAnalysisSelection(selection: SpatialObjectSelection): void {
  analysisCommunityId.value = selection.communityId
  analysisBuildingId.value = selection.buildingId
  riskInterpretation.value = null
  riskInterpretationError.value = ''
}

async function runRiskInterpretation(): Promise<void> {
  const building = selectedAnalysisBuilding.value
  if (!building) return
  riskInterpretationLoading.value = true
  riskInterpretationError.value = ''
  try {
    riskInterpretation.value = await runIntelligentAnalysis({
      businessType: 'RISK_ASSESSMENT',
      businessId: building.buildingId,
      question:
        `请只解释当前已有的正式风险结果：风险等级 ${building.riskLevel || '暂无结果'}，` +
        `风险分 ${building.riskScore ?? '暂无'}，更新优先级 ${building.priorityLevel || '暂无'}，` +
        `数据状态 ${building.freshness || '暂无'}。说明值得关注的因素、资料新鲜度和后续人工治理动作。` +
        '不得修改、覆盖或重新计算正式风险分数。',
      context: {
        riskLevel: building.riskLevel,
        riskScore: building.riskScore,
        priorityLevel: building.priorityLevel,
        freshness: building.freshness,
      },
    })
  } catch (error) {
    riskInterpretationError.value = toAppError(error).message
  } finally {
    riskInterpretationLoading.value = false
  }
}

async function openPreview(buildingId: string): Promise<void> {
  previewVisible.value = true
  previewLoading.value = true
  preview.value = null
  try {
    preview.value = await previewBuildingRiskReport(buildingId)
  } catch (error) {
    previewVisible.value = false
    appStore.notify(toAppError(error).message, 'error')
  } finally {
    previewLoading.value = false
  }
}

async function generate(buildingId: string, force = false): Promise<void> {
  generatingBuildingId.value = buildingId
  try {
    const result = await generateBuildingRiskReport(buildingId, force)
    appStore.notify(result.reused ? `已获取报告 ${result.reportCode}` : `报告 ${result.reportCode} 已生成`, 'success')
    await load()
    if (autoDownloadAfterGenerate.value) {
      const generatedReport = await findGeneratedReport(result.reportCode, buildingId)
      if (generatedReport) {
        const downloaded = await download(generatedReport)
        if (!downloaded) {
          // 自动下载失败不影响已生成报告，用户仍可从历史报告手动下载。
          appStore.notify('报告已生成，但自动下载失败；可从历史报告手动下载。', 'warning')
        }
      } else {
        appStore.notify('报告已生成，暂未在历史列表定位到下载文件，请稍后手动下载。', 'warning')
      }
    }
  } catch (error) {
    appStore.notify(toAppError(error).message, 'error')
  } finally {
    generatingBuildingId.value = ''
  }
}

async function findGeneratedReport(reportCode: string, buildingId: string): Promise<RiskReportRow | null> {
  try {
    const page = await listRiskReports({ buildingId, page: 0, size: 20 })
    return page.content.find((item) => item.reportCode === reportCode)
      ?? page.content.find((item) => item.buildingId === buildingId && ['GENERATED', 'STALE'].includes(item.reportStatus))
      ?? null
  } catch {
    return null
  }
}

async function download(report: RiskReportRow): Promise<boolean> {
  try {
    const blob = await downloadRiskReport(report.reportId)
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `${report.reportCode}.pdf`
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    URL.revokeObjectURL(url)
    return true
  } catch (error) {
    appStore.notify(toAppError(error).message, 'error')
    return false
  }
}

function score(value?: number): string {
  return value == null ? '—' : value.toFixed(2)
}

onMounted(load)
</script>

<template>
  <section v-loading="loading" class="risk-page">
    <AppPageHeader eyebrow="风险治理" title="风险研判中心" description="正式风险仍由确定性规则计算；AI 只负责解释已有评分、补充关注点和辅助治理决策。" show-user-menu>
      <template #actions><el-button @click="load">刷新</el-button></template>
    </AppPageHeader>

    <AppError v-if="errorMessage" :message="errorMessage" @retry="load" />
    <template v-else>
      <AiPageBrief
        v-if="overview"
        title="风险治理辅助看板"
        :metrics="[
          { label: '高风险楼栋', value: overview.summary.highRiskCount, tone: 'danger' },
          { label: '待人工复核', value: overview.summary.lowConfidenceCount, tone: 'attention' },
          { label: '结果过期', value: overview.summary.staleCount, tone: 'attention' },
          { label: '高优先级', value: overview.summary.highPriorityCount },
        ]"
        summary="看板数字均来自当前范围内的正式风险与资料状态汇总，AI 仅据此辅助安排治理关注顺序。"
        suggestion="优先核对高风险、待人工复核和结果过期对象；AI 建议不改变正式风险评分。"
      />

      <AppFilterBar :loading="loading" @query="runQuery" @reset="resetScopeFilters">
        <AppFilterField kind="spatial">
          <SpatialObjectSelector :key="scopeSelectorRevision" v-model:community-id="scopeCommunityId" mode="community" @change="handleScopeSelection" />
        </AppFilterField>
        <AppFilterField kind="status">
          <el-select v-model="riskLevelFilter" clearable placeholder="全部风险等级">
            <el-option label="极高风险" value="VERY_HIGH" /><el-option label="高风险" value="HIGH" /><el-option label="中风险" value="MEDIUM" /><el-option label="低风险" value="LOW" />
          </el-select>
        </AppFilterField>
        <AppFilterField kind="type">
          <el-select v-model="priorityFilter" clearable placeholder="全部优先级">
            <el-option v-for="value in ['P1', 'P2', 'P3', 'P4']" :key="value" :label="value" :value="value" />
          </el-select>
        </AppFilterField>
        <AppFilterField kind="status">
          <el-select v-model="freshnessFilter" clearable placeholder="全部数据状态">
            <el-option label="当前有效" value="CURRENT" /><el-option label="结果过期" value="STALE" /><el-option label="暂无结果" value="NO_RESULT" />
          </el-select>
        </AppFilterField>
      </AppFilterBar>

      <div class="summary-grid">
        <el-card v-for="card in summaryCards" :key="card.label" shadow="never" class="summary-card" :data-tone="card.tone"><span>{{ card.label }}</span><strong>{{ card.value }}</strong></el-card>
      </div>

      <el-card class="surface-card data-card" shadow="never">
        <template #header><div class="card-head"><div><strong>高风险楼栋排行</strong><small>按当前小区与风险条件筛选</small></div><span>{{ filteredTopRiskBuildings.length }} 栋</span></div></template>
        <el-table v-if="pagedTopRiskBuildings.length" :data="pagedTopRiskBuildings" stripe>
          <el-table-column prop="communityName" label="小区" min-width="150" show-overflow-tooltip />
          <el-table-column prop="buildingName" label="楼栋" min-width="135" show-overflow-tooltip />
          <el-table-column label="风险" width="108"><template #default="scope"><AppStatusTag :status="scope.row.riskLevel || 'NO_RESULT'" variant="risk" /></template></el-table-column>
          <el-table-column label="风险分" width="90" align="right"><template #default="scope">{{ score(scope.row.riskScore) }}</template></el-table-column>
          <el-table-column prop="priorityLevel" label="优先级" width="90" />
          <el-table-column prop="freshness" label="数据状态" width="110" />
          <el-table-column label="操作" width="160" fixed="right"><template #default="scope"><AppActionButton @click="openPreview(scope.row.buildingId)">预览</AppActionButton><AppActionButton type="primary" :loading="generatingBuildingId === scope.row.buildingId" @click="generate(scope.row.buildingId)">生成</AppActionButton></template></el-table-column>
        </el-table>
        <AppEmpty v-else description="当前条件没有匹配的高风险楼栋" />
        <AppTablePager v-model:page="topRiskPage" v-model:page-size="topRiskPageSize" :total="filteredTopRiskBuildings.length" />
      </el-card>

      <el-card class="surface-card analysis-card" shadow="never">
        <template #header><div class="card-head"><div><strong>楼栋正式风险与 AI 解读</strong><small>先查看确定性评分事实，再按需生成 AI 自然语言解释</small></div></div></template>
        <div class="analysis-selector">
          <SpatialObjectSelector :key="analysisSelectorRevision" v-model:community-id="analysisCommunityId" v-model:building-id="analysisBuildingId" mode="building" @change="handleAnalysisSelection" />
        </div>
        <div v-if="selectedAnalysisBuilding" class="analysis-grid">
          <div><span>楼栋</span><strong>{{ selectedAnalysisBuilding.communityName }} · {{ selectedAnalysisBuilding.buildingName }}</strong></div>
          <div><span>风险等级</span><AppStatusTag :status="selectedAnalysisBuilding.riskLevel || 'NO_RESULT'" variant="risk" /></div>
          <div><span>风险分</span><strong>{{ score(selectedAnalysisBuilding.riskScore) }}</strong></div>
          <div><span>更新优先级</span><strong>{{ selectedAnalysisBuilding.priorityLevel || '—' }}</strong></div>
          <div><span>数据状态</span><strong>{{ selectedAnalysisBuilding.freshness || '—' }}</strong></div>
          <div><span>空间位置</span><strong>{{ selectedAnalysisBuilding.longitude == null ? '未定位' : `${selectedAnalysisBuilding.longitude}, ${selectedAnalysisBuilding.latitude}` }}</strong></div>
        </div>
        <AppEmpty v-else description="选择楼栋后查看正式风险与 AI 风险解读" />

        <div v-if="selectedAnalysisBuilding" class="interpretation-grid">
          <section class="formal-score-card">
            <header><strong>正式评分因子</strong><el-tag type="success" effect="plain" round>确定性规则</el-tag></header>
            <p>风险等级、风险分和更新优先级均来自现有正式评估链路，AI 不参与改分。</p>
            <dl>
              <div><dt>风险等级</dt><dd>{{ selectedAnalysisBuilding.riskLevel || '暂无结果' }}</dd></div>
              <div><dt>风险分</dt><dd>{{ score(selectedAnalysisBuilding.riskScore) }}</dd></div>
              <div><dt>更新优先级</dt><dd>{{ selectedAnalysisBuilding.priorityLevel || '—' }}</dd></div>
              <div><dt>资料新鲜度</dt><dd>{{ selectedAnalysisBuilding.freshness || '—' }}</dd></div>
            </dl>
          </section>

          <div class="ai-interpretation-card">
            <AiInsightCard title="AI 风险解读" :summary="interpretationSummary" suggestion="AI 仅解释已有正式评分，不修改正式风险分数。">
              <el-alert v-if="riskInterpretationError" :title="riskInterpretationError" type="warning" :closable="false" show-icon />
              <template #actions>
                <el-button type="primary" plain :loading="riskInterpretationLoading" @click="runRiskInterpretation">{{ riskInterpretation ? '刷新 AI 解读' : '生成 AI 解读' }}</el-button>
              </template>
            </AiInsightCard>
          </div>
        </div>

        <div v-if="analysisBuildingId" class="analysis-actions"><el-button @click="openPreview(analysisBuildingId)">预览报告</el-button><el-button type="primary" :loading="generatingBuildingId === analysisBuildingId" @click="generate(analysisBuildingId)">生成报告</el-button></div>
      </el-card>

      <el-card class="surface-card report-card" shadow="never">
        <template #header>
          <div class="card-head">
            <div><strong>历史楼栋报告</strong><small>已生成报告支持直接下载 PDF；新生成报告默认自动下载。</small></div>
            <div class="report-head-actions">
              <label class="auto-download-control"><span>生成后自动下载</span><el-switch v-model="autoDownloadAfterGenerate" /></label>
              <el-select v-model="filters.reportStatus" clearable placeholder="全部状态" style="width: 140px" @change="filters.reportPage = 1; load()"><el-option label="生成中" value="GENERATING" /><el-option label="已生成" value="GENERATED" /><el-option label="失败" value="FAILED" /><el-option label="已过期" value="STALE" /></el-select>
              <span>{{ reportTotal }} 份</span>
            </div>
          </div>
        </template>
        <el-table v-if="reports.length" :data="reports" stripe>
          <el-table-column prop="reportCode" label="报告编号" min-width="190" show-overflow-tooltip /><el-table-column prop="communityName" label="小区" min-width="130" show-overflow-tooltip /><el-table-column prop="buildingName" label="楼栋" min-width="115" show-overflow-tooltip /><el-table-column prop="riskLevel" label="风险等级" width="105" /><el-table-column prop="priorityLevel" label="优先级" width="90" /><el-table-column prop="reportStatus" label="状态" width="105" />
          <el-table-column label="操作" width="92" fixed="right"><template #default="scope"><AppActionButton :disabled="!['GENERATED', 'STALE'].includes(scope.row.reportStatus)" @click="download(scope.row)">下载</AppActionButton></template></el-table-column>
        </el-table>
        <AppEmpty v-else description="暂无历史楼栋报告" /><AppTablePager v-model:page="filters.reportPage" v-model:page-size="filters.reportSize" :total="reportTotal" @change="load" />
      </el-card>
    </template>

    <el-drawer v-model="previewVisible" title="楼栋报告预览" size="min(760px, 92vw)"><div v-loading="previewLoading"><template v-if="preview"><el-descriptions :column="1" border><el-descriptions-item label="楼栋">{{ preview.communityName }} · {{ preview.buildingName }}</el-descriptions-item><el-descriptions-item label="数据状态">{{ preview.freshness }}</el-descriptions-item><el-descriptions-item label="模板版本">{{ preview.templateVersion }}</el-descriptions-item></el-descriptions><div v-if="preview.warnings.length" class="warning-list"><p v-for="warning in preview.warnings" :key="warning">{{ warning }}</p></div><pre class="snapshot">{{ JSON.stringify(preview.sections, null, 2) }}</pre><el-button type="primary" :loading="generatingBuildingId === preview.buildingId" @click="generate(preview.buildingId)">生成 PDF 报告</el-button></template></div></el-drawer>
  </section>
</template>

<style scoped lang="scss">
.risk-page{display:grid;gap:14px}.surface-card,.summary-card{border-radius:var(--usp-radius-xl);box-shadow:var(--usp-shadow-sm)}.analysis-selector{min-width:min(100%,430px)}.summary-grid{display:grid;grid-template-columns:repeat(6,minmax(120px,1fr));gap:10px}.summary-card :deep(.el-card__body){display:grid;gap:5px;padding:12px 14px}.summary-card span,.analysis-grid span{color:var(--usp-color-text-secondary);font-size:11px;font-weight:700}.summary-card strong{font-size:26px;line-height:1}.summary-card[data-tone='danger'] strong{color:var(--usp-color-danger)}.summary-card[data-tone='warning'] strong{color:#b36b00}.summary-card[data-tone='muted'] strong{color:var(--usp-color-text-secondary)}.surface-card :deep(.el-card__body){display:grid;gap:12px}.card-head{display:flex;align-items:center;justify-content:space-between;gap:12px}.card-head>div:first-child{display:grid;gap:2px}.card-head small{color:var(--usp-color-text-secondary)}.analysis-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px}.analysis-grid>div{display:grid;min-height:72px;align-content:center;gap:5px;padding:11px 13px;border:1px solid var(--usp-color-border);border-radius:var(--usp-radius-xl);background:var(--usp-color-surface-muted)}.interpretation-grid{display:grid;grid-template-columns:minmax(280px,.8fr) minmax(0,1.2fr);gap:14px;align-items:stretch}.formal-score-card{display:grid;gap:11px;padding:16px;border:1px solid var(--usp-color-border);border-radius:var(--usp-radius-xl);background:var(--usp-color-surface)}.formal-score-card header{display:flex;align-items:center;justify-content:space-between;gap:10px}.formal-score-card p{margin:0;color:var(--usp-color-text-secondary);line-height:1.6}.formal-score-card dl{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px;margin:0}.formal-score-card dl>div{padding:9px 10px;border-radius:var(--usp-radius-lg);background:var(--usp-color-surface-muted)}.formal-score-card dt{color:var(--usp-color-text-secondary);font-size:11px}.formal-score-card dd{margin:4px 0 0;font-weight:700}.ai-interpretation-card{min-width:0}.analysis-actions{display:flex;justify-content:flex-end;gap:8px}.report-head-actions{display:flex!important;align-items:center;justify-content:flex-end;flex-wrap:wrap;gap:10px!important}.auto-download-control{display:flex;align-items:center;gap:7px;color:var(--usp-color-text-secondary);font-size:12px;font-weight:700}.warning-list{display:grid;gap:8px}.warning-list p{margin:0;padding:10px 12px;border-radius:var(--usp-radius-lg);background:#fffaeb}.snapshot{max-height:360px;overflow:auto;padding:12px;border-radius:var(--usp-radius-xl);background:#101828;color:#f2f4f7}.risk-page :deep(.el-input__wrapper),.risk-page :deep(.el-select__wrapper),.risk-page :deep(.el-button),.risk-page :deep(.el-table){border-radius:var(--usp-radius-lg)}@media(max-width:1100px){.summary-grid{grid-template-columns:repeat(3,1fr)}.analysis-grid{grid-template-columns:repeat(2,1fr)}.interpretation-grid{grid-template-columns:1fr}}@media(max-width:720px){.summary-grid,.analysis-grid{grid-template-columns:1fr 1fr}.analysis-selector{min-width:100%;width:100%}.formal-score-card dl{grid-template-columns:1fr}.card-head{align-items:flex-start;flex-direction:column}.report-head-actions{width:100%;justify-content:space-between}}
</style>
