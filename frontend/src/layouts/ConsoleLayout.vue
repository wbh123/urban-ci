<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useAppStore } from '@/stores/app'
import {
  buildConsoleMenu,
  resolveActiveConsoleMenuPath,
} from '@/shared/navigation/console-menu'
import type { RoleCode } from '@/shared/auth/access'

const authStore = useAuthStore()
const appStore = useAppStore()
const route = useRoute()
const router = useRouter()
const collapsed = ref(false)

const roleLabels: Record<RoleCode, string> = {
  ADMIN: '系统管理员',
  GOVERNMENT_MANAGER: '住建管理',
  COMMUNITY_MANAGER: '社区管理',
  PROPERTY_INSPECTOR: '巡检人员',
  EXPERT: '专业专家',
  PROFESSIONAL_REVIEWER: '专业复核',
  DISPOSAL_OPERATOR: '问题处置',
}

const menuGroups = computed(() => buildConsoleMenu(authStore.user?.roles ?? []))
const activeMenuPath = computed(() => resolveActiveConsoleMenuPath(route.path, menuGroups.value))
const environmentLabel = computed(() => (appStore.apiMode === 'mock' ? '模拟数据' : '业务环境'))
const userName = computed(() => authStore.user?.realName || authStore.user?.username || '当前用户')
const userInitial = computed(() => userName.value.trim().slice(0, 1) || '用')
const userRoleLabel = computed(() => {
  const role = authStore.user?.roles[0]
  return role ? roleLabels[role] : '已登录'
})
const fullWidth = computed(() => route.meta.fullWidth === true)
const breadcrumbs = computed(() => {
  const currentTitle = String(route.meta.title || '管理端')
  if (route.path === '/console') return [{ label: '工作台', path: '' }]
  return [
    { label: '工作台', path: '/console' },
    { label: currentTitle, path: '' },
  ]
})

async function logout(): Promise<void> {
  await authStore.logout()
  await router.replace('/console/login')
}

async function handleUserCommand(command: string): Promise<void> {
  if (command === 'logout') await logout()
}
</script>

<template>
  <el-container class="console-shell">
    <el-aside
      class="console-aside"
      :class="{ 'is-collapsed': collapsed }"
      :style="{
        width: collapsed
          ? 'var(--usp-console-aside-collapsed-width)'
          : 'var(--usp-console-aside-width)',
      }"
    >
      <button class="console-brand" type="button" aria-label="返回管理总览" @click="router.push('/console')">
        <span class="console-brand-mark" aria-hidden="true">安</span>
        <span v-if="!collapsed" class="console-brand-copy">
          <strong>城安智序</strong>
          <small>城市房屋安全治理平台</small>
        </span>
      </button>

      <el-scrollbar class="console-nav-scroll">
        <nav class="console-nav" aria-label="电脑管理端主导航">
          <section v-for="group in menuGroups" :key="group.key" class="console-menu-group">
            <div v-if="!collapsed" class="console-menu-group__label">{{ group.label }}</div>
            <el-menu
              class="console-menu"
              :default-active="activeMenuPath"
              :collapse="collapsed"
              :collapse-transition="false"
            >
              <el-menu-item
                v-for="item in group.items"
                :key="item.path"
                :index="item.path"
                @click="router.push(item.path)"
              >
                <span class="console-menu-icon" aria-hidden="true">{{ item.icon }}</span>
                <template #title>{{ item.label }}</template>
              </el-menu-item>
            </el-menu>
          </section>
        </nav>
      </el-scrollbar>

      <button
        class="console-collapse"
        type="button"
        :aria-label="collapsed ? '展开导航栏' : '收起导航栏'"
        @click="collapsed = !collapsed"
      >
        <span aria-hidden="true">{{ collapsed ? '›' : '‹' }}</span>
        <span v-if="!collapsed">收起导航</span>
      </button>
    </el-aside>

    <el-container class="console-body">
      <el-header class="console-header">
        <div class="console-header__context">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item v-for="item in breadcrumbs" :key="`${item.label}-${item.path}`">
              <button
                v-if="item.path"
                type="button"
                class="console-breadcrumb-link"
                @click="router.push(item.path)"
              >
                {{ item.label }}
              </button>
              <span v-else>{{ item.label }}</span>
            </el-breadcrumb-item>
          </el-breadcrumb>
          <strong class="console-page-title">{{ route.meta.title || '审核管理端' }}</strong>
        </div>

        <div class="console-header__actions">
          <el-tag
            size="small"
            effect="plain"
            :type="appStore.apiMode === 'mock' ? 'warning' : 'success'"
          >
            {{ environmentLabel }}
          </el-tag>

          <el-dropdown trigger="click" @command="handleUserCommand">
            <button type="button" class="console-user-trigger" aria-label="打开用户菜单">
              <span class="console-user-avatar" aria-hidden="true">{{ userInitial }}</span>
              <span class="console-user-copy">
                <strong>{{ userName }}</strong>
                <small>{{ userRoleLabel }}</small>
              </span>
              <span class="console-user-chevron" aria-hidden="true">⌄</span>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="console-main" :class="{ 'console-main--wide': fullWidth }">
        <RouterView />
      </el-main>
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
  z-index: 40;
  display: flex;
  flex-direction: column;
  height: 100vh;
  flex: 0 0 auto;
  overflow: hidden;
  background: var(--usp-color-aside);
  color: var(--usp-color-inverse);
  transition: width var(--usp-transition-fast);
}

.console-brand {
  display: flex;
  align-items: center;
  gap: var(--usp-space-3);
  width: 100%;
  min-height: 82px;
  padding: var(--usp-space-4) var(--usp-space-5);
  border: 0;
  background: transparent;
  color: inherit;
  text-align: left;
}

.console-brand:focus-visible,
.console-collapse:focus-visible,
.console-user-trigger:focus-visible,
.console-breadcrumb-link:focus-visible {
  outline: 2px solid var(--usp-color-primary-light);
  outline-offset: 2px;
}

.console-brand-mark {
  display: grid;
  width: 38px;
  height: 38px;
  flex: 0 0 38px;
  place-items: center;
  border-radius: var(--usp-radius-md);
  background: var(--usp-color-primary);
  font-size: var(--usp-font-size-lg);
  font-weight: 800;
}

.console-brand-copy {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.console-brand-copy strong {
  font-size: 20px;
  letter-spacing: 0.02em;
}

.console-brand-copy small {
  overflow: hidden;
  color: var(--usp-color-aside-text-muted);
  font-size: var(--usp-font-size-xs);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.console-nav-scroll {
  flex: 1;
}

.console-nav {
  display: grid;
  gap: var(--usp-space-3);
  padding: var(--usp-space-2) var(--usp-space-3) var(--usp-space-4);
}

.console-menu-group {
  min-width: 0;
}

.console-menu-group__label {
  padding: var(--usp-space-2) var(--usp-space-3);
  color: var(--usp-color-aside-text-muted);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.console-menu {
  border-right: 0;
  background: transparent;
}

.console-menu:not(.el-menu--collapse) {
  width: 100%;
}

.console-menu :deep(.el-menu-item) {
  height: 44px;
  margin: 2px 0;
  padding: 0 var(--usp-space-3) !important;
  border-radius: var(--usp-radius-sm);
  color: var(--usp-color-aside-text);
  line-height: 44px;
}

.console-menu :deep(.el-menu-item:hover),
.console-menu :deep(.el-menu-item.is-active) {
  background: var(--usp-color-aside-hover);
  color: var(--usp-color-inverse);
}

.console-menu-icon {
  display: inline-grid;
  width: 24px;
  flex: 0 0 24px;
  place-items: center;
  margin-right: var(--usp-space-2);
  font-size: 16px;
  font-weight: 700;
}

.is-collapsed .console-brand {
  justify-content: center;
  padding-inline: var(--usp-space-2);
}

.is-collapsed .console-nav {
  padding-inline: var(--usp-space-2);
}

.is-collapsed .console-menu :deep(.el-menu-item) {
  justify-content: center;
  padding-inline: 0 !important;
}

.is-collapsed .console-menu-icon {
  margin-right: 0;
}

.console-collapse {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--usp-space-2);
  min-height: 48px;
  margin: var(--usp-space-3);
  border: 1px solid rgb(255 255 255 / 10%);
  border-radius: var(--usp-radius-sm);
  background: transparent;
  color: var(--usp-color-aside-text);
}

.console-collapse:hover {
  background: var(--usp-color-aside-hover);
  color: var(--usp-color-inverse);
}

.console-body {
  min-width: 0;
}

.console-header {
  position: sticky;
  top: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: var(--usp-header-height);
  gap: var(--usp-space-5);
  padding: 0 var(--usp-space-6);
  background: var(--usp-color-surface);
  border-bottom: 1px solid var(--usp-color-border);
}

.console-header__context,
.console-header__actions {
  display: flex;
  align-items: center;
  gap: var(--usp-space-3);
}

.console-header__context {
  min-width: 0;
}

.console-page-title {
  padding-left: var(--usp-space-3);
  border-left: 1px solid var(--usp-color-border);
  overflow: hidden;
  color: var(--usp-color-text);
  font-size: var(--usp-font-size-md);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.console-breadcrumb-link {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--usp-color-text-secondary);
}

.console-user-trigger {
  display: flex;
  align-items: center;
  gap: var(--usp-space-2);
  min-height: 42px;
  padding: var(--usp-space-1) var(--usp-space-2);
  border: 0;
  border-radius: var(--usp-radius-md);
  background: transparent;
  color: var(--usp-color-text);
  text-align: left;
}

.console-user-trigger:hover {
  background: var(--usp-color-surface-muted);
}

.console-user-avatar {
  display: grid;
  width: 32px;
  height: 32px;
  flex: 0 0 32px;
  place-items: center;
  border-radius: 50%;
  background: var(--usp-color-primary-soft);
  color: var(--usp-color-primary-strong);
  font-weight: 800;
}

.console-user-copy {
  display: grid;
  gap: 1px;
}

.console-user-copy strong {
  max-width: 130px;
  overflow: hidden;
  font-size: var(--usp-font-size-sm);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.console-user-copy small {
  color: var(--usp-color-text-secondary);
  font-size: var(--usp-font-size-xs);
}

.console-user-chevron {
  color: var(--usp-color-text-tertiary);
}

.console-main {
  width: 100%;
  max-width: var(--usp-content-max-width);
  margin: 0 auto;
  padding: var(--usp-space-6);
}

.console-main--wide {
  max-width: none;
  padding: var(--usp-space-4) var(--usp-space-5) var(--usp-space-5);
}

@media (max-width: 1080px) {
  .console-user-copy,
  .console-page-title {
    display: none;
  }
}

@media (max-width: 820px) {
  .console-header {
    padding-inline: var(--usp-space-4);
  }

  .console-header__context :deep(.el-breadcrumb__item:not(:last-child)) {
    display: none;
  }

  .console-main,
  .console-main--wide {
    padding: var(--usp-space-4);
  }
}
</style>
