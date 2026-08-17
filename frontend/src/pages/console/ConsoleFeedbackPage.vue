<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  completeFeedbackReinspection,
  createFeedbackReinspection,
  createManualFeedback,
  fetchImageBlobUrl,
  getFeedbackAiAssist,
  getFeedbackReinspectionRecommendation,
  listFeedbackImages,
  listFeedbackReports,
  listPublicFeedbackBuildings,
  listPublicFeedbackCommunities,
  submitFeedbackRectification,
  toAppError,
  updateFeedbackStatus,
  waiveFeedbackReinspection,
  type FeedbackAiAssistView,
  type FeedbackChannel,
  type FeedbackImage,
  type FeedbackManagementRow,
  type FeedbackReinspectionDecision,
  type FeedbackReinspectionRecommendation,
  type FeedbackReportType,
  type FeedbackStatus,
  type FeedbackUrgency,
  type PublicFeedbackBuilding,
  type PublicFeedbackCommunity,
} from '@/shared/api'
import { listImages, uploadImage, type AssetImageRow } from '@/shared/api/endpoints/assets'
import AppActionButton from '@/shared/components/AppActionButton.vue'
import AppDateTime from '@/shared/components/AppDateTime.vue'
import AppEmpty from '@/shared/components/AppEmpty.vue'
import AppFilterBar from '@/shared/components/AppFilterBar.vue'
import AppFilterField from '@/shared/components/AppFilterField.vue'
import AppQueryField from '@/shared/components/AppQueryField.vue'
import AppTablePager from '@/shared/components/AppTablePager.vue'
import AiInsightCard from '@/shared/components/ai/AiInsightCard.vue'
import AppPageHeader from '@/shared/components/layout/AppPageHeader.vue'
import SpatialObjectSelector from '@/shared/components/SpatialObjectSelector.vue'
import type { SpatialObjectSelection } from '@/shared/composables/useSpatialObjectSelector'

interface ManagementDisplayImage extends FeedbackImage {
  url: string
}

const loading = ref(false)
const rows = ref<FeedbackManagementRow[]>([])
const total = ref(0)
const statusDrawerVisible = ref(false)
const aiAssistDrawerVisible = ref(false)
const aiAssistLoading = ref(false)
const aiAssist = ref<FeedbackAiAssistView | null>(null)
const manualDialogVisible = ref(false)
const manualSubmitting = ref(false)
const imageDialogVisible = ref(false)
const imageLoading = ref(false)
const managementImages = ref<ManagementDisplayImage[]>([])
const current = ref<FeedbackManagementRow | null>(null)
const communities = ref<PublicFeedbackCommunity[]>([])
const manualBuildings = ref<PublicFeedbackBuilding[]>([])
const feedbackKeyword = ref('')
const selectedCommunityId = ref('')
const selectedBuildingId = ref('')
const dateRange = ref<string[]>([])
const selectorRevision = ref(0)
const closureSaving = ref(false)
const uploadingEvidence = ref(false)
const rectificationEvidence = ref<AssetImageRow[]>([])
const recommendationLoading = ref(false)
const recommendation = ref<FeedbackReinspectionRecommendation | null>(null)
const reinspectionDecision = ref<FeedbackReinspectionDecision>('REQUIRED')
const decisionReason = ref('')
const waiverVisible = ref(false)
const waiverReason = ref('')
const resultVisible = ref(false)
const resultPassed = ref(true)
const reinspectionSummary = ref('')

const filters = reactive({
  status: '' as FeedbackStatus | '',
  feedbackChannel: '' as FeedbackChannel | '',
  page: 1,
  size: 20,
})
const updateForm = reactive({
  status: 'ACCEPTED' as FeedbackStatus,
  handlingSummary: '',
  message: '',
  publicVisible: true,
})
const manualForm = reactive({
  feedbackChannel: 'PHONE' as Exclude<FeedbackChannel, 'WEB'>,
  communityId: '',
  buildingId: '',
  reportType: 'OTHER' as FeedbackReportType,
  urgency: 'NORMAL' as FeedbackUrgency,
  description: '',
  locationText: '',
  reporterName: '',
  contactPhone: '',
  contactEmail: '',
  contactConsent: true,
})

const decisionNeedsReason = computed(() => {
  if (reinspectionDecision.value === 'WAIVED') return true
  return Boolean(
    recommendation.value
      && recommendation.value.recommendedDecision !== reinspectionDecision.value,
  )
})

const statusOptions: Array<{ value: FeedbackStatus; label: string }> = [
  { value: 'SUBMITTED', label: '已提交' },
  { value: 'ACCEPTED', label: '已受理' },
  { value: 'PROCESSING', label: '处理中' },
  { value: 'NEED_MORE_INFO', label: '待补充' },
  { value: 'RESOLVED', label: '待复验' },
  { value: 'CLOSED', label: '已闭环' },
  { value: 'REJECTED', label: '未受理' },
  { value: 'CANCELLED', label: '已取消' },
]
const channelOptions: Array<{ value: FeedbackChannel; label: string }> = [
  { value: 'WEB', label: '网页' },
  { value: 'PHONE', label: '电话' },
  { value: 'SMS', label: '短信' },
  { value: 'COUNTER', label: '窗口' },
  { value: 'INTERNAL', label: '内部登记' },
]
const manualChannelOptions = channelOptions.filter(
  (item): item is { value: Exclude<FeedbackChannel, 'WEB'>; label: string } => item.value !== 'WEB',
)
const statusLabels = Object.fromEntries(statusOptions.map((item) => [item.value, item.label]))
const channelLabels = Object.fromEntries(channelOptions.map((item) => [item.value, item.label]))

function nextStatusOptions(status?: FeedbackStatus): Array<{ value: FeedbackStatus; label: string }> {
  if (status === 'SUBMITTED') return statusOptions.filter((item) => ['ACCEPTED', 'REJECTED'].includes(item.value))
  if (status === 'ACCEPTED') return statusOptions.filter((item) => ['PROCESSING', 'NEED_MORE_INFO', 'CANCELLED'].includes(item.value))
  if (status === 'PROCESSING') return statusOptions.filter((item) => ['RESOLVED', 'NEED_MORE_INFO'].includes(item.value))
  if (status === 'NEED_MORE_INFO') return statusOptions.filter((item) => ['PROCESSING', 'CANCELLED'].includes(item.value))
  return []
}

function defaultNextStatus(status: FeedbackStatus): FeedbackStatus {
  if (status === 'SUBMITTED') return 'ACCEPTED'
  if (status === 'ACCEPTED' || status === 'NEED_MORE_INFO') return 'PROCESSING'
  if (status === 'PROCESSING') return 'RESOLVED'
  return status
}

function reinspectionStatusText(status?: string): string {
  if (status === 'PENDING') return '复查任务待开始'
  if (status === 'IN_PROGRESS') return '复查任务进行中'
  if (status === 'COMPLETED') return '复查任务已完成，等待复验结论'
  if (status === 'CANCELLED') return '复查任务已取消，可重新发起或人工免复检'
  return '尚未创建复查任务'
}

function decisionLabel(decision?: FeedbackReinspectionDecision): string {
  if (decision === 'REQUIRED') return '需要现场复检'
  if (decision === 'WAIVED') return '人工免复检'
  return '未记录复检决策'
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const result = await listFeedbackReports({
      status: filters.status || undefined,
      feedbackChannel: filters.feedbackChannel || undefined,
      communityId: selectedCommunityId.value || undefined,
      buildingId: selectedBuildingId.value || undefined,
      keyword: feedbackKeyword.value.trim() || undefined,
      submittedFrom: dateRange.value[0] ? new Date(`${dateRange.value[0]}T00:00:00`).toISOString() : undefined,
      submittedTo: dateRange.value[1] ? new Date(`${dateRange.value[1]}T23:59:59.999`).toISOString() : undefined,
      page: filters.page - 1,
      size: filters.size,
    })
    rows.value = result.content ?? []
    total.value = Number(result.page?.totalElements ?? rows.value.length)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '反馈列表加载失败')
  } finally {
    loading.value = false
  }
}

async function runQuery(): Promise<void> {
  filters.page = 1
  await load()
}

async function resetFilters(): Promise<void> {
  feedbackKeyword.value = ''
  filters.status = ''
  filters.feedbackChannel = ''
  selectedCommunityId.value = ''
  selectedBuildingId.value = ''
  dateRange.value = []
  filters.page = 1
  selectorRevision.value += 1
  await load()
}

async function handleSpatialSelection(selection: SpatialObjectSelection): Promise<void> {
  selectedCommunityId.value = selection.communityId
  selectedBuildingId.value = selection.buildingId
  await runQuery()
}

async function loadCommunities(): Promise<void> {
  try {
    communities.value = await listPublicFeedbackCommunities()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '小区列表加载失败')
  }
}

watch(
  () => manualForm.communityId,
  async (communityId) => {
    manualForm.buildingId = ''
    manualBuildings.value = []
    if (!communityId) return
    try {
      manualBuildings.value = await listPublicFeedbackBuildings(communityId)
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '楼栋列表加载失败')
    }
  },
)

function releaseManagementImages(): void {
  managementImages.value.forEach((item) => URL.revokeObjectURL(item.url))
  managementImages.value = []
}

watch(imageDialogVisible, (visible) => {
  if (!visible) releaseManagementImages()
})

async function loadRectificationEvidence(reportId: string): Promise<void> {
  try {
    const page = await listImages({ businessType: 'RESIDENT_REPORT', businessId: reportId })
    rectificationEvidence.value = page.content.filter((item) => item.bindingRole === 'RECTIFICATION_PHOTO')
  } catch (error) {
    rectificationEvidence.value = []
    ElMessage.warning(toAppError(error).message)
  }
}

async function loadRecommendation(reportId: string): Promise<void> {
  recommendationLoading.value = true
  recommendation.value = null
  reinspectionDecision.value = 'REQUIRED'
  try {
    const result = await getFeedbackReinspectionRecommendation(reportId)
    recommendation.value = result
    reinspectionDecision.value = result.recommendedDecision
  } catch (error) {
    ElMessage.warning(`复检建议暂不可用，已按“需要现场复检”作为安全默认值：${toAppError(error).message}`)
  } finally {
    recommendationLoading.value = false
  }
}

async function openStatus(row: FeedbackManagementRow): Promise<void> {
  if (!nextStatusOptions(row.status).length) return
  current.value = row
  updateForm.status = defaultNextStatus(row.status)
  updateForm.handlingSummary = row.handlingSummary || ''
  updateForm.message = ''
  updateForm.publicVisible = true
  rectificationEvidence.value = []
  recommendation.value = null
  reinspectionDecision.value = 'REQUIRED'
  decisionReason.value = ''
  if (row.status === 'PROCESSING') {
    await Promise.all([
      loadRectificationEvidence(row.reportId),
      loadRecommendation(row.reportId),
    ])
  }
  statusDrawerVisible.value = true
}

async function uploadRectificationEvidence(event: Event): Promise<void> {
  if (!current.value) return
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  uploadingEvidence.value = true
  try {
    await uploadImage({
      file,
      businessType: 'RESIDENT_REPORT',
      businessId: current.value.reportId,
      bindingRole: 'RECTIFICATION_PHOTO',
    })
    ElMessage.success('整改证据已上传')
    await loadRectificationEvidence(current.value.reportId)
  } catch (error) {
    ElMessage.error(toAppError(error).message)
  } finally {
    uploadingEvidence.value = false
  }
}

async function openAiAssist(row: FeedbackManagementRow): Promise<void> {
  current.value = row
  aiAssist.value = null
  aiAssistDrawerVisible.value = true
  aiAssistLoading.value = true
  try {
    aiAssist.value = await getFeedbackAiAssist(row.reportId)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'AI 初步归类暂时不可用')
  } finally {
    aiAssistLoading.value = false
  }
}

async function openImages(row: FeedbackManagementRow): Promise<void> {
  current.value = row
  imageDialogVisible.value = true
  imageLoading.value = true
  releaseManagementImages()
  try {
    const images = await listFeedbackImages(row.reportId)
    const loaded: ManagementDisplayImage[] = []
    for (const image of images) {
      try {
        const url = await fetchImageBlobUrl(image.assetId)
        loaded.push({ ...image, url })
      } catch {
        // 单张图片失败不阻断其他图片查看。
      }
    }
    managementImages.value = loaded
    if (loaded.length < images.length) ElMessage.warning('部分反馈图片暂时无法加载')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '反馈图片加载失败')
  } finally {
    imageLoading.value = false
  }
}

async function submitStatus(): Promise<void> {
  if (!current.value) return
  if (!updateForm.handlingSummary.trim()) {
    ElMessage.warning('请填写处理或整改说明')
    return
  }
  if (updateForm.status === 'RESOLVED') {
    if (rectificationEvidence.value.length === 0) {
      ElMessage.warning('提交整改完成前至少上传一张整改证据')
      return
    }
    if (decisionNeedsReason.value && decisionReason.value.trim().length < 4) {
      ElMessage.warning('选择免复检或调整系统建议时，请填写至少 4 个字符的人工判断理由')
      return
    }
  }

  closureSaving.value = true
  try {
    if (updateForm.status === 'RESOLVED') {
      const result = await submitFeedbackRectification(current.value.reportId, {
        handlingSummary: updateForm.handlingSummary.trim(),
        message: updateForm.message.trim() || undefined,
        reinspectionDecision: reinspectionDecision.value,
        decisionReason: decisionReason.value.trim() || undefined,
      })
      if (result.status === 'CLOSED') {
        ElMessage.success('整改已提交，经人工确认无需现场复检，工单已闭环')
      } else {
        ElMessage.success('整改已提交，当前进入待复验阶段')
      }
      if (result.manualOverride) ElMessage.info('本次人工决策与系统建议不同，覆盖理由已留痕')
    } else {
      await updateFeedbackStatus(current.value.reportId, updateForm)
      ElMessage.success('反馈状态已更新')
    }
    statusDrawerVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(toAppError(error).message)
  } finally {
    closureSaving.value = false
  }
}

async function createReinspection(row: FeedbackManagementRow): Promise<void> {
  closureSaving.value = true
  try {
    const result = await createFeedbackReinspection(row.reportId)
    ElMessage.success(result.reused ? '已存在复查任务，已刷新状态' : '复查复验任务已创建')
    if (result.formalRiskNotice) ElMessage.info(result.formalRiskNotice)
    await load()
  } catch (error) {
    ElMessage.error(toAppError(error).message)
  } finally {
    closureSaving.value = false
  }
}

async function openWaiver(row: FeedbackManagementRow): Promise<void> {
  current.value = row
  waiverReason.value = ''
  recommendation.value = null
  await loadRecommendation(row.reportId)
  waiverVisible.value = true
}

async function submitWaiver(): Promise<void> {
  if (!current.value) return
  if (waiverReason.value.trim().length < 4) {
    ElMessage.warning('请填写至少 4 个字符的人工免复检判断理由')
    return
  }
  closureSaving.value = true
  try {
    const result = await waiveFeedbackReinspection(current.value.reportId, {
      decisionReason: waiverReason.value.trim(),
    })
    ElMessage.success('已人工确认无需现场复检，整改事项已闭环')
    if (result.manualOverride) ElMessage.info('本次人工决策调整了系统建议，理由已写入审计记录')
    waiverVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(toAppError(error).message)
  } finally {
    closureSaving.value = false
  }
}

function openReinspectionResult(row: FeedbackManagementRow, passed: boolean): void {
  current.value = row
  resultPassed.value = passed
  reinspectionSummary.value = ''
  resultVisible.value = true
}

async function submitReinspectionResult(): Promise<void> {
  if (!current.value) return
  if (reinspectionSummary.value.trim().length < 4) {
    ElMessage.warning('请填写至少 4 个字符的复验说明')
    return
  }
  closureSaving.value = true
  try {
    const result = await completeFeedbackReinspection(current.value.reportId, {
      passed: resultPassed.value,
      summary: reinspectionSummary.value.trim(),
    })
    ElMessage.success(resultPassed.value ? '复验通过，整改事项已闭环' : '复验未通过，已退回继续整改')
    if (result.nextStep) ElMessage.info(result.nextStep)
    resultVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(toAppError(error).message)
  } finally {
    closureSaving.value = false
  }
}

async function submitManual(): Promise<void> {
  if (!manualForm.communityId || manualForm.description.trim().length < 10) {
    ElMessage.warning('请选择小区，并填写不少于 10 个字符的问题描述')
    return
  }
  if ((manualForm.contactPhone || manualForm.contactEmail) && !manualForm.contactConsent) {
    ElMessage.warning('填写联系方式时需要确认联系授权')
    return
  }
  manualSubmitting.value = true
  try {
    const result = await createManualFeedback({
      feedbackChannel: manualForm.feedbackChannel,
      communityId: manualForm.communityId,
      buildingId: manualForm.buildingId || undefined,
      reportType: manualForm.reportType,
      urgency: manualForm.urgency,
      description: manualForm.description.trim(),
      locationText: manualForm.locationText.trim() || undefined,
      reporterName: manualForm.reporterName.trim() || undefined,
      contactPhone: manualForm.contactPhone.trim() || undefined,
      contactEmail: manualForm.contactEmail.trim() || undefined,
      contactConsent: manualForm.contactConsent,
    })
    ElMessage.success(`代录成功：${result.reportCode}`)
    manualDialogVisible.value = false
    manualForm.description = ''
    manualForm.locationText = ''
    manualForm.reporterName = ''
    manualForm.contactPhone = ''
    manualForm.contactEmail = ''
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '反馈代录失败')
  } finally {
    manualSubmitting.value = false
  }
}

onMounted(async () => {
  await Promise.all([load(), loadCommunities()])
})

onBeforeUnmount(releaseManagementImages)
</script>

<template>
  <section class="console-feedback">
    <AppPageHeader
      eyebrow="公众参与 · 整改闭环"
      title="公众反馈管理"
      description="统一查询风险线索；系统给出复检建议，最终由工作人员结合整改证据人工确认。"
      show-user-menu
    >
      <template #actions>
        <el-button @click="load">刷新</el-button>
        <el-button type="primary" @click="manualDialogVisible = true">代录反馈</el-button>
      </template>
    </AppPageHeader>

    <el-alert
      title="治理闭环"
      type="info"
      :closable="false"
      show-icon
      description="处理中 → 上传整改证据 → 系统给出复检建议 → 人工确认需要复检或免复检。需要复检时完成现场复验后闭环；免复检时填写判断理由后直接闭环。两条路径均不会直接修改正式风险评分。"
    />

    <AppFilterBar :loading="loading" @query="runQuery" @reset="resetFilters">
      <AppFilterField kind="keyword">
        <AppQueryField
          v-model="feedbackKeyword"
          placeholder="搜索工单编号、问题描述或位置"
          width="100%"
          @query="runQuery"
        />
      </AppFilterField>
      <AppFilterField kind="status">
        <el-select v-model="filters.status" clearable placeholder="全部状态">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </AppFilterField>
      <AppFilterField kind="type">
        <el-select v-model="filters.feedbackChannel" clearable placeholder="全部渠道">
          <el-option v-for="item in channelOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </AppFilterField>
      <AppFilterField kind="spatial">
        <SpatialObjectSelector
          :key="selectorRevision"
          v-model:community-id="selectedCommunityId"
          v-model:building-id="selectedBuildingId"
          mode="both"
          @change="handleSpatialSelection"
        />
      </AppFilterField>
      <AppFilterField kind="date-range">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="提交开始"
          end-placeholder="提交结束"
        />
      </AppFilterField>
    </AppFilterBar>

    <el-card shadow="never" class="feedback-table-card">
      <el-table v-loading="loading" :data="rows" row-key="reportId" stripe>
        <el-table-column label="小区 / 楼栋" min-width="190">
          <template #default="scope">
            <div class="location-cell">
              <strong>{{ scope.row.communityName }}</strong>
              <small>{{ scope.row.buildingName || scope.row.locationText || '未指定楼栋' }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="渠道" width="90">
          <template #default="scope">{{ channelLabels[scope.row.feedbackChannel] || scope.row.feedbackChannel }}</template>
        </el-table-column>
        <el-table-column label="状态" width="105">
          <template #default="scope"><el-tag effect="plain">{{ statusLabels[scope.row.status] || scope.row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column label="复检决策" min-width="210">
          <template #default="scope">
            <div v-if="scope.row.status === 'RESOLVED'" class="reinspection-cell">
              <strong>{{ scope.row.reinspectionTaskCode || decisionLabel(scope.row.reinspectionDecision) }}</strong>
              <small>{{ reinspectionStatusText(scope.row.reinspectionStatus) }}</small>
              <small v-if="scope.row.reinspectionManualOverride" class="override-label">人工已调整系统建议</small>
            </div>
            <div v-else-if="scope.row.status === 'CLOSED'" class="reinspection-cell">
              <strong class="closed-label">✓ {{ scope.row.reinspectionDecision === 'WAIVED' ? '人工免复检闭环' : '已闭环' }}</strong>
              <small v-if="scope.row.reinspectionDecisionReason">{{ scope.row.reinspectionDecisionReason }}</small>
            </div>
            <span v-else class="muted-label">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="问题描述" min-width="250" show-overflow-tooltip />
        <el-table-column label="提交时间" width="165">
          <template #default="scope"><AppDateTime :value="scope.row.submittedAt" /></template>
        </el-table-column>
        <el-table-column label="图片" width="92" align="center">
          <template #default="scope">
            <AppActionButton :disabled="!scope.row.imageCount" @click="openImages(scope.row)">
              {{ scope.row.imageCount || 0 }} 张
            </AppActionButton>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="410" fixed="right">
          <template #default="scope">
            <div class="row-actions">
              <AppActionButton @click="openAiAssist(scope.row)">AI 初步归类</AppActionButton>
              <AppActionButton
                v-if="nextStatusOptions(scope.row.status).length"
                type="primary"
                @click="openStatus(scope.row)"
              >{{ scope.row.status === 'PROCESSING' ? '提交整改' : '处理' }}</AppActionButton>
              <template v-if="scope.row.status === 'RESOLVED'">
                <template v-if="!scope.row.reinspectionTaskId || scope.row.reinspectionStatus === 'CANCELLED'">
                  <AppActionButton type="primary" @click="createReinspection(scope.row)">发起复查复验</AppActionButton>
                  <AppActionButton type="warning" @click="openWaiver(scope.row)">人工确认无需复检</AppActionButton>
                </template>
                <template v-else-if="scope.row.reinspectionStatus === 'COMPLETED'">
                  <AppActionButton type="success" @click="openReinspectionResult(scope.row, true)">复验通过并关闭</AppActionButton>
                  <AppActionButton type="warning" @click="openReinspectionResult(scope.row, false)">复验不通过</AppActionButton>
                </template>
              </template>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <AppEmpty v-if="!loading && !rows.length" description="当前筛选条件下暂无公众反馈" />
      <AppTablePager
        v-if="total > 0"
        v-model:page="filters.page"
        v-model:page-size="filters.size"
        :total="total"
        @change="load"
      />
    </el-card>

    <el-drawer v-model="aiAssistDrawerVisible" title="AI 初步归类" size="min(620px, 94vw)">
      <div v-loading="aiAssistLoading" class="ai-assist-drawer">
        <div v-if="current" class="current-status-card">
          <span>当前反馈对象</span>
          <strong>{{ current.reportCode }}</strong>
          <small>{{ current.communityName }} · {{ current.buildingName || current.locationText || '未指定楼栋' }}</small>
        </div>
        <AiInsightCard
          v-if="aiAssist"
          title="公众反馈辅助分流"
          :summary="aiAssist.answer"
          suggestion="请结合现场信息与现有工单规则人工确认后，再决定是否受理、巡检或转专业复核。"
          eyebrow="✦ AI"
        />
        <el-alert
          v-if="aiAssist"
          :title="aiAssist.disclaimer || 'AI 初步归类不会自动修改反馈状态，原处理操作仍是唯一状态更新入口。'"
          type="info"
          :closable="false"
          show-icon
        />
        <el-empty v-if="!aiAssistLoading && !aiAssist" description="当前未生成 AI 初步归类结果" />
      </div>
      <template #footer>
        <el-button @click="aiAssistDrawerVisible = false">关闭</el-button>
        <el-button v-if="current && nextStatusOptions(current.status).length" type="primary" @click="aiAssistDrawerVisible = false; openStatus(current)">进入人工处理</el-button>
      </template>
    </el-drawer>

    <el-drawer v-model="statusDrawerVisible" title="处理公众反馈 · 处置与整改" size="min(600px, 94vw)">
      <div class="status-drawer-content">
        <div class="current-status-card">
          <span>当前状态</span>
          <el-tag effect="plain">{{ current ? statusLabels[current.status] || current.status : '—' }}</el-tag>
          <small v-if="current">{{ current.communityName }} · {{ current.buildingName || current.locationText || '未指定楼栋' }}</small>
        </div>
        <el-form label-position="top">
          <el-form-item label="目标状态">
            <el-select v-model="updateForm.status" style="width: 100%">
              <el-option v-for="item in nextStatusOptions(current?.status)" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item :label="updateForm.status === 'RESOLVED' ? '整改说明（必填）' : '处理摘要（必填）'">
            <el-input v-model="updateForm.handlingSummary" type="textarea" :rows="4" maxlength="2000" show-word-limit />
          </el-form-item>
          <el-form-item v-if="updateForm.status === 'RESOLVED'" label="整改证据（至少 1 张）">
            <div class="evidence-block">
              <label class="evidence-upload">
                <input type="file" accept="image/jpeg,image/png,image/webp" :disabled="uploadingEvidence" @change="uploadRectificationEvidence" />
                <span>{{ uploadingEvidence ? '上传中…' : '+ 上传整改后照片' }}</span>
              </label>
              <div v-if="rectificationEvidence.length" class="evidence-list">
                <span v-for="asset in rectificationEvidence" :key="asset.assetId">{{ asset.originalFilename }}</span>
              </div>
              <small v-else>整改完成不能只填文字，请至少上传一张整改证据。</small>
            </div>
          </el-form-item>

          <template v-if="updateForm.status === 'RESOLVED'">
            <div class="recommendation-card" :class="recommendation?.recommendedDecision === 'REQUIRED' ? 'is-required' : 'is-waivable'">
              <div class="recommendation-head">
                <div>
                  <span>系统复检建议</span>
                  <strong v-if="recommendationLoading">正在分析结构化风险信息…</strong>
                  <strong v-else-if="recommendation">
                    {{ recommendation.recommendedDecision === 'REQUIRED' ? '建议现场复检' : '可考虑免现场复检' }}
                  </strong>
                  <strong v-else>建议暂不可用，默认需要现场复检</strong>
                </div>
                <el-tag v-if="recommendation" :type="recommendation.recommendedDecision === 'REQUIRED' ? 'warning' : 'success'" effect="plain">
                  {{ recommendation.recommendedDecision === 'REQUIRED' ? '建议复检' : '可免复检' }}
                </el-tag>
              </div>
              <ul v-if="recommendation?.reasons?.length">
                <li v-for="reason in recommendation.reasons" :key="reason">{{ reason }}</li>
              </ul>
              <small>{{ recommendation?.disclaimer || '系统建议仅用于辅助判断，最终决定由工作人员确认并留痕。' }}</small>
            </div>

            <el-form-item label="人工最终决定">
              <el-radio-group v-model="reinspectionDecision" class="decision-options">
                <el-radio-button value="REQUIRED">需要现场复检</el-radio-button>
                <el-radio-button value="WAIVED">无需复检，直接闭环</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-alert
              v-if="recommendation && recommendation.recommendedDecision !== reinspectionDecision"
              title="当前人工决定与系统建议不同"
              type="warning"
              :closable="false"
              show-icon
              description="允许人工调整，但必须填写理由；系统建议、最终决定和理由都会保存到审计记录。"
            />
            <el-form-item v-if="decisionNeedsReason" label="人工判断理由（必填）" class="decision-reason">
              <el-input
                v-model="decisionReason"
                type="textarea"
                :rows="3"
                maxlength="1000"
                show-word-limit
                placeholder="说明为何免复检或为何调整系统建议，至少 4 个字符"
              />
            </el-form-item>
          </template>

          <el-form-item label="向反馈人说明">
            <el-input v-model="updateForm.message" type="textarea" :rows="4" maxlength="1000" />
          </el-form-item>
          <el-switch v-if="updateForm.status !== 'RESOLVED'" v-model="updateForm.publicVisible" active-text="向反馈人公开本次状态说明" />
        </el-form>
      </div>
      <template #footer>
        <el-button @click="statusDrawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="closureSaving" @click="submitStatus">
          <template v-if="updateForm.status === 'RESOLVED'">
            {{ reinspectionDecision === 'REQUIRED' ? '提交整改并进入待复验' : '提交整改并人工免复检闭环' }}
          </template>
          <template v-else>确认更新</template>
        </el-button>
      </template>
    </el-drawer>

    <el-dialog v-model="waiverVisible" title="人工确认无需现场复检" width="min(94vw, 560px)">
      <el-alert
        title="确认后将直接完成该治理工单闭环"
        type="warning"
        :closable="false"
        show-icon
        description="仅允许尚未派出有效复检任务的待复验工单执行。已经开始或等待结论的复检任务不能绕过。"
      />
      <div v-if="recommendation" class="recommendation-card compact" :class="recommendation.recommendedDecision === 'REQUIRED' ? 'is-required' : 'is-waivable'">
        <div class="recommendation-head">
          <div>
            <span>系统建议</span>
            <strong>{{ recommendation.recommendedDecision === 'REQUIRED' ? '建议现场复检' : '可考虑免现场复检' }}</strong>
          </div>
        </div>
        <small>{{ recommendation.reasons.join('；') }}</small>
      </div>
      <el-input
        v-model="waiverReason"
        type="textarea"
        :rows="5"
        maxlength="1000"
        show-word-limit
        class="waiver-reason"
        placeholder="填写人工确认无需现场复检的依据，至少 4 个字符"
      />
      <p class="risk-boundary">免复检仅关闭治理工单，不会直接修改正式风险评分；形成新证据时仍应重新执行正式评分。</p>
      <template #footer>
        <el-button @click="waiverVisible = false">取消</el-button>
        <el-button type="warning" :loading="closureSaving" @click="submitWaiver">确认免复检并闭环</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resultVisible" :title="resultPassed ? '确认复验通过' : '确认复验不通过'" width="min(94vw, 560px)">
      <el-alert
        :title="resultPassed ? '通过后工单将正式关闭' : '不通过后工单将退回继续整改'"
        :type="resultPassed ? 'success' : 'warning'"
        :closable="false"
        show-icon
      />
      <p class="risk-boundary">复验不会直接修改正式风险评分；如本次形成新的有效现场证据，应重新执行正式评分链。</p>
      <el-input
        v-model="reinspectionSummary"
        type="textarea"
        :rows="5"
        maxlength="2000"
        show-word-limit
        placeholder="填写复查位置、现场结果、是否仍存在原问题等复验依据"
      />
      <template #footer>
        <el-button @click="resultVisible = false">取消</el-button>
        <el-button :type="resultPassed ? 'success' : 'warning'" :loading="closureSaving" @click="submitReinspectionResult">
          {{ resultPassed ? '复验通过并关闭' : '复验不通过' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="imageDialogVisible" :title="`反馈图片 · ${current?.reportCode || ''}`" width="min(94vw, 860px)">
      <div v-loading="imageLoading" class="management-image-grid">
        <el-empty v-if="!imageLoading && !managementImages.length" description="暂无可查看图片" />
        <figure v-for="(image, index) in managementImages" :key="image.assetId">
          <el-image :src="image.url" :preview-src-list="managementImages.map((item) => item.url)" :initial-index="index" fit="cover" preview-teleported />
          <figcaption>{{ image.originalFilename }}</figcaption>
        </figure>
      </div>
    </el-dialog>

    <el-dialog v-model="manualDialogVisible" title="代录电话、短信或窗口反馈" width="min(94vw, 720px)">
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="反馈渠道">
            <el-select v-model="manualForm.feedbackChannel" style="width: 100%">
              <el-option v-for="item in manualChannelOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="紧急程度">
            <el-select v-model="manualForm.urgency" style="width: 100%">
              <el-option label="较低" value="LOW" />
              <el-option label="一般" value="NORMAL" />
              <el-option label="较高" value="HIGH" />
              <el-option label="紧急" value="URGENT" />
            </el-select>
          </el-form-item>
          <el-form-item label="小区">
            <el-select v-model="manualForm.communityId" filterable style="width: 100%">
              <el-option v-for="item in communities" :key="item.communityId" :label="item.communityName" :value="item.communityId" />
            </el-select>
          </el-form-item>
          <el-form-item label="楼栋（可选）">
            <el-select v-model="manualForm.buildingId" clearable filterable style="width: 100%">
              <el-option v-for="item in manualBuildings" :key="item.buildingId" :label="item.buildingName" :value="item.buildingId" />
            </el-select>
          </el-form-item>
          <el-form-item label="问题类型">
            <el-select v-model="manualForm.reportType" style="width: 100%">
              <el-option label="墙体裂缝" value="WALL_CRACK" />
              <el-option label="表面脱落" value="SURFACE_FALLING" />
              <el-option label="渗漏" value="WATER_LEAKAGE" />
              <el-option label="违规改造" value="ILLEGAL_MODIFICATION" />
              <el-option label="消防通道" value="FIRE_ACCESS" />
              <el-option label="其他" value="OTHER" />
            </el-select>
          </el-form-item>
          <el-form-item label="具体位置"><el-input v-model="manualForm.locationText" maxlength="512" /></el-form-item>
          <el-form-item label="反馈人"><el-input v-model="manualForm.reporterName" maxlength="128" /></el-form-item>
          <el-form-item label="联系电话"><el-input v-model="manualForm.contactPhone" maxlength="32" /></el-form-item>
        </div>
        <el-form-item label="电子邮箱"><el-input v-model="manualForm.contactEmail" maxlength="255" /></el-form-item>
        <el-form-item label="问题描述"><el-input v-model="manualForm.description" type="textarea" :rows="4" maxlength="2000" show-word-limit /></el-form-item>
        <el-checkbox v-model="manualForm.contactConsent">反馈人同意工作人员为处理本次事项使用联系方式</el-checkbox>
      </el-form>
      <template #footer>
        <el-button @click="manualDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="manualSubmitting" @click="submitManual">确认代录</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped lang="scss">
.console-feedback { display: grid; gap: 14px; }
.feedback-table-card,
.current-status-card { border-radius: var(--usp-radius-xl); }
.feedback-table-card { box-shadow: var(--usp-shadow-sm); }
.feedback-table-card :deep(.el-card__body) { display: grid; gap: 12px; padding: 14px 16px; }
.location-cell,
.reinspection-cell { display: grid; gap: 2px; }
.location-cell small,
.reinspection-cell small,
.muted-label { color: var(--usp-color-text-secondary); }
.closed-label { color: #176354; font-weight: 700; }
.override-label { color: #b54708 !important; font-weight: 700; }
.row-actions { display: flex; align-items: center; gap: 6px; white-space: nowrap; }
.status-drawer-content,
.ai-assist-drawer { display: grid; gap: 18px; }
.ai-assist-drawer :deep(.ai-insight-card p) { white-space: pre-wrap; }
.current-status-card { display: grid; gap: 7px; padding: 14px; border: 1px solid var(--usp-color-border); background: var(--usp-color-surface-muted); }
.current-status-card small { color: var(--usp-color-text-secondary); }
.evidence-block { display: grid; gap: 10px; width: 100%; }
.evidence-upload { display: flex; min-height: 52px; align-items: center; justify-content: center; border: 1px dashed #8eb9ae; border-radius: var(--usp-radius-lg); background: #f4faf8; color: #176354; font-weight: 700; cursor: pointer; }
.evidence-upload input { display: none; }
.evidence-list { display: grid; gap: 6px; }
.evidence-list span { padding: 8px 10px; border-radius: var(--usp-radius-md); background: var(--usp-color-surface-muted); overflow-wrap: anywhere; font-size: 12px; }
.evidence-block small { color: #b54708; }
.recommendation-card { display: grid; gap: 10px; margin: 0 0 18px; padding: 14px; border: 1px solid var(--usp-color-border); border-radius: var(--usp-radius-xl); background: var(--usp-color-surface-muted); }
.recommendation-card.is-required { border-color: #f0b5a8; background: #fff5f2; }
.recommendation-card.is-waivable { border-color: #a8d8c7; background: #f2faf7; }
.recommendation-card.compact { margin: 14px 0; }
.recommendation-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.recommendation-head > div { display: grid; gap: 4px; }
.recommendation-head span { color: var(--usp-color-text-secondary); font-size: 12px; font-weight: 700; }
.recommendation-card ul { margin: 0; padding-left: 18px; color: var(--usp-color-text-secondary); line-height: 1.65; }
.recommendation-card small { color: var(--usp-color-text-secondary); line-height: 1.6; }
.decision-options { display: flex; width: 100%; }
.decision-options :deep(.el-radio-button) { flex: 1; }
.decision-options :deep(.el-radio-button__inner) { width: 100%; }
.decision-reason { margin-top: 14px; }
.risk-boundary { padding: 10px 12px; border-radius: var(--usp-radius-lg); background: #eef7ff; color: #245b89; line-height: 1.6; }
.waiver-reason { margin-top: 14px; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 14px; }
.management-image-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 14px; min-height: 180px; }
.management-image-grid figure { min-width: 0; margin: 0; }
.management-image-grid :deep(.el-image) { width: 100%; aspect-ratio: 4 / 3; border-radius: var(--usp-radius-xl); background: #eef2f1; }
.management-image-grid figcaption { overflow: hidden; margin-top: 6px; color: var(--usp-color-text-secondary); text-overflow: ellipsis; white-space: nowrap; }
@media (max-width: 900px) {
  .form-grid { grid-template-columns: 1fr; }
}
</style>
