<script setup lang="ts">
import { computed } from 'vue'
import type { BuildingRiskSummaryView } from './building-business'

const props = defineProps<{ summary: BuildingRiskSummaryView }>()

const RISK_LABELS: Record<string, string> = {
  LOW: '低风险',
  MEDIUM: '中风险',
  HIGH: '高风险',
  VERY_HIGH: '极高风险',
  CRITICAL: '重大风险',
}

const PRIORITY_LABELS: Record<string, string> = {
  P1: '一级优先',
  P2: '二级优先',
  P3: '三级优先',
  P4: '四级优先',
}

const freshnessLabel = computed(() => {
  if (props.summary.freshness === 'CURRENT') return '当前有效'
  if (props.summary.freshness === 'STALE') return '结果已过期'
  return '暂无正式风险评分'
})
const freshnessType = computed<'success' | 'warning' | 'info'>(() => props.summary.freshness === 'CURRENT'
  ? 'success'
  : props.summary.freshness === 'STALE' ? 'warning' : 'info')

function fixed(value: number | undefined): string {
  return Number.isFinite(value) ? Number(value).toFixed(2) : '—'
}

function riskLabel(value: string | undefined): string {
  return value ? (RISK_LABELS[value] ?? value) : '—'
}

function priorityLabel(value: string | undefined): string {
  return value ? (PRIORITY_LABELS[value] ?? value) : '—'
}
</script>

<template>
  <el-card shadow="never" class="risk-summary-panel">
    <template #header>
      <div class="risk-head">
        <strong>风险与更新优先级</strong>
        <el-tag :type="freshnessType">{{ freshnessLabel }}</el-tag>
      </div>
    </template>

    <div v-if="summary.freshness === 'NO_RESULT'" class="risk-empty">
      <el-empty description="暂无正式风险评分" :image-size="72" />
      <p>当前楼栋尚未形成可用的正式评分结果，可在证据和巡检信息完整后发起评分。</p>
    </div>

    <template v-else>
      <div class="risk-metrics">
        <div><span>风险评分</span><strong>{{ fixed(summary.riskScore) }}</strong></div>
        <div><span>风险等级</span><strong>{{ riskLabel(summary.riskLevel) }}</strong></div>
        <div><span>完整度</span><strong>{{ fixed(summary.completenessScore) }}</strong></div>
        <div><span>置信度</span><strong>{{ fixed(summary.confidenceScore) }}</strong></div>
        <div><span>优先级评分</span><strong>{{ fixed(summary.priorityScore) }}</strong></div>
        <div><span>更新优先级</span><strong>{{ priorityLabel(summary.priorityLevel) }}</strong></div>
      </div>

      <el-alert
        v-if="summary.needManualReview"
        class="manual-review-alert"
        type="warning"
        :closable="false"
        show-icon
        title="需要人工复核"
        description="当前正式评分存在需要进一步人工核查的因素，请在复核完成后再推进后续处置。"
      />

      <section v-if="summary.recommendations?.length" class="recommendations">
        <strong>建议关注</strong>
        <ul><li v-for="item in summary.recommendations" :key="item">{{ item }}</li></ul>
      </section>
    </template>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="人工智能结果仅用于辅助分析"
      description="辅助分析不作为正式鉴定结论；正式风险评分与处置决策应以系统规则、经审核证据和人工专业判断为准。"
    />
  </el-card>
</template>

<style scoped lang="scss">
.risk-summary-panel{min-width:0}.risk-head{display:flex;align-items:center;justify-content:space-between;gap:var(--usp-space-3)}.risk-metrics{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:var(--usp-space-3);margin-bottom:var(--usp-space-4)}.risk-metrics div{display:grid;gap:4px;padding:12px;border:1px solid var(--usp-color-border);border-radius:var(--usp-radius-md);background:var(--usp-color-bg)}.risk-metrics span{font-size:12px;color:var(--usp-color-text-secondary)}.risk-metrics strong{font-size:18px}.risk-empty{margin-bottom:var(--usp-space-4);text-align:center}.risk-empty p{margin:0;color:var(--usp-color-text-secondary);font-size:13px}.manual-review-alert{margin-bottom:var(--usp-space-3)}.recommendations{margin-bottom:var(--usp-space-3)}.recommendations ul{margin:8px 0 0;padding-left:20px;color:var(--usp-color-text-secondary);line-height:1.7}@media(max-width:800px){.risk-metrics{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:520px){.risk-metrics{grid-template-columns:1fr}.risk-head{align-items:flex-start;flex-direction:column}}
</style>
