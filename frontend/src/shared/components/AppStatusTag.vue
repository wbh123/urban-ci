<script setup lang="ts">
import { computed } from 'vue'

type TagType = '' | 'primary' | 'success' | 'info' | 'warning' | 'danger'

const props = withDefaults(
  defineProps<{
    status: string
    variant?: 'task' | 'severity' | 'risk' | 'health' | 'generic'
    label?: string
  }>(),
  {
    variant: 'generic',
    label: undefined,
  },
)

const TASK_MAP: Record<string, { type: TagType; text: string }> = {
  PENDING: { type: 'info', text: '待开始' },
  IN_PROGRESS: { type: 'warning', text: '进行中' },
  COMPLETED: { type: 'success', text: '已完成' },
  CANCELLED: { type: 'info', text: '已取消' },
}

const SEVERITY_MAP: Record<string, { type: TagType; text: string }> = {
  LOW: { type: 'info', text: '低' },
  MEDIUM: { type: 'warning', text: '中' },
  HIGH: { type: 'danger', text: '高' },
}

const RISK_MAP: Record<string, { type: TagType; text: string }> = {
  LOW: { type: 'success', text: '低风险' },
  MEDIUM: { type: 'warning', text: '中风险' },
  HIGH: { type: 'danger', text: '高风险' },
  CRITICAL: { type: 'danger', text: '重大风险' },
  NO_RESULT: { type: 'info', text: '暂无结果' },
}

const HEALTH_MAP: Record<string, { type: TagType; text: string }> = {
  UP: { type: 'success', text: '正常' },
  DEGRADED: { type: 'warning', text: '降级' },
  DOWN: { type: 'danger', text: '不可用' },
}

const info = computed<{ type: TagType; text: string }>(() => {
  const map =
    props.variant === 'task'
      ? TASK_MAP
      : props.variant === 'severity'
        ? SEVERITY_MAP
        : props.variant === 'risk'
          ? RISK_MAP
          : props.variant === 'health'
            ? HEALTH_MAP
            : null
  if (map && props.status in map) {
    return map[props.status]
  }
  return { type: 'info', text: props.label ?? props.status }
})
</script>

<template>
  <el-tag
    :type="info.type"
    size="small"
    effect="light"
  >
    {{ info.text }}
  </el-tag>
</template>
