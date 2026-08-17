<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  getAiAutomationSettings,
  getAiGovernanceStatus,
  listAiModels,
  updateAiAutomationSettings,
  type AiAutomationSettings,
  type AiGovernanceStatus,
  type AiModelCatalogItem,
  type AiProviderStatus,
} from '@/shared/api'
import { useAppStore } from '@/stores/app'

const appStore = useAppStore()
const loading = ref(false)
const savingAutomation = ref(false)
const status = ref<AiGovernanceStatus | null>(null)
const automationSettings = ref<AiAutomationSettings | null>(null)
const modelCatalog = ref<AiModelCatalogItem[]>([])

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
const defaultVisionModels = computed(() => modelCatalog.value.filter((model) => (
  model.mode === 'REAL'
  && model.status === 'APPROVED'
  && (model.providerCode ?? 'FAST_API') === 'FAST_API'
  && (model.capabilityType ?? 'VISION_INFERENCE') === 'VISION_INFERENCE'
)))
const selectedDefaultModel = computed(() => {
  const modelId = automationSettings.value?.modelId
  if (!modelId) return null
  return defaultVisionModels.value.find((model) => model.modelId === modelId) ?? null
})
const selectedModelReady = computed(() => selectedDefaultModel.value?.runtimeReady === true && selectedDefaultModel.value?.selectable === true)

function providerUsable(providerCode: string, capability: string): boolean {
  const provider = status.value?.providers.find((item) => item.providerCode === providerCode)
  if (!provider || provider.configurationStatus !== 'CONFIGURED' || !provider.capabilities.includes(capability)) {
    return false
  }
  return provider.runtimeStatus === 'READY' || provider.runtimeStatus === 'DEGRADED'
}

const visionReady = computed(() => providerUsable('FAST_API', 'VISION_INFERENCE'))
const workflowReady = computed(() => providerUsable('DIFY', 'WORKFLOW'))
const knowledgeReady = computed(() => providerUsable('SPRING_AI', 'TEXT_GENERATION'))
const autoInferenceReady = computed(() => visionReady.value && selectedModelReady.value)

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

function runtimeTagType(value?: AiProviderStatus['runtimeStatus']): 'success' | 'warning' | 'danger' | 'info' {
  if (value === 'READY') return 'success'
  if (value === 'DEGRADED') return 'warning'
  if (value === 'AUTH_ERROR' || value === 'UNAVAILABLE') return 'danger'
  return 'info'
}

function runtimeLabel(value?: AiProviderStatus['runtimeStatus']): string {
  const labels: Record<string, string> = {
    READY: '就绪',
    DEGRADED: '降级可用',
    UNCONFIGURED: '未配置',
    AUTH_ERROR: '认证错误',
    UNAVAILABLE: '不可用',
    DISABLED: '已禁用',
    UNKNOWN: '未知',
  }
  return value ? (labels[value] ?? value) : '未知'
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

function modelStageLabel(stage: AiModelCatalogItem['deploymentStage']): string {
  const labels: Record<AiModelCatalogItem['deploymentStage'], string> = {
    VALIDATING: '验证中',
    DEMO: '演示',
    SHADOW: '影子运行',
    ACTIVE: '正式启用',
    SUSPENDED: '已暂停',
  }
  return labels[stage] ?? stage
}

function modelOptionLabel(model: AiModelCatalogItem): string {
  const runtime = model.runtimeReady ? '运行时就绪' : '运行时未就绪'
  return `${model.modelName} · ${model.modelId} · ${runtime}`
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const [governance, automation, models] = await Promise.all([
      getAiGovernanceStatus(),
      getAiAutomationSettings(),
      listAiModels(),
    ])
    status.value = governance
    automationSettings.value = automation
    modelCatalog.value = models.content
  } catch (error) {
    appStore.notify(error instanceof Error ? error.message : '人工智能运行状态加载失败', 'error')
  } finally {
    loading.value = false
  }
}

async function saveAutomationSettings(successMessage: string): Promise<void> {
  if (!automationSettings.value) return
  savingAutomation.value = true
  try {
    automationSettings.value = await updateAiAutomationSettings({
      autoInferenceOnUpload: automationSettings.value.autoInferenceOnUpload,
      intelligentWorkflowEnabled: automationSettings.value.intelligentWorkflowEnabled,
      knowledgeQaEnabled: automationSettings.value.knowledgeQaEnabled,
      modelId: automationSettings.value.modelId,
    })
    const models = await listAiModels()
    modelCatalog.value = models.content
    appStore.notify(successMessage, 'success')
  } catch (error) {
    appStore.notify(error instanceof Error ? error.message : '人工智能业务设置保存失败', 'error')
    await load()
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
        <p class="eyebrow">✦ AI Governance</p>
        <h1>AI 运行状态</h1>
        <p>先看业务能力是否可用；Provider、模型能力与连通性等技术信息统一收进专业技术详情。</p>
      </div>
      <el-button type="primary" round @click="load">刷新状态</el-button>
    </header>

    <section v-if="status?.healthSemantics" class="health-card">
      <span class="health-dot" />
      <div>
        <strong>当前 AI 健康状态</strong>
        <p>{{ status.healthSemantics }}</p>
      </div>
    </section>

    <section class="business-capabilities" aria-label="业务能力概览">
      <div class="section-title-row">
        <div>
          <strong>业务能力概览</strong>
          <p>普通业务只需要关注能力是否可用；单项故障不能阻断基础治理页面。</p>
        </div>
      </div>
      <div class="capability-grid">
        <el-card shadow="never" class="capability-card">
          <div class="capability-card__head">
            <strong>本地视觉识别</strong>
            <el-tag :type="visionReady ? 'success' : 'warning'" round>{{ visionReady ? '正常' : '服务未就绪' }}</el-tag>
          </div>
          <p>负责巡检图片高精度识别与病害候选发现。图片上传与后台分析解耦，失败不会回滚图片上传。</p>
        </el-card>
        <el-card shadow="never" class="capability-card">
          <div class="capability-card__head">
            <strong>智能工作流</strong>
            <el-tag :type="workflowReady ? 'success' : 'warning'" round>{{ workflowReady ? '正常' : '当前不可用' }}</el-tag>
          </div>
          <p>Dify 可用时优先参与智能编排；不可用时按既有策略回退到本地高精度模型，不阻断视觉基础能力。</p>
        </el-card>
        <el-card shadow="never" class="capability-card">
          <div class="capability-card__head">
            <strong>知识服务</strong>
            <el-tag :type="knowledgeReady ? 'success' : 'warning'" round>{{ knowledgeReady ? '正常' : '服务未就绪' }}</el-tag>
          </div>
          <p>面向已审核知识的权限检索与问答，证据不足时拒答，不替代专业人员形成正式结论。</p>
        </el-card>
      </div>
    </section>

    <el-card v-if="automationSettings" shadow="never" class="automation-card">
      <template #header>
        <div class="card-title-row">
          <div>
            <strong>智能能力与默认模型</strong>
            <p>控制业务是否使用对应能力，并选择后续视觉任务默认使用的模型；不会在网页中修改密钥或模型权重。</p>
          </div>
        </div>
      </template>

      <div class="automation-list">
        <div class="automation-setting model-setting">
          <div>
            <div class="setting-title">
              <strong>默认视觉模型</strong>
              <el-tag type="success" round>业务已批准</el-tag>
              <el-tag :type="selectedModelReady ? 'success' : 'warning'" round>
                {{ selectedModelReady ? '运行时就绪' : '运行时未就绪' }}
              </el-tag>
              <el-tag v-if="selectedDefaultModel" type="info" effect="plain" round>
                {{ selectedDefaultModel.deploymentStage }} · {{ modelStageLabel(selectedDefaultModel.deploymentStage) }}
              </el-tag>
            </div>
            <p>APPROVED 只表示模型身份与业务登记已批准；真实推理仍要求运行时加载成功。VALIDATING 模型可以查看，但运行时未就绪时不能设为默认。</p>
            <p v-if="selectedDefaultModel?.runtimeErrorMessage" class="model-runtime-error">
              当前运行时：{{ selectedDefaultModel.runtimeErrorMessage }}
            </p>
          </div>
          <el-select
            v-model="automationSettings.modelId"
            class="model-select"
            :disabled="savingAutomation"
            placeholder="选择默认视觉模型"
            @change="saveAutomationSettings('已切换默认视觉模型')"
          >
            <el-option
              v-for="model in defaultVisionModels"
              :key="model.modelId"
              :label="modelOptionLabel(model)"
              :value="model.modelId"
              :disabled="!model.selectable"
            />
          </el-select>
        </div>

        <div class="automation-setting">
          <div>
            <div class="setting-title">
              <strong>上传后自动视觉识别</strong>
              <el-tag :type="autoInferenceReady ? 'success' : 'warning'" round>{{ autoInferenceReady ? '服务可用' : '默认模型未就绪' }}</el-tag>
            </div>
            <p>巡检图片上传成功后自动创建后台高精度视觉分析任务；任务使用上方默认视觉模型。模型或服务未就绪时不能开启，失败也不会回滚图片上传。</p>
          </div>
          <el-switch
            v-model="automationSettings.autoInferenceOnUpload"
            :loading="savingAutomation"
            :disabled="savingAutomation || (!autoInferenceReady && !automationSettings.autoInferenceOnUpload)"
            inline-prompt active-text="开启" inactive-text="关闭"
            @change="saveAutomationSettings(automationSettings.autoInferenceOnUpload ? '已开启上传后自动视觉识别' : '已关闭上传后自动视觉识别')"
          />
        </div>

        <div class="automation-setting">
          <div>
            <div class="setting-title">
              <strong>智能工作流</strong>
              <el-tag :type="workflowReady ? 'success' : 'warning'" round>{{ workflowReady ? 'Dify 可用' : 'Dify 未就绪' }}</el-tag>
            </div>
            <p>开启后视觉分析优先使用 Dify 进行工作流与语义编排；Dify 未配置、不可用或运行故障时按策略回退到本地高精度模型。复核、报告等其他工作流仍按各自配置使用。</p>
          </div>
          <el-switch
            v-model="automationSettings.intelligentWorkflowEnabled"
            :loading="savingAutomation"
            :disabled="savingAutomation || (!workflowReady && !automationSettings.intelligentWorkflowEnabled)"
            inline-prompt active-text="开启" inactive-text="关闭"
            @change="saveAutomationSettings(automationSettings.intelligentWorkflowEnabled ? '已开启智能工作流' : '已关闭智能工作流')"
          />
        </div>

        <div class="automation-setting">
          <div>
            <div class="setting-title">
              <strong>知识问答</strong>
              <el-tag :type="knowledgeReady ? 'success' : 'warning'" round>{{ knowledgeReady ? 'DeepSeek 可用' : 'DeepSeek 未就绪' }}</el-tag>
            </div>
            <p>允许内部知识问答与 Spring AI 知识检索工具工作；仍遵循权限过滤、证据阈值与证据不足拒答。</p>
          </div>
          <el-switch
            v-model="automationSettings.knowledgeQaEnabled"
            :loading="savingAutomation"
            :disabled="savingAutomation || (!knowledgeReady && !automationSettings.knowledgeQaEnabled)"
            inline-prompt active-text="开启" inactive-text="关闭"
            @change="saveAutomationSettings(automationSettings.knowledgeQaEnabled ? '已开启知识问答' : '已关闭知识问答')"
          />
        </div>
      </div>
    </el-card>

    <section class="metrics-section">
      <div class="section-title-row">
        <div>
          <strong>近七日运行概览</strong>
          <p>用于观察整体运行趋势，不作为业务风险评分依据。</p>
        </div>
      </div>
      <div class="summary-grid">
        <el-card shadow="never"><span>近七日任务</span><strong>{{ total?.totalTasks ?? 0 }}</strong></el-card>
        <el-card shadow="never"><span>调用成功率</span><strong>{{ percent(total?.successRate) }}</strong></el-card>
        <el-card shadow="never"><span>平均耗时</span><strong>{{ total?.averageDurationMs ?? 0 }} ms</strong></el-card>
        <el-card shadow="never"><span>待人工复核</span><strong>{{ total?.pendingReviewTasks ?? 0 }}</strong></el-card>
        <el-card shadow="never"><span>配置完整服务</span><strong>{{ configuredCount }}/{{ status?.providers.length ?? 0 }}</strong></el-card>
      </div>
    </section>

    <el-collapse class="technical-card">
      <el-collapse-item title="专业技术详情" name="providers">
        <el-table :data="status?.providers ?? []" stripe>
          <el-table-column label="服务" min-width="170">
            <template #default="{ row }">
              <div class="provider-name"><strong>{{ providerLabel(row.providerCode) }}</strong><small>{{ row.providerCode }}</small></div>
            </template>
          </el-table-column>
          <el-table-column label="运行状态" min-width="140">
            <template #default="{ row }">
              <el-tag :type="runtimeTagType(row.runtimeStatus)" round>{{ runtimeLabel(row.runtimeStatus) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="配置状态" min-width="130">
            <template #default="{ row }"><el-tag :type="tagType(row)" round>{{ configurationLabel(row.configurationStatus) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="能力" min-width="220">
            <template #default="{ row }">
              <el-tag v-for="item in row.capabilities" :key="item" class="inline-tag" effect="plain" round>{{ capabilityLabel(item) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="默认能力" min-width="180">
            <template #default="{ row }"><span v-if="row.defaultFor.length">{{ row.defaultFor.map(capabilityLabel).join('、') }}</span><span v-else>—</span></template>
          </el-table-column>
          <el-table-column label="近七日成功/总数" min-width="150">
            <template #default="{ row }">{{ row.metrics7d.succeededTasks }}/{{ row.metrics7d.totalTasks }}</template>
          </el-table-column>
          <el-table-column label="成功率" min-width="100"><template #default="{ row }">{{ percent(row.metrics7d.successRate) }}</template></el-table-column>
          <el-table-column label="平均耗时" min-width="120"><template #default="{ row }">{{ row.metrics7d.averageDurationMs }} ms</template></el-table-column>
          <el-table-column label="待复核" min-width="90"><template #default="{ row }">{{ row.metrics7d.pendingReviewTasks }}</template></el-table-column>
          <el-table-column label="连通性" min-width="110"><template #default="{ row }"><el-tag type="info" effect="plain" round>{{ row.connectivityStatus }}</el-tag></template></el-table-column>
        </el-table>
      </el-collapse-item>
    </el-collapse>
  </section>
</template>

<style scoped lang="scss">
.system-status-page { display: grid; gap: 20px; }
.page-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 24px; }
.page-header h1 { margin: 4px 0 8px; }
.page-header p { margin: 0; color: #667085; }
.eyebrow { color: #176354 !important; font-size: 12px; font-weight: 800; letter-spacing: .08em; text-transform: uppercase; }
.health-card { display: flex; align-items: flex-start; gap: 12px; padding: 15px 17px; border: 1px solid #d9e9e4; border-radius: var(--usp-radius-lg); background: #f6fbf9; box-shadow: var(--usp-shadow-sm); }
.health-card p { margin: 3px 0 0; color: var(--usp-color-text-secondary); line-height: 1.6; }
.health-dot { width: 10px; height: 10px; margin-top: 6px; border-radius: 999px; background: var(--usp-color-success); box-shadow: 0 0 0 5px rgb(6 118 71 / 10%); }
.section-title-row { display: flex; justify-content: space-between; gap: 16px; }
.section-title-row > div { display: grid; gap: 4px; }
.section-title-row strong { font-size: 16px; }
.section-title-row p { margin: 0; color: #667085; font-size: 13px; }
.business-capabilities, .metrics-section { display: grid; gap: 12px; }
.capability-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; }
.capability-card, .summary-grid .el-card, .automation-card, .technical-card { border-radius: var(--usp-radius-xl); box-shadow: var(--usp-shadow-sm); }
.capability-card :deep(.el-card__body) { display: grid; gap: 10px; }
.capability-card__head { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.capability-card p { margin: 0; color: #667085; line-height: 1.65; }
.summary-grid { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 14px; }
.summary-grid :deep(.el-card__body) { display: grid; gap: 8px; }
.summary-grid span { color: #667085; font-size: 13px; }
.summary-grid strong { color: #152b27; font-size: 24px; }
.card-title-row strong { font-size: 16px; }
.card-title-row p { margin: 5px 0 0; color: #667085; font-size: 13px; }
.automation-list { display: grid; gap: 0; }
.automation-setting { display: flex; align-items: center; justify-content: space-between; gap: 28px; padding: 18px 0; border-bottom: 1px solid var(--usp-color-border); }
.automation-setting:first-child { padding-top: 2px; }
.automation-setting:last-child { padding-bottom: 2px; border-bottom: 0; }
.setting-title { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; }
.automation-setting p { max-width: 860px; margin: 7px 0 0; color: #667085; line-height: 1.6; }
.model-setting { align-items: flex-start; }
.model-select { width: min(520px, 46vw); flex: 0 0 auto; }
.model-runtime-error { color: var(--el-color-warning-dark-2) !important; font-size: 12px; }
.technical-card { border: 1px solid var(--usp-color-border); background: var(--usp-color-surface); }
.technical-card :deep(.el-collapse-item__header) { padding: 0 16px; border-radius: var(--usp-radius-xl); font-weight: 700; }
.technical-card :deep(.el-collapse-item__content) { padding: 0 12px 14px; }
.provider-name { display: grid; gap: 2px; }
.provider-name strong { color: var(--usp-color-text-primary); }
.provider-name small { color: var(--usp-color-text-tertiary); font-size: 11px; }
.inline-tag { margin: 2px 6px 2px 0; }
@media (max-width: 1100px) {
  .capability-grid { grid-template-columns: 1fr; }
  .summary-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .model-setting { flex-direction: column; }
  .model-select { width: 100%; }
}
@media (max-width: 720px) {
  .automation-setting { align-items: flex-start; flex-direction: column; }
}
</style>