<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import AiWorkbenchAttention from '@/components/workbench/AiWorkbenchAttention.vue'
import AiWorkbenchBrief from '@/components/workbench/AiWorkbenchBrief.vue'
import WorkbenchAiDataWall from '@/components/workbench/ai-wall/WorkbenchAiDataWall.vue'
import WorkbenchMapPanel from '@/components/workbench/WorkbenchMapPanel.vue'
import WorkbenchMetricCard from '@/components/workbench/WorkbenchMetricCard.vue'
import WorkbenchRiskSnapshot from '@/components/workbench/WorkbenchRiskSnapshot.vue'
import WorkbenchTodoPanel from '@/components/workbench/WorkbenchTodoPanel.vue'
import WorkbenchTrendPanel from '@/components/workbench/WorkbenchTrendPanel.vue'
import {
  getAiDashboardOverview,
  type AiDashboardOverview,
} from '@/shared/api/endpoints/ai-dashboard'
import { getRiskOverview, type RiskOverview } from '@/shared/api/endpoints/reports'
import AppPageHeader from '@/shared/components/layout/AppPageHeader.vue'
import { useAuthStore } from '@/stores/auth'
import { resolveWorkspaceConfig, type WorkspaceTodo } from './workbench-config'

type DashboardMode = 'WORKBENCH' | 'WALL'

const authStore = useAuthStore()
const router = useRouter()
const dashboardMode = ref<DashboardMode>('WORKBENCH')

const workspace = computed(() => resolveWorkspaceConfig(
  authStore.user?.roles ?? [],
  authStore.user?.permissions ?? [],
))

const riskOverview = ref<RiskOverview | null>(null)
const riskOverviewLoading = ref(false)
const riskOverviewError = ref(false)
const aiOverview = ref<AiDashboardOverview | null>(null)
const aiOverviewLoading = ref(false)
const aiOverviewError = ref(false)

const canLoadRiskOverview = computed(() => (
  workspace.value.enableRisk
  && (
    workspace.value.role === 'ADMIN'
    || workspace.value.role === 'GOVERNMENT_MANAGER'
  )
))
const canLoadAiDashboard = computed(() => (
  workspace.value.enableRisk
  && (
    workspace.value.role === 'ADMIN'
    || workspace.value.role === 'GOVERNMENT_MANAGER'
  )
))
const canReviewFromWorkbench = computed(() => workspace.value.role === 'ADMIN')
const canManageInspectionFromWorkbench = computed(() => workspace.value.role === 'ADMIN')
const pageTitle = computed(() => canLoadAiDashboard.value ? 'AI 工作台' : workspace.value.title)
const pageDescription = computed(() => canLoadAiDashboard.value
  ? '汇总今天发生的安全事件、AI 辅助发现与需要人工接手的事项，再进入原有风险、地图和业务工作区。'
  : workspace.value.description)

async function loadRiskOverview(): Promise<void> {
  riskOverview.value = null
  riskOverviewError.value = false

  if (!canLoadRiskOverview.value) {
    riskOverviewLoading.value = false
    dashboardMode.value = 'WORKBENCH'
    return
  }

  riskOverviewLoading.value = true
  try {
    riskOverview.value = await getRiskOverview('ALL')
  } catch {
    riskOverviewError.value = true
  } finally {
    riskOverviewLoading.value = false
  }
}

async function loadAiOverview(): Promise<void> {
  aiOverview.value = null
  aiOverviewError.value = false
  if (!canLoadAiDashboard.value) {
    aiOverviewLoading.value = false
    return
  }

  aiOverviewLoading.value = true
  try {
    aiOverview.value = await getAiDashboardOverview()
  } catch {
    aiOverviewError.value = true
  } finally {
    aiOverviewLoading.value = false
  }
}

watch(
  () => [workspace.value.role, workspace.value.enableRisk] as const,
  () => {
    void loadRiskOverview()
    void loadAiOverview()
  },
  { immediate: true },
)

function openTodo(todo: WorkspaceTodo): void {
  void router.push(todo.path)
}

function setDashboardMode(mode: DashboardMode): void {
  if (mode === 'WALL' && !canLoadRiskOverview.value) return
  dashboardMode.value = mode
}

function openBuilding(buildingId: string): void {
  void router.push(`/console/buildings/${encodeURIComponent(buildingId)}`)
}
</script>

<template>
  <section class="dashboard-page" :class="{ 'dashboard-page--wall': dashboardMode === 'WALL' }">
    <AppPageHeader :title="pageTitle" :description="pageDescription">
      <template #actions>
        <div v-if="canLoadRiskOverview" class="dashboard-mode-switch" aria-label="首页显示模式">
          <button type="button" :class="{ active: dashboardMode === 'WORKBENCH' }" @click="setDashboardMode('WORKBENCH')">AI 工作台</button>
          <button type="button" :class="{ active: dashboardMode === 'WALL' }" @click="setDashboardMode('WALL')">AI 态势大屏</button>
        </div>
      </template>
    </AppPageHeader>

    <WorkbenchAiDataWall
      v-if="dashboardMode === 'WALL'"
      :overview="riskOverview"
      :loading="riskOverviewLoading"
      :error="riskOverviewError"
      :ai-overview="aiOverview"
      :ai-loading="aiOverviewLoading"
      :ai-error="aiOverviewError"
      :can-review="canReviewFromWorkbench"
      :can-manage-inspection="canManageInspectionFromWorkbench"
      @open-map="router.push('/console/map')"
      @open-risk="router.push('/console/renewal-priorities')"
      @open-building="openBuilding"
      @open-review="router.push('/console/review?status=PENDING')"
      @open-inspection="router.push('/console/inspections')"
    />

    <template v-else>
      <AiWorkbenchBrief
        v-if="canLoadAiDashboard && aiOverview"
        :overview="aiOverview"
        @open-wall="setDashboardMode('WALL')"
        @open-building="openBuilding"
      />

      <AiWorkbenchAttention
        v-if="canLoadAiDashboard && aiOverview"
        :overview="aiOverview"
        :can-review="canReviewFromWorkbench"
        :can-manage-inspection="canManageInspectionFromWorkbench"
        @open-review="router.push('/console/review?status=PENDING')"
        @open-risk="router.push('/console/renewal-priorities')"
        @open-inspection="router.push('/console/inspections?status=IN_PROGRESS')"
        @open-archive="router.push('/console/archive-management')"
      />

      <WorkbenchRiskSnapshot
        v-if="canLoadRiskOverview"
        :overview="riskOverview"
        :loading="riskOverviewLoading"
        :error="riskOverviewError"
        @open="router.push('/console/renewal-priorities')"
      />

      <div class="metric-grid" aria-label="当前职责业务模块">
        <WorkbenchMetricCard
          v-for="metric in workspace.metrics"
          :key="metric.key"
          :kind="metric.key"
          :label="metric.label"
          :description="metric.description"
          @open="router.push(metric.path)"
        />
      </div>

      <div class="dashboard-main">
        <WorkbenchMapPanel
          :mode="workspace.mapMode"
          :enable-risk="workspace.enableRisk"
          :high-risk-count="riskOverview?.summary.highRiskCount"
          :review-count="riskOverview?.summary.lowConfidenceCount"
          @open="router.push('/console/map')"
        />
        <WorkbenchTodoPanel :items="workspace.todos" @select="openTodo" />
      </div>

      <WorkbenchTrendPanel
        :title="workspace.trendTitle"
        :enable-risk="workspace.enableRisk"
        :risk-distribution="riskOverview?.riskDistribution ?? []"
      />
    </template>
  </section>
</template>

<style scoped lang="scss">
.dashboard-page { display: grid; gap: var(--usp-space-4); }
.dashboard-page--wall { gap: var(--usp-space-3); }
.dashboard-mode-switch { display: inline-flex; padding: 3px; border: 1px solid var(--usp-color-border); border-radius: 999px; background: var(--usp-color-surface-muted); }
.dashboard-mode-switch button { min-width: 88px; height: 30px; padding: 0 12px; border: 0; border-radius: 999px; background: transparent; color: var(--usp-color-text-secondary); font-size: 12px; font-weight: 700; transition: background .16s ease, color .16s ease, box-shadow .16s ease; }
.dashboard-mode-switch button.active { background: var(--usp-color-surface); color: var(--usp-color-primary-strong); box-shadow: var(--usp-shadow-sm); }
.metric-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(210px, 1fr)); gap: var(--usp-space-3); }
.dashboard-main { display: grid; grid-template-columns: minmax(0, 1.15fr) minmax(320px, .85fr); gap: var(--usp-space-4); align-items: stretch; }
@media (max-width: 1080px) { .dashboard-main { grid-template-columns: 1fr; } }
@media (max-width: 720px) { .metric-grid { grid-template-columns: 1fr; } }
</style>
