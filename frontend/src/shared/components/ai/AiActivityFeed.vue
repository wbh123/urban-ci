<script setup lang="ts">
defineProps<{
  items: Array<{
    id: string
    time?: string
    title: string
    description?: string
    status?: string
  }>
  emptyText?: string
}>()
</script>

<template>
  <div class="ai-activity-feed">
    <article v-for="item in items" :key="item.id" class="ai-activity-feed__item">
      <time>{{ item.time || '刚刚' }}</time>
      <div><strong>{{ item.title }}</strong><span v-if="item.description">{{ item.description }}</span></div>
    </article>
    <p v-if="items.length === 0" class="ai-activity-feed__empty">{{ emptyText || '暂无新的 AI 研判动态' }}</p>
  </div>
</template>

<style scoped lang="scss">
.ai-activity-feed{display:grid;gap:8px}.ai-activity-feed__item{display:grid;grid-template-columns:52px minmax(0,1fr);gap:10px;padding:8px 0;border-bottom:1px solid var(--usp-color-border)}.ai-activity-feed__item:last-child{border-bottom:0}.ai-activity-feed time{color:var(--usp-color-text-tertiary);font-size:11px;font-variant-numeric:tabular-nums}.ai-activity-feed__item div{display:grid;gap:2px}.ai-activity-feed__item strong{font-size:12px}.ai-activity-feed__item span,.ai-activity-feed__empty{color:var(--usp-color-text-secondary);font-size:11px;line-height:1.5}.ai-activity-feed__empty{margin:0;padding:12px;text-align:center}
</style>
