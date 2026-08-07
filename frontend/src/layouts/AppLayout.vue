<script setup lang="ts">
import { computed } from 'vue'
import { RouterView, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useAppStore } from '@/stores/app'

const authStore = useAuthStore()
const appStore = useAppStore()
const router = useRouter()

const activePath = computed(() => router.currentRoute.value.path)

function handleSelect(index: string): void {
  router.push(index)
}
</script>

<template>
  <el-container class="app-layout">
    <el-header class="app-header">
      <div
        class="app-brand"
        @click="router.push({ name: 'home' })"
      >
        <span class="app-brand-title">城安智序</span>
        <span class="app-brand-sub">UrbanSafe Priority</span>
      </div>
      <el-menu
        mode="horizontal"
        :default-active="activePath"
        class="app-menu"
        @select="handleSelect"
      >
        <el-menu-item index="/">
          首页
        </el-menu-item>
        <el-menu-item index="/workspace">
          巡检工作台
        </el-menu-item>
      </el-menu>
      <div class="app-header-right">
        <el-tag
          size="small"
          effect="plain"
          :type="appStore.apiMode === 'mock' ? 'warning' : 'success'"
        >
          {{ appStore.apiMode === 'mock' ? 'Mock 接口' : '真实接口' }}
        </el-tag>
        <span class="app-user">
          {{ authStore.user?.realName || authStore.user?.username || '未登录' }}
        </span>
      </div>
    </el-header>
    <el-main class="app-main">
      <RouterView />
    </el-main>
  </el-container>
</template>
