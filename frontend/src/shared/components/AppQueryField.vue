<script setup lang="ts">
withDefaults(defineProps<{
  modelValue: string
  placeholder?: string
  width?: string
}>(), {
  placeholder: '请输入关键词',
  width: '240px',
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'query': []
}>()
</script>

<template>
  <el-input
    :model-value="modelValue"
    :placeholder="placeholder"
    :style="{ width }"
    clearable
    @update:model-value="emit('update:modelValue', String($event ?? ''))"
    @keyup.enter="emit('query')"
    @clear="emit('query')"
  />
</template>

<style scoped lang="scss">
:deep(.el-input__wrapper) {
  border-radius: var(--usp-radius-lg);
}
</style>
