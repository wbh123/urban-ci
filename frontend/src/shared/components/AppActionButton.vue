<script setup lang="ts">
withDefaults(defineProps<{
  type?: 'primary' | 'success' | 'warning' | 'danger' | 'info' | 'default'
  disabled?: boolean
  loading?: boolean
}>(), {
  type: 'default',
  disabled: false,
  loading: false,
})
</script>

<template>
  <el-button
    size="small"
    :type="type === 'default' ? undefined : type"
    :disabled="disabled"
    :loading="loading"
  >
    <slot />
  </el-button>
</template>

<style scoped lang="scss">
:deep(.el-button) {
  border-radius: var(--usp-radius-md);
}
</style>
