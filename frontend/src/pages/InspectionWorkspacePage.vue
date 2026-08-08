<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { UploadFile } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  toAppError,
  type CommunityPoint,
  type BuildingListRow,
  type InspectionTask,
  type InspectionRecord,
  type InspectionType,
  type Severity,
} from '@/shared/api'
import * as api from '@/shared/api'
import AppLoading from '@/shared/components/AppLoading.vue'
import AppEmpty from '@/shared/components/AppEmpty.vue'
import AppError from '@/shared/components/AppError.vue'
import AppStatusTag from '@/shared/components/AppStatusTag.vue'
import AiDetectionOverlay from '@/pages/AiDetectionOverlay.vue'
import type { AssetImageRow, AiInferenceTask, AiModelCatalogItem } from '@/shared/api'

const authStore = useAuthStore()
const router = useRouter()

const loginUsername = ref('')
const loginPassword = ref('')
const loginLoading = ref(false)
const notice = ref('请先登录，然后加载数据。')
const busy = ref(false)
const loadError = ref<ReturnType<typeof toAppError> | null>(null)

const communities = ref<CommunityPoint[]>([])
const buildings = ref<BuildingListRow[]>([])
const tasks = ref<InspectionTask[]>([])
const records = ref<InspectionRecord[]>([])

const selectedCommunity = ref('')
const selectedBuilding = ref('')
const selectedTask = ref('')
const taskTitle = ref('现场安全巡检')
const inspectionType = ref<InspectionType>('ROUTINE')
const summary = ref('')
const severity = ref<Severity>('LOW')
const suggestion = ref('')
const photo = ref<File | null>(null)
const photoPreviewUrl = ref('')

// ---- AI 推理 ----
const aiModels = ref<AiModelCatalogItem[]>([])
const aiSelectedModelId = ref('')
const aiImages = ref<AssetImageRow[]>([])
const aiSelectedAssetId = ref('')
const aiInference = ref<AiInferenceTask | null>(null)
const aiImageBlobUrl = ref('')
const aiBusy = ref(false)
const aiReviewComment = ref('')
const aiBlobUrls: string[] = []

const selectedAiModel = computed(
  () => aiModels.value.find((model) => model.modelId === aiSelectedModelId.value) ?? null,
)
const structuredResult = computed(() => aiInference.value?.structuredResult ?? null)
const resultSummary = computed(() =>
  structuredResult.value?.summary
  ?? aiInference.value?.summary?.summary
  ?? '',
)
const resultDetections = computed(() => {
  const structured = structuredResult.value?.detections ?? []
  if (structured.length) return structured
  return (aiInference.value?.detections ?? []).map((item) => ({
    classCode: item.classCode,
    className: item.className,
    confidence: item.confidence,
    boundingBox: item.boundingBox,
  }))
})
const resultRiskSignals = computed(() => structuredResult.value?.riskSignals ?? [])
const resultRecommendations = computed(() => structuredResult.value?.recommendations ?? [])
const resultWarnings = computed(() => {
  const structured = structuredResult.value?.warnings ?? []
  return structured.length ? structured : aiInference.value?.warnings ?? []
})

function confidenceText(value: number | null | undefined): string {
  return `${Math.round(Number(value ?? 0) * 100)}%`
}

function riskTagType(level: string | undefined): 'success' | 'warning' | 'danger' | 'info' {
  if (level === 'HIGH') return 'danger'
  if (level === 'MEDIUM') return 'warning'
  if (level === 'LOW') return 'success'
  return 'info'
}

async function loadAiModels(): Promise<void> {
  const response = await api.listAiModels()
  aiModels.value = response.content ?? []
  const current = aiModels.value.find(
    (model) => model.modelId === aiSelectedModelId.value && model.selectable,
  )
  if (current) return
  aiSelectedModelId.value =
    aiModels.value.find((model) => model.mode === 'REAL' && model.selectable)?.modelId
    ?? aiModels.value.find((model) => model.selectable)?.modelId
    ?? ''
}

async function loadAiImages(): Promise<void> {
  aiImages.value = []
  if (!selectedTask.value) return
  try {
    const res = await api.listImages({
      businessType: 'INSPECTION_TASK',
      businessId: selectedTask.value,
    })
    aiImages.value = res.content ?? []
  } catch (error) {
    notice.value = `巡检图片加载失败：${toAppError(error).message}`
  }
}

async function displayInferenceResult(
  assetId: string,
  inferenceId: string,
  modelId?: string | null,
): Promise<void> {
  aiSelectedAssetId.value = assetId
  if (modelId && aiModels.value.some((model) => model.modelId === modelId)) {
    aiSelectedModelId.value = modelId
  }
  releaseBlobUrl()
  aiImageBlobUrl.value = await api.fetchImageBlobUrl(assetId)
  aiBlobUrls.push(aiImageBlobUrl.value)
  aiInference.value = await api.getAiInference(inferenceId)
}

async function runAiInference(assetId: string): Promise<void> {
  const model = selectedAiModel.value
  if (!model || !model.selectable) {
    notice.value = '当前没有可用的推理模型，请检查真实模型服务状态。'
    return
  }
  aiSelectedAssetId.value = assetId
  aiInference.value = null
  releaseBlobUrl()
  aiBusy.value = true
  try {
    aiImageBlobUrl.value = await api.fetchImageBlobUrl(assetId)
    aiBlobUrls.push(aiImageBlobUrl.value)
    const task = await api.createAiInference({
      assetId,
      mode: model.mode,
      modelId: model.modelId,
      idempotencyKey: `ws-${assetId}-${model.modelId}-${Date.now()}`,
    })
    aiInference.value = task
    notice.value = `AI 推理完成：${task.status}`
  } catch (error) {
    notice.value = toAppError(error).message
  } finally {
    aiBusy.value = false
  }
}

async function retryInference(): Promise<void> {
  if (!aiInference.value) return
  aiBusy.value = true
  try {
    const task = await api.retryAiInference(
      aiInference.value.inferenceId,
      aiSelectedModelId.value || undefined,
    )
    aiInference.value = task
    notice.value = `重试结果：${task.status}`
  } catch (error) {
    notice.value = toAppError(error).message
  } finally {
    aiBusy.value = false
  }
}

async function submitReview(): Promise<void> {
  if (!aiInference.value) return
  aiBusy.value = true
  try {
    const inferenceId = aiInference.value.inferenceId
    await api.submitAiReview(
      inferenceId,
      'CONFIRMED',
      aiReviewComment.value || undefined,
    )
    aiReviewComment.value = ''
    aiInference.value = await api.getAiInference(inferenceId)
    notice.value = '复核已提交，评分证据资格已刷新'
  } catch (error) {
    notice.value = toAppError(error).message
  } finally {
    aiBusy.value = false
  }
}

function releaseBlobUrl(): void {
  if (aiImageBlobUrl.value) {
    URL.revokeObjectURL(aiImageBlobUrl.value)
    aiImageBlobUrl.value = ''
  }
}

function releasePhotoPreview(): void {
  if (photoPreviewUrl.value) {
    URL.revokeObjectURL(photoPreviewUrl.value)
    photoPreviewUrl.value = ''
  }
}

const SEVERITIES: Severity[] = ['LOW', 'MEDIUM', 'HIGH']

const currentTask = computed(
  () => tasks.value.find((t) => t.taskId === selectedTask.value) ?? null,
)

async function run(action: () => Promise<void>): Promise<void> {
  busy.value = true
  try {
    await action()
  } catch (error) {
    notice.value = toAppError(error).message
  } finally {
    busy.value = false
  }
}

async function loadBuildings(): Promise<void> {
  buildings.value = []
  tasks.value = []
  records.value = []
  selectedBuilding.value = ''
  selectedTask.value = ''
  if (!selectedCommunity.value) return
  const res = await api.listBuildings({ communityId: selectedCommunity.value, size: 100 })
  buildings.value = res.content ?? []
  selectedBuilding.value = buildings.value[0]?.id ?? ''
}

async function loadRecords(): Promise<void> {
  records.value = selectedTask.value
    ? await api.listInspectionRecords(selectedTask.value)
    : []
}

async function loadTasks(): Promise<void> {
  const params = selectedBuilding.value ? { buildingId: selectedBuilding.value } : {}
  tasks.value = await api.listInspectionTasks(params)
  if (!tasks.value.some((t) => t.taskId === selectedTask.value)) {
    selectedTask.value = tasks.value[0]?.taskId ?? ''
  }
  await loadRecords()
  await loadAiImages()
}

async function loadAll(): Promise<void> {
  loadError.value = null
  busy.value = true
  try {
    communities.value = await api.listCommunityPoints()
    await loadAiModels()
    if (!selectedCommunity.value && communities.value.length) {
      selectedCommunity.value = communities.value[0].communityId
    }
    await loadBuildings()
    await loadTasks()
    notice.value = '数据加载完成；小区与楼栋空间展示请使用正式空间地图。'
  } catch (error) {
    loadError.value = toAppError(error)
    notice.value = loadError.value.message
  } finally {
    busy.value = false
  }
}

async function handleLogin(): Promise<void> {
  if (!loginUsername.value.trim() || !loginPassword.value.trim()) {
    notice.value = '请输入用户名和密码。'
    return
  }
  loginLoading.value = true
  try {
    await authStore.login(loginUsername.value, loginPassword.value)
    notice.value = '登录成功，正在加载数据…'
    await loadAll()
  } catch (error) {
    notice.value = toAppError(error).message
  } finally {
    loginLoading.value = false
  }
}

async function handleLogout(): Promise<void> {
  await authStore.logout()
  communities.value = []
  buildings.value = []
  tasks.value = []
  records.value = []
  aiModels.value = []
  aiSelectedModelId.value = ''
  aiInference.value = null
  selectedCommunity.value = ''
  selectedBuilding.value = ''
  selectedTask.value = ''
  photo.value = null
  releasePhotoPreview()
  notice.value = '已退出登录。'
  loadError.value = null
}

function selectCommunity(id: string): void {
  selectedCommunity.value = id
  void run(async () => {
    await loadBuildings()
    await loadTasks()
  })
}

function selectTask(id: string): void {
  selectedTask.value = id
  void run(async () => {
    await loadRecords()
    await loadAiImages()
  })
}

async function createTask(): Promise<void> {
  if (!selectedBuilding.value) {
    notice.value = '请先选择楼栋'
    return
  }
  await run(async () => {
    const task = await api.createInspectionTask({
      buildingId: selectedBuilding.value,
      inspectionType: inspectionType.value,
      title: taskTitle.value,
    })
    selectedTask.value = task.taskId
    await loadTasks()
    notice.value = `已创建任务 ${task.taskCode}`
  })
}

async function transition(action: 'start' | 'complete' | 'cancel'): Promise<void> {
  if (!selectedTask.value) return
  await run(async () => {
    await api.transitionInspectionTask(selectedTask.value, action)
    await loadTasks()
    notice.value = `任务状态操作完成：${action}`
  })
}

async function createRecord(): Promise<void> {
  if (!selectedTask.value || !summary.value.trim()) {
    notice.value = '请选择任务并填写巡检摘要'
    return
  }
  await run(async () => {
    const record = await api.createInspectionRecord({
      taskId: selectedTask.value,
      inspectionPart: '现场检查',
      issueType: 'OTHER',
      severity: severity.value,
      summary: summary.value,
      rectificationSuggestion: suggestion.value || undefined,
    })
    summary.value = ''
    await loadRecords()
    notice.value = `已保存巡检记录 ${record.recordId}`
  })
}

function onFileChange(uploadFile: UploadFile): void {
  releasePhotoPreview()
  const file = uploadFile.raw ?? null
  photo.value = file
  if (file) photoPreviewUrl.value = URL.createObjectURL(file)
}

async function uploadPhoto(): Promise<void> {
  const file = photo.value
  if (!file || !selectedTask.value) {
    notice.value = '请选择任务和图片'
    return
  }
  const taskId = selectedTask.value
  await run(async () => {
    const result = await api.uploadImage({
      file,
      businessType: 'INSPECTION_TASK',
      businessId: taskId,
      bindingRole: 'INSPECTION_PHOTO',
    })
    photo.value = null
    releasePhotoPreview()
    await loadAiImages()

    const automatic = result.autoInference
    if (automatic?.triggered && automatic.inferenceId) {
      try {
        await displayInferenceResult(result.assetId, automatic.inferenceId, automatic.modelId)
        notice.value = `图片上传完成并已自动识别：${automatic.status ?? '已完成'}`
      } catch (error) {
        notice.value = `图片已上传，但自动识别结果加载失败：${toAppError(error).message}`
      }
      return
    }
    notice.value = automatic?.enabled
      ? `图片上传完成：${result.originalFilename}；${automatic.message}`
      : `图片上传完成：${result.originalFilename}`
  })
}

async function savePoint(community: CommunityPoint): Promise<void> {
  await run(async () => {
    const address = community.address || community.communityName
    const point = await api.previewGeocoding({ address })
    await api.saveCommunityLocation(community.communityId, {
      longitude: point.longitude,
      latitude: point.latitude,
      formattedAddress: point.formattedAddress,
      provider: point.provider as 'AMAP' | 'MANUAL' | 'IMPORT' | 'MOCK',
      matchLevel: point.matchLevel,
      mock: point.mock,
    })
    await loadAll()
    notice.value = point.mock ? '已保存模拟坐标，可填写高德 Key 后重新校准' : '高德坐标保存完成'
  })
}

onMounted(() => {
  if (authStore.isAuthenticated) void loadAll()
})

onUnmounted(() => {
  releaseBlobUrl()
  releasePhotoPreview()
  for (const url of aiBlobUrls) URL.revokeObjectURL(url)
})

function onLoginKeydown(e: KeyboardEvent): void {
  if (e.key === 'Enter') void handleLogin()
}
</script>

<template>
  <section class="workspace">
    <header class="workspace-head">
      <div>
        <p class="eyebrow">UrbanSafe Priority · Phase 2</p>
        <h1>巡检与图片闭环兼容工作台</h1>
      </div>
      <div v-if="!authStore.isAuthenticated" class="auth" @keydown="onLoginKeydown">
        <el-input v-model="loginUsername" placeholder="用户名" clearable />
        <el-input v-model="loginPassword" type="password" placeholder="密码" show-password clearable />
        <el-button type="primary" :loading="loginLoading" @click="handleLogin">登录</el-button>
      </div>
      <div v-else class="auth">
        <span class="app-user">欢迎，{{ authStore.user?.realName || authStore.user?.username }}</span>
        <el-button @click="handleLogout">退出</el-button>
      </div>
    </header>

    <p class="notice" :class="{ 'notice--error': loadError }">{{ notice }}</p>
    <AppError v-if="loadError" :message="loadError.message" @retry="loadAll" />

    <template v-else>
      <section class="grid two">
        <article class="panel">
          <div class="title"><h2>小区与空间地图</h2><span>兼容入口</span></div>
          <div class="map map--mock formal-map-entry">
            <strong>正式空间地图</strong>
            <p>小区/楼栋 Polygon、风险展示与空间筛选已迁移至正式地图，本兼容工作台不再维护旧 Marker 地图。</p>
            <el-button type="primary" @click="router.push('/console/map')">打开正式空间地图</el-button>
          </div>
          <div class="cards">
            <button
              v-for="item in communities"
              :key="item.communityId"
              class="community"
              :class="{ active: selectedCommunity === item.communityId }"
              @click="selectCommunity(item.communityId)"
            >
              <strong>{{ item.communityName }}</strong>
              <small>{{ item.formattedAddress || item.address || '尚未定位' }}</small>
              <em @click.stop="savePoint(item)">{{ item.longitude ? '重新定位' : '生成坐标' }}</em>
            </button>
          </div>
          <AppEmpty v-if="!communities.length" description="暂无小区" />
        </article>

        <article class="panel">
          <div class="title"><h2>创建巡检任务</h2><span>楼栋 → 任务</span></div>
          <el-form label-position="top">
            <el-form-item label="楼栋">
              <el-select v-model="selectedBuilding" placeholder="选择楼栋" @change="loadTasks">
                <el-option
                  v-for="item in buildings"
                  :key="item.id"
                  :label="item.buildingName || item.buildingCode"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
            <el-alert
              v-if="selectedCommunity && !buildings.length"
              title="当前小区暂无可选择楼栋"
              type="warning"
              :closable="false"
              show-icon
              class="inline-alert"
            />
            <el-form-item label="任务标题"><el-input v-model="taskTitle" /></el-form-item>
            <el-form-item label="巡检类型">
              <el-select v-model="inspectionType">
                <el-option label="日常巡检" value="ROUTINE" />
                <el-option label="专项巡检" value="SPECIAL" />
              </el-select>
            </el-form-item>
            <el-button type="primary" :disabled="!selectedBuilding" @click="createTask">创建任务</el-button>
          </el-form>
        </article>
      </section>

      <section class="grid two">
        <article class="panel">
          <div class="title"><h2>巡检任务</h2><span>{{ tasks.length }} 条</span></div>
          <button
            v-for="item in tasks"
            :key="item.taskId"
            class="task"
            :class="{ active: selectedTask === item.taskId }"
            @click="selectTask(item.taskId)"
          >
            <strong>{{ item.title || item.taskCode }}</strong>
            <small>{{ item.buildingName }} · <AppStatusTag :status="item.status" variant="task" /></small>
          </button>
          <AppEmpty v-if="!tasks.length" description="暂无巡检任务" />
          <div v-if="currentTask" class="actions">
            <el-button size="small" :disabled="currentTask.status !== 'PENDING'" @click="transition('start')">开始</el-button>
            <el-button size="small" :disabled="currentTask.status !== 'IN_PROGRESS'" @click="transition('complete')">完成</el-button>
            <el-button size="small" :disabled="!['PENDING', 'IN_PROGRESS'].includes(currentTask.status)" @click="transition('cancel')">取消</el-button>
          </div>
        </article>

        <article class="panel">
          <div class="title"><h2>现场记录与图片</h2><span>任务 → 记录 → 归档</span></div>
          <el-form label-position="top">
            <el-form-item label="严重程度">
              <el-select v-model="severity"><el-option v-for="s in SEVERITIES" :key="s" :label="s" :value="s" /></el-select>
            </el-form-item>
            <el-form-item label="巡检摘要">
              <el-input v-model="summary" type="textarea" :rows="3" placeholder="描述现场情况" />
            </el-form-item>
            <el-form-item label="整改建议">
              <el-input v-model="suggestion" type="textarea" :rows="2" placeholder="高风险问题必填" />
            </el-form-item>
            <div class="actions">
              <el-button type="primary" @click="createRecord">保存记录</el-button>
              <el-upload :auto-upload="false" :show-file-list="false" :on-change="onFileChange" accept="image/jpeg,image/png,image/webp">
                <el-button>选择图片</el-button>
              </el-upload>
              <el-button :disabled="!photo || !selectedTask" @click="uploadPhoto">上传图片</el-button>
            </div>
            <figure v-if="photoPreviewUrl && photo" class="pending-photo-preview">
              <img :src="photoPreviewUrl" alt="待上传巡检图片预览" />
              <figcaption>
                <strong>{{ photo.name }}</strong>
                <span>{{ (photo.size / 1024 / 1024).toFixed(2) }} MB · 上传前预览</span>
              </figcaption>
            </figure>
          </el-form>
          <div v-for="item in records" :key="item.recordId" class="record">
            <strong><AppStatusTag :status="item.severity" variant="severity" /> · {{ item.inspectionPart || '现场检查' }}</strong>
            <p>{{ item.summary }}</p>
          </div>
          <AppEmpty v-if="!records.length" description="暂无巡检记录" />
        </article>
      </section>

      <section class="grid two">
        <article class="panel">
          <div class="title"><h2>巡检图片</h2><span>{{ aiImages.length }} 张</span></div>
          <el-form label-position="top">
            <el-form-item label="推理模型">
              <el-select v-model="aiSelectedModelId" placeholder="选择可用模型" :disabled="aiBusy">
                <el-option
                  v-for="model in aiModels"
                  :key="model.modelId"
                  :value="model.modelId"
                  :disabled="!model.selectable"
                  :label="`${model.modelName} · ${model.mode} · v${model.modelVersion}${model.runtimeReady ? '' : '（未就绪）'}`"
                />
              </el-select>
            </el-form-item>
          </el-form>
          <div v-if="selectedAiModel" class="model-hint">
            <strong>{{ selectedAiModel.deploymentStage }}</strong>
            <span>{{ selectedAiModel.executionProvider || '运行时未就绪' }}</span>
            <span v-if="!selectedAiModel.formalEvidenceEnabled">当前模型结果不进入正式评分</span>
          </div>
          <div v-for="img in aiImages" :key="img.id" class="image-row">
            <span class="image-filename">{{ img.originalFilename }}</span>
            <el-button
              size="small"
              type="primary"
              :disabled="!selectedAiModel?.selectable"
              :loading="aiBusy && aiSelectedAssetId === img.id"
              @click="runAiInference(img.id)"
            >AI 识别</el-button>
          </div>
          <AppEmpty v-if="!aiImages.length" description="请先上传图片到巡检任务" />
        </article>

        <article class="panel result-panel">
          <div class="title">
            <h2>AI 推理结果</h2>
            <span v-if="aiInference">
              <AppStatusTag :status="aiInference.status" variant="task" />
              <span class="badge">{{ aiInference.mode }}</span>
            </span>
            <span v-else>—</span>
          </div>

          <template v-if="aiInference">
            <AiDetectionOverlay
              v-if="aiInference.status === 'SUCCEEDED' && aiInference.detections?.length && aiImageBlobUrl"
              :detections="aiInference.detections"
              :image-width="aiInference.imageWidth || 1"
              :image-height="aiInference.imageHeight || 1"
              :image-src="aiImageBlobUrl"
            />
            <img v-else-if="aiImageBlobUrl" :src="aiImageBlobUrl" class="result-image" alt="巡检图片" />

            <div class="meta">
              <span>请求：{{ aiInference.requestCode }}</span>
              <span>{{ aiInference.modelName }}</span>
              <span>v{{ aiInference.modelVersion }}</span>
              <span v-if="aiInference.providerCode">{{ aiInference.providerCode }}</span>
              <span v-if="aiInference.capabilityType">{{ aiInference.capabilityType }}</span>
              <span v-if="aiInference.workflowVersion">工作流 {{ aiInference.workflowVersion }}</span>
              <span v-if="aiInference.durationMs">{{ aiInference.durationMs }}ms</span>
              <span>{{ aiInference.assessmentEligibility }}</span>
            </div>

            <section v-if="aiInference.status === 'SUCCEEDED'" class="result-details">
              <div v-if="resultSummary" class="detail-block">
                <h3>分析摘要</h3>
                <p>{{ resultSummary }}</p>
                <span v-if="structuredResult?.confidence != null" class="confidence">
                  综合可信度 {{ confidenceText(structuredResult.confidence) }}
                </span>
              </div>

              <div class="detail-block">
                <h3>候选病害</h3>
                <div v-if="resultDetections.length" class="detail-list">
                  <div v-for="(d, index) in resultDetections" :key="`${d.classCode ?? 'OTHER'}-${index}`" class="detection-item">
                    <span class="detection-class">{{ d.className || d.classCode || '其他异常' }}</span>
                    <span>{{ confidenceText(d.confidence) }}</span>
                  </div>
                </div>
                <p v-else-if="aiInference.applicability === 'NO_DEFECT_FOUND'">未检测到明确目标病害，不等于房屋无安全问题。</p>
                <p v-else-if="aiInference.applicability === 'LOW_QUALITY'">图片质量过低，建议重新拍摄清晰照片。</p>
                <p v-else-if="aiInference.applicability === 'NOT_APPLICABLE'">图片不适用于当前模型，请更换图片或联系管理员。</p>
                <p v-else>未返回明确候选病害。</p>
              </div>

              <div v-if="resultRiskSignals.length" class="detail-block">
                <h3>风险信号</h3>
                <div class="detail-list">
                  <div v-for="(signal, index) in resultRiskSignals" :key="`${signal.code ?? 'RISK'}-${index}`" class="risk-item">
                    <div>
                      <el-tag :type="riskTagType(signal.level)" size="small">{{ signal.level || 'UNKNOWN' }}</el-tag>
                      <strong>{{ signal.code || '风险信号' }}</strong>
                    </div>
                    <p>{{ signal.description || '未提供说明' }}</p>
                    <span v-if="signal.confidence != null">可信度 {{ confidenceText(signal.confidence) }}</span>
                  </div>
                </div>
              </div>

              <div v-if="resultRecommendations.length" class="detail-block">
                <h3>处置与补拍建议</h3>
                <ol>
                  <li v-for="(item, index) in resultRecommendations" :key="index">{{ item }}</li>
                </ol>
              </div>

              <div v-if="resultWarnings.length" class="detail-block warning-block">
                <h3>限制与警告</h3>
                <ul>
                  <li v-for="(item, index) in resultWarnings" :key="index">{{ item }}</li>
                </ul>
              </div>
            </section>

            <div v-if="aiInference.status === 'FAILED' || aiInference.status === 'REJECTED'" class="error-info">
              <p><strong>{{ aiInference.errorCode }}</strong></p>
              <p>{{ aiInference.errorMessage }}</p>
              <el-button v-if="aiInference.status === 'FAILED'" size="small" type="primary" @click="retryInference">重试</el-button>
            </div>

            <div v-if="aiInference.status === 'SUCCEEDED' || aiInference.status === 'REJECTED'" class="review">
              <div class="title"><h2>人工复核</h2><span>{{ aiInference.reviewStatus }}</span></div>
              <el-input v-if="aiInference.reviewStatus === 'UNREVIEWED'" v-model="aiReviewComment" type="textarea" :rows="2" placeholder="填写复核意见" />
              <el-button v-if="aiInference.reviewStatus === 'UNREVIEWED'" size="small" type="primary" @click="submitReview">确认结果</el-button>
              <p>{{ aiInference.assessmentNote }}</p>
            </div>

            <div class="disclaimer"><p>{{ aiInference.disclaimer }}</p></div>
          </template>

          <AppEmpty v-else-if="!aiBusy" description="选择左侧图片并点击「AI 识别」" />
          <AppLoading v-if="aiBusy" :visible="true" inline text="AI 推理中…" />
        </article>
      </section>
    </template>

    <AppLoading :visible="busy" inline text="处理中…" />
  </section>
</template>

<style scoped lang="scss">
.workspace { display: flex; flex-direction: column; gap: 18px; }
.workspace-head {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  align-items: flex-end;
  h1 { margin: 4px 0; font-size: clamp(24px, 3vw, 36px); letter-spacing: -0.02em; }
}
.eyebrow { margin: 0; color: var(--usp-color-primary); font-weight: 800; text-transform: uppercase; letter-spacing: 0.12em; font-size: 12px; }
.auth { display: flex; gap: 8px; min-width: min(460px, 100%); }
.notice {
  padding: 12px 16px;
  border-radius: var(--usp-radius);
  background: var(--usp-color-primary-light);
  color: var(--usp-color-primary);
  margin: 0;
  &--error { background: #fef3f2; color: var(--usp-color-danger); }
}
.grid { display: grid; gap: 18px; }
.grid.two { grid-template-columns: minmax(0, 1.35fr) minmax(330px, 0.65fr); }
.panel { background: var(--usp-color-surface); border: 1px solid var(--usp-color-border); border-radius: var(--usp-radius); padding: 20px; box-shadow: var(--usp-shadow); }
.title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 14px;
  h2 { margin: 0; font-size: 20px; }
  span { color: var(--usp-color-text-secondary); font-size: 13px; }
}
.map { min-height: 210px; border-radius: var(--usp-radius); overflow: hidden; background: #dfe9e7; margin-bottom: 12px; }
.map--mock { display: flex; align-items: center; justify-content: center; color: var(--usp-color-text-secondary); font-size: 14px; }
.formal-map-entry { flex-direction: column; gap: 10px; padding: 24px; text-align: center; }
.formal-map-entry strong { color: var(--usp-color-text-primary); font-size: 18px; }
.formal-map-entry p { max-width: 560px; margin: 0; line-height: 1.65; }
.cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 10px; }
.community, .task {
  text-align: left;
  display: flex;
  flex-direction: column;
  gap: 5px;
  width: 100%;
  padding: 13px;
  border: 1px solid var(--usp-color-border);
  border-radius: 13px;
  background: var(--usp-color-surface);
  color: inherit;
  &.active { border-color: var(--usp-color-primary); background: var(--usp-color-primary-light); }
  small { color: var(--usp-color-text-secondary); }
}
.community em { color: var(--usp-color-primary); font-style: normal; font-size: 12px; }
.actions { display: flex; flex-wrap: wrap; gap: 9px; margin-top: 14px; align-items: center; }
.inline-alert { margin: 0 0 14px; }
.pending-photo-preview { display: grid; gap: 10px; margin: 14px 0 0; padding: 12px; border: 1px solid var(--usp-color-border); border-radius: var(--usp-radius); background: #f8faf9; }
.pending-photo-preview img { width: 100%; max-height: 280px; object-fit: contain; border-radius: 10px; background: #111; }
.pending-photo-preview figcaption { display: flex; justify-content: space-between; gap: 12px; color: var(--usp-color-text-secondary); font-size: 12px; }
.pending-photo-preview strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--usp-color-text-primary); }
.record { border-top: 1px solid var(--usp-color-border); padding-top: 12px; margin-top: 12px; p { margin: 5px 0; color: var(--usp-color-text-secondary); } }
.badge { display: inline-block; margin-left: 8px; padding: 2px 10px; font-size: 11px; font-weight: 700; color: var(--usp-color-warning, #ff9800); background: #fff3e0; border: 1px solid #ffcc80; border-radius: 4px; vertical-align: middle; }
.model-hint { display: flex; flex-wrap: wrap; gap: 8px 14px; margin-bottom: 12px; padding: 10px 12px; border-radius: var(--usp-radius); background: var(--usp-color-primary-light); font-size: 12px; color: var(--usp-color-text-secondary); }
.image-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 8px 0; border-bottom: 1px solid var(--usp-color-border); &:last-child { border-bottom: none; } }
.image-filename { font-size: 13px; color: var(--usp-color-text-secondary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.result-panel { min-width: 0; }
.result-image { width: 100%; max-height: 360px; border-radius: var(--usp-radius); object-fit: contain; background: #1a1a1a; }
.meta { display: flex; flex-wrap: wrap; gap: 8px 12px; margin-top: 10px; font-size: 12px; color: var(--usp-color-text-secondary); }
.result-details { display: grid; gap: 12px; margin-top: 14px; }
.detail-block { padding: 12px; border: 1px solid var(--usp-color-border); border-radius: var(--usp-radius); background: #fafcfb; }
.detail-block h3 { margin: 0 0 8px; font-size: 15px; }
.detail-block p { margin: 0; color: var(--usp-color-text-secondary); line-height: 1.65; }
.detail-block ol, .detail-block ul { margin: 0; padding-left: 20px; color: var(--usp-color-text-secondary); line-height: 1.75; }
.confidence { display: inline-block; margin-top: 8px; color: var(--usp-color-primary); font-size: 12px; font-weight: 700; }
.detail-list { display: grid; gap: 7px; }
.detection-item { display: flex; justify-content: space-between; padding: 7px 9px; border-radius: 6px; background: #fef3f2; font-size: 13px; }
.detection-class { font-weight: 600; color: var(--usp-color-danger); }
.risk-item { padding: 10px; border-radius: 8px; background: #fff; border: 1px solid var(--usp-color-border); }
.risk-item > div { display: flex; align-items: center; gap: 8px; }
.risk-item p { margin-top: 6px; }
.risk-item > span { display: inline-block; margin-top: 5px; color: var(--usp-color-text-secondary); font-size: 12px; }
.warning-block { background: #fffcf5; border-color: #fedf89; }
.error-info { margin-top: 10px; padding: 12px; background: #fef3f2; border-radius: var(--usp-radius); font-size: 13px; p { margin: 0 0 6px; } strong { color: var(--usp-color-danger); } }
.review { margin-top: 14px; padding-top: 12px; border-top: 1px solid var(--usp-color-border); }
.disclaimer { margin-top: 14px; padding: 10px 12px; background: var(--usp-color-primary-light); border-radius: var(--usp-radius); p { margin: 0; font-size: 12px; color: var(--usp-color-primary); } }
@media (max-width: 900px) {
  .workspace-head { align-items: stretch; flex-direction: column; }
  .auth { min-width: 0; }
  .grid.two { grid-template-columns: 1fr; }
  .pending-photo-preview figcaption { flex-direction: column; }
}
</style>
