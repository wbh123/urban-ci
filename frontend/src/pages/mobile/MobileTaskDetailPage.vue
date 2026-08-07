<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as api from '@/shared/api'
import { toAppError, type InspectionRecord, type InspectionTask, type Severity } from '@/shared/api'
import AppLoading from '@/shared/components/AppLoading.vue'
import AppError from '@/shared/components/AppError.vue'
import AppStatusTag from '@/shared/components/AppStatusTag.vue'

const route = useRoute()
const router = useRouter()
const taskId = computed(() => String(route.params.taskId ?? ''))
const task = ref<InspectionTask | null>(null)
const records = ref<InspectionRecord[]>([])
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const notice = ref('')
const severity = ref<Severity>('LOW')
const summary = ref('')
const suggestion = ref('')
const photo = ref<File | null>(null)
const previewUrl = ref('')

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
    notice.value = action === 'start' ? '任务已开始。' : action === 'complete' ? '任务已完成。' : '任务已取消。'
    await load()
  } catch (error) {
    notice.value = toAppError(error).message
  } finally {
    saving.value = false
  }
}

async function saveRecord(): Promise<void> {
  if (!summary.value.trim()) {
    notice.value = '请填写现场情况。'
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
    notice.value = '现场记录已保存。'
  } catch (error) {
    notice.value = toAppError(error).message
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
    notice.value = '请先拍照或选择图片。'
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
    notice.value = `图片已上传：${result.originalFilename}`
    photo.value = null
    releasePreview()
  } catch (error) {
    notice.value = toAppError(error).message
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
      <p v-if="notice" class="notice" role="status">{{ notice }}</p>
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
      <article class="form-card">
        <h2>现场拍照</h2>
        <label class="photo-picker">
          <input type="file" accept="image/jpeg,image/png,image/webp" capture="environment" @change="selectPhoto" />
          <span>{{ photo ? '重新选择照片' : '拍照或选择照片' }}</span>
        </label>
        <img v-if="previewUrl" :src="previewUrl" class="preview" alt="待上传现场照片预览" />
        <button class="primary" type="button" :disabled="saving || !photo" @click="uploadPhoto">上传照片</button>
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
.back { width: fit-content; min-height: 42px; border: 0; background: transparent; color: #176354; font: inherit; }
.task-card, .form-card { padding: 18px; border: 1px solid #dfe7e4; border-radius: 18px; background: #fff; }
.task-card { display: flex; align-items: flex-start; justify-content: space-between; gap: 14px; }
.task-card small, .task-card p { color: #667085; }
.task-card h1 { margin: 5px 0; font-size: 22px; }
.task-card p { margin: 0; }
.notice { margin: 0; padding: 12px 14px; border-radius: 12px; background: #e8f5f1; color: #176354; }
.task-actions { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.task-actions button, .primary { min-height: 48px; border: 0; border-radius: 13px; background: #176354; color: #fff; font: inherit; font-weight: 700; }
button:disabled { opacity: .45; }
.form-card { display: grid; gap: 13px; }
.form-card h2 { margin: 0; font-size: 18px; }
label { display: grid; gap: 7px; color: #475467; font-size: 14px; }
select, textarea { width: 100%; padding: 11px 12px; border: 1px solid #cfd9d5; border-radius: 12px; background: #fff; font: inherit; }
.photo-picker input { position: absolute; width: 1px; height: 1px; opacity: 0; }
.photo-picker span { min-height: 52px; display: grid; place-items: center; border: 1px dashed #71a99b; border-radius: 14px; background: #f0faf7; color: #176354; font-weight: 700; }
.preview { width: 100%; max-height: 340px; border-radius: 14px; object-fit: contain; background: #1b1b1b; }
.record-row { display: grid; grid-template-columns: auto 1fr; align-items: start; gap: 10px; padding-top: 12px; border-top: 1px solid #edf0ef; }
.record-row p { margin: 0; line-height: 1.55; }
.muted { margin: 0; color: #98a2b3; }
</style>
