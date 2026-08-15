<script setup lang="ts">
import { computed } from 'vue'
import { aiTaskStatusLabel, aiTaskStatusTone } from '@/shared/ai/ai-status'

const props = defineProps<{ status?: string | null; label?: string }>()
const text = computed(() => props.label || aiTaskStatusLabel(props.status))
const tone = computed(() => aiTaskStatusTone(props.status))
</script>

<template>
  <span class="ai-status-badge" :data-tone="tone"><i aria-hidden="true" />{{ text }}</span>
</template>

<style scoped lang="scss">
.ai-status-badge{display:inline-flex;align-items:center;gap:6px;width:fit-content;padding:4px 8px;border-radius:999px;background:var(--usp-color-surface-muted);color:var(--usp-color-text-secondary);font-size:11px;font-weight:700}.ai-status-badge i{width:6px;height:6px;border-radius:50%;background:#94a3b8}.ai-status-badge[data-tone='success']{background:#eefbf4;color:#067647}.ai-status-badge[data-tone='success'] i{background:#12b76a}.ai-status-badge[data-tone='warning']{background:#fffaeb;color:#b54708}.ai-status-badge[data-tone='warning'] i{background:#f79009}.ai-status-badge[data-tone='danger']{background:#fff3f2;color:#b42318}.ai-status-badge[data-tone='danger'] i{background:#f04438}
</style>
