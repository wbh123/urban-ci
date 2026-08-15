<script setup lang="ts">
import { computed } from 'vue'
import type { DistributionBucket } from '@/shared/api/endpoints/reports'

const props = defineProps<{
  title: string
  enableRisk: boolean
  riskDistribution: DistributionBucket[]
}>()

const maxCount = computed(() => Math.max(1, ...props.riskDistribution.map((item) => item.count)))

function width(count: number): string {
  return `${Math.max(7, Math.round((count / maxCount.value) * 100))}%`
}

function tone(code: string): string {
  const normalized = code.toUpperCase()
  if (normalized.includes('HIGH') || normalized.includes('CRITICAL')) return 'danger'
  if (normalized.includes('MEDIUM') || normalized.includes('MODERATE')) return 'warning'
  if (normalized.includes('LOW')) return 'success'
  return 'neutral'
}
</script>

<template>
  <el-card class="trend-panel" shadow="never">
    <template #header>
      <div class="trend-header">
        <div>
          <strong>{{ riskDistribution.length > 0 ? '风险等级分布' : title }}</strong>
          <span>{{ riskDistribution.length > 0 ? '当前区域建筑风险结构' : '当前角色业务闭环' }}</span>
        </div>
        <el-tag v-if="riskDistribution.length > 0" type="warning" effect="plain" round>风险态势</el-tag>
      </div>
    </template>

    <div v-if="riskDistribution.length > 0" class="distribution-board">
      <div v-for="bucket in riskDistribution" :key="bucket.code" class="distribution-item">
        <div class="distribution-meta">
          <span><i :data-tone="tone(bucket.code)" />{{ bucket.label }}</span>
          <strong>{{ bucket.count }}</strong>
        </div>
        <div class="distribution-track">
          <span class="distribution-fill" :data-tone="tone(bucket.code)" :style="{ width: width(bucket.count) }" />
        </div>
      </div>
    </div>

    <div v-else class="workflow-board">
      <div class="workflow-step">
        <span class="step-index">01</span>
        <div><strong>发现与受理</strong><p>空间档案、巡检或公众反馈进入业务。</p></div>
      </div>
      <span class="workflow-arrow">→</span>
      <div class="workflow-step">
        <span class="step-index">02</span>
        <div><strong>核查与复核</strong><p>结合现场证据与专业判断完成核查。</p></div>
      </div>
      <span class="workflow-arrow">→</span>
      <div class="workflow-step">
        <span class="step-index">03</span>
        <div><strong>入图与治理</strong><p>确认结果进入空间地图与后续治理。</p></div>
      </div>
    </div>
  </el-card>
</template>

<style scoped lang="scss">
.trend-panel { border-radius: var(--usp-radius-lg); }
.trend-panel :deep(.el-card__header) { padding: 12px 16px; }
.trend-panel :deep(.el-card__body) { padding: 14px 16px; }
.trend-header { display: flex; align-items: center; justify-content: space-between; gap: var(--usp-space-3); }
.trend-header > div { display: flex; align-items: baseline; gap: 10px; }
.trend-header span,
.workflow-step p { color: var(--usp-color-text-secondary); font-size: 11px; }
.distribution-board { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: var(--usp-space-3); }
.distribution-item { display: grid; gap: 8px; padding: 10px 12px; border-radius: var(--usp-radius-md); background: var(--usp-color-surface-muted, #f8fafc); }
.distribution-meta { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.distribution-meta > span { display: inline-flex; align-items: center; gap: 7px; font-size: 12px; }
.distribution-meta i { width: 8px; height: 8px; border-radius: 50%; background: #64748b; }
.distribution-track { overflow: hidden; height: 7px; border-radius: 999px; background: rgba(148, 163, 184, .18); }
.distribution-fill { display: block; height: 100%; border-radius: inherit; background: #64748b; }
.distribution-meta i[data-tone='danger'], .distribution-fill[data-tone='danger'] { background: #dc2626; }
.distribution-meta i[data-tone='warning'], .distribution-fill[data-tone='warning'] { background: #f59e0b; }
.distribution-meta i[data-tone='success'], .distribution-fill[data-tone='success'] { background: #16a34a; }
.workflow-board { display: grid; grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr) auto minmax(0, 1fr); align-items: center; gap: var(--usp-space-3); }
.workflow-step { display: flex; align-items: flex-start; gap: 10px; min-height: 72px; padding: 11px 12px; border-radius: var(--usp-radius-md); background: linear-gradient(135deg, var(--usp-color-surface-muted, #f8fafc), rgba(239, 246, 255, .8)); }
.step-index { color: var(--usp-color-primary); font-size: 11px; font-weight: 900; }
.workflow-step p { margin: 4px 0 0; line-height: 1.5; }
.workflow-arrow { color: var(--usp-color-primary); font-size: 18px; font-weight: 800; }
@media (max-width: 900px) { .workflow-board { grid-template-columns: 1fr; } .workflow-arrow { display: none; } }
</style>
