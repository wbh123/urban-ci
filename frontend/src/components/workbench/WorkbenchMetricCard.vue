<script setup lang="ts">
defineProps<{
  label: string
  description: string
}>()

const emit = defineEmits<{
  open: []
}>()
</script>

<template>
  <button type="button" class="metric-card" @click="emit('open')">
    <span class="metric-kicker">工作模块</span>
    <strong>{{ label }}</strong>
    <span class="metric-description">{{ description }}</span>
    <span class="metric-action">查看业务 →</span>
  </button>
</template>

<style scoped lang="scss">
.metric-card {
  display: grid;
  min-height: 156px;
  gap: var(--usp-space-2);
  padding: var(--usp-space-4);
  border: 1px solid var(--usp-color-border);
  border-radius: var(--usp-radius-lg);
  background: var(--usp-color-surface);
  color: var(--usp-color-text-primary);
  text-align: left;
  cursor: pointer;
  transition: transform .16s ease, border-color .16s ease, box-shadow .16s ease;
}

.metric-card:hover {
  transform: translateY(-2px);
  border-color: var(--usp-color-primary);
  box-shadow: 0 10px 24px rgba(15, 23, 42, .08);
}

.metric-kicker,
.metric-description {
  color: var(--usp-color-text-secondary);
  font-size: 12px;
}

.metric-card strong {
  font-size: 20px;
}

.metric-description {
  line-height: 1.6;
}

.metric-action {
  align-self: end;
  color: var(--usp-color-primary);
  font-weight: 700;
  font-size: 13px;
}
</style>
