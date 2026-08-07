<script setup lang="ts">
withDefaults(
  defineProps<{
    description?: string
  }>(),
  {
    description: '暂无数据',
  },
)
</script>

<template>
  <el-empty
    :description="description"
    class="app-empty"
    role="status"
    aria-live="polite"
  />
</template>

<style scoped lang="scss">
.app-empty {
  padding: 32px 0;
}
</style>
