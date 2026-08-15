<script setup lang="ts">
withDefaults(defineProps<{
  loading?: boolean
  showReset?: boolean
  queryLabel?: string
  resetLabel?: string
}>(), {
  loading: false,
  showReset: true,
  queryLabel: '查询',
  resetLabel: '重置',
})

const emit = defineEmits<{
  'query': []
  'reset': []
}>()
</script>

<template>
  <section class="app-filter-bar" aria-label="筛选条件">
    <div class="app-filter-bar__fields">
      <slot />
    </div>
    <div class="app-filter-bar__actions">
      <slot name="actions">
        <el-button v-if="showReset" @click="emit('reset')">{{ resetLabel }}</el-button>
        <el-button type="primary" :loading="loading" @click="emit('query')">{{ queryLabel }}</el-button>
      </slot>
    </div>
  </section>
</template>

<style scoped lang="scss">
.app-filter-bar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px 16px;
  padding: 14px;
  border: 1px solid var(--usp-color-border, #e4e7ec);
  border-radius: var(--usp-radius-xl);
  background: var(--usp-color-surface, #fff);
}

.app-filter-bar__fields {
  display: flex;
  flex: 1 1 auto;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 10px 12px;
  min-width: 0;
}

.app-filter-bar__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

.app-filter-bar :deep(.app-filter-field) {
  width: min(100%, var(--filter-field-width));
}

.app-filter-bar :deep(.el-input__wrapper),
.app-filter-bar :deep(.el-select__wrapper),
.app-filter-bar :deep(.el-date-editor.el-input__wrapper) {
  border-radius: var(--usp-radius-lg);
}

@media (max-width: 760px) {
  .app-filter-bar,
  .app-filter-bar__actions {
    width: 100%;
  }

  .app-filter-bar {
    align-items: stretch;
    flex-direction: column;
  }

  .app-filter-bar__actions {
    margin-left: 0;
  }

  .app-filter-bar__actions :deep(.el-button) {
    flex: 1;
  }
}
</style>
