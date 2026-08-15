<script setup lang="ts">
import { computed } from 'vue'
import type { AiDashboardActivity } from '@/shared/api/endpoints/ai-dashboard'
import AiActivityFeed from '@/shared/components/ai/AiActivityFeed.vue'

const props = defineProps<{
  activity: AiDashboardActivity | null
  loading?: boolean
}>()

const items = computed(() => (props.activity?.items ?? []).map((item) => ({
  id: item.id,
  time: formatTime(item.occurredAt),
  title: item.title,
  description: item.description || undefined,
  status: item.status || undefined,
})))

function formatTime(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false })
}
</script>

<template>
  <section class="activity-panel">
    <header><div><span>✦ AI</span><strong>AI 实时研判动态</strong></div><small>{{ loading ? '更新中' : '约 4 秒刷新' }}</small></header>
    <AiActivityFeed :items="items" empty-text="暂无新的 AI 研判动态" />
  </section>
</template>

<style scoped lang="scss">
.activity-panel{display:grid;gap:4px;padding:9px 12px;border:1px solid rgba(113,224,205,.13);border-radius:13px;background:rgba(5,28,31,.8);color:#effcf9;backdrop-filter:blur(14px)}.activity-panel>header{display:flex;align-items:center;justify-content:space-between;gap:10px}.activity-panel>header>div{display:flex;align-items:center;gap:6px}.activity-panel>header span{color:#8cf0d9;font-size:8px;font-weight:900}.activity-panel>header strong{font-size:11px}.activity-panel>header small{color:#729892;font-size:7px}.activity-panel :deep(.ai-activity-feed){display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:7px}.activity-panel :deep(.ai-activity-feed__item){grid-template-columns:42px minmax(0,1fr);padding:5px 7px;border:0;border-radius:8px;background:rgba(255,255,255,.035)}.activity-panel :deep(.ai-activity-feed time){color:#6f9790;font-size:8px}.activity-panel :deep(.ai-activity-feed__item strong){overflow:hidden;color:#dff7f1;font-size:8px;text-overflow:ellipsis;white-space:nowrap}.activity-panel :deep(.ai-activity-feed__item span){overflow:hidden;color:#82a7a1;font-size:7px;text-overflow:ellipsis;white-space:nowrap}@media(max-width:980px){.activity-panel :deep(.ai-activity-feed){grid-template-columns:1fr}}
</style>
