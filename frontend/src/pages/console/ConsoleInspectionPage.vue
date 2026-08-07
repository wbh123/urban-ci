<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import * as api from '@/shared/api'
import {
  toAppError,
  type BuildingListRow,
  type CommunityListRow,
  type InspectionTask,
  type InspectionType,
} from '@/shared/api'
import AppLoading from '@/shared/components/AppLoading.vue'
import AppEmpty from '@/shared/components/AppEmpty.vue'
import AppError from '@/shared/components/AppError.vue'
import AppStatusTag from '@/shared/components/AppStatusTag.vue'

const communities = ref<CommunityListRow[]>([])
const buildings = ref<BuildingListRow[]>([])
const tasks = ref<InspectionTask[]>([])
const selectedCommunity = ref('')
const selectedBuilding = ref('')
const title = ref('现场安全巡检')
const inspectionType = ref<InspectionType>('ROUTINE')
const loading = ref(false)
const buildingLoading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const notice = ref('')

const selectedCommunityName = computed(
  () => communities.value.find((item) => item.id === selectedCommunity.value)?.communityName ?? '未选择',
)

async function loadTasks(): Promise<void> {
  tasks.value = selectedBuilding.value
    ? await api.listInspectionTasks({ buildingId: selectedBuilding.value })
    : []
}

async function loadBuildings(): Promise<void> {
  buildings.value = []
  tasks.value = []
  selectedBuilding.value = ''
  if (!selectedCommunity.value) return
  buildingLoading.value = true
  try {
    const page = await api.listBuildings({ communityId: selectedCommunity.value, size: 100 })
    buildings.value = page.content ?? []
    selectedBuilding.value = buildings.value[0]?.id ?? ''
    await loadTasks()
    if (!buildings.value.length) notice.value = '当前小区没有可用楼栋，请先维护楼栋档案。'
  } catch (error) {
    notice.value = toAppError(error).message
  } finally {
    buildingLoading.value = false
  }
}

async function handleCommunityChange(): Promise<void> {
  notice.value = ''
  await loadBuildings()
}

async function handleBuildingChange(): Promise<void> {
  notice.value = ''
  await loadTasks()
}

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  notice.value = ''
  try {
    const page = await api.listCommunities({ status: 'ACTIVE', size: 100 })
    communities.value = page.content ?? []
    selectedCommunity.value = communities.value.some((item) => item.id === selectedCommunity.value)
      ? selectedCommunity.value
      : communities.value[0]?.id ?? ''
    await loadBuildings()
  } catch (error) {
    errorMessage.value = toAppError(error).message
  } finally {
    loading.value = false
  }
}

async function createTask(): Promise<void> {
  if (!selectedBuilding.value || !title.value.trim()) {
    notice.value = '请选择楼栋并填写任务标题。'
    return
  }
  saving.value = true
  try {
    const task = await api.createInspectionTask({
      buildingId: selectedBuilding.value,
      inspectionType: inspectionType.value,
      title: title.value.trim(),
    })
    notice.value = `任务已创建：${task.taskCode}`
    await loadTasks()
  } catch (error) {
    notice.value = toAppError(error).message
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="inspection-page">
    <header class="page-head">
      <div>
        <p class="eyebrow">Community Management</p>
        <h1>巡检组织管理</h1>
        <span>{{ selectedCommunityName }}</span>
      </div>
      <el-button @click="load">刷新</el-button>
    </header>
    <AppLoading :visible="loading" inline text="加载巡检数据中…" />
    <AppError v-if="errorMessage" :message="errorMessage" @retry="load" />
    <template v-if="!loading && !errorMessage">
      <el-row :gutter="18">
        <el-col :xs="24" :lg="9">
          <el-card shadow="never">
            <template #header><strong>创建巡检任务</strong></template>
            <el-form label-position="top">
              <el-form-item label="小区">
                <el-select
                  v-model="selectedCommunity"
                  style="width:100%"
                  filterable
                  placeholder="请选择有权访问的小区"
                  @change="handleCommunityChange"
                >
                  <el-option
                    v-for="item in communities"
                    :key="item.id"
                    :label="item.communityName"
                    :value="item.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="楼栋">
                <el-select
                  v-model="selectedBuilding"
                  style="width:100%"
                  filterable
                  :loading="buildingLoading"
                  :disabled="!selectedCommunity || buildingLoading"
                  placeholder="请选择楼栋"
                  @change="handleBuildingChange"
                >
                  <el-option
                    v-for="item in buildings"
                    :key="item.id"
                    :label="item.buildingName || item.buildingCode"
                    :value="item.id"
                  />
                </el-select>
              </el-form-item>
              <el-alert
                v-if="selectedCommunity && !buildingLoading && !buildings.length"
                title="当前小区暂无可选择楼栋"
                description="请先在基础档案中创建楼栋，或检查当前账号的小区授权范围。"
                type="warning"
                :closable="false"
                show-icon
                class="empty-building"
              />
              <el-form-item label="任务标题"><el-input v-model="title" /></el-form-item>
              <el-form-item label="巡检类型">
                <el-radio-group v-model="inspectionType">
                  <el-radio-button value="ROUTINE">日常巡检</el-radio-button>
                  <el-radio-button value="SPECIAL">专项巡检</el-radio-button>
                </el-radio-group>
              </el-form-item>
              <el-button type="primary" :loading="saving" :disabled="!selectedBuilding" @click="createTask">
                创建并下发
              </el-button>
            </el-form>
            <el-alert v-if="notice" :title="notice" type="info" :closable="false" show-icon class="notice" />
          </el-card>
        </el-col>
        <el-col :xs="24" :lg="15">
          <el-card shadow="never">
            <template #header><div class="card-head"><strong>楼栋巡检任务</strong><span>{{ tasks.length }} 条</span></div></template>
            <el-table v-if="tasks.length" :data="tasks" stripe>
              <el-table-column prop="taskCode" label="任务编号" width="180" />
              <el-table-column prop="title" label="任务标题" min-width="180" />
              <el-table-column prop="buildingName" label="楼栋" min-width="120" />
              <el-table-column label="状态" width="120"><template #default="scope"><AppStatusTag :status="scope.row.status" variant="task" /></template></el-table-column>
            </el-table>
            <AppEmpty v-else description="当前楼栋暂无巡检任务" />
          </el-card>
        </el-col>
      </el-row>
    </template>
  </section>
</template>

<style scoped lang="scss">
.inspection-page { display: grid; gap: 18px; }
.page-head { display: flex; justify-content: space-between; align-items: flex-end; gap: 16px; }
.page-head h1 { margin: 4px 0; font-size: 32px; }
.page-head span { color: #667085; }
.eyebrow { margin: 0; color: #287a6a; font-size: 12px; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
.card-head span { color: #667085; }
.notice, .empty-building { margin-top: 16px; }
</style>
