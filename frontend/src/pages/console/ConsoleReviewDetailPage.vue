<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as api from '@/shared/api'
import { toAppError, type AiInferenceTask, type AiReviewStatus } from '@/shared/api'
import AppLoading from '@/shared/components/AppLoading.vue'
import AppError from '@/shared/components/AppError.vue'
import AppStatusTag from '@/shared/components/AppStatusTag.vue'
import AiDetectionOverlay from '@/pages/AiDetectionOverlay.vue'

const route = useRoute()
const router = useRouter()
const inferenceId = computed(() => String(route.params.inferenceId ?? ''))
const task = ref<AiInferenceTask | null>(null)
const structured = computed(() => task.value?.structuredResult ?? null)
const imageUrl = ref('')
const loading = ref(false)
const imageLoading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const imageErrorMessage = ref('')
const reviewStatus = ref<AiReviewStatus>('CONFIRMED')
const comment = ref('')
const notice = ref('')

function releaseImage(): void {
  if (imageUrl.value) URL.revokeObjectURL(imageUrl.value)
  imageUrl.value = ''
}

async function loadImage(): Promise<void> {
  releaseImage()
  imageErrorMessage.value = ''
  const assetId = task.value?.assetId
  if (!assetId) {
    imageErrorMessage.value = '该推理任务未关联原始图片。'
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

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  imageErrorMessage.value = ''
  releaseImage()
  try {
    task.value = await api.getAiInference(inferenceId.value)
  } catch (error) {
    task.value = null
    errorMessage.value = toAppError(error).message
    return
  } finally {
    loading.value = false
  }
  await loadImage()
}

async function submit(): Promise<void> {
  if (!task.value) return
  saving.value = true
  try {
    await api.submitAiReview(task.value.inferenceId, reviewStatus.value, comment.value.trim() || undefined)
    notice.value = '复核结果已提交。'
    await load()
  } catch (error) {
    notice.value = toAppError(error).message
  } finally {
    saving.value = false
  }
}

onMounted(load)
onBeforeUnmount(releaseImage)
</script>

<template>
  <section class="detail-page">
    <el-button link type="primary" @click="router.push('/console/review')">
      ‹ 返回复核队列
    </el-button>
    <AppLoading :visible="loading" inline text="加载识别结果中…" />
    <AppError v-if="errorMessage" :message="errorMessage" @retry="load" />

    <template v-if="task && !loading">
      <header class="detail-head">
        <div>
          <p class="eyebrow">{{ task.requestCode }}</p>
          <h1>人工智能辅助结果复核</h1>
        </div>
        <div class="status-tags">
          <AppStatusTag :status="task.status" variant="task" />
          <el-tag :type="task.mode === 'MOCK' ? 'warning' : 'success'">{{ task.mode }}</el-tag>
          <el-tag effect="plain">{{ task.providerCode ?? 'FAST_API' }}</el-tag>
          <el-tag effect="plain">{{ task.capabilityType ?? 'VISION_INFERENCE' }}</el-tag>
        </div>
      </header>

      <el-alert v-if="notice" :title="notice" type="info" :closable="false" show-icon />
      <el-alert
        v-if="task.errorCode"
        :title="`${task.errorCode}：${task.errorMessage || '人工智能提供者调用失败'}`"
        type="error"
        :closable="false"
        show-icon
      />
      <el-alert
        :title="task.assessmentNote"
        :type="task.eligibleForFormalAssessment ? 'success' : 'warning'"
        :closable="false"
        show-icon
      />

      <el-row :gutter="20">
        <el-col :xs="24" :xl="15">
          <el-card shadow="never">
            <template #header>
              <div class="card-head">
                <strong>原始图片与检测框</strong>
                <el-button v-if="imageErrorMessage" link type="primary" :loading="imageLoading" @click="loadImage">
                  重新加载图片
                </el-button>
              </div>
            </template>
            <AppLoading :visible="imageLoading" inline text="加载原始图片中…" />
            <el-alert
              v-if="imageErrorMessage"
              :title="`原始图片加载失败：${imageErrorMessage}`"
              type="warning"
              :closable="false"
              show-icon
              class="image-error"
            />
            <AiDetectionOverlay
              v-else-if="imageUrl && task.detections?.length"
              :detections="task.detections"
              :image-width="task.imageWidth || 1"
              :image-height="task.imageHeight || 1"
              :image-src="imageUrl"
            />
            <img
              v-else-if="imageUrl"
              :src="imageUrl"
              class="review-image"
              alt="待复核巡检图片"
            />
            <p v-else-if="!imageLoading" class="muted">当前任务没有可展示的原始图片。</p>
            <el-alert
              :title="task.disclaimer"
              type="warning"
              :closable="false"
              show-icon
              class="disclaimer"
            />
          </el-card>

          <el-card shadow="never">
            <template #header><strong>结构化分析结果</strong></template>
            <p class="summary-text">
              {{ structured?.summary || task.summary?.summary || '当前任务没有可展示的分析摘要。' }}
            </p>
            <div class="result-grid">
              <section>
                <h3>风险信号</h3>
                <div v-if="structured?.riskSignals?.length" class="signal-list">
                  <article v-for="(signal, index) in structured.riskSignals" :key="`${signal.code}-${index}`">
                    <strong>{{ signal.code || 'RISK_SIGNAL' }}</strong>
                    <el-tag size="small" effect="plain">{{ signal.level || '待复核' }}</el-tag>
                    <p>{{ signal.description || '未提供详细描述' }}</p>
                  </article>
                </div>
                <p v-else class="muted">未返回风险信号。</p>
              </section>
              <section>
                <h3>辅助建议</h3>
                <ul v-if="structured?.recommendations?.length" class="plain-list">
                  <li v-for="item in structured.recommendations" :key="item">{{ item }}</li>
                </ul>
                <p v-else class="muted">未返回辅助建议。</p>
              </section>
            </div>
            <el-alert
              v-for="warning in structured?.warnings || task.warnings || []"
              :key="warning"
              :title="warning"
              type="warning"
              :closable="false"
              show-icon
              class="warning-item"
            />
          </el-card>
        </el-col>

        <el-col :xs="24" :xl="9">
          <el-card shadow="never">
            <template #header><strong>提供者、模型与审计信息</strong></template>
            <el-descriptions :column="1" border>
              <el-descriptions-item label="提供者">
                {{ task.providerCode ?? 'FAST_API' }}
              </el-descriptions-item>
              <el-descriptions-item label="能力类型">
                {{ task.capabilityType ?? 'VISION_INFERENCE' }}
              </el-descriptions-item>
              <el-descriptions-item label="模型">
                {{ task.modelName }} v{{ task.modelVersion }}
              </el-descriptions-item>
              <el-descriptions-item v-if="task.workflowCode" label="工作流">
                {{ task.workflowCode }} {{ task.workflowVersion || '' }}
              </el-descriptions-item>
              <el-descriptions-item label="许可证">{{ task.license }}</el-descriptions-item>
              <el-descriptions-item label="耗时">{{ task.durationMs ?? '—' }} ms</el-descriptions-item>
              <el-descriptions-item label="置信度">
                {{ structured?.confidence == null ? '—' : `${Math.round(structured.confidence * 100)}%` }}
              </el-descriptions-item>
              <el-descriptions-item label="适用性">{{ task.applicability ?? '—' }}</el-descriptions-item>
              <el-descriptions-item label="检测数量">{{ task.detectionCount }}</el-descriptions-item>
              <el-descriptions-item label="复核状态">{{ task.reviewStatus }}</el-descriptions-item>
              <el-descriptions-item label="评分资格">{{ task.assessmentEligibility }}</el-descriptions-item>
              <el-descriptions-item label="证据可靠性">{{ task.evidenceReliability }}</el-descriptions-item>
              <el-descriptions-item label="发生降级">
                {{ task.fallbackUsed ? '是' : '否' }}
              </el-descriptions-item>
            </el-descriptions>

            <div class="detection-list">
              <div v-for="item in task.detections" :key="item.sequence">
                <strong>{{ item.className }}</strong>
                <span>{{ Math.round(item.confidence * 100) }}%</span>
              </div>
            </div>

            <el-form label-position="top" class="review-form">
              <el-form-item label="复核结论">
                <el-radio-group v-model="reviewStatus">
                  <el-radio-button value="CONFIRMED">确认</el-radio-button>
                  <el-radio-button value="CORRECTED">修正</el-radio-button>
                  <el-radio-button value="REJECTED">驳回</el-radio-button>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="复核意见">
                <el-input
                  v-model="comment"
                  type="textarea"
                  :rows="4"
                  placeholder="说明判断依据或需要重新采集的内容"
                />
              </el-form-item>
              <el-button
                type="primary"
                :loading="saving"
                :disabled="task.reviewStatus !== 'UNREVIEWED'"
                @click="submit"
              >
                提交复核
              </el-button>
            </el-form>
          </el-card>
        </el-col>
      </el-row>
    </template>
  </section>
</template>

<style scoped lang="scss">
.detail-page { display: grid; gap: 18px; }
.detail-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; }
.status-tags { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 8px; }
.detail-head h1 { margin: 4px 0 0; font-size: 32px; }
.eyebrow { margin: 0; color: #287a6a; font-size: 12px; font-weight: 800; letter-spacing: .1em; }
.card-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.review-image { width: 100%; max-height: 660px; object-fit: contain; border-radius: 12px; background: #161616; }
.image-error { margin-bottom: 14px; }
.disclaimer { margin-top: 14px; }
.summary-text { margin: 0; line-height: 1.8; color: #344054; }
.result-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; margin-top: 18px; }
.result-grid h3 { margin: 0 0 10px; font-size: 15px; }
.signal-list { display: grid; gap: 10px; }
.signal-list article { padding: 12px; border: 1px solid #e4e7ec; border-radius: 10px; }
.signal-list article strong { margin-right: 8px; }
.signal-list article p { margin: 8px 0 0; color: #475467; }
.plain-list { margin: 0; padding-left: 20px; line-height: 1.8; color: #475467; }
.warning-item { margin-top: 10px; }
.detection-list { display: grid; gap: 8px; margin-top: 16px; }
.detection-list div { display: flex; justify-content: space-between; padding: 10px 12px; border-radius: 10px; background: #fef3f2; color: #b42318; }
.review-form { margin-top: 18px; }
.muted { color: #98a2b3; }
@media (max-width: 900px) {
  .detail-head { align-items: flex-start; flex-direction: column; }
  .status-tags { justify-content: flex-start; }
  .result-grid { grid-template-columns: 1fr; }
}
</style>
