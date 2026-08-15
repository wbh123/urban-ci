<script setup lang="ts">
defineProps<{
  title: string
  summary?: string
  suggestion?: string
  eyebrow?: string
  compact?: boolean
}>()
</script>

<template>
  <article class="ai-insight-card" :class="{ 'is-compact': compact }">
    <header><span>{{ eyebrow || '✦ AI' }}</span><strong>{{ title }}</strong></header>
    <p v-if="summary">{{ summary }}</p>
    <div v-if="suggestion" class="ai-insight-card__suggestion"><b>建议</b><span>{{ suggestion }}</span></div>
    <slot />
    <footer v-if="$slots.actions"><slot name="actions" /></footer>
  </article>
</template>

<style scoped lang="scss">
.ai-insight-card{display:grid;gap:10px;padding:16px;border:1px solid #cfe7df;border-radius:var(--usp-radius-xl);background:linear-gradient(145deg,#f8fcfb,#fff);box-shadow:var(--usp-shadow-sm)}.ai-insight-card.is-compact{gap:7px;padding:12px 14px}.ai-insight-card header{display:flex;align-items:center;gap:9px}.ai-insight-card header span{color:#176354;font-size:11px;font-weight:900;letter-spacing:.04em}.ai-insight-card header strong{font-size:15px}.ai-insight-card p{margin:0;color:var(--usp-color-text-secondary);line-height:1.65}.ai-insight-card__suggestion{display:grid;grid-template-columns:auto 1fr;gap:8px;padding:9px 11px;border-radius:var(--usp-radius-lg);background:#eef8f5;color:#344054}.ai-insight-card__suggestion b{color:#176354}.ai-insight-card footer{display:flex;justify-content:flex-end;gap:8px}
</style>
