<script setup lang="ts">
import { useAppStore } from '@/stores/app'

const appStore = useAppStore()
</script>

<template>
  <div
    class="app-notice"
    aria-live="polite"
    aria-atomic="false"
  >
    <el-alert
      v-for="item in appStore.notices"
      :key="item.id"
      :type="item.type"
      :title="item.message"
      show-icon
      :closable="true"
      @close="appStore.dismissNotice(item.id)"
    />
  </div>
</template>

<style scoped lang="scss">
.app-notice {
  position: fixed;
  top: calc(var(--usp-header-height) + 12px);
  right: 24px;
  z-index: 2200;
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-width: 380px;
}
</style>
