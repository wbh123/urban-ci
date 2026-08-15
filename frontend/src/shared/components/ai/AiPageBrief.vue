<script setup lang="ts">
import { computed } from 'vue'

interface AiPageBriefMetric {
  label: string
  value: string | number
  tone?: 'normal' | 'attention' | 'danger' | 'muted'
}

const props = withDefaults(defineProps<{
  title: string
  metrics?: AiPageBriefMetric[]
  summary: string
  suggestion?: string
  empty?: boolean
}>(), {
  metrics: () => [],
  suggestion: '',
  empty: false,
})

const visibleMetrics = computed(() => props.metrics.slice(0, 4))
</script>

<template>
  <section v-if="!empty" class="ai-page-brief" aria-label="页面 AI 看板">
    <header class="ai-page-brief__header">
      <div>
        <span class="ai-page-brief__mark">✦ AI</span>
        <strong>{{ title }}</strong>
      </div>
      <slot name="actions" />
    </header>

    <div v-if="visibleMetrics.length" class="ai-page-brief__metrics">
      <article
        v-for="item in visibleMetrics"
        :key="item.label"
        :data-tone="item.tone || 'normal'"
      >
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
      </article>
    </div>

    <div class="ai-page-brief__copy">
      <p>{{ summary }}</p>
      <small v-if="suggestion">建议：{{ suggestion }}</small>
    </div>
  </section>
</template>

<style scoped lang="scss">
.ai-page-brief{display:grid;gap:10px;padding:13px 15px;border:1px solid color-mix(in srgb,var(--usp-color-primary) 18%,var(--usp-color-border));border-radius:var(--usp-radius-xl);background:linear-gradient(135deg,color-mix(in srgb,var(--usp-color-primary) 5%,var(--usp-color-surface)),var(--usp-color-surface));box-shadow:var(--usp-shadow-sm)}.ai-page-brief__header{display:flex;align-items:center;justify-content:space-between;gap:12px}.ai-page-brief__header>div{display:flex;align-items:center;gap:8px}.ai-page-brief__mark{color:var(--usp-color-primary-strong);font-size:12px;font-weight:900}.ai-page-brief__header strong{font-size:14px}.ai-page-brief__metrics{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:8px}.ai-page-brief__metrics article{display:grid;min-width:0;gap:4px;padding:9px 10px;border-radius:var(--usp-radius-lg);background:var(--usp-color-surface-muted)}.ai-page-brief__metrics span{overflow:hidden;color:var(--usp-color-text-secondary);font-size:10px;font-weight:700;text-overflow:ellipsis;white-space:nowrap}.ai-page-brief__metrics strong{font-size:18px;line-height:1.1}.ai-page-brief__metrics article[data-tone='attention'] strong{color:#a85f00}.ai-page-brief__metrics article[data-tone='danger'] strong{color:var(--usp-color-danger)}.ai-page-brief__metrics article[data-tone='muted'] strong{color:var(--usp-color-text-secondary)}.ai-page-brief__copy{display:grid;gap:3px}.ai-page-brief__copy p,.ai-page-brief__copy small{margin:0;line-height:1.55}.ai-page-brief__copy p{color:var(--usp-color-text-secondary);font-size:12px}.ai-page-brief__copy small{color:var(--usp-color-text-tertiary);font-size:11px}@media(max-width:900px){.ai-page-brief__metrics{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:520px){.ai-page-brief__metrics{grid-template-columns:1fr 1fr}.ai-page-brief__header{align-items:flex-start;flex-direction:column}}
</style>
