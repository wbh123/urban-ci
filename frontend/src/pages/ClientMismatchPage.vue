<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const expectedMobile = computed(() => route.query.expected === 'MOBILE')
</script>

<template>
  <main class="state-page">
    <section>
      <strong class="icon">⇄</strong>
      <h1>当前账号与入口不匹配</h1>
      <p>
        {{ expectedMobile ? '当前账号不具备移动作业权限，请使用电脑审核管理端。' : '当前账号仅具备现场作业权限，请使用移动作业端。' }}
      </p>
      <button type="button" @click="router.replace(expectedMobile ? '/console/login' : '/mobile/login')">
        前往正确入口
      </button>
    </section>
  </main>
</template>

<style scoped lang="scss">
.state-page { min-height: 100vh; display: grid; place-items: center; padding: 24px; background: #f3f6f9; text-align: center; }
.state-page section { max-width: 560px; }
.icon { display: block; font-size: 72px; color: #287a6a; }
p { color: #667085; line-height: 1.7; }
button { min-height: 44px; padding: 0 20px; border: 0; border-radius: 12px; background: #176354; color: #fff; cursor: pointer; }
</style>
