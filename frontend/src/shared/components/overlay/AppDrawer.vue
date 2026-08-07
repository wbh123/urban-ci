<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    title: string
    size?: string
    closeOnClickModal?: boolean
  }>(),
  {
    size: 'min(560px, 92vw)',
    closeOnClickModal: true,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})
</script>

<template>
  <el-drawer
    v-model="visible"
    class="app-drawer"
    :title="title"
    :size="size"
    :close-on-click-modal="closeOnClickModal"
    append-to-body
    destroy-on-close
  >
    <slot />
    <template v-if="$slots.footer" #footer>
      <div class="app-drawer__footer">
        <slot name="footer" />
      </div>
    </template>
  </el-drawer>
</template>

<style lang="scss">
.app-drawer.el-drawer {
  background: var(--usp-color-surface);
}

.app-drawer .el-drawer__header {
  margin-bottom: 0;
  padding-bottom: var(--usp-space-4);
  border-bottom: 1px solid var(--usp-color-border);
}

.app-drawer .el-drawer__body {
  color: var(--usp-color-text);
}

.app-drawer__footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--usp-space-2);
}
</style>
