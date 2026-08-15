<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { loadBuildingDetail, type BuildingDetailModel } from '@/features/building-detail/building-detail-loader'
import BuildingSummaryCard from '@/shared/components/business/BuildingSummaryCard.vue'
import BuildingLifecycleTimeline from '@/shared/components/business/BuildingLifecycleTimeline.vue'
import RiskSummaryPanel from '@/shared/components/business/RiskSummaryPanel.vue'
import EvidenceGallery from '@/shared/components/business/EvidenceGallery.vue'
import AiInsightCard from '@/shared/components/ai/AiInsightCard.vue'
import AiEvidencePanel from '@/shared/components/ai/AiEvidencePanel.vue'
import AppPageHeader from '@/shared/components/layout/AppPageHeader.vue'
import { formatAiDetectionLabel } from '@/shared/ai/ai-display'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const errorMessage = ref('')
const model = ref<BuildingDetailModel | null>(null)
const buildingId = computed(() => String(route.params.buildingId ?? ''))
const activeTab = ref(normalizeTab(route.query.tab))

const latestAnalysis = computed(() => {
  const items = model.value?.analyses ?? []
  return [...items].sort((left, right) => analysisTime(right) - analysisTime(left))[0] ?? null
})

const aiFindings = computed(() => {
  const items = model.value?.analyses ?? []
  const labels: string[] = []
  for (const task of items) {
    const detections = task.structuredResult?.detections?.length ? task.structuredResult.detections : task.detections ?? []
    for (const detection of detections) {
      const name = detection.className?.trim()
      if (!name) continue
      labels.push(formatAiDetectionLabel(name, detection.confidence))
    }
  }
  return labels.slice(0, 8)
})

const aiSummary = computed(() =>
  latestAnalysis.value?.structuredResult?.summary
  || latestAnalysis.value?.summary?.summary
  || latestAnalysis.value?.assessmentNote
  || '当前暂无可展示的 AI 综合研判结果，可继续通过巡检补充现场证据。',
)

const aiSuggestion = computed(() =>
  latestAnalysis.value?.structuredResult?.recommendations?.[0]
  || model.value?.risk.recommendations?.[0]
  || (aiFindings.value.length ? '建议优先核对最近视觉发现，并结合人工专业复核结果推进治理。' : '建议按既有巡检计划持续更新现场证据。'),
)

const aiAttentionLabel = computed(() => {
  const risk = model.value?.risk.riskLevel
  const hasUnreviewed = (model.value?.analyses ?? []).some((item) => item.reviewStatus === 'UNREVIEWED' && item.detectionCount > 0)
  if (risk === 'VERY_HIGH' || risk === 'HIGH' || hasUnreviewed) return '高'
  if (risk === 'MEDIUM' || aiFindings.value.length) return '中'
  return '常规'
})

const evidenceItems = computed(() => [
  { key: 'vision', label: '视觉证据', count: model.value?.evidence.length ?? 0 },
  { key: 'inspection', label: '巡检', count: model.value?.inspections.length ?? 0 },
  { key: 'archive', label: '档案', count: model.value ? 1 : 0 },
  { key: 'risk', label: '正式风险', count: model.value?.assessment ? 1 : 0 },
])

onMounted(load)
watch(buildingId, () => { void load() })
watch(() => route.query.tab, (value) => { activeTab.value = normalizeTab(value) })

async function load(): Promise<void> {
  if (!buildingId.value) return
  loading.value = true
  errorMessage.value = ''
  model.value = null
  try {
    model.value = await loadBuildingDetail(buildingId.value)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : String(error)
  } finally {
    loading.value = false
  }
}

function normalizeTab(value: unknown): string {
  const raw = Array.isArray(value) ? value[0] : value
  const aliases: Record<string, string> = {
    lifecycle: 'governance',
    analysis: 'ai',
    report: 'governance',
  }
  const tab = typeof raw === 'string' ? aliases[raw] ?? raw : ''
  const allowed = new Set(['overview', 'ai', 'inspection', 'risk', 'evidence', 'archive', 'governance'])
  return allowed.has(tab) ? tab : 'overview'
}

function analysisTime(task: BuildingDetailModel['analyses'][number]): number {
  const value = task.completedAt ?? task.createdAt ?? task.requestedAt
  const parsed = value ? Date.parse(value) : 0
  return Number.isFinite(parsed) ? parsed : 0
}

function inspectionStatusLabel(status?: string): string {
  return ({
    PENDING: '待开始',
    IN_PROGRESS: '进行中',
    COMPLETED: '已完成',
    CANCELLED: '已取消',
  } as Record<string, string>)[status ?? ''] ?? (status || '状态待确认')
}

function analysisStatusLabel(status?: string): string {
  return ({
    PENDING: '等待分析',
    RUNNING: '分析中',
    SUCCEEDED: '分析完成',
    FAILED: '分析失败',
    REJECTED: '已拒绝',
    CANCELLED: '已取消',
  } as Record<string, string>)[status ?? ''] ?? (status || '状态待确认')
}

function reviewStatusLabel(status?: string): string {
  return ({
    UNREVIEWED: '待人工复核',
    CONFIRMED: '人工已确认',
    CORRECTED: '人工已修正',
    REJECTED: '人工已排除',
  } as Record<string, string>)[status ?? ''] ?? (status || '复核状态待确认')
}

function reportStatusLabel(status?: string): string {
  return ({
    GENERATING: '生成中',
    GENERATED: '已生成',
    FAILED: '生成失败',
    STALE: '报告已过期',
  } as Record<string, string>)[status ?? ''] ?? (status || '状态待确认')
}

function riskScore(value?: number): string {
  return value == null ? '—' : value.toFixed(1)
}

function openReview(inferenceId: string): void {
  void router.push(`/console/review/${inferenceId}`)
}
</script>

<template>
  <section class="building-detail-page">
    <AppPageHeader
      eyebrow="单栋建筑 AI 治理驾驶舱"
      :title="`${model?.summary.communityName || '楼栋'} · ${model?.summary.buildingName || '详情'}`"
      description="围绕正式风险、AI 发现、巡检证据与人工复核组织单栋治理链路。"
    >
      <template #actions>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </template>
    </AppPageHeader>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon />
    <div v-if="loading" class="page-state">正在读取楼栋业务数据…</div>

    <template v-else-if="model">
      <section class="cockpit-summary">
        <div class="identity-block">
          <span>{{ model.summary.buildingCode }}</span>
          <strong>{{ model.summary.buildingName }}</strong>
          <small>{{ model.summary.address || model.summary.communityName }}</small>
        </div>
        <div><span>正式风险</span><strong>{{ model.risk.riskLevel || '暂无结果' }}</strong></div>
        <div><span>综合评分</span><strong>{{ riskScore(model.risk.riskScore) }}</strong></div>
        <div><span>更新优先级</span><strong>{{ model.risk.priorityLevel || '—' }}</strong></div>
        <div><span>AI关注</span><strong>{{ aiAttentionLabel }}</strong><small>仅用于辅助排序，不写回正式评分</small></div>
      </section>

      <AiInsightCard title="AI 综合研判" :summary="aiSummary" :suggestion="aiSuggestion">
        <div class="insight-findings">
          <span>主要发现</span>
          <div v-if="aiFindings.length">
            <el-tag v-for="item in aiFindings" :key="item" effect="plain" round>{{ item }}</el-tag>
          </div>
          <small v-else>暂无明确视觉病害发现。</small>
        </div>
        <AiEvidencePanel :items="evidenceItems" title="分析依据" />
        <template #actions>
          <el-button v-if="latestAnalysis" type="primary" plain @click="openReview(latestAnalysis.inferenceId)">查看全部依据</el-button>
          <el-button @click="activeTab = 'ai'">查看 AI 发现</el-button>
        </template>
      </AiInsightCard>

      <el-alert
        v-if="model.warnings.length"
        type="warning"
        :closable="false"
        show-icon
        title="部分业务数据暂时不可用"
        :description="model.warnings.map((item) => item.message).join('；')"
      />

      <el-tabs v-model="activeTab" class="detail-tabs">
        <el-tab-pane label="概览" name="overview">
          <div class="overview-grid">
            <RiskSummaryPanel :summary="model.risk" />
            <BuildingLifecycleTimeline :nodes="model.lifecycle" />
          </div>
        </el-tab-pane>

        <el-tab-pane label="AI 发现" name="ai">
          <section class="business-section">
            <el-alert
              type="info"
              :closable="false"
              show-icon
              title="AI 发现仅用于辅助筛查"
              description="AI 不覆盖人工专业结论，也不会直接修改正式风险分数。"
            />
            <article v-for="task in model.analyses" :key="task.inferenceId" class="business-row stacked">
              <div class="row-between">
                <div><strong>AI 视觉识别</strong><small>{{ task.completedAt || task.createdAt || '时间待确认' }}</small></div>
                <div class="row-tags"><el-tag effect="plain">{{ analysisStatusLabel(task.status) }}</el-tag><el-tag effect="plain">{{ reviewStatusLabel(task.reviewStatus) }}</el-tag></div>
              </div>
              <p>{{ task.structuredResult?.summary || task.summary?.summary || task.assessmentNote || '当前结果需结合人工复核后使用。' }}</p>
              <div v-if="(task.structuredResult?.detections?.length || task.detections?.length)" class="finding-tags">
                <el-tag
                  v-for="(item, index) in (task.structuredResult?.detections?.length ? task.structuredResult.detections : task.detections || [])"
                  :key="`${task.inferenceId}-${index}`"
                  effect="plain"
                  round
                >{{ formatAiDetectionLabel(item.className || '疑似病害', item.confidence) }}</el-tag>
              </div>
              <div class="row-actions"><el-button size="small" @click="openReview(task.inferenceId)">查看原图与 Polygon 标注</el-button></div>
            </article>
            <el-empty v-if="model.analyses.length === 0" description="暂无 AI 发现记录" />
          </section>
        </el-tab-pane>

        <el-tab-pane label="巡检" name="inspection">
          <section class="business-section">
            <header class="section-head"><strong>巡检记录</strong><span>{{ model.inspections.length }} 项</span></header>
            <article v-for="(task, index) in model.inspections" :key="index" class="business-row">
              <div><strong>巡检任务 {{ index + 1 }}</strong><small>来自该楼栋正式巡检任务</small></div>
              <el-tag effect="plain">{{ inspectionStatusLabel(task.status) }}</el-tag>
            </article>
            <el-empty v-if="model.inspections.length === 0" description="暂无巡检记录" />
          </section>
        </el-tab-pane>

        <el-tab-pane label="风险研判" name="risk">
          <RiskSummaryPanel :summary="model.risk" />
        </el-tab-pane>

        <el-tab-pane label="证据" name="evidence">
          <section class="business-section">
            <header class="section-head"><strong>现场证据</strong><span>{{ model.evidence.length }} 项</span></header>
            <EvidenceGallery :items="model.evidence" />
          </section>
        </el-tab-pane>

        <el-tab-pane label="档案" name="archive">
          <BuildingSummaryCard :summary="model.summary" />
        </el-tab-pane>

        <el-tab-pane label="治理记录" name="governance">
          <section class="governance-stack">
            <BuildingLifecycleTimeline :nodes="model.lifecycle" />
            <section class="business-section">
              <header class="section-head"><strong>风险报告</strong><span>{{ model.reports.length }} 份</span></header>
              <article v-for="report in model.reports" :key="report.reportId" class="business-row stacked">
                <div class="row-between">
                  <div><strong>{{ report.reportCode }}</strong><small>正式楼栋风险报告</small></div>
                  <el-tag effect="plain">{{ reportStatusLabel(report.reportStatus) }}</el-tag>
                </div>
                <small>{{ report.generatedAt || report.createdAt }}</small>
              </article>
              <el-empty v-if="model.reports.length === 0" description="暂无治理报告记录" />
            </section>
          </section>
        </el-tab-pane>
      </el-tabs>
    </template>

    <el-empty v-else-if="!errorMessage" description="暂无楼栋档案数据" />
  </section>
</template>

<style scoped lang="scss">
.building-detail-page{display:grid;gap:var(--usp-space-4)}
.row-between,.section-head{display:flex;align-items:center;justify-content:space-between;gap:var(--usp-space-4)}
.page-state{padding:var(--usp-space-6);text-align:center;color:var(--usp-color-text-secondary)}
.cockpit-summary{display:grid;grid-template-columns:minmax(220px,1.6fr) repeat(4,minmax(120px,1fr));gap:10px}.cockpit-summary>div{display:grid;align-content:center;gap:3px;min-height:86px;padding:13px 14px;border:1px solid var(--usp-color-border);border-radius:var(--usp-radius-xl);background:var(--usp-color-surface);box-shadow:var(--usp-shadow-sm)}.cockpit-summary span,.cockpit-summary small{color:var(--usp-color-text-secondary);font-size:11px}.cockpit-summary strong{font-size:20px}.identity-block strong{font-size:18px}.insight-findings{display:grid;gap:7px}.insight-findings>span{color:var(--usp-color-text-tertiary);font-size:12px;font-weight:700}.insight-findings>div,.finding-tags,.row-tags,.row-actions{display:flex;flex-wrap:wrap;gap:7px}.insight-findings small{color:var(--usp-color-text-secondary)}
.detail-tabs{min-width:0}.overview-grid,.governance-stack{display:grid;gap:var(--usp-space-4)}.business-section{display:grid;gap:var(--usp-space-3)}.section-head{padding-bottom:var(--usp-space-2);border-bottom:1px solid var(--usp-color-border)}
.business-row{display:flex;align-items:center;justify-content:space-between;gap:var(--usp-space-3);padding:var(--usp-space-3);border:1px solid var(--usp-color-border);border-radius:var(--usp-radius-lg);background:var(--usp-color-surface)}.business-row>div{display:grid;gap:4px}.business-row small,.business-row p{color:var(--usp-color-text-secondary)}.business-row.stacked{display:grid;align-items:stretch}.business-row.stacked p{margin:0;line-height:1.6}.business-row.stacked .row-between{display:flex}.row-actions{justify-content:flex-end}
@media(max-width:960px){.cockpit-summary{grid-template-columns:repeat(2,minmax(0,1fr))}.identity-block{grid-column:1/-1}}
@media(max-width:640px){.business-row,.row-between{align-items:flex-start;flex-direction:column}.business-row.stacked .row-between{align-items:flex-start;flex-direction:column}.cockpit-summary{grid-template-columns:1fr}.identity-block{grid-column:auto}.row-actions{justify-content:flex-start}}
</style>
