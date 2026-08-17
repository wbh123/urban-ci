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
import AiStructuredAnalysisPanel from '@/shared/components/ai/AiStructuredAnalysisPanel.vue'
import AiDetectionOverlay from '@/pages/AiDetectionOverlay.vue'
import { resolveModelAttribution } from '@/pages/console/reviewModelAttribution'
import { resolveReviewOverlayDetections } from '@/pages/console/reviewDetectionOverlay'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const inferenceId = computed(() => String(route.params.inferenceId ?? ''))
const task = ref<AiInferenceTask | null>(null)
const structured = computed(() => task.value?.structuredResult ?? null)
const overlayDetections = computed(() => resolveReviewOverlayDetections(
  structured.value?.detections ?? [],
  task.value?.detections ?? [],
))
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
const autoAnalysisRequestedForInferenceId = ref('')
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
    level: value.confidence >= 0.55 ? 'HIGH' : value.confidence >= 0.25 ? 'MEDIUM' : 'LOW',
    confidence: value.confidence,
    description: `根据已有检测候选整理：${formatAiDetectionLabel(value.name, value.confidence)}。已采用高敏感辅助筛查阈值；该关注等级仅用于人工复核排序，不代表正式风险等级。`,
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
      appStore.notify(
        next.status === 'SUCCEEDED' ? 'AI 视觉识别已完成，正在自动生成综合研判' : 'AI 视觉识别已结束',
        next.status === 'SUCCEEDED' ? 'success' : 'warning',
      )
      await loadImage()
      if (next.status === 'SUCCEEDED') await ensureAutoAgentAnalysis(next)
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
  if (task.value?.status === 'SUCCEEDED') await ensureAutoAgentAnalysis(task.value)
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

async function ensureAutoAgentAnalysis(next: AiInferenceTask): Promise<void> {
  if (next.status !== 'SUCCEEDED' || next.mode !== 'REAL' || !next.buildingId) return
  if (autoAnalysisRequestedForInferenceId.value === next.inferenceId) return
  autoAnalysisRequestedForInferenceId.value = next.inferenceId
  await runAgentAnalysis('AUTO')
}

async function runAgentAnalysis(mode: 'AUTO' | 'MANUAL' = 'MANUAL'): Promise<void> {
  if (!task.value || taskRunning.value || agentAnalysisLoading.value) return
  agentAnalysisLoading.value = true
  agentAnalysisError.value = ''
  try {
    const context: Record<string, unknown> = {}
    if (task.value.assetId) context.assetId = task.value.assetId
    if (mode === 'AUTO') context.sourceInferenceId = task.value.inferenceId
    agentAnalysis.value = await api.runIntelligentAnalysis({
      businessType: 'AI_INFERENCE',
      businessId: task.value.buildingId ?? undefined,
      question: '请结合这栋楼的巡检图片、历史正式风险和当前更新优先级进行综合分析。对细小、低置信度病害保持较高筛查敏感度，给出疑似病害、判断依据与人工复核建议；不得修改正式风险评分。',
      context,
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
        description="先在顶部形成跨模块 AI 综合研判，再结合 AI 判断、原始证据与人工复核完成专业闭环。"
      >
        <template #actions>
          <el-button plain round @click="router.push('/console/review')">← 返回复核中心</el-button>
          <AppStatusTag :status="task.status" variant="task" />
          <el-tag effect="plain" round>{{ reviewStatusLabel(task.reviewStatus) }}</el-tag>
        </template>
      </AppPageHeader>

      <section class="analysis-stage" :class="{ 'analysis-stage--compact': !agentAnalysis }">
        <div v-loading="agentAnalysisLoading" class="analysis-stage__top" :class="{ 'analysis-stage__idle': !agentAnalysis }">
          <div class="analysis-stage__identity">
            <span class="analysis-stage__index">01</span>
            <div>
              <strong>AI 综合研判</strong>
              <small>{{ agentAnalysis ? '跨楼栋档案、巡检证据、视觉结果与治理信息形成辅助研判' : agentAnalysisLoading ? '正在汇聚跨模块证据并形成研判…' : 'REAL 视觉识别完成后会自动生成，也可手动发起一次新研判' }}</small>
            </div>
          </div>
          <div v-if="!agentAnalysis" class="analysis-stage__capabilities" aria-label="综合研判数据范围">
            <span>楼栋档案</span><span>巡检证据</span><span>视觉识别</span><span>风险治理</span>
          </div>
          <div v-if="!agentAnalysis" class="analysis-stage__state">
            <span class="analysis-stage__state-dot" :data-running="agentAnalysisLoading" />
            <b>{{ agentAnalysisLoading ? '研判生成中' : agentAnalysisError ? '上次生成失败' : task.status === 'SUCCEEDED' ? '自动研判待生成' : '等待视觉结果' }}</b>
          </div>
          <el-button type="primary" :plain="Boolean(agentAnalysis)" round :loading="agentAnalysisLoading" :disabled="taskRunning" @click="runAgentAnalysis('MANUAL')">
            {{ agentAnalysis ? '刷新综合研判' : agentAnalysisLoading ? '正在研判' : '生成综合研判' }}
          </el-button>
        </div>
        <el-alert v-if="agentAnalysisError" class="analysis-stage__alert" :title="agentAnalysisError" type="warning" :closable="false" show-icon />
        <div v-if="agentAnalysis" class="analysis-stage__body">
          <AiStructuredAnalysisPanel
            :result="agentAnalysis"
            title="本楼栋综合研判"
            subtitle="将视觉疑似病害与巡检、楼栋档案、正式风险和治理优先级放在同一视图中，便于专业人员集中复核。"
          />
        </div>
      </section>

      <div v-if="taskRunning" class="running-panel">
        <el-icon class="is-loading"><Loading /></el-icon>
        <div><strong>AI 视觉识别正在分析</strong><span>识别完成后将自动进入 AI 综合研判，基础巡检和人工操作不受影响。</span></div>
        <el-tag type="warning" round>分析中</el-tag>
      </div>

      <div v-if="task.errorCode" class="status-panel status-panel--danger">
        <strong>AI 辅助能力暂时不可用</strong>
        <span>{{ translateAiBusinessError(task.errorCode) }}</span>
      </div>

      <div class="review-layout">
        <section class="review-column judgment-column">
          <div class="column-title"><span>02</span><div><strong>AI判断</strong><small>发生了什么、AI 怎么看、为什么这样判断</small></div></div>
          <div class="review-column__body">
            <AiInsightCard title="AI 辅助复核" :summary="aiSummary" :suggestion="aiSuggestion" compact />

            <el-card class="surface-card signal-card" shadow="never">
              <template #header><div class="card-head"><div><strong>AI 视觉风险信号</strong><small>采用高敏感辅助筛查，关注等级不是正式楼栋风险等级。</small></div><el-tag v-if="riskSignalsDerivedFromDetections" type="info" effect="plain" round>由检测候选整理</el-tag></div></template>
              <div v-if="displayRiskSignals.length" class="signal-list">
                <article v-for="signal in displayRiskSignals" :key="`${signal.code}-${signal.description}`">
                  <div><strong>{{ riskSignalTitle(signal) }}</strong><el-tag :type="riskTagType(signal.level)" effect="plain" round>{{ riskLevelLabel(signal.level) }}</el-tag></div>
                  <p>{{ signal.description }}</p>
                  <small>{{ formatAiConfidence(signal.confidence) ? `AI 可信度 ${formatAiConfidence(signal.confidence)}` : '低可信或无可展示百分比 · 请人工核对原始证据' }}</small>
                </article>
              </div>
              <el-empty v-else description="当前没有可展示的视觉风险信号" />
            </el-card>
          </div>
        </section>

        <section class="review-column evidence-column">
          <div class="column-title"><span>03</span><div><strong>原始证据</strong><small>原图、Polygon 与结构化检测候选</small></div></div>
          <div class="review-column__body">
            <el-card class="surface-card evidence-card" shadow="never">
              <template #header><div class="card-head"><div><strong>原始巡检图片</strong><small>可叠加 AI 标注用于位置核对</small></div><el-switch v-model="showAiOverlay" active-text="显示 AI 标注" /></div></template>
              <AppLoading :visible="imageLoading" inline text="加载原始图片中…" />
              <AppError v-if="imageErrorMessage" :message="imageErrorMessage" />
              <AiDetectionOverlay
                v-if="imageUrl && showAiOverlay && overlayDetections.length"
                :detections="overlayDetections"
                :image-width="task.imageWidth"
                :image-height="task.imageHeight"
                :image-src="imageUrl"
              />
              <img v-else-if="imageUrl" :src="imageUrl" class="review-image" alt="巡检原始证据" />
            </el-card>

            <el-card class="surface-card detection-card" shadow="never">
              <template #header><div class="card-head"><div><strong>检测候选</strong><small>低于 40% 的候选保留病害名和区域，不显示百分比。</small></div></div></template>
              <div v-if="(structured?.detections?.length || task.detections?.length)" class="detection-list">
                <article v-for="(item,index) in (structured?.detections?.length ? structured.detections : task.detections)" :key="`${item.classCode}-${index}`">
                  <strong>{{ formatAiDetectionLabel(item.className || item.classCode, item.confidence) }}</strong>
                  <small>{{ item.segmentation ? `区域标注 ${item.segmentation.type}` : '矩形检测区域' }}</small>
                </article>
              </div>
              <el-empty v-else description="没有结构化检测候选" />
            </el-card>
          </div>
        </section>

        <section class="review-column action-column">
          <div class="column-title"><span>04</span><div><strong>人工复核</strong><small>确认、修正或驳回均必须由人工提交</small></div></div>
          <div class="review-column__body">
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

            <details open class="technical-details">
              <summary>专业技术详情</summary>
              <dl>
                <div><dt>请求编号</dt><dd>{{ task.requestCode }}</dd></div>
                <div><dt>识别来源</dt><dd>{{ modelAttribution || '—' }}</dd></div>
                <div><dt>模型版本</dt><dd>{{ task.modelVersion || '—' }}</dd></div>
                <div><dt>任务 ID</dt><dd>{{ task.inferenceId }}</dd></div>
                <div v-if="task.errorCode"><dt>错误码</dt><dd>{{ task.errorCode }}</dd></div>
              </dl>
            </details>
          </div>
        </section>
      </div>
    </template>
  </section>
</template>

<style scoped lang="scss">
.detail-page{display:grid;gap:14px}.running-panel,.status-panel{display:grid;grid-template-columns:auto 1fr auto;align-items:center;gap:12px;padding:12px 14px;border-radius:var(--usp-radius-xl)}.running-panel{border:1px solid #f5d08a;background:#fff9eb;color:#7a4d00}.running-panel>div{display:grid;gap:2px}.running-panel span{font-size:11px}.status-panel--danger{grid-template-columns:1fr;border:1px solid #f0b5b5;background:#fff3f3;color:#8c2a2a}.analysis-stage{display:grid;gap:12px;padding:16px;border:1px solid #d7e7e2;border-radius:var(--usp-radius-xl);background:linear-gradient(180deg,#fbfefd,#fff);box-shadow:var(--usp-shadow-sm)}.analysis-stage--compact{gap:8px;padding:10px 12px}.analysis-stage__top{display:flex;align-items:center;justify-content:space-between;gap:14px;padding:0 2px}.analysis-stage__idle{display:grid;grid-template-columns:minmax(250px,1.25fr) minmax(280px,.9fr) auto auto;align-items:center;gap:14px;min-height:58px}.analysis-stage__identity{display:flex;align-items:center;gap:10px;min-width:0}.analysis-stage__identity>div{display:grid;gap:2px;min-width:0}.analysis-stage__identity strong{font-size:15px}.analysis-stage__identity small{overflow:hidden;color:var(--usp-color-text-secondary);font-size:10px;line-height:1.45;text-overflow:ellipsis;white-space:nowrap}.analysis-stage__index{display:grid;flex:0 0 30px;width:30px;height:30px;place-items:center;border-radius:10px;background:#176354;color:#fff;font-size:10px;font-weight:900}.analysis-stage__capabilities{display:flex;align-items:center;justify-content:flex-start;gap:6px;min-width:0}.analysis-stage__capabilities span{padding:5px 8px;border:1px solid #d8e9e3;border-radius:999px;background:#f6fbf9;color:#41685f;font-size:10px;font-weight:700;white-space:nowrap}.analysis-stage__state{display:flex;align-items:center;gap:7px;color:var(--usp-color-text-secondary);font-size:10px;white-space:nowrap}.analysis-stage__state-dot{width:8px;height:8px;border-radius:999px;background:#98a2b3;box-shadow:0 0 0 4px rgba(152,162,179,.12)}.analysis-stage__state-dot[data-running='true']{background:#f79009;box-shadow:0 0 0 4px rgba(247,144,9,.12)}.analysis-stage__alert{margin-top:0}.analysis-stage__body{min-height:120px}.review-layout{display:grid;grid-template-columns:minmax(280px,.82fr) minmax(360px,1.18fr) minmax(290px,.86fr);gap:14px;align-items:stretch}.review-column{display:grid;grid-template-rows:auto minmax(0,1fr);min-width:0;gap:12px}.review-column__body{display:grid;height:100%;min-height:0;gap:12px}.judgment-column .review-column__body,.evidence-column .review-column__body{grid-template-rows:auto minmax(0,1fr)}.action-column .review-column__body{grid-template-rows:minmax(0,1fr) auto}.column-title{display:flex;align-items:center;gap:9px;min-height:34px;padding:0 2px}.column-title>span{display:grid;width:28px;height:28px;place-items:center;border-radius:999px;background:var(--usp-color-primary-soft);color:var(--usp-color-primary-strong);font-size:10px;font-weight:900}.column-title>div{display:grid;gap:1px}.column-title strong{font-size:14px}.column-title small,.card-head small{color:var(--usp-color-text-secondary);font-size:10px}.surface-card{border-radius:var(--usp-radius-xl);box-shadow:var(--usp-shadow-sm)}.surface-card :deep(.el-card__body){display:grid;gap:11px;padding:13px}.surface-card :deep(.el-card__header){padding:11px 13px}.signal-card,.detection-card,.review-form-card{height:100%}.review-form-card :deep(.el-card__body){grid-template-rows:minmax(0,1fr) auto;height:100%}.card-head{display:flex;align-items:center;justify-content:space-between;gap:9px}.card-head>div:first-child{display:grid;gap:2px}.signal-list,.detection-list{display:grid;align-content:start;gap:8px}.signal-list article,.detection-list article{display:grid;gap:5px;padding:9px 10px;border:1px solid var(--usp-color-border);border-radius:var(--usp-radius-lg);background:var(--usp-color-surface-muted)}.signal-list article>div{display:flex;align-items:center;justify-content:space-between;gap:8px}.signal-list p{margin:0;color:var(--usp-color-text-secondary);line-height:1.6}.signal-list small,.detection-list small{color:var(--usp-color-text-tertiary)}.evidence-card{overflow:hidden}.review-image{width:100%;max-height:440px;object-fit:contain;border-radius:var(--usp-radius-lg);background:#111}.review-form-card :deep(.el-radio-group){display:flex;width:100%}.review-form-card :deep(.el-radio-button){flex:1}.review-form-card :deep(.el-radio-button__inner){width:100%}.review-risk-field{display:grid;width:100%;gap:6px}.review-risk-field small{color:var(--usp-color-text-tertiary);font-size:10px;line-height:1.5}.review-actions{display:flex;justify-content:flex-end;gap:8px}.technical-details{align-self:end;padding:10px 12px;border:1px solid var(--usp-color-border);border-radius:var(--usp-radius-xl);background:var(--usp-color-surface)}.technical-details summary{cursor:pointer;color:var(--usp-color-text-secondary);font-size:11px;font-weight:700}.technical-details dl{display:grid;gap:7px;margin:10px 0 0}.technical-details dl>div{display:grid;grid-template-columns:78px minmax(0,1fr);gap:8px}.technical-details dt{color:var(--usp-color-text-tertiary);font-size:10px}.technical-details dd{min-width:0;margin:0;overflow-wrap:anywhere;color:var(--usp-color-text-secondary);font-size:10px}.detail-page :deep(.el-input__wrapper),.detail-page :deep(.el-select__wrapper),.detail-page :deep(.el-textarea__inner),.detail-page :deep(.el-button),.detail-page :deep(.el-radio-button__inner){border-radius:var(--usp-radius-lg)}@media(max-width:1180px){.analysis-stage__idle{grid-template-columns:minmax(240px,1fr) auto auto}.analysis-stage__capabilities{display:none}.review-layout{grid-template-columns:1fr 1fr;align-items:start}.review-column{grid-template-rows:auto auto}.review-column__body,.judgment-column .review-column__body,.evidence-column .review-column__body,.action-column .review-column__body{grid-template-rows:auto;height:auto}.signal-card,.detection-card,.review-form-card{height:auto}.action-column{grid-column:1/-1}}@media(max-width:760px){.analysis-stage--compact{padding:10px}.analysis-stage__top,.analysis-stage__idle{display:grid;grid-template-columns:1fr;gap:9px}.analysis-stage__identity small{white-space:normal}.analysis-stage__state{display:none}.analysis-stage__top :deep(.el-button){width:100%;margin-left:0}.review-layout{grid-template-columns:1fr}.action-column{grid-column:auto}.review-actions{justify-content:stretch}.review-actions :deep(.el-button){flex:1}}
</style>