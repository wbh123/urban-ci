<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as api from '@/shared/api'
import { toAppError, type AiInferenceStatus, type AiInferenceTask } from '@/shared/api'
import { buildAiInspectionSummary } from '@/shared/ai/ai-inspection-summary'
import { formatAiConfidence } from '@/shared/ai/ai-display'
import AppDateTime from '@/shared/components/AppDateTime.vue'
import AppLoading from '@/shared/components/AppLoading.vue'
import AppEmpty from '@/shared/components/AppEmpty.vue'
import AppError from '@/shared/components/AppError.vue'
import AppStatusTag from '@/shared/components/AppStatusTag.vue'
import AppTablePager from '@/shared/components/AppTablePager.vue'
import AppPageHeader from '@/shared/components/layout/AppPageHeader.vue'
import AiPageBrief from '@/shared/components/ai/AiPageBrief.vue'

const router = useRouter()
const tasks = ref<AiInferenceTask[]>([])
const loading = ref(false)
const errorMessage = ref('')
const status = ref<AiInferenceStatus | 'ALL'>('ALL')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

const statusOptions = [
  { label: '全部', value: 'ALL' },
  { label: '分析成功', value: 'SUCCEEDED' },
  { label: '分析失败', value: 'FAILED' },
  { label: '已拒绝', value: 'REJECTED' },
]

const reviewBriefMetrics = computed(() => [
  { label: '当前页分析', value: tasks.value.length },
  {
    label: '当前页待复核',
    value: tasks.value.filter((item) => !item.reviewStatus || ['UNREVIEWED', 'PENDING', 'NEED_REVIEW'].includes(item.reviewStatus)).length,
    tone: 'attention' as const,
  },
  {
    label: '当前页低可信',
    value: tasks.value.filter((item) => {
      const value = confidenceValue(item)
      return value != null && value < 0.4
    }).length,
    tone: 'attention' as const,
  },
  {
    label: '含视觉标注',
    value: tasks.value.filter((item) => (item.detectionCount ?? detectionItems(item).length) > 0).length,
  },
])

function openTask(task: AiInferenceTask): void {
  void router.push(`/console/review/${task.inferenceId}`)
}

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await api.listAiInferences({
      page: page.value - 1,
      size: pageSize.value,
      status: status.value === 'ALL' ? undefined : status.value,
    })
    tasks.value = response.content ?? []
    total.value = response.page.totalElements
  } catch (error) {
    errorMessage.value = toAppError(error).message
  } finally {
    loading.value = false
  }
}

async function handleStatusChange(): Promise<void> {
  page.value = 1
  await load()
}

function reviewStatusLabel(value?: string | null): string {
  const labels: Record<string, string> = {
    UNREVIEWED: '待复核',
    PENDING: '待复核',
    CONFIRMED: '已确认',
    CORRECTED: '已修正',
    APPROVED: '已通过',
    REJECTED: '已驳回',
    NEED_REVIEW: '需复核',
  }
  if (!value) return '待复核'
  return labels[value] ?? '待复核'
}

function objectLabel(task: AiInferenceTask): string {
  if (task.buildingId) return `楼栋 · ${task.buildingId.slice(0, 8)}`
  if (task.inspectionTaskId) return `巡检任务 · ${task.inspectionTaskId.slice(0, 8)}`
  return '现场图片'
}

function objectHint(task: AiInferenceTask): string {
  if (task.inspectionTaskId) return '来自现场巡检'
  if (task.assetId) return '现场图片证据'
  return '待关联业务对象'
}

function detectionItems(task: AiInferenceTask): Array<{ className?: string | null; confidence?: number | null }> {
  const structured = task.structuredResult?.detections ?? []
  return structured.length ? structured : task.detections ?? []
}

function findingLabel(task: AiInferenceTask): string {
  const summary = buildAiInspectionSummary(detectionItems(task))
  if (!summary.findings.length) return task.status === 'SUCCEEDED' ? '未发现明确疑似病害' : '等待有效识别结果'
  const visible = summary.findings.slice(0, 2).map((item) => `${item.name} ×${item.count}`)
  return summary.findings.length > 2 ? `${visible.join('、')} 等` : visible.join('、')
}

function confidenceValue(task: AiInferenceTask): number | null {
  if (task.structuredResult?.confidence != null) return task.structuredResult.confidence
  const values = detectionItems(task)
    .map((item) => item.confidence)
    .filter((value): value is number => typeof value === 'number' && Number.isFinite(value))
  return values.length ? Math.max(...values) : null
}

function confidenceLabel(task: AiInferenceTask): string {
  const value = confidenceValue(task)
  const percentage = formatAiConfidence(value)
  if (percentage) return percentage
  if (value != null) return '低可信 · 需人工复核'
  return task.status === 'SUCCEEDED' ? '待人工判断' : '暂无可信度'
}

function confidenceTagType(task: AiInferenceTask): 'success' | 'warning' | 'info' {
  const value = confidenceValue(task)
  if (value != null && value >= 0.7) return 'success'
  if (value != null && value < 0.4) return 'warning'
  return 'info'
}

function evidenceLabel(task: AiInferenceTask): string {
  const imageCount = task.assetId ? 1 : 0
  return `${imageCount} 张图 · ${task.detectionCount ?? 0} 个标注`
}

onMounted(load)
</script>

<template>
  <section class="review-page">
    <AppPageHeader
      eyebrow="巡检治理"
      title="AI 人工复核中心"
      description="围绕 AI 发现、可信程度和原始证据进行专业确认、修正或驳回；AI 不会自动提交人工结论。"
      show-user-menu
    >
      <template #actions>
        <el-button @click="load">刷新</el-button>
      </template>
    </AppPageHeader>

    <AiPageBrief
      v-if="!loading && !errorMessage"
      title="复核辅助看板"
      :metrics="reviewBriefMetrics"
      :summary="`当前筛选条件共有 ${total} 条分析记录；看板指标中标注“当前页”的项目只统计当前已加载结果。`"
      suggestion="优先处理低可信、存在视觉标注且尚未形成专业结论的结果；最终确认、修正或驳回必须由专业人员提交。"
    />

    <section class="review-toolbar">
      <div class="review-toolbar__copy">
        <strong>结果筛选</strong>
        <span>当前条件共 {{ total }} 条分析记录；模型与请求编号已下沉到详情。</span>
      </div>
      <el-segmented v-model="status" :options="statusOptions" @change="handleStatusChange" />
    </section>

    <AppLoading :visible="loading" inline text="加载复核队列中…" />
    <AppError v-if="errorMessage" :message="errorMessage" @retry="load" />

    <el-card v-if="!loading && !errorMessage" class="surface-card queue-card" shadow="never">
      <template #header>
        <div class="queue-head">
          <div>
            <strong>待处理与历史复核</strong>
            <small>先看发生了什么，再看 AI 怎么判断，最后由专业人员形成结论。</small>
          </div>
          <el-tag type="warning" effect="plain" round>必须人工复核</el-tag>
        </div>
      </template>

      <div class="queue-table-zone">
        <el-table v-if="tasks.length" :data="tasks" stripe height="100%" @row-click="openTask">
          <el-table-column label="对象" min-width="175">
            <template #default="scope">
              <div class="object-cell"><strong>{{ objectLabel(scope.row) }}</strong><small>{{ objectHint(scope.row) }}</small></div>
            </template>
          </el-table-column>
          <el-table-column label="AI 发现" min-width="220" show-overflow-tooltip>
            <template #default="scope">{{ findingLabel(scope.row) }}</template>
          </el-table-column>
          <el-table-column label="可信程度" width="155">
            <template #default="scope"><el-tag :type="confidenceTagType(scope.row)" effect="plain" round>{{ confidenceLabel(scope.row) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="证据数量" width="135">
            <template #default="scope">{{ evidenceLabel(scope.row) }}</template>
          </el-table-column>
          <el-table-column label="提交时间" min-width="165">
            <template #default="scope"><AppDateTime :value="scope.row.requestedAt || scope.row.createdAt" /></template>
          </el-table-column>
          <el-table-column label="复核状态" width="126">
            <template #default="scope">
              <el-tag effect="plain" round>{{ reviewStatusLabel(scope.row.reviewStatus) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="分析状态" width="118">
            <template #default="scope"><AppStatusTag :status="scope.row.status" variant="task" /></template>
          </el-table-column>
          <el-table-column label="操作" width="88" fixed="right">
            <template #default="scope">
              <el-button size="small" @click.stop="openTask(scope.row)">复核</el-button>
            </template>
          </el-table-column>
        </el-table>
        <AppEmpty v-else description="当前筛选条件下暂无复核任务" />
      </div>

      <AppTablePager
        v-model:page="page"
        v-model:page-size="pageSize"
        :total="total"
        @change="load"
      />
    </el-card>
  </section>
</template>

<style scoped lang="scss">
.review-page { display: grid; gap: 14px; }
.review-toolbar {
  display: flex;
  min-height: 58px;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 14px;
  border: 1px solid var(--usp-color-border);
  border-radius: var(--usp-radius-xl);
  background: var(--usp-color-surface);
  box-shadow: var(--usp-shadow-sm);
}
.review-toolbar__copy { display: grid; gap: 2px; }
.review-toolbar__copy strong { font-size: 13px; }
.review-toolbar__copy span { color: var(--usp-color-text-secondary); font-size: 11px; }
.surface-card { border-radius: var(--usp-radius-xl); box-shadow: var(--usp-shadow-sm); }
.queue-card :deep(.el-card__body) { display: flex; min-height: 590px; flex-direction: column; padding: 0 16px 12px; }
.queue-card :deep(.el-card__header) { padding: 13px 16px; }
.queue-head { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.queue-head > div { display: grid; gap: 3px; }
.queue-head small { color: var(--usp-color-text-secondary); font-size: 11px; }
.queue-table-zone { min-height: 0; flex: 1; padding-top: 8px; }
.queue-table-zone :deep(.el-table) { --el-table-header-bg-color: var(--usp-color-surface-muted); border-radius: var(--usp-radius-lg); cursor: pointer; }
.object-cell { display: grid; gap: 2px; }
.object-cell strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.object-cell small { color: var(--usp-color-text-secondary); }

@media (max-width: 820px) {
  .review-toolbar { align-items: stretch; flex-direction: column; }
  .review-toolbar :deep(.el-segmented) { width: 100%; }
  .review-toolbar :deep(.el-segmented__group) { width: 100%; }
  .review-toolbar :deep(.el-segmented__item) { flex: 1; }
}
</style>
