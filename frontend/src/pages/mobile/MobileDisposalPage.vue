<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  completeFeedbackReinspection,
  createFeedbackReinspection,
  listFeedbackReports,
  submitFeedbackRectification,
  updateFeedbackStatus,
  toAppError,
  type FeedbackManagementRow,
  type FeedbackStatus,
} from '@/shared/api'
import { listImages, uploadImage, type AssetImageRow } from '@/shared/api/endpoints/assets'
import AppLoading from '@/shared/components/AppLoading.vue'
import AppEmpty from '@/shared/components/AppEmpty.vue'
import AppError from '@/shared/components/AppError.vue'
import AppStatusTag from '@/shared/components/AppStatusTag.vue'

const loading = ref(false)
const saving = ref(false)
const uploadingEvidence = ref(false)
const errorMessage = ref('')
const reports = ref<FeedbackManagementRow[]>([])
const statusFilter = ref<FeedbackStatus | 'ALL'>('ALL')
const selected = ref<FeedbackManagementRow | null>(null)
const actionVisible = ref(false)
const targetStatus = ref<FeedbackStatus>('ACCEPTED')
const handlingSummary = ref('')
const publicMessage = ref('')
const rectificationEvidence = ref<AssetImageRow[]>([])
const resultVisible = ref(false)
const resultPassed = ref(true)
const reinspectionSummary = ref('')

const visibleStatuses: FeedbackStatus[] = ['SUBMITTED','ACCEPTED','PROCESSING','NEED_MORE_INFO','RESOLVED','CLOSED']
const filtered = computed(() => statusFilter.value === 'ALL' ? reports.value : reports.value.filter((item) => item.status === statusFilter.value))

function nextActions(status: FeedbackStatus): Array<{ label: string; status: FeedbackStatus; type?: 'primary' | 'success' | 'warning' }> {
  if (status === 'SUBMITTED') return [{ label: '接单受理', status: 'ACCEPTED', type: 'primary' }]
  if (status === 'ACCEPTED') return [{ label: '开始处置', status: 'PROCESSING', type: 'primary' },{ label: '要求补充材料', status: 'NEED_MORE_INFO', type: 'warning' }]
  if (status === 'PROCESSING') return [{ label: '提交整改完成', status: 'RESOLVED', type: 'success' },{ label: '要求补充材料', status: 'NEED_MORE_INFO', type: 'warning' }]
  if (status === 'NEED_MORE_INFO') return [{ label: '恢复处置', status: 'PROCESSING', type: 'primary' }]
  return []
}
function reinspectionStatusText(status?: string): string {
  if (status === 'PENDING') return '复查任务待开始'
  if (status === 'IN_PROGRESS') return '复查任务进行中'
  if (status === 'COMPLETED') return '复查任务已完成，等待复验结论'
  if (status === 'CANCELLED') return '复查任务已取消，可重新发起'
  return '尚未创建复查任务'
}
async function load(): Promise<void> {
  loading.value = true; errorMessage.value = ''
  try { const page = await listFeedbackReports({ page: 0, size: 100 }); reports.value = (page.content ?? []).filter((item) => visibleStatuses.includes(item.status)) }
  catch (error) { errorMessage.value = toAppError(error).message }
  finally { loading.value = false }
}
async function loadRectificationEvidence(reportId: string): Promise<void> {
  try { const page = await listImages({ businessType: 'RESIDENT_REPORT', businessId: reportId }); rectificationEvidence.value = page.content.filter((item) => item.bindingRole === 'RECTIFICATION_PHOTO') }
  catch (error) { rectificationEvidence.value = []; ElMessage.warning(toAppError(error).message) }
}
async function openAction(report: FeedbackManagementRow, status: FeedbackStatus): Promise<void> {
  selected.value = report; targetStatus.value = status; handlingSummary.value = report.handlingSummary || ''; publicMessage.value = ''; rectificationEvidence.value = []; actionVisible.value = true
  if (status === 'RESOLVED') await loadRectificationEvidence(report.reportId)
}
async function uploadRectificationEvidence(event: Event): Promise<void> {
  if (!selected.value) return
  const input = event.target as HTMLInputElement; const file = input.files?.[0]; input.value = ''; if (!file) return
  uploadingEvidence.value = true
  try { await uploadImage({ file, businessType: 'RESIDENT_REPORT', businessId: selected.value.reportId, bindingRole: 'RECTIFICATION_PHOTO' }); ElMessage.success('整改证据已上传'); await loadRectificationEvidence(selected.value.reportId) }
  catch (error) { ElMessage.error(toAppError(error).message) }
  finally { uploadingEvidence.value = false }
}
async function submitAction(): Promise<void> {
  if (!selected.value) return
  if (!handlingSummary.value.trim()) { ElMessage.warning('请填写处置说明'); return }
  if (targetStatus.value === 'RESOLVED' && rectificationEvidence.value.length === 0) { ElMessage.warning('提交整改完成前至少上传一张整改证据'); return }
  saving.value = true
  try {
    if (targetStatus.value === 'RESOLVED') {
      await submitFeedbackRectification(selected.value.reportId, { handlingSummary: handlingSummary.value.trim(), message: publicMessage.value.trim() || undefined })
      ElMessage.success('整改已提交，当前进入待复验阶段')
    } else {
      await updateFeedbackStatus(selected.value.reportId, { status: targetStatus.value, handlingSummary: handlingSummary.value.trim(), message: publicMessage.value.trim() || undefined, publicVisible: Boolean(publicMessage.value.trim()) })
      ElMessage.success('处置状态已更新')
    }
    actionVisible.value = false; await load()
  } catch (error) { ElMessage.error(toAppError(error).message) }
  finally { saving.value = false }
}
async function createReinspection(report: FeedbackManagementRow): Promise<void> {
  saving.value = true
  try { const result = await createFeedbackReinspection(report.reportId); ElMessage.success(result.reused ? '已存在复查任务，已刷新状态' : '复查复验任务已创建，请由巡检人员执行'); await load() }
  catch (error) { ElMessage.error(toAppError(error).message) }
  finally { saving.value = false }
}
function openReinspectionResult(report: FeedbackManagementRow, passed: boolean): void { selected.value = report; resultPassed.value = passed; reinspectionSummary.value = ''; resultVisible.value = true }
async function submitReinspectionResult(): Promise<void> {
  if (!selected.value) return
  if (reinspectionSummary.value.trim().length < 4) { ElMessage.warning('请填写至少 4 个字符的复验说明'); return }
  saving.value = true
  try { const result = await completeFeedbackReinspection(selected.value.reportId, { passed: resultPassed.value, summary: reinspectionSummary.value.trim() }); ElMessage.success(resultPassed.value ? '复验通过，整改事项已闭环' : '复验未通过，已退回继续整改'); if (result.nextStep) ElMessage.info(result.nextStep); resultVisible.value = false; await load() }
  catch (error) { ElMessage.error(toAppError(error).message) }
  finally { saving.value = false }
}
onMounted(load)
</script>

<template>
  <section class="mobile-page">
    <header class="page-head"><div><p>整改闭环</p><h1>问题处置</h1><span>处置问题、上传整改证据、发起复查复验；只有复验通过后才真正关闭工单。</span></div><el-button @click="load">刷新</el-button></header>
    <el-alert title="闭环规则" type="info" :closable="false" show-icon description="处理中 → 上传整改证据 → 已整改待复验 → 巡检员完成复查任务 → 复验通过关闭；复验不通过则退回继续整改。复验不会直接修改正式风险评分。" />
    <el-segmented v-model="statusFilter" :options="[{ label: '全部', value: 'ALL' },{ label: '待受理', value: 'SUBMITTED' },{ label: '待开始', value: 'ACCEPTED' },{ label: '处理中', value: 'PROCESSING' },{ label: '待补充', value: 'NEED_MORE_INFO' },{ label: '待复验', value: 'RESOLVED' },{ label: '已闭环', value: 'CLOSED' }]" class="status-filter" />
    <AppLoading :visible="loading" inline text="加载处置工单中…" />
    <AppError v-if="errorMessage" :message="errorMessage" @retry="load" />
    <div v-if="!loading && !errorMessage && filtered.length" class="work-list">
      <el-card v-for="item in filtered" :key="item.reportId" shadow="never" class="work-card">
        <div class="work-head"><div><strong>{{ item.reportCode }}</strong><span>{{ item.communityName }}{{ item.buildingName ? ` · ${item.buildingName}` : '' }}</span></div><AppStatusTag :status="item.status" variant="task" /></div>
        <p class="description">{{ item.description }}</p>
        <dl><div><dt>问题类型</dt><dd>{{ item.reportType }}</dd></div><div><dt>紧急程度</dt><dd>{{ item.urgency }}</dd></div><div><dt>具体位置</dt><dd>{{ item.locationText || '未填写' }}</dd></div><div><dt>群众原始图片</dt><dd>{{ item.imageCount }} 张</dd></div></dl>
        <p v-if="item.handlingSummary" class="summary"><strong>当前整改说明：</strong>{{ item.handlingSummary }}</p>
        <div v-if="item.status === 'RESOLVED'" class="reinspection-state"><strong>复查复验</strong><span>{{ item.reinspectionTaskCode || '尚未生成任务编号' }} · {{ reinspectionStatusText(item.reinspectionStatus) }}</span></div>
        <div v-else-if="item.status === 'CLOSED'" class="closed-state"><strong>✓ 整改闭环完成</strong><span>该次整改事项已经复查复验通过。如形成新的现场证据，可重新执行正式风险评分。</span></div>
        <div class="actions">
          <el-button v-for="action in nextActions(item.status)" :key="action.status" :type="action.type" @click="openAction(item, action.status)">{{ action.label }}</el-button>
          <template v-if="item.status === 'RESOLVED'">
            <el-button v-if="!item.reinspectionTaskId || item.reinspectionStatus === 'CANCELLED'" type="primary" :loading="saving" @click="createReinspection(item)">发起复查复验</el-button>
            <template v-else-if="item.reinspectionStatus === 'COMPLETED'"><el-button type="success" @click="openReinspectionResult(item, true)">复验通过并关闭</el-button><el-button type="warning" @click="openReinspectionResult(item, false)">复验不通过</el-button></template>
          </template>
        </div>
      </el-card>
    </div>
    <AppEmpty v-else-if="!loading && !errorMessage" description="当前没有需要处理的问题工单" />

    <el-drawer v-model="actionVisible" title="更新处置进度" direction="btt" size="82%">
      <template v-if="selected">
        <el-descriptions :column="1" border><el-descriptions-item label="工单编号">{{ selected.reportCode }}</el-descriptions-item><el-descriptions-item label="目标阶段">{{ targetStatus === 'RESOLVED' ? '整改完成，等待复查复验' : targetStatus }}</el-descriptions-item><el-descriptions-item label="问题位置">{{ selected.locationText || '未填写' }}</el-descriptions-item></el-descriptions>
        <el-form label-position="top" class="action-form">
          <el-form-item label="处置说明（必填）"><el-input v-model="handlingSummary" type="textarea" :rows="4" maxlength="2000" show-word-limit placeholder="填写核查情况、整改措施和整改结果" /></el-form-item>
          <el-form-item v-if="targetStatus === 'RESOLVED'" label="整改证据（至少 1 张）"><div class="evidence-block"><label class="evidence-upload"><input type="file" accept="image/jpeg,image/png,image/webp" :disabled="uploadingEvidence" @change="uploadRectificationEvidence" /><span>{{ uploadingEvidence ? '上传中…' : '+ 上传整改后照片' }}</span></label><div v-if="rectificationEvidence.length" class="evidence-list"><span v-for="asset in rectificationEvidence" :key="asset.assetId">{{ asset.originalFilename }}</span></div><small v-else>整改完成不能只填文字，请至少上传一张整改证据。</small></div></el-form-item>
          <el-form-item label="向群众公开的进度说明（可选）"><el-input v-model="publicMessage" type="textarea" :rows="3" maxlength="1000" show-word-limit :placeholder="targetStatus === 'RESOLVED' ? '例如：整改已完成，现已安排现场复查复验。' : '填写后将在群众查询进度时展示'" /></el-form-item>
          <el-button type="primary" size="large" :loading="saving" class="submit-action" @click="submitAction">{{ targetStatus === 'RESOLVED' ? '提交整改并进入待复验' : '确认更新' }}</el-button>
        </el-form>
      </template>
    </el-drawer>

    <el-dialog v-model="resultVisible" :title="resultPassed ? '确认复验通过' : '确认复验不通过'" width="min(92vw, 520px)">
      <el-alert :title="resultPassed ? '通过后工单将正式关闭' : '不通过后工单将退回继续整改'" :type="resultPassed ? 'success' : 'warning'" :closable="false" show-icon />
      <el-input v-model="reinspectionSummary" type="textarea" :rows="5" maxlength="2000" show-word-limit class="result-summary" placeholder="填写复查位置、现场结果、是否仍存在原问题等复验依据" />
      <template #footer><el-button @click="resultVisible = false">取消</el-button><el-button :type="resultPassed ? 'success' : 'warning'" :loading="saving" @click="submitReinspectionResult">{{ resultPassed ? '确认通过并关闭' : '确认不通过并退回整改' }}</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped lang="scss">
.mobile-page { display: grid; gap: 16px; }.page-head { display: flex; justify-content: space-between; align-items: flex-end; gap: 12px; }.page-head p { margin: 0; color: #287a6a; font-weight: 700; }.page-head h1 { margin: 4px 0; font-size: 28px; }.page-head span { color: #667085; line-height: 1.5; }.status-filter { width: 100%; overflow-x: auto; }.work-list { display: grid; gap: 12px; }.work-card { border-radius: 16px; }.work-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; }.work-head > div { display: grid; gap: 5px; }.work-head span { color: #667085; font-size: 13px; }.description { line-height: 1.7; color: #344054; }dl { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }dl div { padding: 10px; border-radius: 10px; background: #f8faf9; }dt { color: #667085; font-size: 12px; }dd { margin: 4px 0 0; overflow-wrap: anywhere; color: #173f37; }.summary { padding: 12px; border-radius: 10px; background: #fff8e8; color: #7a5b00; line-height: 1.6; }.reinspection-state,.closed-state { display: grid; gap: 4px; padding: 12px; border-radius: 12px; }.reinspection-state { background: #eef7ff; color: #245b89; }.closed-state { background: #edf8f3; color: #176354; }.reinspection-state span,.closed-state span { font-size: 12px; line-height: 1.55; }.actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 12px; }.action-form { margin-top: 16px; }.submit-action { width: 100%; }.evidence-block { display: grid; gap: 10px; width: 100%; }.evidence-upload { display: flex; align-items: center; justify-content: center; min-height: 52px; border: 1px dashed #8eb9ae; border-radius: 12px; background: #f4faf8; color: #176354; font-weight: 700; cursor: pointer; }.evidence-upload input { display: none; }.evidence-list { display: grid; gap: 6px; }.evidence-list span { padding: 8px 10px; border-radius: 8px; background: #f8faf9; overflow-wrap: anywhere; font-size: 12px; }.evidence-block small { color: #b54708; line-height: 1.5; }.result-summary { margin-top: 16px; }@media (max-width: 480px) { dl { grid-template-columns: 1fr; } }
</style>
