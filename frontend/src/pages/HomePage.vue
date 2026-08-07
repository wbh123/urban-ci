<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

function enter(path: string): void {
  if (authStore.isAuthenticated && path !== '/citizen') void router.push('/entry')
  else void router.push(path)
}
</script>

<template>
  <main class="landing">
    <section class="hero">
      <p class="eyebrow">UrbanSafe Priority</p>
      <h1>城安智序</h1>
      <p class="subtitle">老旧小区房屋安全风险筛查与城市更新辅助决策平台</p>
      <div class="entry-grid">
        <button type="button" @click="enter('/citizen')">
          <strong>公众反馈端</strong>
          <span>问题上报、电话与短信入口、处理进度查询</span>
        </button>
        <button type="button" @click="enter('/mobile/login')">
          <strong>移动作业端</strong>
          <span>现场巡检、拍照上传和问题处置</span>
        </button>
        <button type="button" @click="enter('/console/login')">
          <strong>电脑审核管理端</strong>
          <span>任务组织、专业复核、反馈管理和风险分析</span>
        </button>
      </div>
    </section>
  </main>
</template>

<style scoped lang="scss">
.landing { min-height: 100vh; display: grid; place-items: center; padding: 32px; background: linear-gradient(135deg, #102d28, #176354 45%, #2f806f); color: #fff; }
.hero { width: min(1080px, 100%); }
.eyebrow { margin: 0; color: #b9e3d7; font-weight: 800; letter-spacing: .14em; text-transform: uppercase; }
h1 { margin: 12px 0 0; font-size: clamp(54px, 10vw, 110px); letter-spacing: -.06em; }
.subtitle { max-width: 680px; font-size: clamp(18px, 2.8vw, 30px); line-height: 1.5; color: rgb(255 255 255 / 82%); }
.entry-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 18px; margin-top: 42px; }
.entry-grid button { min-height: 170px; display: grid; align-content: center; gap: 12px; padding: 24px; text-align: left; border: 1px solid rgb(255 255 255 / 24%); border-radius: 22px; background: rgb(255 255 255 / 10%); color: #fff; backdrop-filter: blur(12px); cursor: pointer; }
.entry-grid button:hover { background: rgb(255 255 255 / 16%); transform: translateY(-2px); }
.entry-grid strong { font-size: 24px; }
.entry-grid span { color: rgb(255 255 255 / 72%); line-height: 1.5; }
</style>
