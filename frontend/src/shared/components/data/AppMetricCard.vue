<script setup lang="ts">
withDefaults(
  defineProps<{
    label: string
    value: string | number
    unit?: string
    hint?: string
    tone?: 'default' | 'success' | 'warning' | 'danger' | 'info'
  }>(),
  {
    unit: '',
    hint: '',
    tone: 'default',
  },
)
</script>

<template>
  <article class="app-metric-card" :class="`is-${tone}`">
    <div class="app-metric-card__label">{{ label }}</div>
    <div class="app-metric-card__value">
      <strong>{{ value }}</strong>
      <span v-if="unit">{{ unit }}</span>
    </div>
    <div v-if="hint" class="app-metric-card__hint">{{ hint }}</div>
    <slot />
  </article>
</template>

<style scoped lang="scss">
.app-metric-card {
  position: relative;
  min-width: 0;
  padding: var(--usp-space-5);
  overflow: hidden;
  background: var(--usp-color-surface);
  border: 1px solid var(--usp-color-border);
  border-radius: var(--usp-radius-lg);
  box-shadow: var(--usp-shadow-sm);
}

.app-metric-card::before {
  position: absolute;
  inset: 0 auto 0 0;
  width: 3px;
  background: var(--usp-color-primary);
  content: '';
}

.app-metric-card__label {
  color: var(--usp-color-text-secondary);
  font-size: var(--usp-font-size-sm);
  font-weight: 600;
}

.app-metric-card__value {
  display: flex;
  align-items: baseline;
  gap: var(--usp-space-2);
  margin-top: var(--usp-space-2);
  color: var(--usp-color-text);
}

.app-metric-card__value strong {
  font-size: 30px;
  line-height: 1.2;
}

.app-metric-card__value span {
  color: var(--usp-color-text-secondary);
  font-size: var(--usp-font-size-sm);
}

.app-metric-card__hint {
  margin-top: var(--usp-space-2);
  color: var(--usp-color-text-tertiary);
  font-size: var(--usp-font-size-xs);
}

.app-metric-card.is-success::before {
  background: var(--usp-color-success);
}

.app-metric-card.is-warning::before {
  background: var(--usp-color-warning);
}

.app-metric-card.is-danger::before {
  background: var(--usp-color-danger);
}

.app-metric-card.is-info::before {
  background: var(--usp-color-info);
}
</style>
