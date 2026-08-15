<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Loading } from '@element-plus/icons-vue'
import * as api from '@/shared/api'
import {
  toAppError,
  type AiInferenceTask,
  type AiReviewStatus,
  type AiReviewedRiskLevel,
  type AiIntelligentAnalysisResult,
  type AiRiskSignal,
} from '@/shared/api'
import { useAppStore } from '@/stores/app'
import { formatAiConfidence, formatAiDetectionLabel } from '@/shared/ai/ai-display'
import { translateAiBusinessError } from '@/shared/ai/ai-copy'
import AppLoading from '@/shared/components/AppLoading.vue'
import AppError from '@/shared/components/AppError.vue'
import AppStatusTag from '@/shared/components/AppStatusTag.vue'
import AppPageHeader from '@/shared/components/layout/AppPageHeader.vue'
import AiInsightCard from '@/shared/components/ai/AiInsightCard.vue'
import AiDetectionOverlay from '@/pages/AiDetectionOverlay.vue'
import { resolveModelAttribution } from '@/pages/console/reviewModelAttribution'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const inferenceId = computed(() => String(route.params.inferenceId ?? ''))
const task = ref<AiInferenceTask | null>(null)
const structured = computed(() => task.value?.structuredResult ?? null)
const imageUrl = ref('')
const loading = ref(false)
const imageLoading = ref(false)
const showAiOverlay = ref(true)
const saving = ref(false)
const errorMessage = ref('')
const imageErrorMessage = ref('')
const reviewStatus = ref<AiReviewStatus>('CONFIRMED')
const reviewedRiskLevel = ref<AiReviewedRiskLevel | ''>('')
const comment = ref('')
const agentAnalysis = ref<AiIntelligentAnalysisResult | null>(null)
const agentAnalysisLoading = ref(false)
const agentAnalysisError = ref('')
let pollTimer: ReturnType<typeof setTimeout> | undefined

const modelAttribution = computed(() => task.value ? resolveModelAttribution(task.value) : '')
const taskRunning = computed(() => task.value?.status === 'PENDING' || task.value?.status === 'RUNNING')
const aiSummary = computed(() => structured.value?.summary || task.value?.summary?.summary || task.value?.assessmentNote || '当前没有可展示的 AI 判断。')
const aiSuggestion = computed(() => structured.value?.recommendations?.[0] || '请结合原始证据和现场专业判断形成最终复核结论。')

const RISK_CODES: Record<string, string> = {
  CRACK: 'VISUAL_CRACK',
  SPALLING: 'VISUAL_SPALLING',
  EXPOSED_REBAR: 'VISUAL_EXPOSED_REBAR',
  CORROSION: 'VISUAL_CORROSION',
  WATER_STAIN: 'VISUAL_WATER_STAIN',
  SURFACE_DAMAGE: 'VISUAL_SURFACE_DAMAGE',
}
const RISK_TITLES: Record<string, string> = {
  VISUAL_CRACK: '疑似裂缝',
  VISUAL_SPALLING: '疑似剥落',
  VISUAL_EXPOSED_REBAR: '疑似露筋',
  VISUAL_CORROSION: '疑似锈蚀',
  VISUAL_WATER_STAIN: '疑似水渍',
  VISUAL_SURFACE_DAMAGE: '疑似表面损伤',
}

const riskSignalsDerivedFromDetections = computed(
  () => !(structured.value?.riskSignals?.length) && (task.value?.detections?.length ?? 0) > 0,
)
const displayRiskSignals = computed<AiRiskSignal[]>(() => {
  if (structured.value?.riskSignals?.length) return structured.value.riskSignals
  const strongest = new Map<string, { name: string; confidence: number }>()
  for (const detection of task.value?.detections ?? []) {
    const code = RISK_CODES[detection.classCode]
    if (!code) continue
    const current = strongest.get(code)
    if (!current || detection.confidence > current.confidence) strongest.set(code, { name: detection.className, confidence: detection.confidence })
  }
  return [...strongest.entries()].map(([code, value]) => ({
    code,
    level: value.confidence >= 0.65 ? 'HIGH' : value.confidence >= 0.4 ? 'MEDIUM' : 'LOW',
    confidence: value.confidence,
    description: `根据已有检测候选整理：${formatAiDetectionLabel(value.name, value.confidence)}。该信号仅用于复核展示，不代表正式风险等级。`,
  }))
})

function riskSignalTitle(signal: AiRiskSignal): string {
  return RISK_TITLES[signal.code ?? ''] ?? signal.code ?? '视觉风险信号'
}

function riskLevelLabel(level?: string): string {
  if (level === 'VERY_HIGH') return '极高关注'
  if (level === 'HIGH') return '较高关注'
  if (level === 'MEDIUM') return '中等关注'
  if (level === 'LOW') return '一般关注'
  return level || '待复核'
}

function riskTagType(level?: string): 'danger' | 'warning' | 'success' | 'info' {
  if (level === 'VERY_HIGH' || level === 'HIGH') return 'danger'
  if (level === 'MEDIUM') return 'warning'
  if (level === 'LOW') return 'success'
  return 'info'
}

function reviewStatusLabel(value?: string): string {
  return ({ UNREVIEWED: '待人工复核', CONFIRMED: '人工已确认', CORRECTED: '人工已修正', REJECTED: '人工已驳回' } as Record<string, string>)[value ?? ''] ?? '待人工复核'
}

function releaseImage(): void {
  if (imageUrl.value) URL.revokeObjectURL(imageUrl.value)
  imageUrl.value = ''
}

function clearPoll(): void {
  if (pollTimer) clearTimeout(pollTimer)
  pollTimer = undefined
}

function schedulePoll(): void {
  clearPoll()
  if (!taskRunning.value) return
  pollTimer = setTimeout(() => void refreshRunningTask(), 1600)
}

async function refreshRunningTask(): Promise<void> {
  try {
    const next = await api.getAiInference(inferenceId.value)
    const wasRunning = taskRunning.value
    task.value = next
    syncReviewFormFromTask(next)
    if (!taskRunning.value && wasRunning) {
      appStore.notify(next.status === 'SUCCEEDED' ? 'AI 视觉识别已完成' : 'AI 视觉识别已结束', next.status === 'SUCCEEDED' ? 'success' : 'warning')
      await loadImage()
    }
  } catch {
    // 轮询失败不覆盖已有业务数据，下一轮继续尝试。
  } finally {
    schedulePoll()
  }
}

async function loadImage(): Promise<void> {
  releaseImage()
  imageErrorMessage.value = ''
  const assetId = task.value?.assetId
  if (!assetId) {
    imageErrorMessage.value = '该任务未关联原始图片。'
    return
  }
  imageLoading.value = true
  try {
    imageUrl.value = await api.fetchImageBlobUrl(assetId)
  } catch (error) {
    imageErrorMessage.value = toAppError(error).message
  } finally {
    imageLoading.value = false
  }
}

function syncReviewFormFromTask(next: AiInferenceTask): void {
  const savedReviewStatus = next.latestReview?.reviewStatus
  if (savedReviewStatus && ['CONFIRMED', 'CORRECTED', 'REJECTED'].includes(savedReviewStatus)) {
    reviewStatus.value = savedReviewStatus as AiReviewStatus
  }
  reviewedRiskLevel.value = next.latestReview?.correctedData?.reviewedRiskLevel ?? ''
  comment.value = next.latestReview?.comment ?? ''
}

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  imageErrorMessage.value = ''
  clearPoll()
  releaseImage()
  try {
    task.value = await api.getAiInference(inferenceId.value)
    syncReviewFormFromTask(task.value)
  } catch (error) {
    task.value = null
    errorMessage.value = toAppError(error).message
    return
  } finally {
    loading.value = false
  }
  await loadImage()
  schedulePoll()
}

async function submit(): Promise<void> {
  if (!task.value) return
  saving.value = true
  try {
    await api.submitAiReview(
      task.value.inferenceId,
      reviewStatus.value,
      comment.value.trim() || undefined,
      reviewedRiskLevel.value ? { reviewedRiskLevel: reviewedRiskLevel.value } : undefined,
    )
    appStore.notify('人工复核结果已提交。', 'success')
    await load()
  } catch (error) {
    appStore.notify(toAppError(error).message, 'error')
  } finally {
    saving.value = false
  }
}

function openRiskMap(): void {
  void router.push('/console/map')
}

async function runAgentAnalysis(): Promise<void> {
  if (!task.value || taskRunning.value) return
  agentAnalysisLoading.value = true
  agentAnalysisError.value = ''
  try {
    agentAnalysis.value = await api.runIntelligentAnalysis({
      businessType: 'AI_INFERENCE',
      businessId: task.value.buildingId ?? undefined,
      question: '请结合这栋楼的巡检图片、历史正式风险和当前更新优先级进行综合分析，给出疑似病害、判断依据与人工复核建议。不得修改正式风险评分。',
      context: task.value.assetId ? { assetId: task.value.assetId } : undefined,
    })
  } catch (error) {
    agentAnalysisError.value = toAppError(error).message
  } finally {
    agentAnalysisLoading.value = false
  }
}

onMounted(load)
onBeforeUnmount(() => {
  clearPoll()
  releaseImage()
})
</script>

<template>
  <section class="detail-page">
    <AppLoading :visible="loading" inline text="加载复核结果中…" />
    <AppError v-if="errorMessage" :message="errorMessage" @retry="load" />

    <template v-if="task && !loading">
      <AppPageHeader
        :eyebrow="`AI 人工复核 · ${task.requestCode}`"
        title="AI 发现专业复核"
        description="左侧看 AI 判断，中间核对原始证据，右侧由专业人员确认、修正或驳回。"
      >
        <template #actions>
          <el-button plain round @click="router.push('/console/review')">← 返回复核中心</el-button>
          <AppStatusTag :status="task.status" variant="task" />
          <el-tag effect="plain" round>{{ reviewStatusLabel(task.reviewStatus) }}</el-tag>
        </template>
      </AppPageHeader>

      <div v-if="taskRunning" class="running-panel">
        <el-icon class="is-loading"><Loading /></el-icon>
        <div><strong>AI 视觉识别正在分析</strong><span>页面会自动恢复任务轮询，基础巡检和人工操作不受影响。</span></div>
        <el-tag type="warning" round>分析中</el-tag>
      </div>

      <div v-if="task.errorCode" class="status-panel status-panel--danger">
        <strong>AI 辅助能力暂时不可用</strong>
        <span>{{ translateAiBusinessError(task.errorCode) }}</span>
      </div>

      <div class="review-layout">
        <section class="review-column judgment-column">
          <div class="column-title"><span>01</span><div><strong>AI判断</strong><small>发生了什么、AI 怎么看、为什么这样判断</small></div></div>

          <AiInsightCard title="AI 辅助复核" :summary="aiSummary" :suggestion="aiSuggestion" compact>
            <template #actions>
              <el-button type="primary" plain round :loading="agentAnalysisLoading" :disabled="taskRunning" @click="runAgentAnalysis">重新综合研判</el-button>
            </template>
          </AiInsightCard>

          <el-card class="surface-card" shadow="never">
            <template #header><div class="card-head"><div><strong>AI 视觉风险信号</strong><small>关注等级不是正式楼栋风险等级。</small></div><el-tag v-if="riskSignalsDerivedFromDetections" type="info" effect="plain" round>由检测候选整理</el-tag></div></template>
            <div v-if="displayRiskSignals.length" class="signal-list">
              <article v-for="signal in displayRiskSignals" :key="`${signal.code}-${signal.description}`">
                <div><strong>{{ riskSignalTitle(signal) }}</strong><el-tag :type="riskTagType(signal.level)" effect="plain" round>{{ riskLevelLabel(signal.level) }}</el-tag></div>
                <p>{{ signal.description }}</p>
                <small>{{ formatAiConfidence(signal.confidence) ? `AI 可信度 ${formatAiConfidence(signal.confidence)}` : '低可信或无可展示百分比 · 请人工核对原始证据' }}</small>
              </article>
            </div>
            <el-empty v-else description="当前没有可展示的视觉风险信号" />
          </el-card>

          <el-card v-if="agentAnalysis || agentAnalysisLoading || agentAnalysisError" class="surface-card" shadow="never">
            <template #header><div class="card-head"><div><strong>AI 综合研判</strong><small>综合已有视觉、巡检、正式风险与治理优先级证据。</small></div><el-tag v-if="agentAnalysis" effect="plain" round>{{ agentAnalysis.status }}</el-tag></div></template>
            <div v-loading="agentAnalysisLoading" class="agent-analysis-content">
              <el-alert v-if="agentAnalysisError" :title="agentAnalysisError" type="warning" :closable="false" show-icon />
              <p v-if="agentAnalysis">{{ agentAnalysis.answer }}</p>
            </div>
          </el-card>
        </section>

        <section class="review-column evidence-column">
          <div class="column-title"><span>02</span><div><strong>原始证据</strong><small>原图、Polygon 与结构化检测候选</small></div></div>

          <el-card class="surface-card evidence-card" shadow="never">
            <template #header><div class="card-head"><div><strong>原始巡检图片</strong><small>可叠加 AI 标注用于位置核对</small></div><el-switch v-model="showAiOverlay" active-text="显示 AI 标注" /></div></template>
            <AppLoading :visible="imageLoading" inline text="加载原始图片中…" />
            <AppError v-if="imageErrorMessage" :message="imageErrorMessage" />
            <AiDetectionOverlay
              v-if="imageUrl && showAiOverlay && (structured?.detections?.length || task.detections?.length)"
              :detections="structured?.detections?.length ? structured.detections : task.detections"
              :image-width="task.imageWidth || 1"
              :image-height="task.imageHeight || 1"
              :image-src="imageUrl"
            />
            <img v-else-if="imageUrl" :src="imageUrl" class="review-image" alt="巡检原始证据" />
          </el-card>

          <el-card class="surface-card" shadow="never">
            <template #header><div class="card-head"><div><strong>检测候选</strong><small>低于 40% 的候选保留病害名和区域，不显示百分比。</small></div></div></template>
            <div v-if="(structured?.detections?.length || task.detections?.length)" class="detection-list">
              <article v-for="(item,index) in (structured?.detections?.length ? structured.detections : task.detections)" :key="`${item.classCode}-${index}`">
                <strong>{{ formatAiDetectionLabel(item.className || item.classCode, item.confidence) }}</strong>
                <small>{{ item.segmentation ? `区域标注 ${item.segmentation.type}` : '矩形检测区域' }}</small>
              </article>
            </div>
            <el-empty v-else description="没有结构化检测候选" />
          </el-card>
        </section>

        <section class="review-column action-column">
          <div class="column-title"><span>03</span><div><strong>人工复核</strong><small>确认、修正或驳回均必须由人工提交</small></div></div>

          <el-card class="surface-card review-form-card" shadow="never">
            <template #header><div class="card-head"><div><strong>形成专业结论</strong><small>AI 绝不会自动提交这里的选择。</small></div></div></template>
            <el-form label-position="top">
              <el-form-item label="复核结论">
                <el-radio-group v-model="reviewStatus">
                  <el-radio-button value="CONFIRMED">确认</el-radio-button>
                  <el-radio-button value="CORRECTED">修正</el-radio-button>
                  <el-radio-button value="REJECTED">驳回</el-radio-button>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="复核风险度（辅助）">
                <div class="review-risk-field">
                  <el-select v-model="reviewedRiskLevel" clearable placeholder="选择人工复核后的关注程度">
                    <el-option label="低" value="LOW" />
                    <el-option label="中" value="MEDIUM" />
                    <el-option label="高" value="HIGH" />
                    <el-option label="极高" value="VERY_HIGH" />
                  </el-select>
                  <small>仅保存本次人工复核的辅助风险判断，不直接修改正式风险评分；正式风险仍需由规则评分链重新计算。</small>
                </div>
              </el-form-item>
              <el-form-item label="专业意见">
                <el-input v-model="comment" type="textarea" :rows="8" maxlength="2000" show-word-limit placeholder="填写核对依据、修正内容或驳回原因" />
              </el-form-item>
            </el-form>
            <div class="review-actions"><el-button @click="openRiskMap">查看地图</el-button><el-button type="primary" :loading="saving" @click="submit">提交人工复核</el-button></div>
          </el-card>

          <details class="technical-details">
            <summary>专业技术详情</summary>
            <dl>
              <div><dt>请求编号</dt><dd>{{ task.requestCode }}</dd></div>
              <div><dt>识别来源</dt><dd>{{ modelAttribution || '—' }}</dd></div>
              <div><dt>模型版本</dt><dd>{{ task.modelVersion || '—' }}</dd></div>
              <div><dt>任务 ID</dt><dd>{{ task.inferenceId }}</dd></div>
              <div v-if="task.errorCode"><dt>错误码</dt><dd>{{ task.errorCode }}</dd></div>
            </dl>
          </details>
        </section>
      </div>
    </template>
  </section>
</template>

<style scoped lang="scss">
.detail-page{display:grid;gap:14px}.running-panel,.status-panel{display:grid;grid-template-columns:auto 1fr auto;align-items:center;gap:12px;padding:12px 14px;border-radius:var(--usp-radius-xl)}.running-panel{border:1px solid #f5d08a;background:#fff9eb;color:#7a4d00}.running-panel>div{display:grid;gap:2px}.running-panel span{font-size:11px}.status-panel--danger{grid-template-columns:1fr;border:1px solid #f0b5b5;background:#fff3f3;color:#8c2a2a}.review-layout{display:grid;grid-template-columns:minmax(280px,.82fr) minmax(360px,1.18fr) minmax(290px,.86fr);gap:14px;align-items:start}.review-column{display:grid;min-width:0;gap:12px}.column-title{display:flex;align-items:center;gap:9px;padding:0 2px}.column-title>span{display:grid;width:28px;height:28px;place-items:center;border-radius:999px;background:var(--usp-color-primary-soft);color:var(--usp-color-primary-strong);font-size:10px;font-weight:900}.column-title>div{display:grid;gap:1px}.column-title strong{font-size:14px}.column-title small,.card-head small{color:var(--usp-color-text-secondary);font-size:10px}.surface-card{border-radius:var(--usp-radius-xl);box-shadow:var(--usp-shadow-sm)}.surface-card :deep(.el-card__body){display:grid;gap:11px;padding:13px}.surface-card :deep(.el-card__header){padding:11px 13px}.card-head{display:flex;align-items:center;justify-content:space-between;gap:9px}.card-head>div:first-child{display:grid;gap:2px}.signal-list,.detection-list{display:grid;gap:8px}.signal-list article,.detection-list article{display:grid;gap:5px;padding:9px 10px;border:1px solid var(--usp-color-border);border-radius:var(--usp-radius-lg);background:var(--usp-color-surface-muted)}.signal-list article>div{display:flex;align-items:center;justify-content:space-between;gap:8px}.signal-list p,.agent-analysis-content p{margin:0;color:var(--usp-color-text-secondary);line-height:1.6}.signal-list small,.detection-list small{color:var(--usp-color-text-tertiary)}.evidence-card{overflow:hidden}.review-image{width:100%;max-height:560px;object-fit:contain;border-radius:var(--usp-radius-lg);background:#111}.review-form-card :deep(.el-radio-group){display:flex;width:100%}.review-form-card :deep(.el-radio-button){flex:1}.review-form-card :deep(.el-radio-button__inner){width:100%}.review-risk-field{display:grid;width:100%;gap:6px}.review-risk-field small{color:var(--usp-color-text-tertiary);font-size:10px;line-height:1.5}.review-actions{display:flex;justify-content:flex-end;gap:8px}.technical-details{padding:10px 12px;border:1px solid var(--usp-color-border);border-radius:var(--usp-radius-xl);background:var(--usp-color-surface)}.technical-details summary{cursor:pointer;color:var(--usp-color-text-secondary);font-size:11px;font-weight:700}.technical-details dl{display:grid;gap:7px;margin:10px 0 0}.technical-details dl>div{display:grid;grid-template-columns:78px minmax(0,1fr);gap:8px}.technical-details dt{color:var(--usp-color-text-tertiary);font-size:10px}.technical-details dd{min-width:0;margin:0;overflow-wrap:anywhere;color:var(--usp-color-text-secondary);font-size:10px}.detail-page :deep(.el-input__wrapper),.detail-page :deep(.el-select__wrapper),.detail-page :deep(.el-textarea__inner),.detail-page :deep(.el-button),.detail-page :deep(.el-radio-button__inner){border-radius:var(--usp-radius-lg)}@media(max-width:1180px){.review-layout{grid-template-columns:1fr 1fr}.action-column{grid-column:1/-1}}@media(max-width:760px){.review-layout{grid-template-columns:1fr}.action-column{grid-column:auto}.review-actions{justify-content:stretch}.review-actions :deep(.el-button){flex:1}}
</style>
