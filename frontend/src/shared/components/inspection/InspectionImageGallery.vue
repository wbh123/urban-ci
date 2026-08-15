<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import {
  fetchImageBlobUrl,
  getInspectionImageExecution,
  getInspectionImageRichResult,
  listImages,
  listInspectionTaskExecutions,
  listInspectionTaskInferences,
  mapInferenceToImageStatus,
  submitInspectionImageAnalysis,
  toAppError,
  type AiInferenceTask,
  type AssetImageRow,
  type InspectionImageAnalysisStatus,
  type InspectionImageExecution,
} from '@/shared/api'
import AiDetectionOverlay from '@/pages/AiDetectionOverlay.vue'
import AppEmpty from '@/shared/components/AppEmpty.vue'
import AppLoading from '@/shared/components/AppLoading.vue'
import {
  selectDrawableDetections,
  type DrawableDetection,
} from './detection-display'

const props = withDefaults(defineProps<{
  taskId: string
  editable?: boolean
  showTechnicalRoute?: boolean
  showResultAction?: boolean
  refreshKey?: number
}>(), {
  editable: true,
  showTechnicalRoute: false,
  showResultAction: true,
  refreshKey: 0,
})

const emit = defineEmits<{
  'result-selected': [assetId: string, inferenceId: string]
  'analysis-changed': []
}>()

const POLL_INTERVAL_MS = 2000
const MAX_POLLS = 300

interface GalleryRow {
  image: AssetImageRow
  status: InspectionImageAnalysisStatus
  execution: InspectionImageExecution | null
  inference: AiInferenceTask | null
  richInference: AiInferenceTask | null
  drawableDetections: DrawableDetection[]
  inferenceId: string | null
  imageUrl: string
}

const images = ref<AssetImageRow[]>([])
const executions = ref<InspectionImageExecution[]>([])
const inferences = ref<AiInferenceTask[]>([])
const richResults = ref<Record<string, AiInferenceTask>>({})
const imageUrls = ref<Record<string, string>>({})
const submittingAssetIds = ref<string[]>([])
const richLoadingIds = ref<string[]>([])
const loading = ref(false)
const aiLoading = ref(false)
const errorMessage = ref('')
const aiMessage = ref('')
const pollingGenerations = new Map<string, number>()
let generation = 0
let disposed = false

function epoch(value: string | null | undefined): number {
  if (!value) return 0
  const parsed = new Date(value).getTime()
  return Number.isFinite(parsed) ? parsed : 0
}

const latestExecutionByAsset = computed(() => {
  const map = new Map<string, InspectionImageExecution>()
  const ordered = [...executions.value].sort(
    (a, b) => epoch(b.createdAt) - epoch(a.createdAt),
  )
  for (const item of ordered) {
    if (item.assetId && !map.has(item.assetId)) map.set(item.assetId, item)
  }
  return map
})

const latestInferenceByAsset = computed(() => {
  const map = new Map<string, AiInferenceTask>()
  const ordered = [...inferences.value].sort(
    (a, b) => epoch(b.createdAt ?? b.requestedAt) - epoch(a.createdAt ?? a.requestedAt),
  )
  for (const item of ordered) {
    if (item.assetId && !map.has(item.assetId)) map.set(item.assetId, item)
  }
  return map
})

const rows = computed<GalleryRow[]>(() => images.value.map((image) => {
  const execution = latestExecutionByAsset.value.get(image.assetId) ?? null
  const inference = latestInferenceByAsset.value.get(image.assetId) ?? null
  const stateSource = execution?.status ?? inference?.status
  const inferenceId = execution?.inferenceId ?? inference?.inferenceId ?? null
  const richInference = inferenceId ? richResults.value[inferenceId] ?? null : null
  return {
    image,
    status: mapInferenceToImageStatus(stateSource),
    execution,
    inference,
    richInference,
    drawableDetections: selectDrawableDetections(richInference),
    inferenceId,
    imageUrl: imageUrls.value[image.assetId] ?? '',
  }
}))

const analyzableRows = computed(() => rows.value.filter(
  (row) => row.status === 'NOT_ANALYZED' || row.status === 'FAILED',
))

function statusLabel(status: InspectionImageAnalysisStatus): string {
  const labels: Record<InspectionImageAnalysisStatus, string> = {
    NOT_ANALYZED: '未分析',
    QUEUED: '排队中',
    RUNNING: 'AI 分析中',
    SUCCEEDED: '已完成',
    FAILED: '分析失败',
  }
  return labels[status]
}

function statusType(status: InspectionImageAnalysisStatus): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'SUCCEEDED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'QUEUED' || status === 'RUNNING') return 'warning'
  return 'info'
}

function routeLabel(row: GalleryRow): string {
  const execution = row.execution
  if (execution?.fallback) return '本地高精度模型 · 已自动回退'
  if (execution?.orchestrationMode === 'DIFY_PREFERRED') return '智能工作流'
  if (execution?.orchestrationMode === 'SPRING_AI_LOCAL') return '本地高精度模型'
  if (row.inference?.providerCode === 'DIFY') return '智能工作流'
  if (row.inference) return '本地高精度模型'
  return '待确定'
}

function formatTime(value: string | null | undefined): string {
  if (!value) return '—'
  const date = new Date(value)
  if (!Number.isFinite(date.getTime())) return value
  return date.toLocaleString('zh-CN', { hour12: false })
}

function revokeImageUrls(): void {
  for (const url of Object.values(imageUrls.value)) {
    if (url) URL.revokeObjectURL(url)
  }
  imageUrls.value = {}
}

async function loadImageUrls(currentGeneration: number): Promise<void> {
  revokeImageUrls()
  const next: Record<string, string> = {}
  await Promise.all(images.value.map(async (image) => {
    try {
      const url = await fetchImageBlobUrl(image.assetId)
      if (disposed || currentGeneration !== generation) {
        URL.revokeObjectURL(url)
        return
      }
      next[image.assetId] = url
    } catch {
      // 单张图片内容不可用不应阻塞其余历史图片和 AI 状态。
    }
  }))
  if (!disposed && currentGeneration === generation) imageUrls.value = next
}

function isActiveExecution(status: InspectionImageExecution['status']): boolean {
  return status === 'PENDING'
    || status === 'READY'
    || status === 'RUNNING'
    || status === 'RETRY_WAIT'
}

function replaceExecution(next: InspectionImageExecution): void {
  const remaining = executions.value.filter((item) => item.taskId !== next.taskId)
  executions.value = [next, ...remaining]
}

function setRichLoading(inferenceId: string, active: boolean): void {
  const current = new Set(richLoadingIds.value)
  if (active) current.add(inferenceId)
  else current.delete(inferenceId)
  richLoadingIds.value = [...current]
}

async function loadSucceededRichResults(): Promise<void> {
  const targets = rows.value.filter((row) =>
    row.status === 'SUCCEEDED'
    && Boolean(row.inferenceId)
    && !row.richInference
    && !richLoadingIds.value.includes(row.inferenceId as string),
  )
  await Promise.all(targets.map(async (row) => {
    const inferenceId = row.inferenceId
    if (!inferenceId) return
    setRichLoading(inferenceId, true)
    try {
      const rich = await getInspectionImageRichResult(inferenceId)
      if (!disposed) richResults.value = { ...richResults.value, [inferenceId]: rich }
    } catch (error) {
      if (!disposed) aiMessage.value = `AI 结果加载失败：${toAppError(error).message}`
    } finally {
      setRichLoading(inferenceId, false)
    }
  }))
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

async function pollExecution(assetId: string, taskId: string, startGeneration: number): Promise<void> {
  for (let attempt = 0; attempt < MAX_POLLS; attempt += 1) {
    await delay(POLL_INTERVAL_MS)
    if (disposed || startGeneration !== generation) return
    try {
      const execution = await getInspectionImageExecution(taskId)
      replaceExecution({ ...execution, assetId: execution.assetId ?? assetId })
      if (execution.status === 'SUCCEEDED' || execution.status === 'REJECTED') {
        await refreshAiState(false)
        emit('analysis-changed')
        return
      }
      if (execution.status === 'FAILED' || execution.status === 'CANCELLED') {
        aiMessage.value = execution.errorMessage || 'AI 分析失败，可重新分析。'
        emit('analysis-changed')
        return
      }
    } catch (error) {
      aiMessage.value = `AI 状态刷新失败：${toAppError(error).message}`
      return
    }
  }
  aiMessage.value = 'AI 仍在后台运行，可稍后刷新查看结果。'
}

function startPolling(assetId: string, taskId: string): void {
  const startGeneration = generation
  if (pollingGenerations.get(taskId) === startGeneration) return
  pollingGenerations.set(taskId, startGeneration)
  void pollExecution(assetId, taskId, startGeneration).finally(() => {
    if (pollingGenerations.get(taskId) === startGeneration) pollingGenerations.delete(taskId)
  })
}

function resumeActivePolling(): void {
  for (const execution of executions.value) {
    if (execution.assetId && isActiveExecution(execution.status)) {
      startPolling(execution.assetId, execution.taskId)
    }
  }
}

async function refreshAiState(resumePolling = true): Promise<void> {
  if (!props.taskId) {
    executions.value = []
    inferences.value = []
    richResults.value = {}
    return
  }
  aiLoading.value = true
  aiMessage.value = ''
  try {
    const [executionRows, inferencePage] = await Promise.all([
      listInspectionTaskExecutions(props.taskId),
      listInspectionTaskInferences(props.taskId),
    ])
    executions.value = executionRows
    inferences.value = inferencePage.content ?? []
    if (resumePolling) resumeActivePolling()
    await loadSucceededRichResults()
  } catch (error) {
    aiMessage.value = `AI 状态加载失败：${toAppError(error).message}`
  } finally {
    aiLoading.value = false
  }
}

async function refresh(): Promise<void> {
  generation += 1
  pollingGenerations.clear()
  const currentGeneration = generation
  errorMessage.value = ''
  aiMessage.value = ''
  if (!props.taskId) {
    images.value = []
    executions.value = []
    inferences.value = []
    richResults.value = {}
    revokeImageUrls()
    return
  }
  loading.value = true
  try {
    const imagePage = await listImages({
      businessType: 'INSPECTION_TASK',
      businessId: props.taskId,
    })
    if (disposed || currentGeneration !== generation) return
    images.value = imagePage.content ?? []
    await loadImageUrls(currentGeneration)
  } catch (error) {
    errorMessage.value = `巡检图片加载失败：${toAppError(error).message}`
  } finally {
    if (!disposed && currentGeneration === generation) loading.value = false
  }
  // AI 服务不可用不能阻止已经上传的图片显示。
  if (!disposed && currentGeneration === generation) await refreshAiState()
}

function trackExecution(assetId: string, taskId: string): void {
  replaceExecution({
    taskId,
    assetId,
    status: 'PENDING',
    triggerType: 'UPLOAD_AUTO',
  })
  startPolling(assetId, taskId)
}

function setSubmitting(assetId: string, active: boolean): void {
  const current = new Set(submittingAssetIds.value)
  if (active) current.add(assetId)
  else current.delete(assetId)
  submittingAssetIds.value = [...current]
}

async function submitOne(
  assetId: string,
  triggerType: 'MANUAL_SINGLE' | 'MANUAL_BATCH',
): Promise<void> {
  setSubmitting(assetId, true)
  aiMessage.value = ''
  try {
    const submission = await submitInspectionImageAnalysis(assetId, triggerType)
    replaceExecution({
      taskId: submission.taskId,
      assetId,
      status: 'PENDING',
      triggerType,
    })
    startPolling(assetId, submission.taskId)
    emit('analysis-changed')
  } catch (error) {
    aiMessage.value = `AI 分析任务提交失败：${toAppError(error).message}`
  } finally {
    setSubmitting(assetId, false)
  }
}

async function analyzeAll(): Promise<void> {
  const targets = analyzableRows.value.map((row) => row.image.assetId)
  for (const assetId of targets) {
    await submitOne(assetId, 'MANUAL_BATCH')
  }
}

function showResult(row: GalleryRow): void {
  if (!row.inferenceId) return
  emit('result-selected', row.image.assetId, row.inferenceId)
}

watch(
  () => [props.taskId, props.refreshKey] as const,
  () => { void refresh() },
  { immediate: true },
)

onBeforeUnmount(() => {
  disposed = true
  generation += 1
  pollingGenerations.clear()
  revokeImageUrls()
})

defineExpose({ refresh, trackExecution })
</script>

<template>
  <section class="inspection-gallery">
    <header class="gallery-head">
      <div>
        <strong>现场图片</strong>
        <small>共 {{ rows.length }} 张；图片上传成功后立即可见，AI 分析独立在后台运行。</small>
      </div>
      <el-button
        v-if="props.editable && analyzableRows.length"
        type="primary"
        plain
        :disabled="aiLoading"
        @click="analyzeAll"
      >
        分析全部未分析图片
      </el-button>
    </header>

    <AppLoading :visible="loading" inline text="加载巡检图片中…" />
    <p v-if="errorMessage" class="gallery-message error">{{ errorMessage }}</p>
    <p v-if="aiMessage" class="gallery-message warning">{{ aiMessage }}</p>

    <div v-if="rows.length" class="image-grid">
      <article v-for="row in rows" :key="row.image.assetId" class="image-card">
        <div class="image-visual">
          <AiDetectionOverlay
            v-if="row.status === 'SUCCEEDED' && row.richInference && row.drawableDetections.length && row.imageUrl"
            :detections="row.drawableDetections"
            :image-width="row.richInference.imageWidth || 1"
            :image-height="row.richInference.imageHeight || 1"
            :image-src="row.imageUrl"
          />
          <el-image
            v-else
            class="image-preview"
            :src="row.imageUrl"
            :preview-src-list="row.imageUrl ? [row.imageUrl] : []"
            fit="cover"
            preview-teleported
          >
            <template #error><div class="image-unavailable">图片暂不可预览</div></template>
          </el-image>
          <span
            v-if="row.status === 'SUCCEEDED' && row.richInference && !row.drawableDetections.length"
            class="no-detection-badge"
          >未检测到病害候选</span>
        </div>

        <div class="image-body">
          <div class="image-title-row">
            <strong :title="row.image.originalFilename">{{ row.image.originalFilename }}</strong>
            <el-tag :type="statusType(row.status)" round>{{ statusLabel(row.status) }}</el-tag>
          </div>
          <small>上传时间：{{ formatTime(row.image.createdAt) }}</small>
          <small v-if="props.showTechnicalRoute && row.status !== 'NOT_ANALYZED'">
            分析方式：{{ routeLabel(row) }}
          </small>
          <small v-if="props.showTechnicalRoute && row.execution?.fallbackReason">
            回退原因：{{ row.execution.fallbackReason }}
          </small>
          <small v-if="row.status === 'SUCCEEDED' && row.richInference">
            实际模型：{{ row.richInference.modelName }} · v{{ row.richInference.modelVersion }}
          </small>

          <div v-if="props.editable || (props.showResultAction && row.inferenceId)" class="image-actions">
            <el-button
              v-if="props.editable && (row.status === 'NOT_ANALYZED' || row.status === 'FAILED')"
              size="small"
              type="primary"
              :loading="submittingAssetIds.includes(row.image.assetId)"
              @click="submitOne(row.image.assetId, 'MANUAL_SINGLE')"
            >
              {{ row.status === 'FAILED' ? '重新分析' : 'AI 分析' }}
            </el-button>
            <el-button
              v-else-if="row.status === 'QUEUED' || row.status === 'RUNNING'"
              size="small"
              loading
              disabled
            >
              {{ row.status === 'QUEUED' ? '排队中' : 'AI 分析中' }}
            </el-button>
            <el-button
              v-if="props.showResultAction && row.inferenceId && row.status === 'SUCCEEDED'"
              size="small"
              @click="showResult(row)"
            >
              查看结果
            </el-button>
          </div>
        </div>
      </article>
    </div>
    <AppEmpty v-else-if="!loading && !errorMessage" description="当前巡检任务尚未上传现场图片" />
  </section>
</template>

<style scoped lang="scss">
.inspection-gallery { display: grid; gap: 12px; }
.gallery-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.gallery-head > div { display: grid; gap: 3px; }
.gallery-head small { color: var(--usp-color-text-secondary); font-size: 12px; }
.image-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(230px, 1fr)); gap: 12px; }
.image-card { overflow: hidden; border: 1px solid var(--usp-color-border); border-radius: var(--usp-radius-xl); background: var(--usp-color-surface); box-shadow: var(--usp-shadow-sm); }
.image-visual { position: relative; min-height: 168px; background: var(--usp-color-surface-muted, #f5f7f6); }
.image-preview { width: 100%; height: 168px; background: var(--usp-color-surface-muted, #f5f7f6); }
.image-visual :deep(.detection-overlay) { min-height: 220px; max-height: 320px; border-radius: 0; }
.image-visual :deep(.overlay-image) { min-height: 220px; max-height: 320px; }
.no-detection-badge { position: absolute; left: 10px; bottom: 10px; padding: 5px 9px; border-radius: 999px; background: rgba(20, 30, 28, 0.78); color: #fff; font-size: 12px; }
.image-unavailable { display: grid; width: 100%; height: 100%; place-items: center; color: var(--usp-color-text-tertiary); font-size: 12px; }
.image-body { display: grid; gap: 7px; padding: 12px; }
.image-title-row { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.image-title-row strong { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.image-body small { color: var(--usp-color-text-secondary); }
.image-actions { display: flex; flex-wrap: wrap; gap: 8px; padding-top: 3px; }
.gallery-message { margin: 0; padding: 9px 11px; border-radius: var(--usp-radius-lg); font-size: 13px; }
.gallery-message.warning { color: #8a5a00; background: #fff8e6; }
.gallery-message.error { color: #b42318; background: #fff1f0; }
.inspection-gallery :deep(.el-button), .inspection-gallery :deep(.el-tag) { border-radius: var(--usp-radius-lg); }

@media (max-width: 640px) {
  .gallery-head { align-items: stretch; flex-direction: column; }
  .image-grid { grid-template-columns: 1fr; }
}
</style>
