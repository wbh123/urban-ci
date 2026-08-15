<script setup lang="ts">
import { computed } from 'vue'
import AiStatusBadge from './AiStatusBadge.vue'
import { aiTaskStatusLabel } from '@/shared/ai/ai-status'

const props = defineProps<{ status?: string | null; message?: string }>()
const copy = computed(() => props.message || aiTaskStatusLabel(props.status))
const running = computed(() => ['PENDING', 'RUNNING'].includes(props.status?.toUpperCase() ?? ''))
</script>

<template>
  <section class="ai-analysis-progress" :data-running="running">
    <div class="ai-analysis-progress__line"><span aria-hidden="true">✦ AI</span><strong>{{ copy }}</strong><AiStatusBadge :status="status" /></div>
    <div v-if="running" class="ai-analysis-progress__track"><span /></div>
  </section>
</template>

<style scoped lang="scss">
.ai-analysis-progress{display:grid;gap:7px;padding:10px 12px;border-radius:var(--usp-radius-lg);background:var(--usp-color-surface-muted)}.ai-analysis-progress__line{display:flex;align-items:center;gap:8px}.ai-analysis-progress__line>span{color:#176354;font-size:10px;font-weight:900}.ai-analysis-progress__line strong{min-width:0;flex:1;font-size:12px}.ai-analysis-progress__track{height:3px;overflow:hidden;border-radius:999px;background:#dce9e5}.ai-analysis-progress__track span{display:block;width:42%;height:100%;border-radius:inherit;background:#2d8c79;animation:ai-progress 1.3s ease-in-out infinite alternate}@keyframes ai-progress{from{transform:translateX(-15%)}to{transform:translateX(145%)}}
</style>
