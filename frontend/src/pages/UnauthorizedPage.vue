<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
</script>

<template>
  <main class="state-page">
    <section>
      <strong class="code">403</strong>
      <h1>当前角色无权访问此页面</h1>
      <p>页面入口已经按照岗位隔离。若职责发生变化，请联系管理员调整角色。</p>
      <button type="button" @click="router.replace(authStore.defaultEntry())">返回可用入口</button>
    </section>
  </main>
</template>

<style scoped lang="scss">
.state-page { min-height: 100vh; display: grid; place-items: center; padding: 24px; background: #f3f6f9; text-align: center; }
.state-page section { max-width: 540px; }
.code { font-size: 80px; color: #d92d20; }
p { color: #667085; line-height: 1.7; }
button { min-height: 44px; padding: 0 20px; border: 0; border-radius: 12px; background: #176354; color: #fff; cursor: pointer; }
</style>
