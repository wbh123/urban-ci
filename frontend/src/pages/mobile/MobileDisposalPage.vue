<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  completeFeedbackReinspection,
  createFeedbackReinspection,
  getFeedbackReinspectionRecommendation,
  listFeedbackReports,
  submitFeedbackRectification,
  updateFeedbackStatus,
  waiveFeedbackReinspection,
  toAppError,
  type FeedbackManagementRow,
  type FeedbackReinspectionDecision,
  type FeedbackReinspectionRecommendation,
  type FeedbackStatus,
} from '@/shared/api'
import { listImages, uploadImage, type AssetImageRow } from '@/shared/api/endpoints/assets'
import AppLoading from '@/shared/components/AppLoading.vue'
import AppEmpty from '@/shared/components/AppEmpty.vue'
import AppError from '@/shared/components/AppError.vue'
import AppStatusTag from '@/shared/components/AppStatusTag.vue'

const PAGE_SIZE = 20
const loading = ref(false)
const loadingMore = ref(false)
const saving = ref(false)
const uploadingEvidence = ref(false)
const recommendationLoading = ref(false)
const errorMessage = ref('')
const reports = ref<FeedbackManagementRow[]>([])
const statusFilter = ref<FeedbackStatus | 'ALL'>('ALL')
const currentPage = ref(0)
const hasMore = ref(true)
const selected = ref<FeedbackManagementRow | null>(null)
const actionVisible = ref(false)
const targetStatus = ref<FeedbackStatus>('ACCEPTED')
const handlingSummary = ref('')
const publicMessage = ref('')
const rectificationEvidence = ref<AssetImageRow[]>([])
const recommendation = ref<FeedbackReinspectionRecommendation | null>(null)
const reinspectionDecision = ref<FeedbackReinspectionDecision>('REQUIRED')
const decisionReason = ref('')
const waiverVisible = ref(false)
const waiverReason = ref('')
const resultVisible = ref(false)
const resultPassed = ref(true)
const reinspectionSummary = ref('')

const visibleStatuses: FeedbackStatus[] = [
  'SUBMITTED',
  'ACCEPTED',
  'PROCESSING',
  'NEED_MORE_INFO',
  'RESOLVED',
  'CLOSED',
]

const filtered = computed(() =>
  reports.value.filter((item) => visibleStatuses.includes(item.status)),
)

const decisionNeedsReason = computed(() => {
  if (reinspectionDecision.value === 'WAIVED') return true
  return Boolean(
    recommendation.value
      && reinspectionDecision.value !== recommendation.value.recommendedDecision,
  )
})

function nextActions(status: FeedbackStatus): Array<{ label: string; status: FeedbackStatus; type?: 'primary' | 'success' | 'warning' }> {
  if (status === 'SUBMITTED') return [{ label: '接单受理', status: 'ACCEPTED', type: 'primary' }]
  if (status === 'ACCEPTED') {
    return [
      { label: '开始处置', status: 'PROCESSING', type: 'primary' },
      { label: '要求补充材料', status: 'NEED_MORE_INFO', type: 'warning' },
    ]
  }
  if (status === 'PROCESSING') {
    return [
      { label: '提交整改完成', status: 'RESOLVED', type: 'success' },
      { label: '要求补充材料', status: 'NEED_MORE_INFO', type: 'warning' },
    ]
  }
  if (status === 'NEED_MORE_INFO') return [{ label: '恢复处置', status: 'PROCESSING', type: 'primary' }]
  return []
}

function reinspectionStatusText(status?: string): string {
  if (status === 'PENDING') return '复查任务待开始'
  if (status === 'IN_PROGRESS') return '复查任务进行中'
  if (status === 'COMPLETED') return '复查任务已完成，等待复验结论'
  if (status === 'CANCELLED') return '复查任务已取消，可重新发起或人工确认免复检'
  return '尚未创建复查任务'
}

function decisionLabel(decision?: FeedbackReinspectionDecision): string {
  if (decision === 'WAIVED') return '人工确认无需现场复检'
  if (decision === 'REQUIRED') return '需要现场复检'
  return '尚未形成复检决策'
}

async function load(reset = true): Promise<void> {
  if (reset) {
    currentPage.value = 0
    reports.value = []
    loading.value = true
  } else {
    loadingMore.value = true
  }
  errorMessage.value = ''
  try {
    const page = await listFeedbackReports({
      ...(statusFilter.value === 'ALL' ? {} : { status: statusFilter.value }),
      page: currentPage.value,
      size: PAGE_SIZE,
    })
    const rows = page.content ?? []
    reports.value = reset ? rows : [...reports.value, ...rows]
    hasMore.value = currentPage.value + 1 < page.page.totalPages
  } catch (error) {
    errorMessage.value = toAppError(error).message
    if (!reset && currentPage.value > 0) currentPage.value -= 1
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

async function loadMore(): Promise<void> {
  if (loading.value || loadingMore.value || !hasMore.value) return
  currentPage.value += 1
  await load(false)
}

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

async function openAction(report: FeedbackManagementRow, status: FeedbackStatus): Promise<void> {
  selected.value = report
  targetStatus.value = status
  handlingSummary.value = report.handlingSummary || ''
  publicMessage.value = ''
  rectificationEvidence.value = []
  recommendation.value = null
  reinspectionDecision.value = 'REQUIRED'
  decisionReason.value = ''
  if (status === 'RESOLVED') {
    await Promise.all([
      loadRectificationEvidence(report.reportId),
      loadRecommendation(report.reportId),
    ])
  }
  actionVisible.value = true
}

async function uploadRectificationEvidence(event: Event): Promise<void> {
  if (!selected.value) return
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  uploadingEvidence.value = true
  try {
    await uploadImage({
      file,
      businessType: 'RESIDENT_REPORT',
      businessId: selected.value.reportId,
      bindingRole: 'RECTIFICATION_PHOTO',
    })
    ElMessage.success('整改证据已上传')
    await loadRectificationEvidence(selected.value.reportId)
  } catch (error) {
    ElMessage.error(toAppError(error).message)
  } finally {
    uploadingEvidence.value = false
  }
}

async function submitAction(): Promise<void> {
  if (!selected.value) return
  if (!handlingSummary.value.trim()) {
    ElMessage.warning('请填写处置说明')
    return
  }
  if (targetStatus.value === 'RESOLVED' && rectificationEvidence.value.length === 0) {
    ElMessage.warning('提交整改完成前至少上传一张整改证据')
    return
  }
  if (
    targetStatus.value === 'RESOLVED'
    && decisionNeedsReason.value
    && decisionReason.value.trim().length < 4
  ) {
    ElMessage.warning('选择免复检或调整系统建议时，请填写至少 4 个字符的人工判断理由')
    return
  }

  saving.value = true
  try {
    if (targetStatus.value === 'RESOLVED') {
      const result = await submitFeedbackRectification(selected.value.reportId, {
        handlingSummary: handlingSummary.value.trim(),
        message: publicMessage.value.trim() || undefined,
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
      await updateFeedbackStatus(selected.value.reportId, {
        status: targetStatus.value,
        handlingSummary: handlingSummary.value.trim(),
        message: publicMessage.value.trim() || undefined,
        publicVisible: Boolean(publicMessage.value.trim()),
      })
      ElMessage.success('处置状态已更新')
    }
    actionVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(toAppError(error).message)
  } finally {
    saving.value = false
  }
}

async function createReinspection(report: FeedbackManagementRow): Promise<void> {
  saving.value = true
  try {
    const result = await createFeedbackReinspection(report.reportId)
    ElMessage.success(result.reused ? '已存在复查任务，已刷新状态' : '复查复验任务已创建，请由巡检人员执行')
    await load()
  } catch (error) {
    ElMessage.error(toAppError(error).message)
  } finally {
    saving.value = false
  }
}

async function openWaiver(report: FeedbackManagementRow): Promise<void> {
  selected.value = report
  waiverReason.value = ''
  recommendation.value = null
  await loadRecommendation(report.reportId)
  waiverVisible.value = true
}

async function submitWaiver(): Promise<void> {
  if (!selected.value) return
  if (waiverReason.value.trim().length < 4) {
    ElMessage.warning('请填写至少 4 个字符的人工免复检判断理由')
    return
  }
  saving.value = true
  try {
    const result = await waiveFeedbackReinspection(selected.value.reportId, {
      decisionReason: waiverReason.value.trim(),
    })
    ElMessage.success('已人工确认无需现场复检，整改事项已闭环')
    if (result.manualOverride) ElMessage.info('本次人工决策调整了系统建议，理由已写入审计记录')
    waiverVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(toAppError(error).message)
  } finally {
    saving.value = false
  }
}

function openReinspectionResult(report: FeedbackManagementRow, passed: boolean): void {
  selected.value = report
  resultPassed.value = passed
  reinspectionSummary.value = ''
  resultVisible.value = true
}

async function submitReinspectionResult(): Promise<void> {
  if (!selected.value) return
  if (reinspectionSummary.value.trim().length < 4) {
    ElMessage.warning('请填写至少 4 个字符的复验说明')
    return
  }
  saving.value = true
  try {
    const result = await completeFeedbackReinspection(selected.value.reportId, {
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
    saving.value = false
  }
}

watch(statusFilter, () => { void load(true) })
onMounted(() => { void load(true) })
</script>

<template>
  <section class="mobile-page">
    <header class="page-head">
      <div>
        <p>整改闭环</p>
        <h1>问题处置</h1>
        <span>系统给出复检建议，工作人员结合整改证据做最终决定；需要复检的工单再进入现场复验。</span>
      </div>
      <el-button @click="load(true)">刷新</el-button>
    </header>

    <el-alert
      title="闭环规则"
      type="info"
      :closable="false"
      show-icon
      description="处理中 → 上传整改证据 → 系统给出复检建议 → 人工确认需要复检或免复检；需要复检时完成现场复验后闭环，免复检时填写理由后直接闭环。复验和免复检均不会直接修改正式风险评分。"
    />

    <el-segmented
      v-model="statusFilter"
      :options="[
        { label: '全部', value: 'ALL' },
        { label: '待受理', value: 'SUBMITTED' },
        { label: '待开始', value: 'ACCEPTED' },
        { label: '处理中', value: 'PROCESSING' },
        { label: '待补充', value: 'NEED_MORE_INFO' },
        { label: '待复验', value: 'RESOLVED' },
        { label: '已闭环', value: 'CLOSED' },
      ]"
      class="status-filter"
    />

    <AppLoading :visible="loading" inline text="加载处置工单中…" />
    <AppError v-if="errorMessage" :message="errorMessage" @retry="load(true)" />

    <div v-if="!loading && !errorMessage && filtered.length" class="work-list">
      <el-card v-for="item in filtered" :key="item.reportId" shadow="never" class="work-card">
        <div class="work-head">
          <div>
            <strong>{{ item.reportCode }}</strong>
            <span>{{ item.communityName }}{{ item.buildingName ? ` · ${item.buildingName}` : '' }}</span>
          </div>
          <AppStatusTag :status="item.status" variant="task" />
        </div>
        <p class="description">{{ item.description }}</p>
        <dl>
          <div><dt>问题类型</dt><dd>{{ item.reportType }}</dd></div>
          <div><dt>紧急程度</dt><dd>{{ item.urgency }}</dd></div>
          <div><dt>具体位置</dt><dd>{{ item.locationText || '未填写' }}</dd></div>
          <div><dt>群众原始图片</dt><dd>{{ item.imageCount }} 张</dd></div>
        </dl>
        <p v-if="item.handlingSummary" class="summary"><strong>当前整改说明：</strong>{{ item.handlingSummary }}</p>

        <div v-if="item.reinspectionDecision" class="decision-state">
          <strong>{{ decisionLabel(item.reinspectionDecision) }}</strong>
          <span v-if="item.reinspectionManualOverride">人工已调整系统建议，决策理由已留痕。</span>
          <span v-if="item.reinspectionDecisionReason">理由：{{ item.reinspectionDecisionReason }}</span>
        </div>

        <div v-if="item.status === 'RESOLVED'" class="reinspection-state">
          <strong>复查复验</strong>
          <span>{{ item.reinspectionTaskCode || '尚未生成任务编号' }} · {{ reinspectionStatusText(item.reinspectionStatus) }}</span>
        </div>
        <div v-else-if="item.status === 'CLOSED'" class="closed-state">
          <strong>✓ 整改闭环完成</strong>
          <span v-if="item.reinspectionDecision === 'WAIVED'">本次经人工确认无需现场复检并完成闭环。如形成新的现场证据，可重新执行正式风险评分。</span>
          <span v-else>该次整改事项已经复查复验通过。如形成新的现场证据，可重新执行正式风险评分。</span>
        </div>

        <div class="actions">
          <el-button
            v-for="action in nextActions(item.status)"
            :key="action.status"
            :type="action.type"
            @click="openAction(item, action.status)"
          >{{ action.label }}</el-button>

          <template v-if="item.status === 'RESOLVED'">
            <template v-if="!item.reinspectionTaskId || item.reinspectionStatus === 'CANCELLED'">
              <el-button type="primary" :loading="saving" @click="createReinspection(item)">发起复查复验</el-button>
              <el-button :loading="saving" @click="openWaiver(item)">人工确认无需复检</el-button>
            </template>
            <template v-else-if="item.reinspectionStatus === 'COMPLETED'">
              <el-button type="success" @click="openReinspectionResult(item, true)">复验通过并关闭</el-button>
              <el-button type="warning" @click="openReinspectionResult(item, false)">复验不通过</el-button>
            </template>
          </template>
        </div>
      </el-card>
    </div>
    <el-button
      v-if="!loading && !errorMessage && hasMore"
      class="load-more"
      :loading="loadingMore"
      :disabled="loadingMore"
      @click="loadMore"
    >加载更多</el-button>
    <AppEmpty v-else-if="!loading && !errorMessage && !filtered.length" description="当前没有需要处理的问题工单" />

    <el-drawer v-model="actionVisible" title="更新处置进度" direction="btt" size="88%">
      <template v-if="selected">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="工单编号">{{ selected.reportCode }}</el-descriptions-item>
          <el-descriptions-item label="目标阶段">
            {{ targetStatus === 'RESOLVED' ? '整改完成，由人工确认是否需要复检' : targetStatus }}
          </el-descriptions-item>
          <el-descriptions-item label="问题位置">{{ selected.locationText || '未填写' }}</el-descriptions-item>
        </el-descriptions>
        <el-form label-position="top" class="action-form">
          <el-form-item label="处置说明（必填）">
            <el-input
              v-model="handlingSummary"
              type="textarea"
              :rows="4"
              maxlength="2000"
              show-word-limit
              placeholder="填写核查情况、整改措施和整改结果"
            />
          </el-form-item>

          <el-form-item v-if="targetStatus === 'RESOLVED'" label="整改证据（至少 1 张）">
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

          <template v-if="targetStatus === 'RESOLVED'">
            <div class="recommendation-card" :class="recommendation?.recommendedDecision === 'REQUIRED' ? 'is-required' : 'is-waivable'">
              <span>系统复检建议</span>
              <strong v-if="recommendationLoading">正在分析结构化风险信息…</strong>
              <strong v-else-if="recommendation">
                {{ recommendation.recommendedDecision === 'REQUIRED' ? '建议现场复检' : '可考虑免现场复检' }}
              </strong>
              <strong v-else>建议暂不可用，默认需要现场复检</strong>
              <ul v-if="recommendation?.reasons?.length">
                <li v-for="reason in recommendation.reasons" :key="reason">{{ reason }}</li>
              </ul>
              <small>{{ recommendation?.disclaimer || '系统建议仅辅助判断，最终决定由工作人员确认并留痕。' }}</small>
            </div>

            <el-form-item label="人工最终决定">
              <el-radio-group v-model="reinspectionDecision" class="decision-options">
                <el-radio-button value="REQUIRED">需要现场复检</el-radio-button>
                <el-radio-button value="WAIVED">无需复检，直接闭环</el-radio-button>
              </el-radio-group>
            </el-form-item>

            <el-form-item v-if="decisionNeedsReason" label="人工判断理由（必填）">
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

          <el-form-item label="向群众公开的进度说明（可选）">
            <el-input
              v-model="publicMessage"
              type="textarea"
              :rows="3"
              maxlength="1000"
              show-word-limit
              :placeholder="targetStatus === 'RESOLVED' ? '例如：整改已完成，复检安排由工作人员结合证据确认。' : '填写后将在群众查询进度时展示'"
            />
          </el-form-item>
          <el-button type="primary" size="large" :loading="saving" class="submit-action" @click="submitAction">
            <template v-if="targetStatus === 'RESOLVED'">
              {{ reinspectionDecision === 'REQUIRED' ? '提交整改并进入待复验' : '提交整改并人工免复检闭环' }}
            </template>
            <template v-else>确认更新</template>
          </el-button>
        </el-form>
      </template>
    </el-drawer>

    <el-dialog v-model="waiverVisible" title="人工确认无需现场复检" width="min(92vw, 520px)">
      <el-alert
        title="该操作将直接完成治理工单闭环"
        type="warning"
        :closable="false"
        show-icon
        description="仅适用于尚未派出有效复检任务的待复验工单。系统建议不会替代人工判断，人工理由将写入审计记录。"
      />
      <div v-if="recommendation" class="recommendation-card compact" :class="recommendation.recommendedDecision === 'REQUIRED' ? 'is-required' : 'is-waivable'">
        <span>系统建议</span>
        <strong>{{ recommendation.recommendedDecision === 'REQUIRED' ? '建议现场复检' : '可考虑免现场复检' }}</strong>
        <small>{{ recommendation.reasons.join('；') }}</small>
      </div>
      <el-input
        v-model="waiverReason"
        type="textarea"
        :rows="5"
        maxlength="1000"
        show-word-limit
        class="result-summary"
        placeholder="填写人工确认无需现场复检的依据，至少 4 个字符"
      />
      <p class="risk-boundary">免复检只关闭治理工单，不会直接修改正式风险评分。</p>
      <template #footer>
        <el-button @click="waiverVisible = false">取消</el-button>
        <el-button type="warning" :loading="saving" @click="submitWaiver">确认免复检并闭环</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resultVisible" :title="resultPassed ? '确认复验通过' : '确认复验不通过'" width="min(92vw, 520px)">
      <el-alert
        :title="resultPassed ? '通过后工单将正式关闭' : '不通过后工单将退回继续整改'"
        :type="resultPassed ? 'success' : 'warning'"
        :closable="false"
        show-icon
      />
      <p class="risk-boundary">复验不会直接修改正式风险评分；如形成新的有效现场证据，应重新执行正式评分链。</p>
      <el-input
        v-model="reinspectionSummary"
        type="textarea"
        :rows="5"
        maxlength="2000"
        show-word-limit
        class="result-summary"
        placeholder="填写复查位置、现场结果、是否仍存在原问题等复验依据"
      />
      <template #footer>
        <el-button @click="resultVisible = false">取消</el-button>
        <el-button :type="resultPassed ? 'success' : 'warning'" :loading="saving" @click="submitReinspectionResult">
          {{ resultPassed ? '确认通过并关闭' : '确认不通过并退回整改' }}
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped lang="scss">
.mobile-page { display: grid; gap: 16px; }
.page-head { display: flex; justify-content: space-between; align-items: flex-end; gap: 12px; }
.page-head p { margin: 0; color: #287a6a; font-weight: 700; }
.page-head h1 { margin: 4px 0; font-size: 28px; }
.page-head span { color: #667085; line-height: 1.5; }
.status-filter { width: 100%; overflow-x: auto; }
.work-list { display: grid; gap: 12px; }
.work-card { border-radius: 16px; }
.work-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; }
.work-head > div { display: grid; gap: 5px; }
.work-head span { color: #667085; font-size: 13px; }
.description { line-height: 1.7; color: #344054; }
dl { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
dl div { padding: 10px; border-radius: 10px; background: #f8faf9; }
dt { color: #667085; font-size: 12px; }
dd { margin: 4px 0 0; overflow-wrap: anywhere; color: #173f37; }
.summary { padding: 12px; border-radius: 10px; background: #fff8e8; color: #7a5b00; line-height: 1.6; }
.reinspection-state,.closed-state,.decision-state { display: grid; gap: 4px; padding: 12px; border-radius: 12px; }
.reinspection-state { background: #eef7ff; color: #245b89; }
.closed-state { background: #edf8f3; color: #176354; }
.decision-state { background: #f8fafc; color: #344054; border: 1px solid #e4e7ec; }
.reinspection-state span,.closed-state span,.decision-state span { font-size: 12px; line-height: 1.55; }
.actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 12px; }
.load-more { width: 100%; min-height: 46px; border-radius: 14px; }
.action-form { margin-top: 16px; }
.submit-action { width: 100%; }
.evidence-block { display: grid; gap: 10px; width: 100%; }
.evidence-upload { display: flex; align-items: center; justify-content: center; min-height: 52px; border: 1px dashed #8eb9ae; border-radius: 12px; background: #f4faf8; color: #176354; font-weight: 700; cursor: pointer; }
.evidence-upload input { display: none; }
.evidence-list { display: grid; gap: 6px; }
.evidence-list span { padding: 8px 10px; border-radius: 8px; background: #f8faf9; overflow-wrap: anywhere; font-size: 12px; }
.evidence-block small { color: #b54708; line-height: 1.5; }
.recommendation-card { display: grid; gap: 8px; margin-bottom: 16px; padding: 13px; border: 1px solid #d0d5dd; border-radius: 12px; background: #f8fafc; }
.recommendation-card.is-required { border-color: #f0b5a8; background: #fff5f2; }
.recommendation-card.is-waivable { border-color: #a8d8c7; background: #f2faf7; }
.recommendation-card span { color: #667085; font-size: 12px; font-weight: 700; }
.recommendation-card strong { color: #173f37; }
.recommendation-card ul { margin: 0; padding-left: 18px; color: #475467; line-height: 1.6; }
.recommendation-card small { color: #667085; line-height: 1.55; }
.recommendation-card.compact { margin: 14px 0; }
.decision-options { display: flex; width: 100%; }
.decision-options :deep(.el-radio-button) { flex: 1; }
.decision-options :deep(.el-radio-button__inner) { width: 100%; }
.result-summary { margin-top: 16px; }
.risk-boundary { padding: 10px 12px; border-radius: 10px; background: #eef7ff; color: #245b89; line-height: 1.6; }
@media (max-width: 480px) { dl { grid-template-columns: 1fr; } }
</style>