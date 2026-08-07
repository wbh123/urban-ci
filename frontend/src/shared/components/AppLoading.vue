<script setup lang="ts">
withDefaults(
  defineProps<{
    visible?: boolean
    text?: string
    inline?: boolean
  }>(),
  {
    visible: false,
    text: '加载中…',
    inline: false,
  },
)
</script>

<template>
  <div
    v-if="visible"
    class="app-loading"
    :class="{ 'app-loading--inline': inline }"
    role="status"
    aria-live="polite"
  >
    <span
      class="app-loading__spinner"
      aria-hidden="true"
    />
    <span class="app-loading__text">{{ text }}</span>
  </div>
</template>

<style scoped lang="scss">
.app-loading {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  background: rgba(242, 245, 249, 0.7);
  backdrop-filter: blur(2px);

  &--inline {
    position: static;
    padding: 24px;
    background: transparent;
    backdrop-filter: none;
  }
}

.app-loading__spinner {
  width: 32px;
  height: 32px;
  border: 3px solid var(--usp-color-border);
  border-top-color: var(--usp-color-primary);
  border-radius: 50%;
  animation: usp-spin 0.8s linear infinite;
}

.app-loading__text {
  color: var(--usp-color-text-secondary);
  font-size: 14px;
}

@keyframes usp-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
