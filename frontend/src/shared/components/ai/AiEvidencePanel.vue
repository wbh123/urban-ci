<script setup lang="ts">
defineProps<{
  items: Array<{ key?: string; label: string; count?: number; description?: string }>
  title?: string
}>()
</script>

<template>
  <section class="ai-evidence-panel">
    <header><strong>{{ title || '分析依据' }}</strong><span>{{ items.length }} 类证据</span></header>
    <div class="ai-evidence-grid">
      <article v-for="(item, index) in items" :key="item.key || `${item.label}-${index}`">
        <span>{{ item.label }}</span><strong v-if="item.count != null">{{ item.count }}</strong><small v-if="item.description">{{ item.description }}</small>
      </article>
    </div>
  </section>
</template>

<style scoped lang="scss">
.ai-evidence-panel{display:grid;gap:9px}.ai-evidence-panel header{display:flex;align-items:center;justify-content:space-between;gap:10px}.ai-evidence-panel header span{color:var(--usp-color-text-secondary);font-size:11px}.ai-evidence-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(110px,1fr));gap:8px}.ai-evidence-grid article{display:grid;gap:2px;padding:9px 10px;border:1px solid var(--usp-color-border);border-radius:var(--usp-radius-lg);background:var(--usp-color-surface)}.ai-evidence-grid span,.ai-evidence-grid small{color:var(--usp-color-text-secondary);font-size:11px}.ai-evidence-grid strong{font-size:18px}
</style>
