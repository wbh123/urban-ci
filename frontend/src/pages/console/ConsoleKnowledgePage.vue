<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import KnowledgeQaPanel from '@/features/knowledge/KnowledgeQaPanel.vue'
import {
  createKnowledgeDocument,
  toAppError,
  type KnowledgeDocumentCreateRequest,
  type KnowledgeDocumentView,
} from '@/shared/api'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const canRegister = computed(() => authStore.hasRole('ADMIN'))
const dialogVisible = ref(false)
const saving = ref(false)
const createdDocument = ref<KnowledgeDocumentView | null>(null)

type RoleScope = KnowledgeDocumentCreateRequest['roleScope'][number]
type SecurityLevel = KnowledgeDocumentCreateRequest['securityLevel']
type DocumentStatus = KnowledgeDocumentCreateRequest['status']

interface RegistrationForm {
  documentCode: string
  title: string
  documentType: string
  documentVersion: string
  securityLevel: SecurityLevel
  roleScope: RoleScope[]
  communityId: string
  buildingId: string
  status: DocumentStatus
  sourceUri: string
  sectionTitle: string
  pageNumber?: number
  content: string
}

const form = reactive<RegistrationForm>({
  documentCode: '',
  title: '',
  documentType: 'GUIDELINE',
  documentVersion: '1.0.0',
  securityLevel: 'INTERNAL',
  roleScope: ['ADMIN', 'PROPERTY_INSPECTOR', 'EXPERT'],
  communityId: '',
  buildingId: '',
  status: 'ACTIVE',
  sourceUri: '',
  sectionTitle: '',
  pageNumber: undefined,
  content: '',
})

function resetForm(): void {
  Object.assign(form, {
    documentCode: '',
    title: '',
    documentType: 'GUIDELINE',
    documentVersion: '1.0.0',
    securityLevel: 'INTERNAL',
    roleScope: ['ADMIN', 'PROPERTY_INSPECTOR', 'EXPERT'] satisfies RoleScope[],
    communityId: '',
    buildingId: '',
    status: 'ACTIVE',
    sourceUri: '',
    sectionTitle: '',
    pageNumber: undefined,
    content: '',
  })
}

async function sha256(content: string): Promise<string> {
  const bytes = new TextEncoder().encode(content)
  const digest = await crypto.subtle.digest('SHA-256', bytes)
  return [...new Uint8Array(digest)].map((value) => value.toString(16).padStart(2, '0')).join('')
}

async function submitDocument(): Promise<void> {
  if (!form.documentCode.trim() || !form.title.trim() || !form.documentVersion.trim() || !form.content.trim()) {
    ElMessage.warning('请填写文档编号、标题、版本和知识内容')
    return
  }
  if (!form.roleScope.length) {
    ElMessage.warning('至少选择一个可访问角色')
    return
  }

  saving.value = true
  try {
    const content = form.content.trim()
    const request: KnowledgeDocumentCreateRequest = {
      documentCode: form.documentCode.trim().toUpperCase(),
      title: form.title.trim(),
      documentType: form.documentType.trim(),
      documentVersion: form.documentVersion.trim(),
      securityLevel: form.securityLevel,
      roleScope: [...form.roleScope],
      status: form.status,
      contentChecksum: await sha256(content),
      chunks: [
        {
          chunkIndex: 0,
          content,
          sectionTitle: form.sectionTitle.trim() || null,
          pageNumber: form.pageNumber ?? null,
          metadata: { createdFrom: 'KNOWLEDGE_CONSOLE' },
        },
      ],
      metadata: { createdFrom: 'KNOWLEDGE_CONSOLE' },
    }
    if (form.communityId.trim()) request.communityId = form.communityId.trim()
    if (form.buildingId.trim()) request.buildingId = form.buildingId.trim()
    if (form.sourceUri.trim()) request.sourceUri = form.sourceUri.trim()

    createdDocument.value = await createKnowledgeDocument(request)
    ElMessage.success('知识文档已登记')
    dialogVisible.value = false
    resetForm()
  } catch (error) {
    ElMessage.error(toAppError(error).message)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <section class="console-knowledge-page">
    <header class="page-header">
      <div>
        <p class="eyebrow">AI Knowledge Service</p>
        <h1>AI 知识助手</h1>
        <p>在角色与治理对象范围内检索已审核知识，优先给出可追溯引用；证据不足时拒答，不把资料内容当作系统指令。</p>
      </div>
      <el-button v-if="canRegister" type="primary" @click="dialogVisible = true">登记知识文档</el-button>
    </header>

    <el-alert
      v-if="createdDocument"
      :title="`已登记 ${createdDocument.title}（${createdDocument.documentVersion}），共 ${createdDocument.chunkCount} 个片段。`"
      type="success"
      :closable="true"
      show-icon
      @close="createdDocument = null"
    />

    <KnowledgeQaPanel />

    <el-dialog v-model="dialogVisible" title="登记经过审核的知识片段" width="min(760px, 94vw)" destroy-on-close>
      <el-alert
        title="只登记经过审核且已明确权限范围的内容；原文中的指令性文字只作为资料，不会被当作系统命令执行。"
        type="warning"
        :closable="false"
        show-icon
      />
      <el-form class="registration-form" label-position="top" @submit.prevent="submitDocument">
        <div class="form-grid">
          <el-form-item label="文档编号">
            <el-input v-model="form.documentCode" placeholder="如 INSPECTION_GUIDE" />
          </el-form-item>
          <el-form-item label="文档版本">
            <el-input v-model="form.documentVersion" />
          </el-form-item>
          <el-form-item label="标题">
            <el-input v-model="form.title" />
          </el-form-item>
          <el-form-item label="文档类型">
            <el-input v-model="form.documentType" />
          </el-form-item>
          <el-form-item label="密级">
            <el-select v-model="form.securityLevel">
              <el-option label="公开" value="PUBLIC" />
              <el-option label="内部" value="INTERNAL" />
              <el-option label="受限" value="RESTRICTED" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="form.status">
              <el-option label="草稿" value="DRAFT" />
              <el-option label="启用" value="ACTIVE" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="可访问角色">
          <el-checkbox-group v-model="form.roleScope">
            <el-checkbox value="ADMIN">管理员</el-checkbox>
            <el-checkbox value="PROPERTY_INSPECTOR">巡检人员</el-checkbox>
            <el-checkbox value="EXPERT">专家</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="社区编号（可选）"><el-input v-model="form.communityId" /></el-form-item>
          <el-form-item label="楼栋编号（可选）"><el-input v-model="form.buildingId" /></el-form-item>
          <el-form-item label="章节（可选）"><el-input v-model="form.sectionTitle" /></el-form-item>
          <el-form-item label="页码（可选）"><el-input-number v-model="form.pageNumber" :min="1" controls-position="right" /></el-form-item>
        </div>
        <el-form-item label="原始来源（可选）">
          <el-input v-model="form.sourceUri" placeholder="受控文档编号或内部地址，不要填写密钥" />
        </el-form-item>
        <el-form-item label="知识内容">
          <el-input v-model="form.content" type="textarea" :rows="10" maxlength="12000" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitDocument">计算摘要并登记</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped lang="scss">
.console-knowledge-page { display: grid; gap: 20px; }
.page-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 24px; }
.page-header h1 { margin: 4px 0 8px; }
.page-header p { max-width: 900px; margin: 0; color: #667085; line-height: 1.6; }
.eyebrow { color: #176354 !important; font-size: 12px; font-weight: 800; letter-spacing: .08em; text-transform: uppercase; }
.registration-form { margin-top: 18px; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 16px; }
.form-grid :deep(.el-select), .form-grid :deep(.el-input-number) { width: 100%; }
@media (max-width: 720px) {
  .page-header { flex-direction: column; }
  .form-grid { grid-template-columns: 1fr; }
}
</style>
