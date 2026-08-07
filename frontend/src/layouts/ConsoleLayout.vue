<script setup lang="ts">
import { computed } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useAppStore } from '@/stores/app'

const authStore = useAuthStore()
const appStore = useAppStore()
const route = useRoute()
const router = useRouter()
const menuItems = computed(() => {
  const items = [{ path: '/console', label: '管理总览' }]
  if (authStore.hasAnyRole(['COMMUNITY_MANAGER', 'ADMIN'])) items.push({ path: '/console/inspections', label: '巡检管理' })
  if (authStore.hasAnyRole(['COMMUNITY_MANAGER', 'GOVERNMENT_MANAGER', 'ADMIN'])) items.push({ path: '/console/feedback', label: '公众反馈' })
  if (authStore.hasAnyRole(['EXPERT', 'PROFESSIONAL_REVIEWER', 'ADMIN'])) items.push({ path: '/console/review', label: '专业复核' })
  if (authStore.hasAnyRole(['EXPERT', 'ADMIN'])) items.push({ path: '/console/knowledge', label: '知识问答' })
  if (authStore.hasAnyRole(['GOVERNMENT_MANAGER', 'ADMIN'])) items.push({ path: '/console/renewal-priorities', label: '更新优先级' })
  if (authStore.hasAnyRole(['EXPERT', 'PROFESSIONAL_REVIEWER', 'COMMUNITY_MANAGER', 'GOVERNMENT_MANAGER', 'ADMIN'])) items.push({ path: '/console/assessment-rules', label: '评分规则' })
  if (authStore.hasRole('ADMIN')) items.push({ path: '/console/system-status', label: 'AI运行状态' })
  if (authStore.hasRole('ADMIN')) items.push({ path: '/console/legacy-workspace', label: '兼容工作台' })
  return items
})
async function logout(): Promise<void> { await authStore.logout(); await router.replace('/console/login') }
</script>
<template>
  <el-container class="console-shell">
    <el-aside width="228px" class="console-aside">
      <div class="console-brand" @click="router.push('/console')"><strong>城安智序</strong><span>审核管理端</span></div>
      <el-menu :default-active="route.path" router class="console-menu"><el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">{{ item.label }}</el-menu-item></el-menu>
    </el-aside>
    <el-container>
      <el-header class="console-header"><div><strong>{{ route.meta.title || '审核管理端' }}</strong><el-tag size="small" effect="plain" :type="appStore.apiMode === 'mock' ? 'warning' : 'success'">{{ appStore.apiMode === 'mock' ? 'Mock' : 'Real' }}</el-tag></div><div class="console-user"><span>{{ authStore.user?.realName || authStore.user?.username }}</span><el-button size="small" @click="logout">退出</el-button></div></el-header>
      <el-main class="console-main"><RouterView /></el-main>
    </el-container>
  </el-container>
</template>
<style scoped lang="scss">
.console-shell {
  min-height: 100vh;
  background: var(--usp-color-bg);
}

.console-aside {
  position: sticky;
  top: 0;
  height: 100vh;
  background: var(--usp-color-aside);
  color: var(--usp-color-inverse);
}

.console-brand {
  display: grid;
  gap: var(--usp-space-1);
  padding: var(--usp-space-6) var(--usp-space-5);
  cursor: pointer;
}

.console-brand strong {
  font-size: 22px;
}

.console-brand span {
  color: var(--usp-color-aside-text-muted);
  font-size: var(--usp-font-size-sm);
}

.console-menu {
  border-right: 0;
  background: transparent;
}

.console-menu :deep(.el-menu-item) {
  color: var(--usp-color-aside-text);
}

.console-menu :deep(.el-menu-item:hover),
.console-menu :deep(.el-menu-item.is-active) {
  background: var(--usp-color-aside-hover);
  color: var(--usp-color-inverse);
}

.console-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--usp-space-5);
  background: var(--usp-color-surface);
  border-bottom: 1px solid var(--usp-color-border);
}

.console-header > div {
  display: flex;
  align-items: center;
  gap: var(--usp-space-3);
}

.console-user {
  color: var(--usp-color-text-secondary);
}

.console-main {
  width: 100%;
  max-width: var(--usp-content-max-width);
  margin: 0 auto;
}

@media (max-width: 900px) {
  .console-aside {
    width: var(--usp-console-aside-collapsed-width) !important;
  }

  .console-brand span,
  .console-menu :deep(.el-menu-item) {
    font-size: 0;
  }

  .console-brand {
    padding: var(--usp-space-5) var(--usp-space-2);
  }
}
</style>
