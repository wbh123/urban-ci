<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as api from '@/shared/api'
import { toAppError, type InspectionTask, type InspectionTaskStatus } from '@/shared/api'
import AppLoading from '@/shared/components/AppLoading.vue'
import AppEmpty from '@/shared/components/AppEmpty.vue'
import AppError from '@/shared/components/AppError.vue'
import AppStatusTag from '@/shared/components/AppStatusTag.vue'

const router = useRouter()
const tasks = ref<InspectionTask[]>([])
const loading = ref(false)
const errorMessage = ref('')
const status = ref<InspectionTaskStatus | 'ALL'>('ALL')
const statusOptions = ['ALL', 'PENDING', 'IN_PROGRESS', 'COMPLETED'] as const

const filteredTasks = computed(() => status.value === 'ALL' ? tasks.value : tasks.value.filter((task) => task.status === status.value))
function selectStatus(value: (typeof statusOptions)[number]): void { status.value = value }
function inspectionTypeLabel(value?: string | null): string {
  if (value === 'ROUTINE') return '日常巡检'
  if (value === 'SPECIAL') return '专项巡检'
  if (value === 'REINSPECTION') return '复查复验'
  return value || '其他巡检'
}
async function loadTasks(): Promise<void> {
  loading.value = true; errorMessage.value = ''
  try { tasks.value = await api.listInspectionTasks() }
  catch (error) { errorMessage.value = toAppError(error).message }
  finally { loading.value = false }
}
onMounted(loadTasks)
</script>

<template>
  <section class="mobile-page">
    <header class="page-head"><div><p>现场采集</p><h1>我的巡检任务</h1></div><button type="button" @click="loadTasks">刷新</button></header>
    <div class="filters" role="group" aria-label="任务状态筛选">
      <button v-for="item in statusOptions" :key="item" type="button" :class="{ active: status === item }" @click="selectStatus(item)">{{ item === 'ALL' ? '全部' : item }}</button>
    </div>
    <AppLoading :visible="loading" inline text="加载任务中…" />
    <AppError v-if="errorMessage" :message="errorMessage" @retry="loadTasks" />
    <div v-if="!loading && !errorMessage" class="task-list">
      <button v-for="task in filteredTasks" :key="task.taskId" type="button" @click="router.push(`/mobile/tasks/${task.taskId}`)">
        <div><strong>{{ task.title || task.taskCode }}</strong><span>{{ task.buildingName || task.buildingId }}</span></div>
        <AppStatusTag :status="task.status" variant="task" />
        <small>{{ task.taskCode }} · {{ inspectionTypeLabel(task.inspectionType) }}</small>
      </button>
      <AppEmpty v-if="!filteredTasks.length" description="当前筛选条件下暂无任务" />
    </div>
    <p class="scope-note">任务范围由后端根据账号和分配关系控制；前端不自行扩大数据范围。</p>
  </section>
</template>

<style scoped lang="scss">
.mobile-page { display: grid; gap: 14px; }
.page-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 12px; }
.page-head p { margin: 0; color: #287a6a; font-size: 13px; font-weight: 700; }
.page-head h1 { margin: 4px 0 0; font-size: 26px; }
.page-head button { min-width: 64px; min-height: 44px; border: 1px solid #cddbd6; border-radius: 12px; background: #fff; color: #176354; }
.filters { display: flex; gap: 8px; overflow-x: auto; padding-bottom: 2px; }
.filters button { flex: 0 0 auto; min-height: 40px; padding: 0 14px; border: 1px solid #dbe4e1; border-radius: 999px; background: #fff; color: #667085; }
.filters button.active { border-color: #176354; background: #e8f5f1; color: #176354; font-weight: 700; }
.task-list { display: grid; gap: 12px; }
.task-list > button { min-height: 112px; display: grid; grid-template-columns: 1fr auto; gap: 10px; padding: 16px; border: 1px solid #dde6e3; border-radius: 17px; background: #fff; text-align: left; color: inherit; }
.task-list div { display: grid; gap: 5px; }.task-list strong { font-size: 17px; }.task-list span, .task-list small { color: #667085; }.task-list small { grid-column: 1 / -1; }
.scope-note { margin: 0; padding: 12px; border-radius: 12px; background: #eef3f7; color: #667085; font-size: 12px; line-height: 1.5; }
</style>
