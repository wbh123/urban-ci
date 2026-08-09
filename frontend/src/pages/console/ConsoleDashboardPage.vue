<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import WorkbenchMapPanel from '@/components/workbench/WorkbenchMapPanel.vue'
import WorkbenchMetricCard from '@/components/workbench/WorkbenchMetricCard.vue'
import WorkbenchTodoPanel from '@/components/workbench/WorkbenchTodoPanel.vue'
import WorkbenchTrendPanel from '@/components/workbench/WorkbenchTrendPanel.vue'
import AppPageHeader from '@/shared/components/layout/AppPageHeader.vue'
import { useAuthStore } from '@/stores/auth'
import { resolveWorkspaceConfig, type WorkspaceTodo } from './workbench-config'

const authStore = useAuthStore()
const router = useRouter()

const workspace = computed(() => resolveWorkspaceConfig(
  authStore.user?.roles ?? [],
  authStore.user?.permissions ?? [],
))

function openTodo(todo: WorkspaceTodo): void {
  void router.push(todo.path)
}
</script>

<template>
  <section class="dashboard-page">
    <AppPageHeader
      :title="workspace.title"
      :description="workspace.description"
    />

    <div class="metric-grid" aria-label="当前职责业务模块">
      <WorkbenchMetricCard
        v-for="metric in workspace.metrics"
        :key="metric.key"
        :label="metric.label"
        :description="metric.description"
        @open="router.push(metric.path)"
      />
    </div>

    <div class="dashboard-main">
      <WorkbenchMapPanel
        :mode="workspace.mapMode"
        :enable-risk="workspace.enableRisk"
        @open="router.push('/console/map')"
      />
      <WorkbenchTodoPanel :items="workspace.todos" @select="openTodo" />
    </div>

    <WorkbenchTrendPanel
      :title="workspace.trendTitle"
      :enable-risk="workspace.enableRisk"
    />
  </section>
</template>

<style scoped lang="scss">
.dashboard-page {
  display: grid;
  gap: var(--usp-space-5);
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
  gap: var(--usp-space-3);
}

.dashboard-main {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(320px, .8fr);
  gap: var(--usp-space-4);
  align-items: stretch;
}

@media (max-width: 1080px) {
  .dashboard-main {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .metric-grid {
    grid-template-columns: 1fr;
  }
}
</style>
