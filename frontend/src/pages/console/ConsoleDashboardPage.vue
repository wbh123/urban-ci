<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const router = useRouter()

const cards = computed(() => {
  const items: Array<{ title: string; description: string; path?: string; roles: string }> = []
  if (authStore.hasAnyRole(['COMMUNITY_MANAGER', 'ADMIN'])) {
    items.push({ title: '巡检组织管理', description: '维护辖区楼栋，创建任务并查看现场执行进度。', path: '/console/inspections', roles: '社区管理' })
  }
  if (authStore.hasAnyRole(['EXPERT', 'PROFESSIONAL_REVIEWER', 'ADMIN'])) {
    items.push({ title: '专业复核队列', description: '查看人工智能识别结果、现场证据并提交复核意见。', path: '/console/review', roles: '专业复核' })
  }
  if (authStore.hasAnyRole(['GOVERNMENT_MANAGER', 'ADMIN'])) {
    items.push({
      title: '区域风险与报告',
      description: '查看真实风险总览、楼栋点位并预览、生成和下载楼栋报告。',
      path: '/console/renewal-priorities',
      roles: '住建管理',
    })
  }
  if (authStore.hasRole('ADMIN')) {
    items.push({ title: '系统管理', description: '查看人工智能提供者配置、运行状态和任务统计。', path: '/console/system-status', roles: '系统管理员' })
  }
  return items
})
</script>

<template>
  <section class="dashboard-page">
    <header>
      <p class="eyebrow">Console Workspace</p>
      <h1>审核管理总览</h1>
      <p>根据当前账号角色，仅展示允许进入的工作模块。</p>
    </header>
    <div class="dashboard-grid">
      <el-card v-for="card in cards" :key="card.title" shadow="hover" class="dashboard-card">
        <template #header><div class="card-head"><strong>{{ card.title }}</strong><el-tag size="small" effect="plain">{{ card.roles }}</el-tag></div></template>
        <p>{{ card.description }}</p>
        <el-button v-if="card.path" type="primary" @click="router.push(card.path)">进入</el-button>
        <el-button v-else disabled>暂不可用</el-button>
      </el-card>
    </div>
  </section>
</template>

<style scoped lang="scss">
.dashboard-page { display: grid; gap: 24px; }
header h1 { margin: 5px 0; font-size: 34px; }
header > p:last-child { color: #667085; }
.eyebrow { margin: 0; color: #287a6a; font-size: 12px; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
.dashboard-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(290px, 1fr)); gap: 18px; }
.dashboard-card { min-height: 220px; }
.card-head { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.dashboard-card p { min-height: 70px; color: #667085; line-height: 1.7; }
</style>
