<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createManualFeedback,
  fetchImageBlobUrl,
  getFeedbackAiAssist,
  listFeedbackImages,
  listFeedbackReports,
  listPublicFeedbackBuildings,
  listPublicFeedbackCommunities,
  updateFeedbackStatus,
  type FeedbackAiAssistView,
  type FeedbackChannel,
  type FeedbackImage,
  type FeedbackManagementRow,
  type FeedbackReportType,
  type FeedbackStatus,
  type FeedbackUrgency,
  type PublicFeedbackBuilding,
  type PublicFeedbackCommunity,
} from '@/shared/api'
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

const statusOptions: Array<{ value: FeedbackStatus; label: string }> = [
  { value: 'SUBMITTED', label: '已提交' },
  { value: 'ACCEPTED', label: '已受理' },
  { value: 'PROCESSING', label: '处理中' },
  { value: 'NEED_MORE_INFO', label: '待补充' },
  { value: 'RESOLVED', label: '已处理' },
  { value: 'CLOSED', label: '已关闭' },
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

function openStatus(row: FeedbackManagementRow): void {
  current.value = row
  updateForm.status = row.status === 'SUBMITTED' ? 'ACCEPTED' : 'PROCESSING'
  updateForm.handlingSummary = row.handlingSummary || ''
  updateForm.message = ''
  updateForm.publicVisible = true
  statusDrawerVisible.value = true
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
  try {
    await updateFeedbackStatus(current.value.reportId, updateForm)
    ElMessage.success('反馈状态已更新')
    statusDrawerVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '状态更新失败')
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
      eyebrow="公众参与"
      title="公众反馈管理"
      description="统一查询和处理网页、电话、短信、窗口与内部登记的风险线索。"
      show-user-menu
    >
      <template #actions>
        <el-button @click="load">刷新</el-button>
        <el-button type="primary" @click="manualDialogVisible = true">代录反馈</el-button>
      </template>
    </AppPageHeader>

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
        <el-table-column prop="description" label="问题描述" min-width="280" show-overflow-tooltip />
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
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="scope">
            <div class="row-actions">
              <AppActionButton @click="openAiAssist(scope.row)">AI 初步归类</AppActionButton>
              <AppActionButton type="primary" @click="openStatus(scope.row)">处理</AppActionButton>
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
        <el-button v-if="current" type="primary" @click="aiAssistDrawerVisible = false; openStatus(current)">进入人工处理</el-button>
      </template>
    </el-drawer>

    <el-drawer v-model="statusDrawerVisible" title="处理公众反馈" size="min(520px, 92vw)">
      <div class="status-drawer-content">
        <div class="current-status-card">
          <span>当前状态</span>
          <el-tag effect="plain">{{ current ? statusLabels[current.status] || current.status : '—' }}</el-tag>
          <small v-if="current">{{ current.communityName }} · {{ current.buildingName || current.locationText || '未指定楼栋' }}</small>
        </div>
        <el-form label-position="top">
          <el-form-item label="目标状态">
            <el-select v-model="updateForm.status" style="width: 100%">
              <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="处理摘要">
            <el-input v-model="updateForm.handlingSummary" type="textarea" :rows="4" maxlength="2000" />
          </el-form-item>
          <el-form-item label="向反馈人说明">
            <el-input v-model="updateForm.message" type="textarea" :rows="4" maxlength="2000" />
          </el-form-item>
          <el-switch v-model="updateForm.publicVisible" active-text="向反馈人公开本次状态说明" />
        </el-form>
      </div>
      <template #footer>
        <el-button @click="statusDrawerVisible = false">取消</el-button>
        <el-button type="primary" @click="submitStatus">确认更新</el-button>
      </template>
    </el-drawer>

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
              <el-option label="较低" value="LOW" /><el-option label="一般" value="NORMAL" />
              <el-option label="较高" value="HIGH" /><el-option label="紧急" value="URGENT" />
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
              <el-option label="墙体裂缝" value="WALL_CRACK" /><el-option label="表面脱落" value="SURFACE_FALLING" />
              <el-option label="渗漏" value="WATER_LEAKAGE" /><el-option label="违规改造" value="ILLEGAL_MODIFICATION" />
              <el-option label="消防通道" value="FIRE_ACCESS" /><el-option label="其他" value="OTHER" />
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
.location-cell { display: grid; gap: 2px; }
.location-cell small { color: var(--usp-color-text-secondary); }
.row-actions { display: flex; align-items: center; gap: 6px; white-space: nowrap; }
.status-drawer-content,
.ai-assist-drawer { display: grid; gap: 18px; }
.ai-assist-drawer :deep(.ai-insight-card p) { white-space: pre-wrap; }
.current-status-card { display: grid; gap: 7px; padding: 14px; border: 1px solid var(--usp-color-border); background: var(--usp-color-surface-muted); }
.current-status-card small { color: var(--usp-color-text-secondary); }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 14px; }
.management-image-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 14px; min-height: 180px; }
.management-image-grid figure { min-width: 0; margin: 0; }
.management-image-grid :deep(.el-image) { width: 100%; aspect-ratio: 4 / 3; border-radius: var(--usp-radius-xl); background: #eef2f1; }
.management-image-grid figcaption { overflow: hidden; margin-top: 6px; color: var(--usp-color-text-secondary); text-overflow: ellipsis; white-space: nowrap; }
.console-feedback :deep(.el-input__wrapper),
.console-feedback :deep(.el-select__wrapper),
.console-feedback :deep(.el-date-editor.el-input__wrapper),
.console-feedback :deep(.el-textarea__inner),
.console-feedback :deep(.el-button) { border-radius: var(--usp-radius-lg); }
.console-feedback :deep(.el-table) { border-radius: var(--usp-radius-lg); }

@media (max-width: 720px) {
  .form-grid { grid-template-columns: 1fr; }
}
</style>
