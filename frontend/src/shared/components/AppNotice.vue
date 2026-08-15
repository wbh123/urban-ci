<script setup lang="ts">
import { useAppStore, type NoticeType } from '@/stores/app'

const appStore = useAppStore()

function iconFor(type: NoticeType): string {
  if (type === 'success') return '✓'
  if (type === 'warning') return '!'
  if (type === 'error') return '×'
  return 'i'
}
</script>

<template>
  <div class="app-notice" aria-live="polite" aria-atomic="false">
    <TransitionGroup name="notice-list">
      <article
        v-for="item in appStore.notices"
        :key="item.id"
        class="notice-card"
        :class="`notice-card--${item.type}`"
        role="status"
      >
        <span class="notice-icon" aria-hidden="true">{{ iconFor(item.type) }}</span>
        <p>{{ item.message }}</p>
        <button type="button" class="notice-close" aria-label="关闭提示" @click="appStore.dismissNotice(item.id)">×</button>
      </article>
    </TransitionGroup>
  </div>
</template>

<style scoped lang="scss">
.app-notice {
  position: fixed;
  top: calc(var(--usp-header-height) + 16px);
  right: 22px;
  z-index: 2200;
  display: flex;
  width: min(390px, calc(100vw - 32px));
  flex-direction: column;
  gap: 10px;
  pointer-events: none;
}

.notice-card {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr) 26px;
  align-items: center;
  gap: 10px;
  min-height: 54px;
  padding: 11px 12px;
  border: 1px solid var(--usp-color-border);
  border-radius: var(--usp-radius-lg);
  background: rgb(255 255 255 / 96%);
  box-shadow: var(--usp-shadow-md);
  backdrop-filter: blur(12px);
  pointer-events: auto;
}

.notice-card p {
  margin: 0;
  color: var(--usp-color-text);
  font-size: 13px;
  line-height: 1.5;
}

.notice-icon {
  display: grid;
  width: 28px;
  height: 28px;
  place-items: center;
  border-radius: 999px;
  background: var(--usp-color-surface-muted);
  color: var(--usp-color-info);
  font-weight: 800;
}

.notice-card--success .notice-icon { background: #ecfdf3; color: var(--usp-color-success); }
.notice-card--warning .notice-icon { background: #fffaeb; color: var(--usp-color-warning); }
.notice-card--error .notice-icon { background: #fef3f2; color: var(--usp-color-danger); }
.notice-card--info .notice-icon { background: #eff8ff; color: var(--usp-color-info); }

.notice-close {
  display: grid;
  width: 26px;
  height: 26px;
  padding: 0;
  place-items: center;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: var(--usp-color-text-tertiary);
  cursor: pointer;
}

.notice-close:hover { background: var(--usp-color-surface-muted); color: var(--usp-color-text); }

.notice-list-enter-active,
.notice-list-leave-active { transition: opacity 180ms ease, transform 180ms ease; }
.notice-list-enter-from,
.notice-list-leave-to { opacity: 0; transform: translateY(-8px) scale(.98); }

@media (max-width: 720px) {
  .app-notice { top: 14px; right: 16px; left: 16px; width: auto; }
}
</style>
