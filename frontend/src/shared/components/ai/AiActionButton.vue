<script setup lang="ts">
withDefaults(defineProps<{
  label: string
  loading?: boolean
  disabled?: boolean
  type?: '' | 'primary' | 'success' | 'warning' | 'danger' | 'info'
}>(), { type: 'primary' })

defineEmits<{ click: [] }>()
</script>

<template>
  <el-button :type="type" :loading="loading" :disabled="disabled" round @click="$emit('click')">
    <span class="ai-action-mark" aria-hidden="true">✦ AI</span>{{ label }}
  </el-button>
</template>

<style scoped lang="scss">
.ai-action-mark{margin-right:5px;font-size:10px;font-weight:900;letter-spacing:.03em}
</style>
