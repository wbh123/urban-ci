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
.mobile-shell { min-height: 100vh; background: #f4f7f6; color: #172033; }
.mobile-header { position: sticky; top: 0; z-index: 20; display: flex; align-items: center; justify-content: space-between; padding: 14px 16px; background: #176354; color: #fff; box-shadow: 0 4px 16px rgb(23 99 84 / 18%); }
.mobile-header div { display: grid; gap: 2px; }
.mobile-header small { opacity: .8; }
.text-button { min-width: 48px; min-height: 44px; border: 0; background: transparent; color: inherit; font: inherit; }
.mobile-main { width: min(100%, 720px); margin: 0 auto; padding: 16px 14px 92px; }
.mobile-nav { position: fixed; inset: auto 0 0; z-index: 30; display: flex; justify-content: center; gap: 4px; padding: 8px max(12px, env(safe-area-inset-right)) calc(8px + env(safe-area-inset-bottom)) max(12px, env(safe-area-inset-left)); background: #fff; border-top: 1px solid #dce5e2; }
.mobile-nav button { flex: 1; max-width: 180px; min-height: 54px; display: grid; place-items: center; gap: 2px; border: 0; border-radius: 12px; background: transparent; color: #667085; font: inherit; }
.mobile-nav button span { font-size: 20px; line-height: 1; }
.mobile-nav button.active { background: #e8f5f1; color: #176354; font-weight: 700; }
</style>
