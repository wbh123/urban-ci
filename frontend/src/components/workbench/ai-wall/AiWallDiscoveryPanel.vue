<script setup lang="ts">
import type { AiDashboardOverview } from '@/shared/api/endpoints/ai-dashboard'

defineProps<{ overview: AiDashboardOverview }>()
</script>

<template>
  <section class="ai-wall-panel discovery-panel">
    <header><div><span>✦ AI</span><strong>AI 今日发现</strong></div><small>来自真实视觉分析任务</small></header>
    <div class="discovery-grid">
      <div><span>疑似裂缝</span><strong>{{ overview.today.crackCount }}</strong></div>
      <div><span>表面剥落</span><strong>{{ overview.today.spallingCount }}</strong></div>
      <div><span>渗水 / 水渍</span><strong>{{ overview.today.waterStainCount }}</strong></div>
      <div><span>其他候选</span><strong>{{ overview.today.otherDetectionCount }}</strong></div>
    </div>
    <div class="task-status">
      <span>今日分析 <b>{{ overview.today.totalAnalyses }}</b></span>
      <span data-tone="success">完成 <b>{{ overview.today.succeeded }}</b></span>
      <span data-tone="running">分析中 <b>{{ overview.today.running }}</b></span>
      <span data-tone="failed">失败 <b>{{ overview.today.failed }}</b></span>
    </div>
  </section>
</template>

<style scoped lang="scss">
.ai-wall-panel{display:grid;gap:10px;padding:12px;border:1px solid rgba(113,224,205,.15);border-radius:14px;background:rgba(5,28,31,.78);color:#effcf9;box-shadow:0 14px 36px rgba(0,0,0,.16);backdrop-filter:blur(14px)}.ai-wall-panel header{display:flex;align-items:center;justify-content:space-between;gap:8px}.ai-wall-panel header>div{display:flex;align-items:center;gap:6px}.ai-wall-panel header>div span{color:#8cf0d9;font-size:9px;font-weight:900}.ai-wall-panel header strong{font-size:12px}.ai-wall-panel header small{color:#769b96;font-size:8px}.discovery-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:6px}.discovery-grid>div{display:grid;gap:3px;padding:8px;border-radius:10px;background:rgba(255,255,255,.045)}.discovery-grid span{color:#8fb0ac;font-size:8px}.discovery-grid strong{font-size:18px}.task-status{display:grid;grid-template-columns:repeat(4,1fr);gap:5px}.task-status>span{display:grid;gap:2px;padding:6px;border-radius:8px;background:rgba(255,255,255,.035);color:#86a8a4;font-size:8px;text-align:center}.task-status b{color:#d8f7f0;font-size:11px}.task-status span[data-tone='success'] b{color:#78e2b3}.task-status span[data-tone='running'] b{color:#f2ce7f}.task-status span[data-tone='failed'] b{color:#ff938d}@media(max-width:760px){.task-status{grid-template-columns:repeat(2,1fr)}}
</style>
