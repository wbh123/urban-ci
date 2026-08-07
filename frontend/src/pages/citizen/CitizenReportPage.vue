<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  ElMessage,
  type FormInstance,
  type FormRules,
  type UploadProps,
  type UploadUserFile,
} from 'element-plus'
import {
  createPublicFeedback,
  listPublicFeedbackBuildings,
  listPublicFeedbackCommunities,
  uploadPublicFeedbackImage,
  type FeedbackCreatedResult,
  type FeedbackReportType,
  type FeedbackUrgency,
  type PublicFeedbackBuilding,
  type PublicFeedbackCommunity,
} from '@/shared/api'

interface FailedUpload {
  file: File
  message: string
}

interface PreviewHolder {
  url?: string
}

const MAX_IMAGE_COUNT = 6
const MAX_IMAGE_SIZE = 10 * 1024 * 1024
const SUPPORTED_IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp'])

const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)
const uploadingImages = ref(false)
const communities = ref<PublicFeedbackCommunity[]>([])
const buildings = ref<PublicFeedbackBuilding[]>([])
const fileList = ref<UploadUserFile[]>([])
const createdResult = ref<FeedbackCreatedResult | null>(null)
const failedUploads = ref<FailedUpload[]>([])
const uploadedCount = ref(0)

const form = reactive({
  communityId: '',
  buildingId: '',
  reportType: 'WALL_CRACK' as FeedbackReportType,
  description: '',
  urgency: 'NORMAL' as FeedbackUrgency,
  reporterName: '',
  contactPhone: '',
  contactEmail: '',
  locationText: '',
  contactConsent: false,
})

const rules: FormRules = {
  communityId: [{ required: true, message: '请选择小区', trigger: 'change' }],
  reportType: [{ required: true, message: '请选择问题类型', trigger: 'change' }],
  description: [
    { required: true, message: '请描述问题', trigger: 'blur' },
    { min: 10, max: 2000, message: '问题描述需为 10～2000 个字符', trigger: 'blur' },
  ],
  contactConsent: [
    {
      validator: (_rule, value, callback) => {
        if ((form.contactPhone || form.contactEmail) && !value) callback(new Error('填写联系方式时请确认联系授权'))
        else callback()
      },
      trigger: 'change',
    },
  ],
}

onMounted(async () => {
  try {
    communities.value = await listPublicFeedbackCommunities()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '小区列表加载失败')
  }
})

watch(
  () => form.communityId,
  async (communityId) => {
    form.buildingId = ''
    buildings.value = []
    if (!communityId) return
    try {
      buildings.value = await listPublicFeedbackBuildings(communityId)
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '楼栋列表加载失败')
    }
  },
)

function releasePreview(file: PreviewHolder): void {
  if (file.url?.startsWith('blob:')) URL.revokeObjectURL(file.url)
}

const handleImageChange: UploadProps['onChange'] = (uploadFile, uploadFiles) => {
  const raw = uploadFile.raw
  if (!raw) return
  if (!SUPPORTED_IMAGE_TYPES.has(raw.type)) {
    ElMessage.warning('仅支持 JPEG、PNG、WebP 图片')
    fileList.value = uploadFiles.filter((item) => item.uid !== uploadFile.uid)
    return
  }
  if (raw.size > MAX_IMAGE_SIZE) {
    ElMessage.warning('单张图片不能超过 10MB')
    fileList.value = uploadFiles.filter((item) => item.uid !== uploadFile.uid)
    return
  }
  uploadFile.url = URL.createObjectURL(raw)
  fileList.value = uploadFiles.slice(0, MAX_IMAGE_COUNT)
}

const handleImageRemove: UploadProps['onRemove'] = (uploadFile) => {
  releasePreview(uploadFile)
}

const handleImageExceed: UploadProps['onExceed'] = () => {
  ElMessage.warning(`每个反馈最多选择 ${MAX_IMAGE_COUNT} 张图片`)
}

async function copyText(value: string, label: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(value)
    ElMessage.success(`${label}已复制`)
  } catch {
    const input = document.createElement('textarea')
    input.value = value
    input.style.position = 'fixed'
    input.style.opacity = '0'
    document.body.appendChild(input)
    input.select()
    document.execCommand('copy')
    input.remove()
    ElMessage.success(`${label}已复制`)
  }
}

async function uploadFiles(
  result: FeedbackCreatedResult,
  files: File[],
): Promise<FailedUpload[]> {
  const failures: FailedUpload[] = []
  uploadingImages.value = true
  try {
    for (const file of files) {
      try {
        await uploadPublicFeedbackImage(result.reportCode, result.trackingSecret, file)
        uploadedCount.value += 1
      } catch (error) {
        failures.push({
          file,
          message: error instanceof Error ? error.message : '图片上传失败',
        })
      }
    }
  } finally {
    uploadingImages.value = false
  }
  return failures
}

async function submit(): Promise<void> {
  if (createdResult.value) return
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const result = await createPublicFeedback({
      communityId: form.communityId,
      buildingId: form.buildingId || undefined,
      reportType: form.reportType,
      description: form.description,
      urgency: form.urgency,
      reporterName: form.reporterName || undefined,
      contactPhone: form.contactPhone || undefined,
      contactEmail: form.contactEmail || undefined,
      contactConsent: form.contactConsent,
      locationText: form.locationText || undefined,
    })
    createdResult.value = result
    localStorage.setItem(`urban-safe-feedback-secret:${result.reportCode}`, result.trackingSecret)
    localStorage.setItem('urban-safe-last-feedback-code', result.reportCode)

    const files = fileList.value.flatMap((item) => (item.raw ? [item.raw as File] : []))
    failedUploads.value = await uploadFiles(result, files)
    if (failedUploads.value.length === 0) {
      ElMessage.success(files.length ? '反馈和图片均已提交成功，请保存查询凭证' : '反馈提交成功，请保存查询凭证')
      return
    }
    ElMessage.warning(`反馈已提交，但有 ${failedUploads.value.length} 张图片上传失败，可在当前页面重试`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '反馈提交失败')
  } finally {
    loading.value = false
  }
}

async function retryFailedImages(): Promise<void> {
  if (!createdResult.value || failedUploads.value.length === 0) return
  const retryFiles = failedUploads.value.map((item) => item.file)
  failedUploads.value = []
  const failures = await uploadFiles(createdResult.value, retryFiles)
  failedUploads.value = failures
  if (failures.length === 0) {
    ElMessage.success('失败图片已全部重新上传成功')
  } else {
    ElMessage.warning(`仍有 ${failures.length} 张图片上传失败`)
  }
}

async function viewCreatedReport(): Promise<void> {
  if (!createdResult.value) return
  await router.push(`/citizen/reports/${encodeURIComponent(createdResult.value.reportCode)}`)
}

function submitAnother(): void {
  createdResult.value = null
  failedUploads.value = []
  uploadedCount.value = 0
  fileList.value.forEach(releasePreview)
  fileList.value = []
  form.description = ''
  form.locationText = ''
}

onBeforeUnmount(() => {
  fileList.value.forEach(releasePreview)
})
</script>

<template>
  <section class="page-heading">
    <el-button text @click="router.push('/citizen')">返回首页</el-button>
    <h1>提交问题反馈</h1>
    <p>请尽量说明具体楼栋、位置和现象，并可上传现场图片帮助工作人员核查。</p>
  </section>

  <el-card v-if="createdResult" shadow="never" class="credential-card">
    <el-result icon="success" title="反馈提交成功">
      <template #sub-title>
        <p class="credential-warning">请立即保存以下查询信息。查询凭证仅在本次提交成功后展示。</p>
        <div class="credential-grid">
          <div>
            <span>查询编号</span>
            <strong>{{ createdResult.reportCode }}</strong>
            <el-button link type="primary" @click="copyText(createdResult.reportCode, '查询编号')">复制</el-button>
          </div>
          <div>
            <span>查询凭证</span>
            <strong>{{ createdResult.trackingSecret }}</strong>
            <el-button link type="primary" @click="copyText(createdResult.trackingSecret, '查询凭证')">复制</el-button>
          </div>
        </div>
        <p v-if="failedUploads.length" class="upload-warning">
          文字反馈已经保存；{{ failedUploads.length }} 张图片尚未上传成功，可在下方重试。
        </p>
        <p v-else>已成功上传 {{ uploadedCount }} 张现场图片。</p>
      </template>
      <template #extra>
        <el-button type="primary" @click="viewCreatedReport">查看处理进度</el-button>
        <el-button @click="submitAnother">继续提交反馈</el-button>
      </template>
    </el-result>
  </el-card>

  <el-card v-if="createdResult && failedUploads.length" shadow="never" class="partial-result">
    <div class="partial-head">
      <strong>部分图片上传失败</strong>
      <span>已成功上传 {{ uploadedCount }} 张，仍有 {{ failedUploads.length }} 张失败。</span>
    </div>
    <ul>
      <li v-for="item in failedUploads" :key="item.file.name + item.file.lastModified">
        {{ item.file.name }}：{{ item.message }}
      </li>
    </ul>
    <el-button type="primary" :loading="uploadingImages" @click="retryFailedImages">重试失败图片</el-button>
  </el-card>

  <el-card v-if="!createdResult" shadow="never" class="form-card">
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <div class="two-columns">
        <el-form-item label="所在小区" prop="communityId">
          <el-select v-model="form.communityId" filterable placeholder="请选择小区" style="width: 100%">
            <el-option
              v-for="item in communities"
              :key="item.communityId"
              :label="item.communityName"
              :value="item.communityId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="具体楼栋（可选）">
          <el-select v-model="form.buildingId" clearable filterable placeholder="不确定时可不选" style="width: 100%">
            <el-option
              v-for="item in buildings"
              :key="item.buildingId"
              :label="item.buildingName"
              :value="item.buildingId"
            />
          </el-select>
        </el-form-item>
      </div>

      <div class="two-columns">
        <el-form-item label="问题类型" prop="reportType">
          <el-select v-model="form.reportType" style="width: 100%">
            <el-option label="墙体或外立面裂缝" value="WALL_CRACK" />
            <el-option label="表面脱落或坠落风险" value="SURFACE_FALLING" />
            <el-option label="渗水或漏水" value="WATER_LEAKAGE" />
            <el-option label="疑似违规改造" value="ILLEGAL_MODIFICATION" />
            <el-option label="消防通道问题" value="FIRE_ACCESS" />
            <el-option label="其他问题" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="紧急程度">
          <el-select v-model="form.urgency" style="width: 100%">
            <el-option label="一般" value="NORMAL" />
            <el-option label="较低" value="LOW" />
            <el-option label="较高" value="HIGH" />
            <el-option label="紧急" value="URGENT" />
          </el-select>
        </el-form-item>
      </div>

      <el-form-item label="具体位置">
        <el-input v-model="form.locationText" maxlength="512" placeholder="例如：3 栋西侧外墙、2 单元楼梯间" />
      </el-form-item>
      <el-form-item label="问题描述" prop="description">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="6"
          maxlength="2000"
          show-word-limit
          placeholder="请描述发现时间、问题现象、是否扩大、是否影响通行等信息"
        />
      </el-form-item>

      <el-form-item label="现场图片（可选，最多 6 张）">
        <el-upload
          v-model:file-list="fileList"
          list-type="picture-card"
          :auto-upload="false"
          :limit="MAX_IMAGE_COUNT"
          accept="image/jpeg,image/png,image/webp,.jpg,.jpeg,.png,.webp"
          :on-change="handleImageChange"
          :on-remove="handleImageRemove"
          :on-exceed="handleImageExceed"
        >
          <span class="upload-trigger">添加图片</span>
          <template #tip>
            <div class="upload-tip">支持 JPEG、PNG、WebP，单张不超过 10MB。提交前可预览和移除。</div>
          </template>
        </el-upload>
      </el-form-item>

      <el-divider content-position="left">联系信息（可选）</el-divider>
      <div class="two-columns">
        <el-form-item label="姓名或称呼">
          <el-input v-model="form.reporterName" maxlength="128" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.contactPhone" maxlength="32" inputmode="tel" />
        </el-form-item>
      </div>
      <el-form-item label="电子邮箱">
        <el-input v-model="form.contactEmail" maxlength="255" inputmode="email" />
      </el-form-item>
      <el-form-item prop="contactConsent">
        <el-checkbox v-model="form.contactConsent">
          我同意工作人员仅为核实和处理本次反馈使用上述联系方式
        </el-checkbox>
      </el-form-item>

      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="反馈用于问题线索收集，不代表正式房屋安全鉴定结论。"
      />
      <el-button
        class="submit-button"
        type="primary"
        size="large"
        :loading="loading || uploadingImages"
        @click="submit"
      >
        {{ uploadingImages ? '正在上传图片…' : '提交反馈' }}
      </el-button>
    </el-form>
  </el-card>
</template>

<style scoped lang="scss">
.page-heading h1 { margin: 8px 0; color: #173f37; }
.page-heading p { margin: 0 0 18px; color: #667085; }
.form-card, .partial-result, .credential-card { margin-bottom: 18px; border-radius: 18px; }
.two-columns { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.submit-button { width: 100%; min-height: 48px; margin-top: 20px; }
.upload-trigger { color: #16705c; font-size: 14px; }
.upload-tip { color: #667085; line-height: 1.6; }
.credential-warning { color: #b54708; font-weight: 700; }
.credential-grid { display: grid; gap: 12px; margin: 18px auto; max-width: 680px; text-align: left; }
.credential-grid > div { display: grid; grid-template-columns: 90px minmax(0, 1fr) auto; align-items: center; gap: 12px; padding: 14px; border: 1px solid #d0d5dd; border-radius: 12px; background: #f8faf9; }
.credential-grid span { color: #667085; }
.credential-grid strong { overflow-wrap: anywhere; color: #173f37; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }
.upload-warning { color: #b54708; }
.partial-head { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 10px; }
.partial-result ul { margin: 12px 0; color: #b54708; }
@media (max-width: 560px) {
  .two-columns { grid-template-columns: 1fr; gap: 0; }
  .credential-grid > div { grid-template-columns: 1fr; }
}
</style>
