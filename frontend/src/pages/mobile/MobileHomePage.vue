<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const router = useRouter()

const actions = computed(() => {
  const items: Array<{ path: string; title: string; description: string; accent: string }> = []
  if (authStore.hasAnyRole(['PROPERTY_INSPECTOR', 'ADMIN'])) {
    items.push({ path: '/mobile/tasks', title: '我的巡检任务', description: '到场检查、填写记录并上传现场图片。', accent: '巡检' })
  }
  if (authStore.hasAnyRole(['DISPOSAL_OPERATOR', 'ADMIN'])) {
    items.push({ path: '/mobile/disposal', title: '问题处置', description: '接收整改任务并提交处理证据。', accent: '处置' })
  }
  return items
})
</script>

<template>
  <section class="mobile-page">
    <header class="welcome-card">
      <p>你好，{{ authStore.user?.realName || authStore.user?.username }}</p>
      <h1>今天需要处理什么？</h1>
      <span>现场作业结果将进入电脑端审核流程。</span>
    </header>
    <div class="action-list">
      <button v-for="item in actions" :key="item.path" type="button" @click="router.push(item.path)">
        <span class="accent">{{ item.accent }}</span>
        <strong>{{ item.title }}</strong>
        <small>{{ item.description }}</small>
        <b aria-hidden="true">›</b>
      </button>
    </div>
    <article class="tip-card">
      <strong>现场提示</strong>
      <p>优先拍摄整体环境，再拍摄病害近景。人工智能结果仅作为风险筛查辅助，不代表正式鉴定结论。</p>
    </article>
  </section>
</template>

<style scoped lang="scss">
.mobile-page { display: grid; gap: 16px; }
.welcome-card { padding: 22px; border-radius: 22px; background: linear-gradient(135deg, #176354, #2d8775); color: #fff; box-shadow: 0 16px 36px rgb(23 99 84 / 20%); }
.welcome-card p, .welcome-card span { margin: 0; color: rgb(255 255 255 / 78%); }
.welcome-card h1 { margin: 8px 0 12px; font-size: 28px; }
.action-list { display: grid; gap: 12px; }
.action-list button { position: relative; min-height: 116px; display: grid; grid-template-columns: 54px 1fr 22px; grid-template-rows: auto auto; gap: 5px 12px; align-items: center; padding: 18px; border: 1px solid #dfe7e4; border-radius: 18px; background: #fff; text-align: left; color: #172033; }
.accent { grid-row: 1 / 3; width: 54px; height: 54px; display: grid; place-items: center; border-radius: 16px; background: #e8f5f1; color: #176354; font-weight: 800; }
.action-list strong { font-size: 18px; }
.action-list small { color: #667085; line-height: 1.5; }
.action-list b { grid-column: 3; grid-row: 1 / 3; font-size: 28px; color: #98a2b3; }
.tip-card { padding: 16px; border-radius: 16px; background: #fff8e8; color: #7a5b00; }
.tip-card p { margin: 6px 0 0; line-height: 1.65; font-size: 14px; }
</style>
