<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as api from '@/shared/api'
import { toAppError, type AiInferenceStatus, type AiInferenceTask } from '@/shared/api'
import AppLoading from '@/shared/components/AppLoading.vue'
import AppEmpty from '@/shared/components/AppEmpty.vue'
import AppError from '@/shared/components/AppError.vue'
import AppStatusTag from '@/shared/components/AppStatusTag.vue'

const router = useRouter()
const tasks = ref<AiInferenceTask[]>([])
const loading = ref(false)
const errorMessage = ref('')
const status = ref<AiInferenceStatus | 'ALL'>('ALL')

const filtered = computed(() =>
  status.value === 'ALL' ? tasks.value : tasks.value.filter((item) => item.status === status.value),
)

function openTask(task: AiInferenceTask): void {
  void router.push(`/console/review/${task.inferenceId}`)
}

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await api.listAiInferences({ page: 0, size: 100 })
    tasks.value = response.content ?? []
  } catch (error) {
    errorMessage.value = toAppError(error).message
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="review-page">
    <header class="page-head">
      <div>
        <p class="eyebrow">Expert Review</p>
        <h1>专业复核队列</h1>
        <p>人工智能识别结果必须经过具备权限的专业人员复核。</p>
      </div>
      <el-button @click="load">刷新</el-button>
    </header>
    <el-segmented v-model="status" :options="['ALL', 'SUCCEEDED', 'FAILED', 'REJECTED']" />
    <AppLoading :visible="loading" inline text="加载复核队列中…" />
    <AppError v-if="errorMessage" :message="errorMessage" @retry="load" />
    <el-card v-if="!loading && !errorMessage" shadow="never">
      <el-table v-if="filtered.length" :data="filtered" stripe @row-click="openTask">
        <el-table-column prop="requestCode" label="请求编号" min-width="190" />
        <el-table-column label="模型" min-width="180">
          <template #default="scope">
            {{ scope.row.modelName }}<br><small>v{{ scope.row.modelVersion }} · {{ scope.row.mode }}</small>
          </template>
        </el-table-column>
        <el-table-column label="任务状态" width="120">
          <template #default="scope"><AppStatusTag :status="scope.row.status" variant="task" /></template>
        </el-table-column>
        <el-table-column prop="detectionCount" label="检测数量" width="100" />
        <el-table-column prop="reviewStatus" label="复核状态" width="130" />
        <el-table-column label="操作" width="100">
          <template #default="scope">
            <el-button link type="primary" @click.stop="openTask(scope.row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      <AppEmpty v-else description="暂无需要复核的推理任务" />
    </el-card>
  </section>
</template>

<style scoped lang="scss">
.review-page { display: grid; gap: 18px; }
.page-head { display: flex; justify-content: space-between; align-items: flex-end; gap: 16px; }
.page-head h1 { margin: 4px 0; font-size: 32px; }
.page-head p:last-child { margin: 0; color: #667085; }
.eyebrow { margin: 0; color: #287a6a; font-size: 12px; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
small { color: #667085; }
</style>
