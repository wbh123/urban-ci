<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  fetchPublicFeedbackImageBlobUrl,
  trackPublicFeedback,
  type FeedbackImage,
  type PublicFeedbackDetail,
} from '@/shared/api'
import AppLoading from '@/shared/components/AppLoading.vue'

interface DisplayImage extends FeedbackImage {
  url: string
}

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const detail = ref<PublicFeedbackDetail | null>(null)
const displayImages = ref<DisplayImage[]>([])
const reportCode = computed(() => String(route.params.reportCode || ''))
const previewUrls = computed(() => displayImages.value.map((item) => item.url))

const statusLabels: Record<string, string> = {
  SUBMITTED: '已提交',
  ACCEPTED: '已受理',
  PROCESSING: '处理中',
  NEED_MORE_INFO: '待补充信息',
  RESOLVED: '已处理',
  CLOSED: '已关闭',
  REJECTED: '未受理',
  CANCELLED: '已取消',
}

const statusType = computed(() => {
  const status = detail.value?.status
  if (status === 'RESOLVED' || status === 'CLOSED') return 'success'
  if (status === 'REJECTED' || status === 'CANCELLED') return 'danger'
  if (status === 'NEED_MORE_INFO') return 'warning'
  return 'primary'
})

function releaseImages(): void {
  displayImages.value.forEach((item) => URL.revokeObjectURL(item.url))
  displayImages.value = []
}

async function loadImages(images: FeedbackImage[], trackingSecret: string): Promise<void> {
  releaseImages()
  const loaded: DisplayImage[] = []
  for (const image of images) {
    try {
      const url = await fetchPublicFeedbackImageBlobUrl(
        reportCode.value,
        trackingSecret,
        image.assetId,
      )
      loaded.push({ ...image, url })
    } catch {
      // 单张图片失败不影响反馈详情和其他图片展示。
    }
  }
  displayImages.value = loaded
  if (loaded.length < images.length) {
    ElMessage.warning('部分反馈图片暂时无法加载')
  }
}

onMounted(async () => {
  const secret = localStorage.getItem(`urban-safe-feedback-secret:${reportCode.value}`)
  if (!secret) {
    ElMessage.info('请输入工单编号和查询凭证')
    await router.replace('/citizen/track')
    loading.value = false
    return
  }
  try {
    detail.value = await trackPublicFeedback(reportCode.value, secret)
    await loadImages(detail.value.images ?? [], secret)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '反馈详情加载失败')
    await router.replace('/citizen/track')
  } finally {
    loading.value = false
  }
})

onBeforeUnmount(releaseImages)
</script>

<template>
  <AppLoading :visible="loading" text="正在加载反馈进度…" />
  <section v-if="detail" class="detail-page">
    <div class="heading-row">
      <div>
        <el-button text @click="router.push('/citizen')">返回首页</el-button>
        <h1>{{ detail.reportCode }}</h1>
        <p>{{ detail.communityName }}<template v-if="detail.buildingName"> · {{ detail.buildingName }}</template></p>
      </div>
      <el-tag size="large" :type="statusType">{{ statusLabels[detail.status] || detail.status }}</el-tag>
    </div>

    <el-card shadow="never" class="summary-card">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="反馈渠道">{{ detail.feedbackChannel }}</el-descriptions-item>
        <el-descriptions-item label="紧急程度">{{ detail.urgency }}</el-descriptions-item>
        <el-descriptions-item label="具体位置">{{ detail.locationText || '未填写' }}</el-descriptions-item>
        <el-descriptions-item label="问题描述">{{ detail.description }}</el-descriptions-item>
        <el-descriptions-item label="联系信息">
          {{ detail.contactPhone || detail.contactEmail || '未提供' }}
        </el-descriptions-item>
        <el-descriptions-item label="现场图片">{{ detail.imageCount || 0 }} 张</el-descriptions-item>
        <el-descriptions-item label="处理摘要">{{ detail.handlingSummary || '等待工作人员更新' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card v-if="displayImages.length" shadow="never" class="images-card">
      <template #header><strong>现场图片</strong></template>
      <div class="image-grid">
        <figure v-for="(image, index) in displayImages" :key="image.assetId">
          <el-image
            :src="image.url"
            :preview-src-list="previewUrls"
            :initial-index="index"
            fit="cover"
            preview-teleported
          />
          <figcaption>{{ image.originalFilename }}</figcaption>
        </figure>
      </div>
    </el-card>

    <el-card shadow="never" class="timeline-card">
      <template #header><strong>处理时间线</strong></template>
      <el-timeline>
        <el-timeline-item
          v-for="(event, index) in detail.events"
          :key="`${event.createdAt}-${index}`"
          :timestamp="new Date(event.createdAt).toLocaleString()"
          placement="top"
        >
          <strong>{{ event.toStatus ? statusLabels[event.toStatus] || event.toStatus : event.eventType }}</strong>
          <p>{{ event.message || '状态已更新' }}</p>
        </el-timeline-item>
      </el-timeline>
    </el-card>

    <el-alert type="warning" :closable="false" show-icon :title="detail.disclaimer" />
  </section>
</template>

<style scoped lang="scss">
.detail-page { display: grid; gap: 18px; }
.heading-row { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
h1 { margin: 8px 0 4px; color: #173f37; font-size: clamp(24px, 7vw, 36px); }
.heading-row p { margin: 0; color: #667085; }
.summary-card, .timeline-card, .images-card { border-radius: 18px; }
.image-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 12px; }
.image-grid figure { margin: 0; min-width: 0; }
.image-grid :deep(.el-image) { width: 100%; aspect-ratio: 4 / 3; border-radius: 12px; background: #eef2f1; }
.image-grid figcaption { margin-top: 6px; overflow: hidden; color: #667085; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.timeline-card p { margin: 8px 0 0; color: #667085; line-height: 1.6; }
@media (max-width: 520px) { .heading-row { flex-direction: column; } }
</style>
