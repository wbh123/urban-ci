<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  listFeedbackReports,
  updateFeedbackStatus,
  toAppError,
  type FeedbackManagementRow,
  type FeedbackStatus,
} from '@/shared/api'
import AppLoading from '@/shared/components/AppLoading.vue'
import AppEmpty from '@/shared/components/AppEmpty.vue'
import AppError from '@/shared/components/AppError.vue'
import AppStatusTag from '@/shared/components/AppStatusTag.vue'

const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const reports = ref<FeedbackManagementRow[]>([])
const statusFilter = ref<FeedbackStatus | 'ALL'>('ALL')
const selected = ref<FeedbackManagementRow | null>(null)
const actionVisible = ref(false)
const targetStatus = ref<FeedbackStatus>('ACCEPTED')
const handlingSummary = ref('')
const publicMessage = ref('')

const actionableStatuses: FeedbackStatus[] = [
  'SUBMITTED',
  'ACCEPTED',
  'PROCESSING',
  'NEED_MORE_INFO',
  'RESOLVED',
]

const filtered = computed(() =>
  statusFilter.value === 'ALL'
    ? reports.value
    : reports.value.filter((item) => item.status === statusFilter.value),
)

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
      { label: '完成处置', status: 'RESOLVED', type: 'success' },
      { label: '要求补充材料', status: 'NEED_MORE_INFO', type: 'warning' },
    ]
  }
  if (status === 'NEED_MORE_INFO') return [{ label: '恢复处置', status: 'PROCESSING', type: 'primary' }]
  if (status === 'RESOLVED') return [{ label: '重新处理', status: 'PROCESSING', type: 'warning' }]
  return []
}

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const page = await listFeedbackReports({ page: 0, size: 100 })
    reports.value = (page.content ?? []).filter((item) => actionableStatuses.includes(item.status))
  } catch (error) {
    errorMessage.value = toAppError(error).message
  } finally {
    loading.value = false
  }
}

function openAction(report: FeedbackManagementRow, status: FeedbackStatus): void {
  selected.value = report
  targetStatus.value = status
  handlingSummary.value = report.handlingSummary || ''
  publicMessage.value = ''
  actionVisible.value = true
}

async function submitAction(): Promise<void> {
  if (!selected.value) return
  if (!handlingSummary.value.trim()) {
    ElMessage.warning('请填写处置说明')
    return
  }
  saving.value = true
  try {
    await updateFeedbackStatus(selected.value.reportId, {
      status: targetStatus.value,
      handlingSummary: handlingSummary.value.trim(),
      message: publicMessage.value.trim() || undefined,
      publicVisible: Boolean(publicMessage.value.trim()),
    })
    ElMessage.success('处置状态已更新')
    actionVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(toAppError(error).message)
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="mobile-page">
    <header class="page-head">
      <div>
        <p>整改闭环</p>
        <h1>问题处置</h1>
        <span>处理群众反馈形成的问题线索，记录受理、处置和办结过程。</span>
      </div>
      <el-button @click="load">刷新</el-button>
    </header>

    <el-segmented
      v-model="statusFilter"
      :options="[
        { label: '全部', value: 'ALL' },
        { label: '待受理', value: 'SUBMITTED' },
        { label: '待开始', value: 'ACCEPTED' },
        { label: '处理中', value: 'PROCESSING' },
        { label: '待补充', value: 'NEED_MORE_INFO' },
        { label: '已处置', value: 'RESOLVED' },
      ]"
      class="status-filter"
    />

    <AppLoading :visible="loading" inline text="加载处置工单中…" />
    <AppError v-if="errorMessage" :message="errorMessage" @retry="load" />

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
          <div><dt>现场图片</dt><dd>{{ item.imageCount }} 张</dd></div>
        </dl>
        <p v-if="item.handlingSummary" class="summary"><strong>当前处置说明：</strong>{{ item.handlingSummary }}</p>
        <div class="actions">
          <el-button
            v-for="action in nextActions(item.status)"
            :key="action.status"
            :type="action.type"
            @click="openAction(item, action.status)"
          >{{ action.label }}</el-button>
        </div>
      </el-card>
    </div>
    <AppEmpty v-else-if="!loading && !errorMessage" description="当前没有需要处理的问题工单" />

    <el-drawer v-model="actionVisible" title="更新处置进度" direction="btt" size="78%">
      <template v-if="selected">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="工单编号">{{ selected.reportCode }}</el-descriptions-item>
          <el-descriptions-item label="目标状态">{{ targetStatus }}</el-descriptions-item>
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
              placeholder="填写核查情况、处置措施、结果或需要补充的材料"
            />
          </el-form-item>
          <el-form-item label="向群众公开的进度说明（可选）">
            <el-input
              v-model="publicMessage"
              type="textarea"
              :rows="3"
              maxlength="1000"
              show-word-limit
              placeholder="填写后将在群众查询进度时展示"
            />
          </el-form-item>
          <el-button type="primary" size="large" :loading="saving" class="submit-action" @click="submitAction">
            确认更新
          </el-button>
        </el-form>
      </template>
    </el-drawer>
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
.actions { display: flex; flex-wrap: wrap; gap: 8px; }
.action-form { margin-top: 16px; }
.submit-action { width: 100%; }
@media (max-width: 480px) { dl { grid-template-columns: 1fr; } }
</style>
