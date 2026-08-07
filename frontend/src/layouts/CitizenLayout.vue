<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'
</script>

<template>
  <div class="citizen-shell">
    <header class="citizen-header">
      <RouterLink to="/citizen" class="citizen-brand">
        <strong>城安智序</strong>
        <span>公众问题反馈</span>
      </RouterLink>
      <nav>
        <RouterLink to="/citizen/report">问题反馈</RouterLink>
        <RouterLink to="/citizen/track">查询进度</RouterLink>
      </nav>
    </header>
    <main class="citizen-main">
      <RouterView />
    </main>
    <footer class="citizen-footer">
      本平台用于风险线索收集和治理协同，不替代正式房屋安全鉴定。
    </footer>
  </div>
</template>

<style scoped lang="scss">
.citizen-shell { min-height: 100vh; background: #f5f7f6; color: #1f2937; }
.citizen-header { position: sticky; top: 0; z-index: 20; display: flex; align-items: center; justify-content: space-between; gap: 16px; min-height: 64px; padding: 10px max(16px, calc((100vw - 760px) / 2)); background: rgb(255 255 255 / 94%); border-bottom: 1px solid #e5e7eb; backdrop-filter: blur(12px); }
.citizen-brand { display: grid; color: #173f37; text-decoration: none; }
.citizen-brand strong { font-size: 20px; }
.citizen-brand span { font-size: 12px; color: #667085; }
nav { display: flex; gap: 14px; }
nav a { color: #344054; text-decoration: none; font-size: 14px; }
nav a.router-link-active { color: #16705c; font-weight: 700; }
.citizen-main { width: min(100%, 760px); min-height: calc(100vh - 124px); margin: 0 auto; padding: 20px 16px 36px; }
.citizen-footer { padding: 18px 16px 28px; color: #667085; text-align: center; font-size: 12px; }
@media (max-width: 520px) { .citizen-header { align-items: flex-start; } nav { padding-top: 4px; gap: 10px; } }
</style>
