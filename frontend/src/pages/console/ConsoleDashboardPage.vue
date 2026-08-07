<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import AppPageHeader from '@/shared/components/layout/AppPageHeader.vue'

const authStore = useAuthStore()
const router = useRouter()

const cards = computed(() => {
  const items: Array<{ title: string; description: string; path?: string; roles: string }> = []
  if (authStore.hasAnyRole(['COMMUNITY_MANAGER', 'ADMIN'])) {
    items.push({ title: '巡检组织管理', description: '维护辖区楼栋，创建任务并查看现场执行进度。', path: '/console/inspections', roles: '社区管理' })
  }
  if (authStore.hasAnyRole(['EXPERT', 'PROFESSIONAL_REVIEWER', 'ADMIN'])) {
    items.push({ title: '专业复核队列', description: '查看智能辅助识别结果、现场证据并提交复核意见。', path: '/console/review', roles: '专业复核' })
  }
  if (authStore.hasAnyRole(['GOVERNMENT_MANAGER', 'ADMIN'])) {
    items.push({
      title: '区域风险与报告',
      description: '查看区域风险总览、楼栋信息并预览、生成和下载楼栋报告。',
      path: '/console/renewal-priorities',
      roles: '住建管理',
    })
  }
  if (authStore.hasRole('ADMIN')) {
    items.push({ title: '系统管理', description: '查看智能能力提供者配置、运行状态和任务统计。', path: '/console/system-status', roles: '系统管理员' })
  }
  return items
})
</script>

<template>
  <section class="dashboard-page">
    <AppPageHeader
      title="审核管理总览"
      description="根据当前账号职责展示可进入的业务模块与工作入口。"
    />

    <div class="dashboard-grid">
      <el-card v-for="card in cards" :key="card.title" shadow="hover" class="dashboard-card">
        <template #header>
          <div class="card-head">
            <strong>{{ card.title }}</strong>
            <el-tag size="small" effect="plain">{{ card.roles }}</el-tag>
          </div>
        </template>
        <p>{{ card.description }}</p>
        <el-button v-if="card.path" type="primary" @click="router.push(card.path)">进入</el-button>
        <el-button v-else disabled>暂不可用</el-button>
      </el-card>
    </div>
  </section>
</template>

<style scoped lang="scss">
.dashboard-page {
  display: grid;
  gap: var(--usp-space-6);
}

.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(290px, 1fr));
  gap: var(--usp-space-5);
}

.dashboard-card {
  min-height: 220px;
  border-radius: var(--usp-radius-lg);
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--usp-space-3);
}

.dashboard-card p {
  min-height: 70px;
  color: var(--usp-color-text-secondary);
  line-height: var(--usp-line-height-body);
}
</style>
