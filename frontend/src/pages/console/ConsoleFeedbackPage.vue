<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createManualFeedback,
  fetchImageBlobUrl,
  listFeedbackImages,
  listFeedbackReports,
  listPublicFeedbackBuildings,
  listPublicFeedbackCommunities,
  updateFeedbackStatus,
  type FeedbackChannel,
  type FeedbackImage,
  type FeedbackManagementRow,
  type FeedbackReportType,
  type FeedbackStatus,
  type FeedbackUrgency,
  type PublicFeedbackBuilding,
  type PublicFeedbackCommunity,
} from '@/shared/api'

interface ManagementDisplayImage extends FeedbackImage {
  url: string
}

const loading = ref(false)
const rows = ref<FeedbackManagementRow[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const manualDialogVisible = ref(false)
const manualSubmitting = ref(false)
const imageDialogVisible = ref(false)
const imageLoading = ref(false)
const managementImages = ref<ManagementDisplayImage[]>([])
const current = ref<FeedbackManagementRow | null>(null)
const communities = ref<PublicFeedbackCommunity[]>([])
const manualBuildings = ref<PublicFeedbackBuilding[]>([])

const filters = reactive({
  status: '' as FeedbackStatus | '',
  feedbackChannel: '' as FeedbackChannel | '',
  page: 0,
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
      page: filters.page,
      size: filters.size,
    })
    rows.value = result.content
    total.value = result.page.totalElements
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '反馈列表加载失败')
  } finally {
    loading.value = false
  }
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
  dialogVisible.value = true
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
    dialogVisible.value = false
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

function changePage(page: number): void {
  filters.page = page - 1
  void load()
}

onMounted(async () => {
  await Promise.all([load(), loadCommunities()])
})

onBeforeUnmount(releaseManagementImages)
</script>

<template>
  <section class="console-feedback">
    <div class="page-title">
      <div>
        <h1>公众反馈管理</h1>
        <p>统一处理网页、电话、短信、窗口和内部登记的风险线索。</p>
      </div>
      <div class="actions">
        <el-button @click="load">刷新</el-button>
        <el-button type="primary" @click="manualDialogVisible = true">代录反馈</el-button>
      </div>
    </div>

    <el-card shadow="never" class="filter-card">
      <el-form inline>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部状态" style="width: 170px">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="渠道">
          <el-select v-model="filters.feedbackChannel" clearable placeholder="全部渠道" style="width: 150px">
            <el-option v-for="item in channelOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-button type="primary" @click="filters.page = 0; load()">查询</el-button>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="rows" row-key="reportId">
        <el-table-column prop="reportCode" label="工单编号" min-width="190" />
        <el-table-column label="小区/楼栋" min-width="180">
          <template #default="scope">
            <div>{{ scope.row.communityName }}</div>
            <small>{{ scope.row.buildingName || scope.row.locationText || '未指定楼栋' }}</small>
          </template>
        </el-table-column>
        <el-table-column label="渠道" width="90">
          <template #default="scope">{{ channelLabels[scope.row.feedbackChannel] || scope.row.feedbackChannel }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="scope"><el-tag effect="plain">{{ statusLabels[scope.row.status] || scope.row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="description" label="问题描述" min-width="250" show-overflow-tooltip />
        <el-table-column label="图片" width="90" align="center">
          <template #default="scope">
            <el-button
              link
              type="primary"
              :disabled="!scope.row.imageCount"
              @click="openImages(scope.row)"
            >
              {{ scope.row.imageCount || 0 }} 张
            </el-button>
          </template>
        </el-table-column>
        <el-table-column prop="contactPhone" label="联系电话" width="140" />
        <el-table-column prop="submittedAt" label="提交时间" min-width="180" />
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="openStatus(scope.row)">更新状态</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="pagination"
        background
        layout="total, prev, pager, next"
        :total="total"
        :page-size="filters.size"
        :current-page="filters.page + 1"
        @current-change="changePage"
      />
    </el-card>

    <el-dialog
      v-model="imageDialogVisible"
      :title="`反馈图片 · ${current?.reportCode || ''}`"
      width="min(94vw, 860px)"
    >
      <div v-loading="imageLoading" class="management-image-grid">
        <el-empty v-if="!imageLoading && !managementImages.length" description="暂无可查看图片" />
        <figure v-for="(image, index) in managementImages" :key="image.assetId">
          <el-image
            :src="image.url"
            :preview-src-list="managementImages.map((item) => item.url)"
            :initial-index="index"
            fit="cover"
            preview-teleported
          />
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

    <el-dialog v-model="dialogVisible" title="更新反馈状态" width="min(92vw, 560px)">
      <el-form label-position="top">
        <el-form-item label="目标状态">
          <el-select v-model="updateForm.status" style="width: 100%">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="处理摘要"><el-input v-model="updateForm.handlingSummary" type="textarea" :rows="3" maxlength="2000" /></el-form-item>
        <el-form-item label="时间线说明"><el-input v-model="updateForm.message" type="textarea" :rows="3" maxlength="2000" /></el-form-item>
        <el-checkbox v-model="updateForm.publicVisible">向反馈人公开本次状态说明</el-checkbox>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitStatus">确认更新</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped lang="scss">
.console-feedback { display: grid; gap: 16px; }
.page-title { display: flex; align-items: center; justify-content: space-between; gap: 18px; }
.actions { display: flex; gap: 10px; }
h1 { margin: 0 0 6px; color: #173f37; }
p { margin: 0; color: #667085; }
small { color: #667085; }
.filter-card :deep(.el-card__body) { padding-bottom: 2px; }
.pagination { justify-content: flex-end; margin-top: 18px; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 14px; }
.management-image-grid { min-height: 180px; display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 14px; }
.management-image-grid figure { margin: 0; min-width: 0; }
.management-image-grid :deep(.el-image) { width: 100%; aspect-ratio: 4 / 3; border-radius: 12px; background: #eef2f1; }
.management-image-grid figcaption { margin-top: 6px; overflow: hidden; color: #667085; text-overflow: ellipsis; white-space: nowrap; }
@media (max-width: 720px) { .page-title { align-items: flex-start; flex-direction: column; } .form-grid { grid-template-columns: 1fr; } }
</style>
