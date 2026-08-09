<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { loadBuildingDetail, type BuildingDetailModel } from '@/features/building-detail/building-detail-loader'
import BuildingSummaryCard from '@/shared/components/business/BuildingSummaryCard.vue'
import BuildingLifecycleTimeline from '@/shared/components/business/BuildingLifecycleTimeline.vue'
import RiskSummaryPanel from '@/shared/components/business/RiskSummaryPanel.vue'
import EvidenceGallery from '@/shared/components/business/EvidenceGallery.vue'

const route = useRoute()
const loading = ref(false)
const errorMessage = ref('')
const model = ref<BuildingDetailModel | null>(null)
const buildingId = computed(() => String(route.params.buildingId ?? ''))
const activeTab = ref(normalizeTab(route.query.tab))

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
  const tab = Array.isArray(value) ? value[0] : value
  const allowed = new Set(['archive', 'lifecycle', 'inspection', 'analysis', 'risk', 'evidence', 'report'])
  return typeof tab === 'string' && allowed.has(tab) ? tab : 'archive'
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
</script>

<template>
  <section class="building-detail-page">
    <header class="page-head">
      <div>
        <h1>楼栋统一档案</h1>
        <p>集中查看楼栋档案、巡检、辅助分析、正式评分、证据和风险报告。</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </header>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon />
    <div v-if="loading" class="page-state">正在读取楼栋业务数据…</div>

    <template v-else-if="model">
      <el-alert
        v-if="model.warnings.length"
        type="warning"
        :closable="false"
        show-icon
        title="部分业务数据暂时不可用"
        :description="model.warnings.map((item) => item.message).join('；')"
      />

      <el-tabs v-model="activeTab" class="detail-tabs">
        <el-tab-pane label="基础档案" name="archive">
          <BuildingSummaryCard :summary="model.summary" />
        </el-tab-pane>

        <el-tab-pane label="业务进度" name="lifecycle">
          <BuildingLifecycleTimeline :nodes="model.lifecycle" />
        </el-tab-pane>

        <el-tab-pane label="巡检记录" name="inspection">
          <section class="business-section">
            <header class="section-head"><strong>巡检记录</strong><span>{{ model.inspections.length }} 项</span></header>
            <article v-for="(task, index) in model.inspections" :key="index" class="business-row">
              <div><strong>巡检任务 {{ index + 1 }}</strong><small>来自该楼栋正式巡检任务</small></div>
              <el-tag effect="plain">{{ inspectionStatusLabel(task.status) }}</el-tag>
            </article>
            <el-empty v-if="model.inspections.length === 0" description="暂无巡检记录" />
          </section>
        </el-tab-pane>

        <el-tab-pane label="辅助分析" name="analysis">
          <section class="business-section">
            <el-alert
              type="info"
              :closable="false"
              show-icon
              title="人工智能结果仅用于辅助分析"
              description="辅助分析不作为正式鉴定结论，应结合经审核证据、系统规则和人工专业判断使用。"
            />
            <article v-for="task in model.analyses" :key="task.inferenceId" class="business-row stacked">
              <div class="row-between">
                <div><strong>{{ task.modelName || '辅助分析任务' }}</strong><small>{{ task.requestCode }}</small></div>
                <el-tag effect="plain">{{ analysisStatusLabel(task.status) }}</el-tag>
              </div>
              <p>{{ task.assessmentNote || task.disclaimer || '当前分析结果需结合人工复核后使用。' }}</p>
              <small>{{ reviewStatusLabel(task.reviewStatus) }}<template v-if="task.completedAt"> · {{ task.completedAt }}</template></small>
            </article>
            <el-empty v-if="model.analyses.length === 0" description="暂无辅助分析记录" />
          </section>
        </el-tab-pane>

        <el-tab-pane label="风险与优先级" name="risk">
          <RiskSummaryPanel :summary="model.risk" />
        </el-tab-pane>

        <el-tab-pane label="现场证据" name="evidence">
          <section class="business-section">
            <header class="section-head"><strong>现场证据</strong><span>{{ model.evidence.length }} 项</span></header>
            <EvidenceGallery :items="model.evidence" />
          </section>
        </el-tab-pane>

        <el-tab-pane label="风险报告" name="report">
          <section class="business-section">
            <header class="section-head"><strong>风险报告</strong><span>{{ model.reports.length }} 份</span></header>
            <article v-for="report in model.reports" :key="report.reportId" class="business-row stacked">
              <div class="row-between">
                <div><strong>{{ report.reportCode }}</strong><small>模板 {{ report.templateVersion }}</small></div>
                <el-tag effect="plain">{{ reportStatusLabel(report.reportStatus) }}</el-tag>
              </div>
              <small>{{ report.generatedAt || report.createdAt }}</small>
            </article>
            <el-empty v-if="model.reports.length === 0" description="暂无风险报告" />
          </section>
        </el-tab-pane>
      </el-tabs>
    </template>

    <el-empty v-else-if="!errorMessage" description="暂无楼栋档案数据" />
  </section>
</template>

<style scoped lang="scss">
.building-detail-page{display:grid;gap:var(--usp-space-4)}
.page-head,.row-between,.section-head{display:flex;align-items:center;justify-content:space-between;gap:var(--usp-space-4)}
.page-head h1{margin:0}.page-head p{margin:4px 0 0;color:var(--usp-color-text-secondary)}
.page-state{padding:var(--usp-space-6);text-align:center;color:var(--usp-color-text-secondary)}
.detail-tabs{min-width:0}.business-section{display:grid;gap:var(--usp-space-3)}.section-head{padding-bottom:var(--usp-space-2);border-bottom:1px solid var(--usp-color-border)}
.business-row{display:flex;align-items:center;justify-content:space-between;gap:var(--usp-space-3);padding:var(--usp-space-3);border:1px solid var(--usp-color-border);border-radius:var(--usp-radius-md);background:var(--usp-color-surface)}
.business-row>div{display:grid;gap:4px}.business-row small,.business-row p{color:var(--usp-color-text-secondary)}.business-row.stacked{display:grid;align-items:stretch}.business-row.stacked p{margin:0;line-height:1.6}.business-row.stacked .row-between{display:flex}
@media(max-width:640px){.page-head,.business-row,.row-between{align-items:flex-start;flex-direction:column}.business-row.stacked .row-between{align-items:flex-start;flex-direction:column}}
</style>
