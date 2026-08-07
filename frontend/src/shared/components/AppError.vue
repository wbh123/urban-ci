<script setup lang="ts">
withDefaults(
  defineProps<{
    message?: string
    retryText?: string
  }>(),
  {
    message: '加载失败，请稍后重试。',
    retryText: '重试',
  },
)

defineEmits<{
  retry: []
}>()
</script>

<template>
  <div
    class="app-error"
    role="alert"
    aria-live="assertive"
  >
    <el-result
      icon="error"
      :sub-title="message"
    >
      <template #extra>
        <el-button
          type="primary"
          @click="$emit('retry')"
        >
          {{ retryText }}
        </el-button>
      </template>
    </el-result>
  </div>
</template>

<style scoped lang="scss">
.app-error {
  padding: 16px 0;
}
</style>
