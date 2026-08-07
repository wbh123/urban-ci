<script setup lang="ts">
import { computed } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()

const navItems = computed(() => {
  const items = [{ path: '/mobile', label: '首页', icon: '⌂' }]
  if (authStore.hasAnyRole(['PROPERTY_INSPECTOR', 'ADMIN'])) {
    items.push({ path: '/mobile/tasks', label: '巡检', icon: '✓' })
  }
  if (authStore.hasAnyRole(['PROPERTY_INSPECTOR', 'ADMIN'])) {
    items.push({ path: '/mobile/knowledge', label: '知识', icon: '?' })
  }
  if (authStore.hasAnyRole(['DISPOSAL_OPERATOR', 'ADMIN'])) {
    items.push({ path: '/mobile/disposal', label: '处置', icon: '↻' })
  }
  return items
})

async function logout(): Promise<void> {
  await authStore.logout()
  await router.replace('/mobile/login')
}
</script>

<template>
  <div class="mobile-shell">
    <header class="mobile-header">
      <div>
        <strong>城安智序作业端</strong>
        <small>{{ authStore.user?.realName || authStore.user?.username }}</small>
      </div>
      <button type="button" class="text-button" @click="logout">退出</button>
    </header>
    <main class="mobile-main">
      <RouterView />
    </main>
    <nav class="mobile-nav" aria-label="移动端主导航">
      <button
        v-for="item in navItems"
        :key="item.path"
        type="button"
        :class="{ active: route.path === item.path || (item.path !== '/mobile' && route.path.startsWith(item.path)) }"
        @click="router.push(item.path)"
      >
        <span aria-hidden="true">{{ item.icon }}</span>
        <small>{{ item.label }}</small>
      </button>
    </nav>
  </div>
</template>

<style scoped lang="scss">
.mobile-shell {
  min-height: 100vh;
  background: var(--usp-color-bg);
  color: var(--usp-color-text);
}

.mobile-header {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: var(--usp-header-height);
  padding: var(--usp-space-3) var(--usp-space-4);
  background: var(--usp-color-primary-strong);
  color: var(--usp-color-inverse);
  box-shadow: var(--usp-shadow-sm);
}

.mobile-header div {
  display: grid;
  gap: 2px;
}

.mobile-header small {
  color: var(--usp-color-aside-text-muted);
}

.text-button {
  min-width: 48px;
  min-height: 44px;
  border: 0;
  border-radius: var(--usp-radius-sm);
  background: transparent;
  color: inherit;
}

.text-button:focus-visible {
  outline: 2px solid var(--usp-color-inverse);
  outline-offset: 2px;
}

.mobile-main {
  width: min(100%, var(--usp-mobile-content-max-width));
  margin: 0 auto;
  padding: var(--usp-space-4) 14px 92px;
}

.mobile-nav {
  position: fixed;
  inset: auto 0 0;
  z-index: 30;
  display: flex;
  justify-content: center;
  gap: var(--usp-space-1);
  padding: var(--usp-space-2) max(var(--usp-space-3), env(safe-area-inset-right))
    calc(var(--usp-space-2) + env(safe-area-inset-bottom))
    max(var(--usp-space-3), env(safe-area-inset-left));
  background: var(--usp-color-surface);
  border-top: 1px solid var(--usp-color-border);
  box-shadow: 0 -6px 18px rgb(16 24 40 / 6%);
}

.mobile-nav button {
  flex: 1;
  max-width: 180px;
  min-height: 54px;
  display: grid;
  place-items: center;
  gap: 2px;
  border: 0;
  border-radius: var(--usp-radius-md);
  background: transparent;
  color: var(--usp-color-text-secondary);
  transition:
    background var(--usp-transition-fast),
    color var(--usp-transition-fast);
}

.mobile-nav button span {
  font-size: 20px;
  line-height: 1;
}

.mobile-nav button.active {
  background: var(--usp-color-primary-soft);
  color: var(--usp-color-primary-strong);
  font-weight: 700;
}
</style>
