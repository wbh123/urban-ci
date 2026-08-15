<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as api from '@/shared/api'
import { toAppError, type InspectionRecord, type InspectionTask, type Severity } from '@/shared/api'
import { useAppStore } from '@/stores/app'
import AppLoading from '@/shared/components/AppLoading.vue'
import AppError from '@/shared/components/AppError.vue'
import AppStatusTag from '@/shared/components/AppStatusTag.vue'
import InspectionImageGallery from '@/shared/components/inspection/InspectionImageGallery.vue'

type InspectionGalleryHandle = {
  refresh: () => Promise<void>
  trackExecution: (assetId: string, taskId: string) => void
}

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const taskId = computed(() => String(route.params.taskId ?? ''))
const task = ref<InspectionTask | null>(null)
const records = ref<InspectionRecord[]>([])
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const severity = ref<Severity>('LOW')
const summary = ref('')
const suggestion = ref('')
const photo = ref<File | null>(null)
const previewUrl = ref('')
const aiTriggered = ref(false)
const galleryRef = ref<InspectionGalleryHandle | null>(null)

const nextStepText = computed(() => {
  if (aiTriggered.value) return '现场图片已进入后台智能分析；你可以继续巡检或离开页面，稍后回来会自动恢复分析状态。'
  if (task.value?.status === 'COMPLETED') return '任务已完成；历史现场图片仍可查看，未分析或失败图片可继续手动发起人工智能分析。'
  if (records.value.length > 0 || task.value?.status === 'IN_PROGRESS' || photo.value) {
    return '继续补充现场记录和照片；图片上传后会立即进入历史图库，不需要等待人工智能分析完成。'
  }
  return '到场后先开始任务，记录现场情况并上传可清晰识别问题位置的照片。'
})

function releasePreview(): void {
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
}

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const tasks = await api.listInspectionTasks()
    task.value = tasks.find((item) => item.taskId === taskId.value) ?? null
    records.value = await api.listInspectionRecords(taskId.value)
    if (!task.value) errorMessage.value = '任务不存在或当前账号无权访问。'
  } catch (error) {
    errorMessage.value = toAppError(error).message
  } finally {
    loading.value = false
  }
}

async function transition(action: 'start' | 'complete' | 'cancel'): Promise<void> {
  saving.value = true
  try {
    await api.transitionInspectionTask(taskId.value, action)
    appStore.notify(
      action === 'start' ? '任务已开始。' : action === 'complete' ? '任务已完成。' : '任务已取消。',
      action === 'cancel' ? 'warning' : 'success',
    )
    await load()
  } catch (error) {
    appStore.notify(toAppError(error).message, 'error')
  } finally {
    saving.value = false
  }
}

async function saveRecord(): Promise<void> {
  if (!summary.value.trim()) {
    appStore.notify('请填写现场情况。', 'warning')
    return
  }
  saving.value = true
  try {
    await api.createInspectionRecord({
      taskId: taskId.value,
      inspectionPart: '现场检查',
      issueType: 'OTHER',
      severity: severity.value,
      summary: summary.value.trim(),
      rectificationSuggestion: suggestion.value.trim() || undefined,
    })
    summary.value = ''
    suggestion.value = ''
    records.value = await api.listInspectionRecords(taskId.value)
    appStore.notify('现场记录已保存。', 'success')
  } catch (error) {
    appStore.notify(toAppError(error).message, 'error')
  } finally {
    saving.value = false
  }
}

function selectPhoto(event: Event): void {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0] ?? null
  releasePreview()
  photo.value = file
  if (file) previewUrl.value = URL.createObjectURL(file)
}

async function uploadPhoto(): Promise<void> {
  if (!photo.value) {
    appStore.notify('请先拍照或选择图片。', 'warning')
    return
  }
  saving.value = true
  try {
    const result = await api.uploadImage({
      file: photo.value,
      businessType: 'INSPECTION_TASK',
      businessId: taskId.value,
      bindingRole: 'INSPECTION_PHOTO',
    })
    photo.value = null
    releasePreview()

    // 图片资产已经持久化；先刷新服务器图库，人工智能状态随后独立恢复/轮询。
    await galleryRef.value?.refresh()

    const automatic = result.autoInference
    aiTriggered.value = Boolean(automatic?.triggered)
    if (automatic?.triggered && automatic.executionTaskId) {
      galleryRef.value?.trackExecution(result.assetId, automatic.executionTaskId)
      appStore.notify('现场图片已上传，AI 已进入后台分析，可继续巡检。', 'success')
    } else if (automatic?.enabled) {
      appStore.notify(`图片已上传；${automatic.message}`, 'warning')
    } else {
      appStore.notify(`图片已上传：${result.originalFilename}；可在下方历史图库手动启动 AI 分析。`, 'success')
    }
  } catch (error) {
    appStore.notify(toAppError(error).message, 'error')
  } finally {
    saving.value = false
  }
}

onMounted(load)
onBeforeUnmount(releasePreview)
</script>

<template>
  <section class="mobile-page">
    <button class="back" type="button" @click="router.back()">‹ 返回任务列表</button>
    <div class="next-step-hint"><strong>下一步</strong><span>{{ nextStepText }}</span></div>
    <AppLoading :visible="loading" inline text="加载任务中…" />
    <AppError v-if="errorMessage" :message="errorMessage" @retry="load" />
    <template v-if="task && !loading">
      <header class="task-card">
        <div>
          <small>{{ task.taskCode }}</small>
          <h1>{{ task.title }}</h1>
          <p>{{ task.buildingName || task.buildingId }}</p>
        </div>
        <AppStatusTag :status="task.status" variant="task" />
      </header>
      <div class="task-actions">
        <button type="button" :disabled="saving || task.status !== 'PENDING'" @click="transition('start')">到场开始</button>
        <button type="button" :disabled="saving || task.status !== 'IN_PROGRESS'" @click="transition('complete')">提交完成</button>
      </div>
      <article class="form-card">
        <h2>填写现场记录</h2>
        <label>严重程度
          <select v-model="severity"><option value="LOW">低</option><option value="MEDIUM">中</option><option value="HIGH">高</option></select>
        </label>
        <label>现场情况
          <textarea v-model="summary" rows="4" placeholder="描述位置、现象和范围" />
        </label>
        <label>整改建议
          <textarea v-model="suggestion" rows="3" placeholder="高风险问题建议填写" />
        </label>
        <button class="primary" type="button" :disabled="saving" @click="saveRecord">保存记录</button>
      </article>
      <article class="form-card evidence-card">
        <div class="form-heading">
          <div>
            <h2>现场取证</h2>
            <small>先拍照或选择图片；上传成功后图片立即进入服务器历史图库，人工智能分析在后台独立运行。</small>
          </div>
          <span class="status-chip">图片证据</span>
        </div>
        <label class="photo-picker">
          <input type="file" accept="image/jpeg,image/png,image/webp" capture="environment" @change="selectPhoto" />
          <span>{{ photo ? '重新选择照片' : '拍照或选择照片' }}</span>
        </label>
        <img v-if="previewUrl" :src="previewUrl" class="preview" alt="待上传现场照片预览" />
        <button class="primary" type="button" :disabled="saving || !photo" @click="uploadPhoto">上传现场照片</button>
      </article>

      <article class="form-card history-card">
        <div class="form-heading">
          <div>
            <h2>历史现场图片</h2>
            <small>支持逐图 AI 分析或分析全部未分析图片；页面关闭后任务仍在后台运行，重新进入会恢复状态。</small>
          </div>
          <span class="status-chip">服务器图库</span>
        </div>
        <InspectionImageGallery
          ref="galleryRef"
          :task-id="taskId"
          editable
          :show-result-action="false"
        />
      </article>

      <article class="form-card">
        <h2>已保存记录</h2>
        <div v-for="record in records" :key="record.recordId" class="record-row">
          <AppStatusTag :status="record.severity" variant="severity" />
          <p>{{ record.summary }}</p>
        </div>
        <p v-if="!records.length" class="muted">暂无记录。</p>
      </article>
    </template>
  </section>
</template>

<style scoped lang="scss">
.mobile-page { display: grid; gap: 14px; }
.back { width: fit-content; min-height: 42px; border: 0; border-radius: 10px; background: transparent; color: #176354; font: inherit; }
.next-step-hint { display: flex; gap: 8px; padding: 11px 13px; border: 1px solid #d8ebe5; border-radius: 16px; background: #f5fbf9; color: #667085; font-size: 13px; line-height: 1.55; }
.next-step-hint strong { flex: 0 0 auto; color: #176354; }
.task-card, .form-card { padding: 18px; border: 1px solid #dfe7e4; border-radius: 20px; background: #fff; box-shadow: 0 8px 24px rgb(16 52 44 / 5%); }
.task-card { display: flex; align-items: flex-start; justify-content: space-between; gap: 14px; }
.task-card small, .task-card p { color: #667085; }
.task-card h1 { margin: 5px 0; font-size: 22px; }
.task-card p { margin: 0; }
.task-actions { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.task-actions button, .primary { min-height: 48px; border: 0; border-radius: 13px; background: #176354; color: #fff; font: inherit; font-weight: 700; }
button:disabled { opacity: .45; }
.form-card { display: grid; gap: 13px; }
.form-card h2 { margin: 0; font-size: 18px; }
.form-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.form-heading > div { display: grid; gap: 4px; }
.form-heading small { color: #667085; line-height: 1.45; }
.status-chip { flex: 0 0 auto; padding: 5px 9px; border-radius: 999px; background: #eef6f3; color: #176354; font-size: 11px; font-weight: 800; }
label { display: grid; gap: 7px; color: #475467; font-size: 14px; }
select, textarea { width: 100%; padding: 11px 12px; border: 1px solid #cfd9d5; border-radius: 12px; background: #fff; font: inherit; }
.photo-picker input { position: absolute; width: 1px; height: 1px; opacity: 0; }
.photo-picker span { min-height: 52px; display: grid; place-items: center; border: 1px dashed #71a99b; border-radius: 16px; background: #f0faf7; color: #176354; font-weight: 700; }
.preview { width: 100%; max-height: 340px; border-radius: 16px; object-fit: contain; background: #1b1b1b; }
.history-card { overflow: hidden; }
.history-card :deep(.gallery-head) { align-items: stretch; flex-direction: column; }
.history-card :deep(.image-grid) { grid-template-columns: 1fr; }
.record-row { display: grid; grid-template-columns: auto 1fr; align-items: start; gap: 10px; padding-top: 12px; border-top: 1px solid #edf0ef; }
.record-row p { margin: 0; line-height: 1.55; }
.muted { margin: 0; color: #98a2b3; }
</style>
