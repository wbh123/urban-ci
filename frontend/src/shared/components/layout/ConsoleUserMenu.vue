<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import type { RoleCode } from '@/shared/auth/access'

const authStore = useAuthStore()
const router = useRouter()

const roleLabels: Record<RoleCode, string> = {
  ADMIN: '系统管理员',
  GOVERNMENT_MANAGER: '住建管理',
  COMMUNITY_MANAGER: '社区管理',
  PROPERTY_INSPECTOR: '巡检人员',
  EXPERT: '专业专家',
  PROFESSIONAL_REVIEWER: '专业复核',
  DISPOSAL_OPERATOR: '问题处置',
}

const userName = computed(() => authStore.user?.realName || authStore.user?.username || '当前用户')
const userInitial = computed(() => userName.value.trim().slice(0, 1) || '用')
const userRoleLabel = computed(() => {
  const role = authStore.user?.roles[0]
  return role ? roleLabels[role] : '已登录'
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
  <el-dropdown trigger="click" @command="handleUserCommand">
    <button type="button" class="console-user-menu" aria-label="打开用户菜单">
      <span class="console-user-menu__avatar" aria-hidden="true">{{ userInitial }}</span>
      <span class="console-user-menu__copy">
        <strong>{{ userName }}</strong>
        <small>{{ userRoleLabel }}</small>
      </span>
      <span class="console-user-menu__chevron" aria-hidden="true">⌄</span>
    </button>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item command="logout">退出登录</el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<style scoped lang="scss">
.console-user-menu {
  display: flex;
  min-height: 38px;
  align-items: center;
  gap: 8px;
  padding: 3px 8px;
  border: 1px solid transparent;
  border-radius: var(--usp-radius-lg);
  background: transparent;
  color: var(--usp-color-text);
  cursor: pointer;
  text-align: left;
  transition: background var(--usp-transition-fast), border-color var(--usp-transition-fast);
}
.console-user-menu:hover {
  border-color: var(--usp-color-border);
  background: var(--usp-color-surface-muted);
}
.console-user-menu:focus-visible { outline: 2px solid var(--usp-color-primary-light); outline-offset: 2px; }
.console-user-menu__avatar {
  display: grid;
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  place-items: center;
  border-radius: 50%;
  background: var(--usp-color-primary-soft);
  color: var(--usp-color-primary-strong);
  font-weight: 800;
}
.console-user-menu__copy { display: grid; min-width: 0; gap: 0; line-height: 1.15; }
.console-user-menu__copy strong {
  max-width: 132px;
  overflow: hidden;
  font-size: var(--usp-font-size-sm);
  text-overflow: ellipsis;
  white-space: nowrap;
}
.console-user-menu__copy small { color: var(--usp-color-text-secondary); font-size: 10px; white-space: nowrap; }
.console-user-menu__chevron { color: var(--usp-color-text-tertiary); font-size: 12px; }

@media (max-width: 880px) {
  .console-user-menu__copy { display: none; }
}
</style>
