<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  downloadRiskReport,
  generateBuildingRiskReport,
  getRiskMap,
  getRiskOverview,
  listRiskReports,
  previewBuildingRiskReport,
  type DashboardBuilding,
  type RiskOverview,
  type RiskReportPreview,
  type RiskReportRow,
  type RiskScopeType,
} from '@/shared/api'
import { toAppError } from '@/shared/api'
import AppError from '@/shared/components/AppError.vue'
import AppEmpty from '@/shared/components/AppEmpty.vue'
import AppStatusTag from '@/shared/components/AppStatusTag.vue'

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

const filters = reactive({
  scopeType: 'ALL' as RiskScopeType,
  scopeId: '',
  reportStatus: '',
  reportPage: 0,
  reportSize: 10,
})

const summaryCards = computed(() => {
  const summary = overview.value?.summary
  return [
    ['覆盖楼栋', summary?.buildingCount ?? 0],
    ['已评分楼栋', summary?.assessedBuildingCount ?? 0],
    ['高风险楼栋', summary?.highRiskCount ?? 0],
    ['高优先级', summary?.highPriorityCount ?? 0],
    ['待人工复核', summary?.lowConfidenceCount ?? 0],
    ['无评分结果', summary?.noResultCount ?? 0],
  ]
})

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const scopeId = filters.scopeType === 'ALL' ? undefined : filters.scopeId.trim() || undefined
    const [overviewData, mapData, reportPage] = await Promise.all([
      getRiskOverview(filters.scopeType, scopeId),
      getRiskMap(filters.scopeType, scopeId),
      listRiskReports({
        status: filters.reportStatus || undefined,
        page: filters.reportPage,
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

async function openPreview(buildingId: string): Promise<void> {
  previewVisible.value = true
  previewLoading.value = true
  preview.value = null
  try {
    preview.value = await previewBuildingRiskReport(buildingId)
  } catch (error) {
    previewVisible.value = false
    ElMessage.error(toAppError(error).message)
  } finally {
    previewLoading.value = false
  }
}

async function generate(buildingId: string, force = false): Promise<void> {
  generatingBuildingId.value = buildingId
  try {
    const result = await generateBuildingRiskReport(buildingId, force)
    ElMessage.success(result.reused ? `已复用报告 ${result.reportCode}` : `报告 ${result.reportCode} 已生成`)
    await load()
  } catch (error) {
    ElMessage.error(toAppError(error).message)
  } finally {
    generatingBuildingId.value = ''
  }
}

async function download(report: RiskReportRow): Promise<void> {
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
  } catch (error) {
    ElMessage.error(toAppError(error).message)
  }
}

function score(value?: number): string {
  return value == null ? '—' : value.toFixed(2)
}

onMounted(load)
</script>

<template>
  <section v-loading="loading" class="page">
    <header class="page-head">
      <div>
        <p class="eyebrow">Risk Overview & Building Reports</p>
        <h1>风险总览与楼栋报告</h1>
        <p>页面直接读取第五阶段正式总览、地图点位和报告接口，不使用 Mock 数据。</p>
      </div>
      <el-button type="primary" @click="load">刷新</el-button>
    </header>

    <AppError v-if="errorMessage" :message="errorMessage" @retry="load" />
    <template v-else>
      <el-alert
        :title="overview?.disclaimer || '系统结果仅用于风险筛查与辅助决策，不作为正式房屋安全鉴定结论。'"
        type="warning"
        :closable="false"
        show-icon
      />

      <el-card shadow="never">
        <el-form inline class="filters" @submit.prevent="load">
          <el-form-item label="范围">
            <el-select v-model="filters.scopeType" style="width: 150px">
              <el-option label="全部" value="ALL" />
              <el-option label="行政区域" value="REGION" />
              <el-option label="小区" value="COMMUNITY" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="filters.scopeType !== 'ALL'" label="范围编号">
            <el-input v-model="filters.scopeId" placeholder="请输入区域或小区编号" style="width: 280px" />
          </el-form-item>
          <el-form-item label="报告状态">
            <el-select v-model="filters.reportStatus" clearable style="width: 150px">
              <el-option label="生成中" value="GENERATING" />
              <el-option label="已生成" value="GENERATED" />
              <el-option label="失败" value="FAILED" />
              <el-option label="已过期" value="STALE" />
            </el-select>
          </el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
        </el-form>
      </el-card>

      <div class="summary-grid">
        <el-card v-for="card in summaryCards" :key="String(card[0])" shadow="never" class="summary-card">
          <span>{{ card[0] }}</span>
          <strong>{{ card[1] }}</strong>
        </el-card>
      </div>

      <el-row :gutter="18">
        <el-col :xs="24" :xl="12">
          <el-card shadow="never">
            <template #header><strong>高风险楼栋</strong></template>
            <el-table v-if="overview?.topRiskBuildings.length" :data="overview.topRiskBuildings" stripe>
              <el-table-column prop="communityName" label="小区" min-width="140" />
              <el-table-column prop="buildingName" label="楼栋" min-width="130" />
              <el-table-column label="风险" width="110">
                <template #default="scope"><AppStatusTag :status="scope.row.riskLevel || 'NO_RESULT'" variant="risk" /></template>
              </el-table-column>
              <el-table-column label="风险分" width="90"><template #default="scope">{{ score(scope.row.riskScore) }}</template></el-table-column>
              <el-table-column label="操作" width="150">
                <template #default="scope">
                  <el-button link type="primary" @click="openPreview(scope.row.buildingId)">预览</el-button>
                  <el-button link type="primary" :loading="generatingBuildingId === scope.row.buildingId" @click="generate(scope.row.buildingId)">生成报告</el-button>
                </template>
              </el-table-column>
            </el-table>
            <AppEmpty v-else description="当前范围没有已评分的高风险楼栋" />
          </el-card>
        </el-col>

        <el-col :xs="24" :xl="12">
          <el-card shadow="never">
            <template #header><strong>楼栋风险点位</strong></template>
            <el-table v-if="mapBuildings.length" :data="mapBuildings.slice(0, 20)" stripe max-height="420">
              <el-table-column prop="communityName" label="小区" min-width="130" />
              <el-table-column prop="buildingName" label="楼栋" min-width="120" />
              <el-table-column label="坐标" min-width="170">
                <template #default="scope">
                  {{ scope.row.longitude == null ? '未定位' : `${scope.row.longitude}, ${scope.row.latitude}` }}
                </template>
              </el-table-column>
              <el-table-column prop="freshness" label="数据状态" width="110" />
            </el-table>
            <AppEmpty v-else description="当前范围没有楼栋风险点位" />
          </el-card>
        </el-col>
      </el-row>

      <el-card shadow="never">
        <template #header><div class="card-head"><strong>楼栋报告中心</strong><span>{{ reportTotal }} 份</span></div></template>
        <el-table v-if="reports.length" :data="reports" stripe>
          <el-table-column prop="reportCode" label="报告编号" min-width="210" />
          <el-table-column prop="communityName" label="小区" min-width="130" />
          <el-table-column prop="buildingName" label="楼栋" min-width="120" />
          <el-table-column prop="riskLevel" label="风险等级" width="110" />
          <el-table-column prop="priorityLevel" label="更新优先级" width="120" />
          <el-table-column prop="reportStatus" label="状态" width="110" />
          <el-table-column label="操作" width="110">
            <template #default="scope">
              <el-button
                link
                type="primary"
                :disabled="!['GENERATED', 'STALE'].includes(scope.row.reportStatus)"
                @click="download(scope.row)"
              >下载</el-button>
            </template>
          </el-table-column>
        </el-table>
        <AppEmpty v-else description="暂无已生成楼栋报告，可从上方楼栋生成" />
      </el-card>
    </template>

    <el-drawer v-model="previewVisible" title="楼栋报告预览" size="min(760px, 92vw)">
      <div v-loading="previewLoading">
        <template v-if="preview">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="楼栋">{{ preview.communityName }} · {{ preview.buildingName }}</el-descriptions-item>
            <el-descriptions-item label="数据状态">{{ preview.freshness }}</el-descriptions-item>
            <el-descriptions-item label="模板版本">{{ preview.templateVersion }}</el-descriptions-item>
            <el-descriptions-item label="数据摘要">{{ preview.sourceChecksum }}</el-descriptions-item>
          </el-descriptions>
          <el-alert v-for="warning in preview.warnings" :key="warning" :title="warning" type="warning" :closable="false" class="preview-warning" />
          <pre class="snapshot">{{ JSON.stringify(preview.sections, null, 2) }}</pre>
          <el-button type="primary" :loading="generatingBuildingId === preview.buildingId" @click="generate(preview.buildingId)">生成正式 PDF 报告</el-button>
        </template>
      </div>
    </el-drawer>
  </section>
</template>

<style scoped lang="scss">
.page { display: grid; gap: 18px; }
.page-head { display: flex; justify-content: space-between; align-items: flex-end; gap: 18px; }
.page-head h1 { margin: 4px 0; font-size: 34px; }
.page-head p:last-child { margin: 0; color: #667085; }
.eyebrow { margin: 0; color: #287a6a; font-size: 12px; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
.summary-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 12px; }
.summary-card :deep(.el-card__body) { display: grid; gap: 8px; }
.summary-card span { color: #667085; }
.summary-card strong { font-size: 30px; color: #173f37; }
.card-head { display: flex; justify-content: space-between; }
.card-head span { color: #667085; }
.preview-warning { margin-top: 12px; }
.snapshot { max-height: 420px; overflow: auto; padding: 14px; border-radius: 12px; background: #101828; color: #e4e7ec; font-size: 12px; }
@media (max-width: 720px) { .page-head { align-items: flex-start; flex-direction: column; } }
</style>
