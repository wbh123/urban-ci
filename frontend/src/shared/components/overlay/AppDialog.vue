<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    title: string
    width?: string
    closeOnClickModal?: boolean
  }>(),
  {
    width: 'min(560px, calc(100vw - 32px))',
    closeOnClickModal: false,
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
  <el-dialog
    v-model="visible"
    class="app-dialog"
    :title="title"
    :width="width"
    :close-on-click-modal="closeOnClickModal"
    append-to-body
    destroy-on-close
    align-center
  >
    <slot />
    <template v-if="$slots.footer" #footer>
      <div class="app-dialog__footer">
        <slot name="footer" />
      </div>
    </template>
  </el-dialog>
</template>

<style lang="scss">
.app-dialog.el-dialog {
  border-radius: var(--usp-radius-lg);
  box-shadow: var(--usp-shadow-lg);
}

.app-dialog .el-dialog__header {
  padding-bottom: var(--usp-space-4);
  border-bottom: 1px solid var(--usp-color-border);
}

.app-dialog .el-dialog__body {
  color: var(--usp-color-text);
}

.app-dialog__footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--usp-space-2);
}
</style>
