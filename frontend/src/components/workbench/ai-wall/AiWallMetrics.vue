<script setup lang="ts">
import { computed } from 'vue'
import type { AiDashboardOverview } from '@/shared/api/endpoints/ai-dashboard'

const props = defineProps<{ overview: AiDashboardOverview }>()

const items = computed(() => [
  { key: 'buildings', label: '纳管楼栋', value: props.overview.metrics.buildingCount, suffix: '栋' },
  { key: 'analyzed', label: 'AI 已分析', value: props.overview.metrics.aiAnalyzedBuildingCount, suffix: '栋' },
  { key: 'detections', label: 'AI 发现病害', value: props.overview.metrics.detectionCount, suffix: '处' },
  { key: 'risk', label: '高风险楼栋', value: props.overview.metrics.highRiskCount, suffix: '栋' },
  { key: 'review', label: '待人工复核', value: props.overview.metrics.pendingReviewCount, suffix: '项' },
  { key: 'coverage', label: 'AI 分析覆盖率', value: props.overview.metrics.analysisCoverageRate, suffix: '%' },
])
</script>

<template>
  <section class="ai-wall-metrics" aria-label="AI 态势核心指标">
    <article v-for="item in items" :key="item.key" :data-kind="item.key">
      <span>{{ item.label }}</span>
      <div><strong>{{ item.value.toLocaleString('zh-CN') }}</strong><small>{{ item.suffix }}</small></div>
    </article>
  </section>
</template>

<style scoped lang="scss">
.ai-wall-metrics{position:relative;z-index:32;display:grid;grid-template-columns:repeat(6,minmax(105px,1fr));gap:8px}.ai-wall-metrics article{display:grid;gap:5px;padding:9px 11px;border:1px solid rgba(119,218,202,.13);border-radius:13px;background:rgba(5,29,32,.76);color:#eefcf9;backdrop-filter:blur(12px)}.ai-wall-metrics article>span{color:#8cb2ad;font-size:9px;font-weight:800}.ai-wall-metrics article>div{display:flex;align-items:baseline;gap:4px}.ai-wall-metrics strong{font-size:21px;line-height:1;font-variant-numeric:tabular-nums}.ai-wall-metrics small{color:#7da49f;font-size:8px}.ai-wall-metrics article[data-kind='detections'] strong{color:#f4ca7d}.ai-wall-metrics article[data-kind='risk'] strong{color:#ff9d92}.ai-wall-metrics article[data-kind='review'] strong{color:#d4b6ff}.ai-wall-metrics article[data-kind='coverage'] strong{color:#8df2d9}@media(max-width:1100px){.ai-wall-metrics{grid-template-columns:repeat(3,1fr)}}@media(max-width:620px){.ai-wall-metrics{grid-template-columns:repeat(2,1fr)}}
</style>
