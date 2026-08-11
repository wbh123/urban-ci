<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getAiAutomationSettings,
  getAiGovernanceStatus,
  updateAiAutomationSettings,
  type AiAutomationSettings,
  type AiGovernanceStatus,
  type AiProviderStatus,
} from '@/shared/api'

const loading = ref(false)
const savingAutomation = ref(false)
const status = ref<AiGovernanceStatus | null>(null)
const automationSettings = ref<AiAutomationSettings | null>(null)

const PROVIDER_LABELS: Record<string, string> = {
  FAST_API: '本地视觉模型',
  SPRING_AI: 'DeepSeek 文本模型',
  DIFY: '智能工作流',
}
const CAPABILITY_LABELS: Record<string, string> = {
  VISION_INFERENCE: '视觉识别',
  TEXT_GENERATION: '文本研判',
  WORKFLOW: '工作流',
}

const total = computed(() => status.value?.total7d)
const configuredCount = computed(
  () => status.value?.providers.filter((item) => item.configurationStatus === 'CONFIGURED').length ?? 0,
)
const visionReady = computed(() =>
  status.value?.providers.some((item) =>
    item.providerCode === 'FAST_API'
    && item.configurationStatus === 'CONFIGURED'
    && item.capabilities.includes('VISION_INFERENCE'),
  ) ?? false,
)

function tagType(item: AiProviderStatus): 'success' | 'warning' | 'info' {
  if (item.configurationStatus === 'CONFIGURED') return 'success'
  if (item.configurationStatus === 'NOT_CONFIGURED') return 'warning'
  return 'info'
}

function configurationLabel(value: AiProviderStatus['configurationStatus']): string {
  if (value === 'CONFIGURED') return '配置完整'
  if (value === 'NOT_CONFIGURED') return '配置不完整'
  return '已禁用'
}

function providerLabel(code: string): string {
  return PROVIDER_LABELS[code] ?? code
}

function capabilityLabel(code: string): string {
  return CAPABILITY_LABELS[code] ?? code
}

function percent(value: number | undefined): string {
  return `${Number(value ?? 0).toFixed(2)}%`
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const [governance, automation] = await Promise.all([
      getAiGovernanceStatus(),
      getAiAutomationSettings(),
    ])
    status.value = governance
    automationSettings.value = automation
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '人工智能运行状态加载失败')
  } finally {
    loading.value = false
  }
}

async function saveAutomationSetting(): Promise<void> {
  if (!automationSettings.value) return
  const desired = automationSettings.value.autoInferenceOnUpload
  savingAutomation.value = true
  try {
    automationSettings.value = await updateAiAutomationSettings(desired)
    ElMessage.success(desired ? '已开启上传后自动识别' : '已关闭上传后自动识别')
  } catch (error) {
    automationSettings.value.autoInferenceOnUpload = !desired
    ElMessage.error(error instanceof Error ? error.message : '自动识别开关保存失败')
  } finally {
    savingAutomation.value = false
  }
}

onMounted(load)
</script>

<template>
  <section v-loading="loading" class="system-status-page">
    <header class="page-header">
      <div>
        <p class="eyebrow">AI Governance</p>
        <h1>人工智能运行状态</h1>
        <p>查看本地视觉模型、DeepSeek 文本模型和智能工作流的配置与近七日调用质量，不展示任何密钥或内部路径。</p>
      </div>
      <el-button type="primary" @click="load">刷新</el-button>
    </header>

    <el-alert
      v-if="status"
      :title="status.healthSemantics"
      type="info"
      :closable="false"
      show-icon
    />

    <div class="summary-grid">
      <el-card shadow="never">
        <span>近七日任务</span>
        <strong>{{ total?.totalTasks ?? 0 }}</strong>
      </el-card>
      <el-card shadow="never">
        <span>调用成功率</span>
        <strong>{{ percent(total?.successRate) }}</strong>
      </el-card>
      <el-card shadow="never">
        <span>平均耗时</span>
        <strong>{{ total?.averageDurationMs ?? 0 }} ms</strong>
      </el-card>
      <el-card shadow="never">
        <span>待人工复核</span>
        <strong>{{ total?.pendingReviewTasks ?? 0 }}</strong>
      </el-card>
      <el-card shadow="never">
        <span>配置完整服务</span>
        <strong>{{ configuredCount }}/{{ status?.providers.length ?? 0 }}</strong>
      </el-card>
    </div>

    <el-card v-if="automationSettings" shadow="never" class="automation-card">
      <div class="automation-setting">
        <div>
          <div class="setting-title">
            <strong>上传巡检图片后自动执行识别</strong>
            <el-tag :type="automationSettings.autoInferenceOnUpload ? 'success' : 'info'">
              {{ automationSettings.autoInferenceOnUpload ? '已开启' : '已关闭' }}
            </el-tag>
          </div>
          <p>
            开启后，成功上传并绑定巡检任务的图片将自动调用
            {{ providerLabel(automationSettings.providerCode) }} / {{ capabilityLabel(automationSettings.capabilityType) }}，
            使用模型 {{ automationSettings.modelId }}。识别失败不会回滚图片上传。
          </p>
          <p v-if="!visionReady" class="setting-warning">
            本地视觉模型服务尚未就绪，当前不能开启此开关。
          </p>
        </div>
        <el-switch
          v-model="automationSettings.autoInferenceOnUpload"
          :loading="savingAutomation"
          :disabled="savingAutomation || (!visionReady && !automationSettings.autoInferenceOnUpload)"
          inline-prompt
          active-text="自动"
          inactive-text="手动"
          @change="saveAutomationSetting"
        />
      </div>
    </el-card>

    <el-card shadow="never">
      <el-table :data="status?.providers ?? []" stripe>
        <el-table-column label="人工智能服务" min-width="170">
          <template #default="{ row }">
            <div class="provider-name">
              <strong>{{ providerLabel(row.providerCode) }}</strong>
              <small>{{ row.providerCode }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="配置状态" min-width="130">
          <template #default="{ row }">
            <el-tag :type="tagType(row)">{{ configurationLabel(row.configurationStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="能力" min-width="220">
          <template #default="{ row }">
            <el-tag v-for="item in row.capabilities" :key="item" class="inline-tag" effect="plain">
              {{ capabilityLabel(item) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="默认路由" min-width="180">
          <template #default="{ row }">
            <span v-if="row.defaultFor.length">{{ row.defaultFor.map(capabilityLabel).join('、') }}</span>
            <span v-else>非默认</span>
          </template>
        </el-table-column>
        <el-table-column label="近七日成功/总数" min-width="150">
          <template #default="{ row }">
            {{ row.metrics7d.succeededTasks }}/{{ row.metrics7d.totalTasks }}
          </template>
        </el-table-column>
        <el-table-column label="成功率" min-width="100">
          <template #default="{ row }">{{ percent(row.metrics7d.successRate) }}</template>
        </el-table-column>
        <el-table-column label="平均耗时" min-width="120">
          <template #default="{ row }">{{ row.metrics7d.averageDurationMs }} ms</template>
        </el-table-column>
        <el-table-column label="待复核" min-width="90">
          <template #default="{ row }">{{ row.metrics7d.pendingReviewTasks }}</template>
        </el-table-column>
        <el-table-column label="连通性" min-width="110">
          <template #default="{ row }">
            <el-tag type="info" effect="plain">{{ row.connectivityStatus }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-alert
      v-if="status"
      :title="status.disclaimer"
      type="warning"
      :closable="false"
      show-icon
    />
  </section>
</template>

<style scoped lang="scss">
.system-status-page { display: grid; gap: 20px; }
.page-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 24px; }
.page-header h1 { margin: 4px 0 8px; }
.page-header p { margin: 0; color: #667085; }
.eyebrow { color: #176354 !important; font-size: 12px; font-weight: 800; letter-spacing: .08em; text-transform: uppercase; }
.summary-grid { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 14px; }
.summary-grid :deep(.el-card__body) { display: grid; gap: 8px; }
.summary-grid span { color: #667085; font-size: 13px; }
.summary-grid strong { color: #152b27; font-size: 24px; }
.automation-setting { display: flex; align-items: center; justify-content: space-between; gap: 28px; }
.setting-title { display: flex; align-items: center; gap: 10px; }
.automation-setting p { max-width: 860px; margin: 8px 0 0; color: #667085; line-height: 1.65; }
.automation-setting .setting-warning { color: #b54708; }
.provider-name { display: grid; gap: 2px; }
.provider-name strong { color: #152b27; }
.provider-name small { color: #98a2b3; font-size: 11px; }
.inline-tag { margin: 2px 6px 2px 0; }
@media (max-width: 1100px) { .summary-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 720px) { .automation-setting { align-items: flex-start; flex-direction: column; } }
</style>
