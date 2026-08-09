<script setup lang="ts">
import type { BuildingLifecycleNode, BuildingLifecycleStatus } from './building-lifecycle'

defineProps<{ nodes: BuildingLifecycleNode[] }>()

const STATUS_LABELS: Record<BuildingLifecycleStatus, string> = {
  NOT_STARTED: '未开始',
  PENDING: '待处理',
  IN_PROGRESS: '进行中',
  COMPLETED: '已完成',
  ATTENTION: '需关注',
  STALE: '已过期',
}

const STATUS_TYPES: Record<BuildingLifecycleStatus, 'success' | 'warning' | 'danger' | 'info' | 'primary'> = {
  NOT_STARTED: 'info',
  PENDING: 'warning',
  IN_PROGRESS: 'primary',
  COMPLETED: 'success',
  ATTENTION: 'danger',
  STALE: 'warning',
}
</script>

<template>
  <el-card shadow="never" class="lifecycle-card">
    <template #header><strong>楼栋业务生命周期</strong></template>
    <el-timeline class="lifecycle-timeline">
      <el-timeline-item
        v-for="node in nodes"
        :key="node.stage"
        :timestamp="node.updatedAt"
        placement="top"
      >
        <div class="node-head">
          <strong>{{ node.label }}</strong>
          <div class="node-tags">
            <el-tag v-if="node.count > 0" size="small" effect="plain">{{ node.count }} 项</el-tag>
            <el-tag size="small" :type="STATUS_TYPES[node.status]">{{ STATUS_LABELS[node.status] }}</el-tag>
          </div>
        </div>
        <p>{{ node.description }}</p>
      </el-timeline-item>
    </el-timeline>
  </el-card>
</template>

<style scoped lang="scss">
.lifecycle-card{min-width:0}.lifecycle-timeline{padding-top:4px}.node-head{display:flex;align-items:center;justify-content:space-between;gap:var(--usp-space-3)}.node-tags{display:flex;gap:6px;flex-wrap:wrap;justify-content:flex-end}.node-head strong{font-size:15px}.lifecycle-timeline p{margin:6px 0 0;color:var(--usp-color-text-secondary);line-height:1.6}@media(max-width:640px){.node-head{align-items:flex-start;flex-direction:column}.node-tags{justify-content:flex-start}}
</style>
