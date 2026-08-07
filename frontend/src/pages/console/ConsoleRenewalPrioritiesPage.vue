<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import OfficialRankingTable from '@/components/assessment/OfficialRankingTable.vue'
import { apiGet, apiPost, httpClient } from '@/shared/api/client'
import {
  listRenewalPriorities,
  type RankingScopeType,
  type RenewalPriorityRow,
} from '@/shared/api'

interface DistributionBucket {
  code: string
  label: string
  count: number
}

interface DashboardBuilding {
  buildingId: string
  buildingCode: string
  buildingName: string
  communityId: string
  communityName: string
  longitude?: number
  latitude?: number
  riskScore?: number
  riskLevel?: string
  confidenceScore?: number
  completenessScore?: number
  priorityScore?: number
  priorityLevel?: string
  ranking?: number
  freshness: 'CURRENT' | 'STALE' | 'NO_RESULT'
  needManualReview?: boolean
}

interface RiskOverview {
  scopeKey: string
  generatedAt: string
  summary: {
    communityCount: number
    buildingCount: number
    assessedBuildingCount: number
    highRiskCount: number
    lowConfidenceCount: number
    highPriorityCount: number
    staleCount: number
    noResultCount: number
  }
  riskDistribution: DistributionBucket[]
  completenessDistribution: DistributionBucket[]
  priorityDistribution: DistributionBucket[]
  freshnessDistribution: DistributionBucket[]
  topRiskBuildings: DashboardBuilding[]
  topPriorityBuildings: DashboardBuilding[]
  reviewRequiredBuildings: DashboardBuilding[]
  disclaimer: string
}

interface RiskMapResponse {
  scopeKey: string
  generatedAt: string
  buildings: DashboardBuilding[]
  disclaimer: string
}

interface RiskReportRow {
  reportId: string
  reportCode: string
  buildingId: string
  buildingCode: string
  buildingName: string
  communityId: string
  communityName: string
  reportStatus: 'GENERATING' | 'GENERATED' | 'FAILED' | 'STALE'
  reportFormat: 'PDF'
  templateVersion: string
  sourceChecksum: string
  riskLevel?: string
  priorityLevel?: string
  generatedAt?: string
  createdAt: string
}

interface RiskReportPage {
  content: RiskReportRow[]
  page: { page: number; size: number; totalElements: number; totalPages: number }
}

interface RiskReportPreview {
  buildingId: string
  buildingCode: string
  buildingName: string
  communityName: string
  freshness: 'CURRENT' | 'STALE' | 'NO_RESULT'
  sourceChecksum: string
  templateVersion: string
  sections: {
    building?: Record<string, unknown>
    assessment?: {
      completeness?: Record<string, unknown>
      risk?: Record<string, unknown>
      renewalPriorities?: Array<Record<string, unknown>>
    }
    inspections?: Array<Record<string, unknown>>
    evidence?: Array<Record<string, unknown>>
    aiEvidence?: Array<Record<string, unknown>>
  }
  warnings: string[]
  disclaimer: string
}

interface RiskReportGeneration {
  reportId: string
  reportCode: string
  reportStatus: string
  reportFormat: string
  templateVersion: string
  sourceChecksum: string
  reused: boolean
  generatedAt?: string
}

const router = useRouter()
const loading = ref(false)
const generatingBuildingId = ref('')
const rows = ref<RenewalPriorityRow[]>([])
const overview = ref<RiskOverview | null>(null)
const mapRows = ref<DashboardBuilding[]>([])
const reports = ref<RiskReportRow[]>([])
const reportTotal = ref(0)
const previewVisible = ref(false)
const previewLoading = ref(false)
const preview = ref<RiskReportPreview | null>(null)
const degraded = ref(false)

const filters = reactive({
  scopeType: 'ALL' as RankingScopeType,
  scopeId: '',
  priorityLevel: '',
  riskLevel: '',
  page: 0,
  size: 20,
  reportPage: 0,
  reportSize: 10,
  reportStatus: '',
})

const disclaimer = computed(
  () => overview.value?.disclaimer || '系统结果仅用于风险筛查与辅助决策，不作为正式房屋安全鉴定结论。',
)

const riskAssessment = computed(() => preview.value?.sections.assessment?.risk || {})
const completenessAssessment = computed(() => preview.value?.sections.assessment?.completeness || {})
const priorityAssessment = computed(
  () => preview.value?.sections.assessment?.renewalPriorities?.[0] || {},
)

const coordinateRows = computed(() =>
  mapRows.value.filter((item) => item.longitude != null && item.latitude != null),
)

const mapBounds = computed(() => {
  const points = coordinateRows.value
  if (!points.length) return { minX: 0, maxX: 1, minY: 0, maxY: 1 }
  const xs = points.map((item) => Number(item.longitude))
  const ys = points.map((item) => Number(item.latitude))
  const minX = Math.min(...xs)
  const maxX = Math.max(...xs)
  const minY = Math.min(...ys)
  const maxY = Math.max(...ys)
  return {
    minX,
    maxX: maxX === minX ? minX + 0.001 : maxX,
    minY,
    maxY: maxY === minY ? minY + 0.001 : maxY,
  }
})

function pointX(item: DashboardBuilding): number {
  const { minX, maxX } = mapBounds.value
  return 40 + ((Number(item.longitude) - minX) / (maxX - minX)) * 520
}

function pointY(item: DashboardBuilding): number {
  const { minY, maxY } = mapBounds.value
  return 280 - ((Number(item.latitude) - minY) / (maxY - minY)) * 240
}

function riskClass(level?: string): string {
  if (level === 'VERY_HIGH') return 'risk-very-high'
  if (level === 'HIGH') return 'risk-high'
  if (level === 'MEDIUM') return 'risk-medium'
  if (level === 'LOW') return 'risk-low'
  return 'risk-none'
}

function score(value: unknown): string {
  return value == null || value === '' ? '--' : Number(value).toFixed(2)
}

function countBy<T>(items: T[], predicate: (item: T) => boolean): number {
  return items.filter(predicate).length
}

function distribution(
  items: RenewalPriorityRow[],
  values: string[],
  labels: string[],
  field: 'riskLevel' | 'priorityLevel',
): DistributionBucket[] {
  return values.map((code, index) => ({
    code,
    label: labels[index],
    count: countBy(items, (item) => item[field] === code),
  }))
}

function deriveOverview(ranking: RenewalPriorityRow[]): RiskOverview {
  const mapped: DashboardBuilding[] = ranking.map((item) => ({
    buildingId: item.buildingId,
    buildingCode: item.buildingCode,
    buildingName: item.buildingName,
    communityId: item.communityId,
    communityName: item.communityName,
    riskScore: item.riskScore,
    riskLevel: item.riskLevel,
    confidenceScore: item.confidenceScore,
    completenessScore: item.completenessScore,
    priorityScore: item.priorityScore,
    priorityLevel: item.priorityLevel,
    ranking: item.ranking,
    freshness: item.status === 'STALE' ? 'STALE' : 'CURRENT',
    needManualReview: item.needManualReview,
  }))
  return {
    scopeKey: filters.scopeType === 'ALL' ? 'ALL' : `${filters.scopeType}:${filters.scopeId}`,
    generatedAt: new Date().toISOString(),
    summary: {
      communityCount: new Set(mapped.map((item) => item.communityId)).size,
      buildingCount: mapped.length,
      assessedBuildingCount: mapped.length,
      highRiskCount: countBy(mapped, (item) => ['HIGH', 'VERY_HIGH'].includes(item.riskLevel || '')),
      lowConfidenceCount: countBy(mapped, (item) => Number(item.confidenceScore || 0) < 60),
      highPriorityCount: countBy(mapped, (item) => ['P1', 'P2'].includes(item.priorityLevel || '')),
      staleCount: countBy(mapped, (item) => item.freshness === 'STALE'),
      noResultCount: 0,
    },
    riskDistribution: distribution(ranking, ['LOW', 'MEDIUM', 'HIGH', 'VERY_HIGH'], ['低', '中', '高', '很高'], 'riskLevel'),
    completenessDistribution: [
      { code: 'INSUFFICIENT', label: '不足', count: countBy(mapped, (item) => Number(item.completenessScore || 0) < 40) },
      { code: 'LIMITED', label: '有限', count: countBy(mapped, (item) => Number(item.completenessScore || 0) >= 40 && Number(item.completenessScore || 0) < 60) },
      { code: 'GOOD', label: '良好', count: countBy(mapped, (item) => Number(item.completenessScore || 0) >= 60 && Number(item.completenessScore || 0) < 80) },
      { code: 'EXCELLENT', label: '优秀', count: countBy(mapped, (item) => Number(item.completenessScore || 0) >= 80) },
    ],
    priorityDistribution: distribution(ranking, ['P1', 'P2', 'P3', 'P4'], ['优先一', '优先二', '优先三', '优先四'], 'priorityLevel'),
    freshnessDistribution: [
      { code: 'CURRENT', label: '当前', count: countBy(mapped, (item) => item.freshness === 'CURRENT') },
      { code: 'STALE', label: '已过期', count: countBy(mapped, (item) => item.freshness === 'STALE') },
      { code: 'NO_RESULT', label: '无结果', count: 0 },
    ],
    topRiskBuildings: [...mapped].sort((a, b) => Number(b.riskScore || 0) - Number(a.riskScore || 0)).slice(0, 10),
    topPriorityBuildings: [...mapped].sort((a, b) => Number(a.ranking || 999999) - Number(b.ranking || 999999)).slice(0, 10),
    reviewRequiredBuildings: mapped.filter((item) => item.needManualReview || Number(item.confidenceScore || 0) < 60).slice(0, 10),
    disclaimer: '当前处于 Mock 降级展示：总览由服务端正式排行榜推导，地图和报告功能需要第五阶段后端。',
  }
}

async function load(): Promise<void> {
  loading.value = true
  degraded.value = false
  try {
    const scopeId = filters.scopeType === 'ALL' ? undefined : filters.scopeId || undefined
    const ranking = await listRenewalPriorities({
      scopeType: filters.scopeType,
      scopeId,
      priorityLevel: filters.priorityLevel || undefined,
      riskLevel: filters.riskLevel || undefined,
      page: filters.page,
      size: filters.size,
    })
    rows.value = ranking.content

    const [overviewResult, mapResult, reportResult] = await Promise.allSettled([
      apiGet<RiskOverview>('/api/v1/dashboard/risk-overview', {
        scopeType: filters.scopeType,
        scopeId,
      }),
      apiGet<RiskMapResponse>('/api/v1/dashboard/risk-map', {
        scopeType: filters.scopeType,
        scopeId,
      }),
      apiGet<RiskReportPage>('/api/v1/risk-reports', {
        status: filters.reportStatus || undefined,
        page: filters.reportPage,
        size: filters.reportSize,
      }),
    ])

    if (overviewResult.status === 'fulfilled') {
      overview.value = overviewResult.value
    } else {
      overview.value = deriveOverview(ranking.content)
      degraded.value = true
    }
    if (mapResult.status === 'fulfilled') {
      mapRows.value = mapResult.value.buildings
    } else {
      mapRows.value = overview.value.topRiskBuildings
      degraded.value = true
    }
    if (reportResult.status === 'fulfilled') {
      reports.value = reportResult.value.content
      reportTotal.value = reportResult.value.page.totalElements
    } else {
      reports.value = []
      reportTotal.value = 0
      degraded.value = true
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '风险总览加载失败')
  } finally {
    loading.value = false
  }
}

function search(): void {
  filters.page = 0
  void load()
}

async function openPreview(buildingId: string): Promise<void> {
  previewVisible.value = true
  previewLoading.value = true
  preview.value = null
  try {
    preview.value = await apiGet<RiskReportPreview>(`/api/v1/risk-reports/buildings/${buildingId}/preview`)
  } catch (error) {
    previewVisible.value = false
    ElMessage.error(error instanceof Error ? error.message : '报告预览加载失败')
  } finally {
    previewLoading.value = false
  }
}

async function generateReport(buildingId: string, force = false): Promise<void> {
  generatingBuildingId.value = buildingId
  try {
    const result = await apiPost<RiskReportGeneration>(
      `/api/v1/risk-reports/buildings/${buildingId}/generate`,
      { force, includeEvidenceImages: true },
    )
    ElMessage.success(result.reused ? `已复用报告 ${result.reportCode}` : `报告 ${result.reportCode} 已生成`)
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '报告生成失败')
  } finally {
    generatingBuildingId.value = ''
  }
}

async function downloadReport(report: RiskReportRow): Promise<void> {
  try {
    const response = await httpClient.get<Blob>(`/api/v1/risk-reports/${report.reportId}/download`, {
      responseType: 'blob',
    })
    const url = URL.createObjectURL(response.data)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `${report.reportCode}.pdf`
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    URL.revokeObjectURL(url)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '报告下载失败')
  }
}

function maxCount(items: DistributionBucket[]): number {
  return Math.max(1, ...items.map((item) => item.count))
}

function barWidth(item: DistributionBucket, items: DistributionBucket[]): string {
  return `${Math.max(4, (item.count / maxCount(items)) * 100)}%`
}

onMounted(load)
</script>

<template>
  <section v-loading="loading" class="risk-overview-page">
    <header class="page-header">
      <div>
        <p class="eyebrow">Risk Overview & Reports</p>
        <h1>风险总览与楼栋报告</h1>
        <p>从区域指标下钻到正式排名、楼栋评分和可追溯报告。</p>
      </div>
      <el-button type="primary" @click="load">刷新总览</el-button>
    </header>

    <el-alert :title="disclaimer" type="warning" :closable="false" show-icon />
    <el-alert
      v-if="degraded"
      title="部分第五阶段接口不可用，页面已使用现有正式排行榜进行降级展示。"
      type="info"
      :closable="false"
      show-icon
    />

    <el-card shadow="never">
      <el-form inline class="filters" @submit.prevent="search">
        <el-form-item label="统计范围">
          <el-select v-model="filters.scopeType" style="width: 150px">
            <el-option label="全部楼栋" value="ALL" />
            <el-option label="行政区域" value="REGION" />
            <el-option label="社区" value="COMMUNITY" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="filters.scopeType !== 'ALL'" label="范围标识">
          <el-input v-model="filters.scopeId" placeholder="区域名称或社区 UUID" style="width: 260px" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="filters.priorityLevel" clearable style="width: 120px">
            <el-option v-for="level in ['P1', 'P2', 'P3', 'P4']" :key="level" :label="level" :value="level" />
          </el-select>
        </el-form-item>
        <el-form-item label="风险等级">
          <el-select v-model="filters.riskLevel" clearable style="width: 150px">
            <el-option label="低" value="LOW" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="高" value="HIGH" />
            <el-option label="很高" value="VERY_HIGH" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" @click="search">查询</el-button></el-form-item>
      </el-form>
    </el-card>

    <div v-if="overview" class="metric-grid">
      <el-card shadow="never"><span>小区总数</span><strong>{{ overview.summary.communityCount }}</strong></el-card>
      <el-card shadow="never"><span>楼栋总数</span><strong>{{ overview.summary.buildingCount }}</strong></el-card>
      <el-card shadow="never"><span>已评分楼栋</span><strong>{{ overview.summary.assessedBuildingCount }}</strong></el-card>
      <el-card shadow="never" class="danger-metric"><span>高风险楼栋</span><strong>{{ overview.summary.highRiskCount }}</strong></el-card>
      <el-card shadow="never" class="warning-metric"><span>低置信度</span><strong>{{ overview.summary.lowConfidenceCount }}</strong></el-card>
      <el-card shadow="never"><span>高优先级</span><strong>{{ overview.summary.highPriorityCount }}</strong></el-card>
      <el-card shadow="never"><span>过期评分</span><strong>{{ overview.summary.staleCount }}</strong></el-card>
      <el-card shadow="never"><span>无评分结果</span><strong>{{ overview.summary.noResultCount }}</strong></el-card>
    </div>

    <div v-if="overview" class="distribution-grid">
      <el-card v-for="group in [
        { title: '风险等级分布', items: overview.riskDistribution },
        { title: '资料完整度分布', items: overview.completenessDistribution },
        { title: '更新优先级分布', items: overview.priorityDistribution },
        { title: '评分新鲜度分布', items: overview.freshnessDistribution },
      ]" :key="group.title" shadow="never">
        <template #header><strong>{{ group.title }}</strong></template>
        <div v-for="item in group.items" :key="item.code" class="bar-row">
          <span>{{ item.label }}</span>
          <div class="bar-track"><i :style="{ width: barWidth(item, group.items) }" /></div>
          <b>{{ item.count }}</b>
        </div>
      </el-card>
    </div>

    <div class="visual-grid">
      <el-card shadow="never">
        <template #header>
          <div class="card-header"><strong>风险空间点位</strong><span>坐标散点降级视图</span></div>
        </template>
        <svg v-if="coordinateRows.length" class="risk-map" viewBox="0 0 600 320" role="img" aria-label="楼栋风险空间点位">
          <rect x="0" y="0" width="600" height="320" rx="12" class="map-background" />
          <g v-for="item in coordinateRows" :key="item.buildingId" class="map-point" @click="router.push(`/console/buildings/${item.buildingId}/assessment`)">
            <circle :cx="pointX(item)" :cy="pointY(item)" r="9" :class="riskClass(item.riskLevel)" />
            <title>{{ item.communityName }} {{ item.buildingName }} · 风险 {{ score(item.riskScore) }}</title>
          </g>
        </svg>
        <el-empty v-else description="当前范围暂无可用楼栋坐标，仍可通过下方列表查看" :image-size="80" />
        <div class="legend">
          <span><i class="risk-low" />低</span><span><i class="risk-medium" />中</span><span><i class="risk-high" />高</span><span><i class="risk-very-high" />很高</span>
        </div>
      </el-card>

      <el-card shadow="never">
        <template #header><strong>需要复核的重点对象</strong></template>
        <el-table :data="overview?.reviewRequiredBuildings || []" size="small" max-height="360">
          <el-table-column prop="buildingName" label="楼栋" min-width="150" />
          <el-table-column prop="riskScore" label="风险" width="80" />
          <el-table-column prop="confidenceScore" label="置信度" width="90" />
          <el-table-column label="操作" width="90">
            <template #default="scope"><el-button link type="primary" @click="router.push(`/console/buildings/${scope.row.buildingId}/assessment`)">查看</el-button></template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>

    <el-card shadow="never">
      <template #header><div class="card-header"><strong>城市更新优先级正式排行榜</strong><span>严格使用服务端 ranking</span></div></template>
      <OfficialRankingTable
        :rows="rows"
        @open="(buildingId: string) => router.push(`/console/buildings/${buildingId}/assessment`)"
      />
      <el-pagination
        :current-page="filters.page + 1"
        v-model:page-size="filters.size"
        :page-sizes="[10, 20, 50, 100]"
        :total="overview?.summary.assessedBuildingCount || rows.length"
        layout="total, sizes, prev, pager, next"
        @current-change="(value: number) => { filters.page = value - 1; load() }"
        @size-change="() => { filters.page = 0; load() }"
      />
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <strong>高风险对象与报告操作</strong>
          <span>先预览源数据，再生成 PDF</span>
        </div>
      </template>
      <el-table :data="overview?.topRiskBuildings || []" size="small">
        <el-table-column prop="communityName" label="小区" min-width="150" />
        <el-table-column prop="buildingName" label="楼栋" min-width="150" />
        <el-table-column prop="riskScore" label="风险分" width="90" />
        <el-table-column prop="riskLevel" label="风险等级" width="100" />
        <el-table-column prop="confidenceScore" label="置信度" width="90" />
        <el-table-column prop="priorityLevel" label="优先级" width="90" />
        <el-table-column label="操作" min-width="230" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="openPreview(scope.row.buildingId)">预览报告</el-button>
            <el-button
              link
              type="success"
              :loading="generatingBuildingId === scope.row.buildingId"
              @click="generateReport(scope.row.buildingId)"
            >生成 PDF</el-button>
            <el-button link @click="router.push(`/console/buildings/${scope.row.buildingId}/assessment`)">评分详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <strong>报告中心</strong>
          <el-select v-model="filters.reportStatus" clearable placeholder="全部状态" style="width: 150px" @change="() => { filters.reportPage = 0; load() }">
            <el-option label="生成中" value="GENERATING" />
            <el-option label="已生成" value="GENERATED" />
            <el-option label="已失败" value="FAILED" />
            <el-option label="已过期" value="STALE" />
          </el-select>
        </div>
      </template>
      <el-table :data="reports" size="small">
        <el-table-column prop="reportCode" label="报告编号" min-width="210" />
        <el-table-column prop="communityName" label="小区" min-width="150" />
        <el-table-column prop="buildingName" label="楼栋" min-width="150" />
        <el-table-column prop="reportStatus" label="状态" width="100" />
        <el-table-column prop="riskLevel" label="风险" width="90" />
        <el-table-column prop="priorityLevel" label="优先级" width="90" />
        <el-table-column prop="templateVersion" label="模板版本" min-width="150" />
        <el-table-column prop="generatedAt" label="生成时间" min-width="180" />
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="openPreview(scope.row.buildingId)">查看源数据</el-button>
            <el-button
              link
              type="success"
              :disabled="!['GENERATED', 'STALE'].includes(scope.row.reportStatus)"
              @click="downloadReport(scope.row)"
            >下载</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        :current-page="filters.reportPage + 1"
        v-model:page-size="filters.reportSize"
        :page-sizes="[10, 20, 50]"
        :total="reportTotal"
        layout="total, sizes, prev, pager, next"
        @current-change="(value: number) => { filters.reportPage = value - 1; load() }"
        @size-change="() => { filters.reportPage = 0; load() }"
      />
    </el-card>

    <el-drawer v-model="previewVisible" title="楼栋风险报告预览" size="62%">
      <section v-loading="previewLoading" class="preview-panel">
        <template v-if="preview">
          <el-alert v-for="warning in preview.warnings" :key="warning" :title="warning" type="warning" :closable="false" show-icon />
          <div class="preview-title">
            <div><h2>{{ preview.buildingName }}</h2><p>{{ preview.communityName }} · {{ preview.buildingCode }}</p></div>
            <el-button
              type="primary"
              :loading="generatingBuildingId === preview.buildingId"
              @click="generateReport(preview.buildingId)"
            >生成或复用 PDF</el-button>
          </div>
          <div class="preview-score-grid">
            <el-card shadow="never"><span>资料完整度</span><strong>{{ score(completenessAssessment.completenessScore) }}</strong><small>{{ completenessAssessment.completenessLevel || '--' }}</small></el-card>
            <el-card shadow="never"><span>风险筛查</span><strong>{{ score(riskAssessment.riskScore) }}</strong><small>{{ riskAssessment.riskLevel || '--' }}</small></el-card>
            <el-card shadow="never"><span>判断置信度</span><strong>{{ score(riskAssessment.confidenceScore) }}</strong><small>证据充分程度</small></el-card>
            <el-card shadow="never"><span>更新优先级</span><strong>{{ score(priorityAssessment.priorityScore) }}</strong><small>{{ priorityAssessment.priorityLevel || '--' }}</small></el-card>
          </div>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="评分状态">{{ preview.freshness }}</el-descriptions-item>
            <el-descriptions-item label="模板版本">{{ preview.templateVersion }}</el-descriptions-item>
            <el-descriptions-item label="源数据摘要"><code>{{ preview.sourceChecksum }}</code></el-descriptions-item>
            <el-descriptions-item label="巡检记录">{{ preview.sections.inspections?.length || 0 }} 条</el-descriptions-item>
            <el-descriptions-item label="业务证据">{{ preview.sections.evidence?.length || 0 }} 条</el-descriptions-item>
            <el-descriptions-item label="人工智能证据">{{ preview.sections.aiEvidence?.length || 0 }} 条</el-descriptions-item>
          </el-descriptions>
          <el-alert :title="preview.disclaimer" type="warning" :closable="false" show-icon />
        </template>
      </section>
    </el-drawer>
  </section>
</template>

<style scoped lang="scss">
.risk-overview-page { display: grid; gap: 18px; }
.page-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 20px; }
.page-header h1 { margin: 4px 0; font-size: 32px; }
.page-header p { margin: 0; color: #667085; }
.eyebrow { color: #287a6a !important; font-size: 12px; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
.filters { display: flex; flex-wrap: wrap; }
.metric-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; }
.metric-grid :deep(.el-card__body) { display: grid; gap: 8px; }
.metric-grid span { color: #667085; }
.metric-grid strong { font-size: 34px; line-height: 1; }
.danger-metric strong { color: #b42318; }
.warning-metric strong { color: #b54708; }
.distribution-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; }
.bar-row { display: grid; grid-template-columns: 64px 1fr 36px; align-items: center; gap: 10px; margin: 12px 0; color: #475467; }
.bar-track { height: 10px; overflow: hidden; border-radius: 999px; background: #edf2f5; }
.bar-track i { display: block; height: 100%; border-radius: inherit; background: #287a6a; }
.visual-grid { display: grid; grid-template-columns: 1.3fr 1fr; gap: 16px; }
.card-header { display: flex; justify-content: space-between; align-items: center; gap: 16px; }
.card-header span { color: #667085; font-size: 13px; }
.risk-map { width: 100%; min-height: 320px; }
.map-background { fill: #f2f6f5; stroke: #d0ddd9; }
.map-point { cursor: pointer; }
.map-point circle, .legend i { stroke: #fff; stroke-width: 2; }
.risk-low { fill: #2e8b57; background: #2e8b57; }
.risk-medium { fill: #d99b22; background: #d99b22; }
.risk-high { fill: #d85d3f; background: #d85d3f; }
.risk-very-high { fill: #a61b1b; background: #a61b1b; }
.risk-none { fill: #98a2b3; background: #98a2b3; }
.legend { display: flex; flex-wrap: wrap; gap: 16px; margin-top: 10px; color: #667085; }
.legend span { display: flex; align-items: center; gap: 6px; }
.legend i { width: 12px; height: 12px; border-radius: 50%; }
.el-pagination { margin-top: 18px; justify-content: flex-end; }
.preview-panel { display: grid; gap: 18px; }
.preview-title { display: flex; justify-content: space-between; align-items: flex-start; gap: 20px; }
.preview-title h2 { margin: 0 0 6px; }
.preview-title p { margin: 0; color: #667085; }
.preview-score-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }
.preview-score-grid :deep(.el-card__body) { display: grid; gap: 8px; }
.preview-score-grid span, .preview-score-grid small { color: #667085; }
.preview-score-grid strong { font-size: 30px; }
code { overflow-wrap: anywhere; }
@media (max-width: 1200px) { .metric-grid, .distribution-grid, .preview-score-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 900px) { .visual-grid { grid-template-columns: 1fr; } }
@media (max-width: 700px) { .page-header, .preview-title { display: grid; } .metric-grid, .distribution-grid, .preview-score-grid { grid-template-columns: 1fr; } }
</style>
