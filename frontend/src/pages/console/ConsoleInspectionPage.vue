<script setup lang="ts">
import { computed, ref } from 'vue'
import * as api from '@/shared/api'
import {
  toAppError,
  type AiInferenceTask,
  type InspectionAiCombinedSummary,
  type InspectionRecord,
  type InspectionTask,
  type InspectionTaskStatus,
  type InspectionType,
} from '@/shared/api'
import { useAppStore } from '@/stores/app'
import AiDetectionOverlay from '@/pages/AiDetectionOverlay.vue'
import AppDateTime from '@/shared/components/AppDateTime.vue'
import AppEmpty from '@/shared/components/AppEmpty.vue'
import AppError from '@/shared/components/AppError.vue'
import AppFilterBar from '@/shared/components/AppFilterBar.vue'
import AppFilterField from '@/shared/components/AppFilterField.vue'
import AppLoading from '@/shared/components/AppLoading.vue'
import AppQueryField from '@/shared/components/AppQueryField.vue'
import AppSpatialScopeFilter from '@/shared/components/AppSpatialScopeFilter.vue'
import AppStatusTag from '@/shared/components/AppStatusTag.vue'
import AppTablePager from '@/shared/components/AppTablePager.vue'
import InspectionImageGallery from '@/shared/components/inspection/InspectionImageGallery.vue'
import AppPageHeader from '@/shared/components/layout/AppPageHeader.vue'
import SpatialObjectSelector from '@/shared/components/SpatialObjectSelector.vue'
import AiInspectionSummary from '@/shared/components/ai/AiInspectionSummary.vue'
import AiInspectionCombinedSummary from '@/shared/components/ai/AiInspectionCombinedSummary.vue'
import AiPageBrief from '@/shared/components/ai/AiPageBrief.vue'
import type { SpatialObjectSelection } from '@/shared/composables/useSpatialObjectSelector'

const appStore = useAppStore()
const tasks = ref<InspectionTask[]>([])
const selectedCommunity = ref('')
const selectedBuilding = ref('')
const taskKeyword = ref('')
const statusFilter = ref<InspectionTaskStatus | ''>('')
const typeFilter = ref<InspectionType | ''>('')
const dateRange = ref<string[]>([])
const page = ref(1)
const pageSize = ref(20)
const title = ref('现场安全巡检')
const inspectionType = ref<InspectionType>('ROUTINE')
const drawerVisible = ref(false)
const createCommunityId = ref('')
const createBuildingId = ref('')
const loading = ref(false)
const saving = ref(false)
const completingTaskId = ref('')
const errorMessage = ref('')
const selectorRevision = ref(0)
const createSelectorRevision = ref(0)
const inspectionAiTasks = ref<AiInferenceTask[]>([])
const inspectionAiTotal = ref(0)
const inspectionAiLoading = ref(false)
const inspectionAiNotice = ref('')

const detailVisible = ref(false)
const detailLoading = ref(false)
const detailTask = ref<InspectionTaskDisplay | null>(null)
const detailRecords = ref<InspectionRecord[]>([])
const detailGalleryKey = ref(0)
const detailInference = ref<AiInferenceTask | null>(null)
const detailImageUrl = ref('')
const detailAiLoading = ref(false)
const detailCombinedSummary = ref<InspectionAiCombinedSummary | null>(null)
const detailCombinedSummaryLoading = ref(false)

type InspectionTaskDisplay = InspectionTask & {
  inspectionType?: string | null
  createdAt?: string | null
}

const filteredTasks = computed(() => {
  const keyword = taskKeyword.value.trim().toLowerCase()
  const start = dateRange.value[0] ? new Date(`${dateRange.value[0]}T00:00:00`).getTime() : null
  const end = dateRange.value[1] ? new Date(`${dateRange.value[1]}T23:59:59.999`).getTime() : null
  return tasks.value.filter((rawTask) => {
    const task = rawTask as InspectionTaskDisplay
    if (keyword) {
      const haystack = [task.taskCode, task.title, task.buildingName].filter(Boolean).join(' ').toLowerCase()
      if (!haystack.includes(keyword)) return false
    }
    if (typeFilter.value && task.inspectionType !== typeFilter.value) return false
    if ((start != null || end != null) && task.createdAt) {
      const createdAt = new Date(task.createdAt).getTime()
      if (Number.isFinite(createdAt)) {
        if (start != null && createdAt < start) return false
        if (end != null && createdAt > end) return false
      }
    }
    return true
  })
})

const pagedTasks = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return filteredTasks.value.slice(start, start + pageSize.value)
})

const pendingAiReviewCount = computed(() => inspectionAiTasks.value.filter((item) => (
  item.status === 'SUCCEEDED' && (!item.reviewStatus || item.reviewStatus === 'UNREVIEWED')
)).length)

const highAttentionAiCount = computed(() => inspectionAiTasks.value.filter((item) => (
  item.structuredResult?.riskSignals?.some((signal) => ['HIGH', 'VERY_HIGH'].includes(String(signal.level ?? '').toUpperCase()))
)).length)

const inspectionAiMetrics = computed(() => [
  { label: '当前巡检任务', value: filteredTasks.value.length },
  { label: '视觉分析记录', value: inspectionAiTotal.value },
  { label: '待人工复核', value: pendingAiReviewCount.value, tone: pendingAiReviewCount.value > 0 ? 'attention' as const : 'normal' as const },
  { label: '高关注信号', value: highAttentionAiCount.value, tone: highAttentionAiCount.value > 0 ? 'danger' as const : 'normal' as const },
])

const inspectionAiSummary = computed(() => {
  if (inspectionAiLoading.value) return '正在汇总当前空间范围内的巡检与 AI 视觉分析记录。'
  if (inspectionAiTotal.value === 0) return '当前空间范围尚未产生可汇总的 AI 视觉分析记录，巡检任务仍可正常创建和执行。'
  return `当前空间范围共有 ${inspectionAiTotal.value} 条视觉分析记录，其中 ${pendingAiReviewCount.value} 条等待人工复核，${highAttentionAiCount.value} 条包含高关注风险信号。`
})

const inspectionAiSuggestion = computed(() => {
  if (pendingAiReviewCount.value > 0) return '优先处理待人工复核结果，再结合巡检员现场记录形成专业结论。'
  if (highAttentionAiCount.value > 0) return '高关注信号应回看原图和现场记录，必要时补拍或安排专业检测。'
  return '继续按巡检计划补充现场证据；AI 看板用于辅助发现遗漏，不替代专业判断。'
})

const detailDetections = computed(() => {
  const structured = detailInference.value?.structuredResult?.detections ?? []
  return structured.length ? structured : detailInference.value?.detections ?? []
})

const detailSummary = computed(() =>
  detailInference.value?.structuredResult?.summary
  ?? detailInference.value?.summary?.summary
  ?? '',
)

async function loadTasks(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    tasks.value = await api.listInspectionTasks({
      buildingId: selectedBuilding.value || undefined,
      status: statusFilter.value || undefined,
    })
    const maxPage = Math.max(1, Math.ceil(filteredTasks.value.length / pageSize.value))
    if (page.value > maxPage) page.value = maxPage
    await loadInspectionAiMetrics()
  } catch (error) {
    errorMessage.value = toAppError(error).message
  } finally {
    loading.value = false
  }
}

async function loadInspectionAiMetrics(): Promise<void> {
  inspectionAiLoading.value = true
  inspectionAiNotice.value = ''
  try {
    const response = await api.listAiInferences({
      page: 0,
      size: 100,
      capabilityType: 'VISION_INFERENCE',
      communityId: selectedCommunity.value || undefined,
      buildingId: selectedBuilding.value || undefined,
    })
    inspectionAiTasks.value = response.content ?? []
    inspectionAiTotal.value = Number(response.page?.totalElements ?? inspectionAiTasks.value.length)
  } catch (error) {
    inspectionAiTasks.value = []
    inspectionAiTotal.value = 0
    inspectionAiNotice.value = `AI 看板暂不可用：${toAppError(error).message}`
  } finally {
    inspectionAiLoading.value = false
  }
}

async function handleSpatialSelection(selection: SpatialObjectSelection): Promise<void> {
  selectedCommunity.value = selection.communityId
  selectedBuilding.value = selection.buildingId
  page.value = 1
  await loadTasks()
}

function handleCreateSelection(selection: SpatialObjectSelection): void {
  createCommunityId.value = selection.communityId
  createBuildingId.value = selection.buildingId
}

async function runQuery(): Promise<void> {
  page.value = 1
  await loadTasks()
}

async function resetFilters(): Promise<void> {
  selectedCommunity.value = ''
  selectedBuilding.value = ''
  taskKeyword.value = ''
  statusFilter.value = ''
  typeFilter.value = ''
  dateRange.value = []
  page.value = 1
  selectorRevision.value += 1
  await loadTasks()
}

async function refresh(): Promise<void> {
  await loadTasks()
}

function openCreateDrawer(): void {
  createCommunityId.value = selectedCommunity.value
  createBuildingId.value = selectedBuilding.value
  title.value = '现场安全巡检'
  inspectionType.value = 'ROUTINE'
  createSelectorRevision.value += 1
  drawerVisible.value = true
}

async function createTask(): Promise<void> {
  if (!createBuildingId.value || !title.value.trim()) {
    appStore.notify('请选择楼栋并填写任务标题。', 'warning')
    return
  }
  saving.value = true
  try {
    const task = await api.createInspectionTask({
      buildingId: createBuildingId.value,
      inspectionType: inspectionType.value,
      title: title.value.trim(),
    })
    appStore.notify(`任务已创建：${task.taskCode}`, 'success')
    drawerVisible.value = false
    if (!selectedBuilding.value || selectedBuilding.value === createBuildingId.value) await loadTasks()
  } catch (error) {
    appStore.notify(toAppError(error).message, 'error')
  } finally {
    saving.value = false
  }
}

async function confirmTaskCompletion(task: InspectionTaskDisplay): Promise<void> {
  completingTaskId.value = task.taskId
  try {
    await api.transitionInspectionTask(task.taskId, 'complete')
    appStore.notify(`任务已确认完成：${task.taskCode}`, 'success')
    await loadTasks()
  } catch (error) {
    appStore.notify(toAppError(error).message, 'error')
  } finally {
    completingTaskId.value = ''
  }
}

async function openDetail(task: InspectionTaskDisplay): Promise<void> {
  releaseDetailImageUrl()
  detailTask.value = task
  detailRecords.value = []
  detailInference.value = null
  detailCombinedSummary.value = null
  detailVisible.value = true
  detailLoading.value = true
  detailGalleryKey.value += 1
  try {
    detailRecords.value = await api.listInspectionRecords(task.taskId)
  } catch (error) {
    appStore.notify(`巡检记录加载失败：${toAppError(error).message}`, 'error')
  } finally {
    detailLoading.value = false
  }
}

async function displayDetailInference(assetId: string, inferenceId: string): Promise<void> {
  detailAiLoading.value = true
  detailCombinedSummary.value = null
  try {
    releaseDetailImageUrl()
    detailImageUrl.value = await api.fetchImageBlobUrl(assetId)
    detailInference.value = await api.getInspectionImageRichResult(inferenceId)
    if (detailTask.value && detailRecords.value.length && detailInference.value.status === 'SUCCEEDED') {
      await loadCombinedSummary(detailTask.value.taskId, inferenceId)
    }
  } catch (error) {
    appStore.notify(`AI 结果加载失败：${toAppError(error).message}`, 'error')
  } finally {
    detailAiLoading.value = false
  }
}

async function loadCombinedSummary(taskId: string, inferenceId: string): Promise<void> {
  detailCombinedSummaryLoading.value = true
  try {
    detailCombinedSummary.value = await api.getInspectionAiCombinedSummary(taskId, inferenceId)
  } catch (error) {
    detailCombinedSummary.value = null
    appStore.notify(`AI 巡检综合总结暂不可用：${toAppError(error).message}`, 'warning')
  } finally {
    detailCombinedSummaryLoading.value = false
  }
}

function closeDetail(): void {
  releaseDetailImageUrl()
  detailInference.value = null
  detailCombinedSummary.value = null
  detailRecords.value = []
  detailTask.value = null
}

function releaseDetailImageUrl(): void {
  if (detailImageUrl.value) {
    URL.revokeObjectURL(detailImageUrl.value)
    detailImageUrl.value = ''
  }
}

function inspectionTypeLabel(value?: string | null): string {
  if (value === 'ROUTINE') return '日常巡检'
  if (value === 'SPECIAL') return '专项巡检'
  return value || '—'
}
</script>

<template>
  <section class="inspection-page">
    <AppPageHeader
      eyebrow="巡检治理"
      title="巡检管理"
      description="统一组织现场巡检、后台确认、图片上传与 AI 视觉识别；巡检员提交现场作业后，由后台确认任务最终完成。"
      show-user-menu
    >
      <template #actions>
        <el-button @click="refresh">刷新</el-button>
        <el-button type="primary" @click="openCreateDrawer">创建巡检任务</el-button>
      </template>
    </AppPageHeader>

    <AppFilterBar :loading="loading" @query="runQuery" @reset="resetFilters">
      <AppSpatialScopeFilter
        :key="selectorRevision"
        v-model:community-id="selectedCommunity"
        v-model:building-id="selectedBuilding"
        mode="both"
        @change="handleSpatialSelection"
      />
      <AppFilterField kind="keyword" width="250px">
        <AppQueryField
          v-model="taskKeyword"
          placeholder="搜索任务编号、标题或楼栋"
          width="100%"
          @query="runQuery"
        />
      </AppFilterField>
      <AppFilterField kind="status">
        <el-select v-model="statusFilter" clearable placeholder="全部状态">
          <el-option label="待开始" value="PENDING" />
          <el-option label="进行中" value="IN_PROGRESS" />
          <el-option label="待后台确认" value="ONSITE_COMPLETED" />
          <el-option label="已完成" value="COMPLETED" />
          <el-option label="已取消" value="CANCELLED" />
        </el-select>
      </AppFilterField>
      <AppFilterField kind="type">
        <el-select v-model="typeFilter" clearable placeholder="全部类型">
          <el-option label="日常巡检" value="ROUTINE" />
          <el-option label="专项巡检" value="SPECIAL" />
        </el-select>
      </AppFilterField>
      <AppFilterField kind="date-range">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
        />
      </AppFilterField>
    </AppFilterBar>

    <AiPageBrief
      v-if="!errorMessage"
      title="巡检 AI 看板"
      :metrics="inspectionAiMetrics"
      :summary="inspectionAiSummary"
      :suggestion="inspectionAiSuggestion"
    >
      <template #actions>
        <el-tag :type="inspectionAiLoading ? 'info' : 'success'" effect="plain" round>
          {{ inspectionAiLoading ? '汇总中' : '辅助研判' }}
        </el-tag>
      </template>
    </AiPageBrief>
    <el-alert v-if="inspectionAiNotice" :title="inspectionAiNotice" type="warning" :closable="false" show-icon />

    <section class="inspection-ai-chain" aria-label="巡检 AI 链路">
      <div><span>1</span><strong>现场巡检</strong><small>任务、巡检记录、原始图片</small></div>
      <b>→</b>
      <div><span>2</span><strong>后台确认</strong><small>现场作业提交后由管理端确认完成</small></div>
      <b>→</b>
      <div><span>3</span><strong>证据入库与追溯</strong><small>绑定小区、楼栋、任务与图片</small></div>
      <b>→</b>
      <div><span>4</span><strong>ACCURACY 视觉识别</strong><small>FastAPI 高精度病害候选与区域标注</small></div>
      <b>→</b>
      <div><span>5</span><strong>人工复核</strong><small>确认、修正或驳回 AI 结果</small></div>
      <b>→</b>
      <div><span>6</span><strong>规则评分</strong><small>正式风险仍由规则评分形成，AI 不直接改分</small></div>
    </section>

    <AppLoading :visible="loading" inline text="加载巡检任务中…" />
    <AppError v-if="errorMessage" :message="errorMessage" @retry="loadTasks" />

    <el-card v-if="!errorMessage" class="surface-card inspection-task-table" shadow="never">
      <template #header>
        <div class="card-head">
          <div>
            <strong>巡检任务</strong>
            <small>当前条件共 {{ filteredTasks.length }} 条</small>
            <small>“待后台确认”表示现场巡查已经结束；后台确认后任务才正式完成。</small>
          </div>
        </div>
      </template>
      <el-table v-if="pagedTasks.length" :data="pagedTasks" stripe row-key="taskId">
        <el-table-column prop="taskCode" label="任务编号" min-width="175" show-overflow-tooltip />
        <el-table-column prop="title" label="任务标题" min-width="190" show-overflow-tooltip />
        <el-table-column prop="buildingName" label="楼栋" min-width="130" show-overflow-tooltip />
        <el-table-column label="巡检类型" width="110">
          <template #default="scope">{{ inspectionTypeLabel(scope.row.inspectionType) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="scope"><AppStatusTag :status="scope.row.status" variant="task" /></template>
        </el-table-column>
        <el-table-column label="创建时间" width="165">
          <template #default="scope"><AppDateTime :value="scope.row.createdAt" /></template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="scope">
            <div class="task-actions">
              <el-button link type="primary" @click="openDetail(scope.row)">查看详情</el-button>
              <el-button
                v-if="scope.row.status === 'ONSITE_COMPLETED'"
                link
                type="success"
                :loading="completingTaskId === scope.row.taskId"
                @click="confirmTaskCompletion(scope.row)"
              >确认任务完成</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <AppEmpty v-else description="当前条件下暂无巡检任务" />
      <AppTablePager
        v-if="filteredTasks.length > 0"
        v-model:page="page"
        v-model:page-size="pageSize"
        :total="filteredTasks.length"
      />
    </el-card>

    <el-drawer v-model="drawerVisible" title="创建巡检任务" size="min(520px, 92vw)">
      <div class="drawer-form">
        <SpatialObjectSelector
          :key="createSelectorRevision"
          v-model:community-id="createCommunityId"
          v-model:building-id="createBuildingId"
          mode="building"
          @change="handleCreateSelection"
        />
        <el-form label-position="top">
          <el-form-item label="任务标题"><el-input v-model="title" /></el-form-item>
          <el-form-item label="巡检类型">
            <el-radio-group v-model="inspectionType">
              <el-radio-button value="ROUTINE">日常巡检</el-radio-button>
              <el-radio-button value="SPECIAL">专项巡检</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <div class="drawer-footer">
          <el-button @click="drawerVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" :disabled="!createBuildingId" @click="createTask">创建并下发</el-button>
        </div>
      </template>
    </el-drawer>

    <el-drawer
      v-model="detailVisible"
      title="巡检详情"
      size="min(1040px, 96vw)"
      destroy-on-close
      @closed="closeDetail"
    >
      <AppLoading :visible="detailLoading" inline text="加载巡检详情中…" />
      <div v-if="detailTask" class="detail-layout">
        <el-card shadow="never" class="detail-card">
          <template #header><strong>任务基本信息</strong></template>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="任务编号">{{ detailTask.taskCode }}</el-descriptions-item>
            <el-descriptions-item label="状态"><AppStatusTag :status="detailTask.status" variant="task" /></el-descriptions-item>
            <el-descriptions-item label="任务标题">{{ detailTask.title || '—' }}</el-descriptions-item>
            <el-descriptions-item label="楼栋">{{ detailTask.buildingName || '—' }}</el-descriptions-item>
            <el-descriptions-item label="巡检类型">{{ inspectionTypeLabel(detailTask.inspectionType) }}</el-descriptions-item>
            <el-descriptions-item label="创建时间"><AppDateTime :value="detailTask.createdAt" /></el-descriptions-item>
          </el-descriptions>
        </el-card>

        <el-card shadow="never" class="detail-card">
          <template #header><strong>巡检记录</strong></template>
          <div v-if="detailRecords.length" class="record-list">
            <div v-for="record in detailRecords" :key="record.recordId" class="record-row">
              <div>
                <strong>{{ record.inspectionPart || '现场检查' }}</strong>
                <AppStatusTag :status="record.severity" variant="severity" />
              </div>
              <p>{{ record.summary }}</p>
              <small v-if="record.rectificationSuggestion">整改建议：{{ record.rectificationSuggestion }}</small>
            </div>
          </div>
          <AppEmpty v-else description="当前任务暂无巡检记录" />
        </el-card>

        <el-card shadow="never" class="detail-card">
          <InspectionImageGallery
            :task-id="detailTask.taskId"
            :refresh-key="detailGalleryKey"
            editable
            @result-selected="displayDetailInference"
          />
        </el-card>

        <el-card shadow="never" class="detail-card ai-result-card">
          <template #header>
            <div class="card-head">
              <div>
                <strong>✦ AI 视觉识别</strong>
                <small>选择已完成的图片结果后，查看标注区域、AI 巡检摘要与人工复核建议。</small>
              </div>
            </div>
          </template>
          <AppLoading :visible="detailAiLoading" inline text="加载 AI 视觉识别结果中…" />
          <template v-if="detailInference">
            <AiInspectionSummary :detections="detailDetections" compact />
            <AppLoading :visible="detailCombinedSummaryLoading" inline text="结合巡检员记录生成 AI 巡检综合总结…" />
            <AiInspectionCombinedSummary
              v-if="detailCombinedSummary"
              :summary="detailCombinedSummary"
            />
            <AiDetectionOverlay
              v-if="detailInference.status === 'SUCCEEDED' && detailDetections.length && detailImageUrl"
              :detections="detailDetections"
              :image-width="detailInference.imageWidth || 1"
              :image-height="detailInference.imageHeight || 1"
              :image-src="detailImageUrl"
            />
            <img v-else-if="detailImageUrl" :src="detailImageUrl" class="detail-result-image" alt="巡检分析图片" />
            <div class="ai-result-meta">
              <AppStatusTag :status="detailInference.status" variant="task" />
              <span>人工复核：{{ detailInference.reviewStatus === 'UNREVIEWED' ? '待复核' : '已处理' }}</span>
            </div>
            <p v-if="detailSummary" class="ai-summary">{{ detailSummary }}</p>
          </template>
          <AppEmpty v-else-if="!detailAiLoading" description="图片分析完成后点击「查看结果」即可显示不规则高亮区域" />
        </el-card>
      </div>
    </el-drawer>
  </section>
</template>

<style scoped lang="scss">
.inspection-page { display: grid; gap: 14px; }
.surface-card, .detail-card { border-radius: var(--usp-radius-xl); box-shadow: var(--usp-shadow-sm); }
.surface-card :deep(.el-card__body), .detail-card :deep(.el-card__body) { display: grid; gap: 12px; }
.card-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.card-head > div { display: grid; gap: 2px; }
.card-head small { color: var(--usp-color-text-secondary); font-size: 12px; }
.inspection-ai-chain { display: flex; align-items: stretch; gap: 8px; padding: 12px; overflow-x: auto; border: 1px solid var(--usp-color-border); border-radius: var(--usp-radius-xl); background: var(--usp-color-surface); box-shadow: var(--usp-shadow-sm); }
.inspection-ai-chain > div { display: grid; min-width: 150px; flex: 1; gap: 3px; padding: 10px 11px; border-radius: var(--usp-radius-lg); background: var(--usp-color-surface-muted); }
.inspection-ai-chain > div span { display: grid; width: 22px; height: 22px; place-items: center; border-radius: 999px; background: var(--usp-color-primary-soft); color: var(--usp-color-primary-strong); font-size: 10px; font-weight: 900; }
.inspection-ai-chain > div strong { font-size: 12px; }
.inspection-ai-chain > div small { color: var(--usp-color-text-secondary); font-size: 10px; line-height: 1.45; }
.inspection-ai-chain > b { align-self: center; color: var(--usp-color-text-tertiary); font-size: 15px; }
.task-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.drawer-form { display: grid; gap: 18px; }
.drawer-footer { display: flex; justify-content: flex-end; gap: 8px; }
.detail-layout { display: grid; gap: 16px; padding-bottom: 12px; }
.record-list { display: grid; gap: 10px; }
.record-row { padding: 12px; border: 1px solid var(--usp-color-border); border-radius: var(--usp-radius-lg); background: var(--usp-color-surface); }
.record-row > div { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.record-row p { margin: 7px 0 0; color: var(--usp-color-text-secondary); line-height: 1.6; }
.record-row small { display: block; margin-top: 6px; color: var(--usp-color-text-tertiary); }
.detail-result-image { width: 100%; max-height: 440px; object-fit: contain; border-radius: var(--usp-radius-lg); background: #111; }
.ai-result-meta { display: flex; align-items: center; flex-wrap: wrap; gap: 8px 14px; color: var(--usp-color-text-secondary); font-size: 12px; }
.ai-summary { margin: 0; padding: 12px; border-radius: var(--usp-radius-lg); background: var(--usp-color-primary-light); color: var(--usp-color-text-secondary); line-height: 1.65; }
.inspection-page :deep(.el-input__wrapper),
.inspection-page :deep(.el-select__wrapper),
.inspection-page :deep(.el-date-editor.el-input__wrapper),
.inspection-page :deep(.el-button),
.inspection-page :deep(.el-radio-button__inner) { border-radius: var(--usp-radius-lg); }
.inspection-page :deep(.el-table) { border-radius: var(--usp-radius-lg); }

@media (max-width: 760px) {
  .detail-layout :deep(.el-descriptions__body) { overflow-x: auto; }
}
</style>