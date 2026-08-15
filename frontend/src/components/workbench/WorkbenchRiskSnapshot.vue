<script setup lang="ts">
import { computed } from 'vue'
import type { RiskOverview } from '@/shared/api/endpoints/reports'

const props = defineProps<{
  overview: RiskOverview | null
  loading: boolean
  error: boolean
}>()

const emit = defineEmits<{
  open: []
}>()

const metrics = computed(() => {
  const summary = props.overview?.summary
  if (!summary) return []
  return [
    { key: 'buildings', label: '楼栋总数', value: summary.buildingCount, hint: '当前统计范围', tone: 'primary' },
    { key: 'assessed', label: '已评估', value: summary.assessedBuildingCount, hint: '已有风险结果', tone: 'success' },
    { key: 'highRisk', label: '高风险', value: summary.highRiskCount, hint: '建议重点关注', tone: 'danger' },
    { key: 'highPriority', label: '高优先级', value: summary.highPriorityCount, hint: '更新治理优先', tone: 'warning' },
  ] as const
})

const distributionMax = computed(() => Math.max(
  1,
  ...(props.overview?.riskDistribution ?? []).map((item) => item.count),
))

function distributionWidth(count: number): string {
  return `${Math.max(6, Math.round((count / distributionMax.value) * 100))}%`
}

function riskTone(code: string): string {
  const normalized = code.toUpperCase()
  if (normalized.includes('HIGH') || normalized.includes('CRITICAL')) return 'danger'
  if (normalized.includes('MEDIUM') || normalized.includes('MODERATE')) return 'warning'
  if (normalized.includes('LOW')) return 'success'
  return 'neutral'
}
</script>

<template>
  <section class="risk-snapshot" aria-label="区域风险态势快照">
    <div class="snapshot-heading">
      <div>
        <span class="snapshot-kicker">区域风险态势</span>
        <h2>城市建筑安全态势</h2>
        <p>汇总当前统计范围内的楼栋风险和治理优先级，帮助快速识别重点对象。</p>
      </div>
      <el-button type="primary" plain round @click="emit('open')">查看风险全景 →</el-button>
    </div>

    <el-skeleton v-if="loading" :rows="2" animated />

    <div v-else-if="overview" class="snapshot-body">
      <div class="snapshot-metrics">
        <article
          v-for="metric in metrics"
          :key="metric.key"
          class="snapshot-metric"
          :data-tone="metric.tone"
        >
          <span>{{ metric.label }}</span>
          <strong>{{ metric.value.toLocaleString('zh-CN') }}</strong>
          <small>{{ metric.hint }}</small>
        </article>
      </div>

      <div class="risk-distribution">
        <div class="distribution-title">
          <strong>风险等级分布</strong>
          <small>更新时间 {{ new Date(overview.generatedAt).toLocaleString('zh-CN') }}</small>
        </div>
        <div class="distribution-list">
          <div
            v-for="bucket in overview.riskDistribution"
            :key="bucket.code"
            class="distribution-row"
          >
            <span class="distribution-label">
              <i :data-tone="riskTone(bucket.code)" />
              {{ bucket.label }}
            </span>
            <span class="distribution-track">
              <span
                class="distribution-bar"
                :data-tone="riskTone(bucket.code)"
                :style="{ width: distributionWidth(bucket.count) }"
              />
            </span>
            <strong>{{ bucket.count }}</strong>
          </div>
          <p v-if="overview.riskDistribution.length === 0" class="distribution-empty">当前暂无风险分布数据</p>
        </div>
      </div>
    </div>

    <div v-else-if="error" class="snapshot-unavailable">
      <strong>风险态势暂未加载</strong>
      <span>其他业务功能仍可正常使用，可稍后刷新风险总览。</span>
    </div>
  </section>
</template>

<style scoped lang="scss">
.risk-snapshot {
  position: relative;
  overflow: hidden;
  display: grid;
  gap: var(--usp-space-4);
  padding: 22px 24px;
  border: 1px solid rgba(59, 130, 246, .14);
  border-radius: var(--usp-radius-xl);
  background:
    radial-gradient(circle at 92% 0%, rgba(59, 130, 246, .13), transparent 34%),
    linear-gradient(135deg, rgba(248, 250, 252, .98), rgba(239, 246, 255, .9));
  box-shadow: var(--usp-shadow-md);
}

.snapshot-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--usp-space-4);
}

.snapshot-heading > div {
  display: grid;
  gap: 5px;
}

.snapshot-kicker {
  color: var(--usp-color-primary);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: .12em;
}

.snapshot-heading h2,
.snapshot-heading p {
  margin: 0;
}

.snapshot-heading h2 {
  font-size: 22px;
}

.snapshot-heading p,
.distribution-title small,
.snapshot-metric small,
.snapshot-unavailable span {
  color: var(--usp-color-text-secondary);
}

.snapshot-body {
  display: grid;
  grid-template-columns: minmax(0, 1.12fr) minmax(320px, .88fr);
  gap: var(--usp-space-4);
}

.snapshot-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--usp-space-3);
}

.snapshot-metric {
  position: relative;
  overflow: hidden;
  display: grid;
  min-height: 112px;
  align-content: center;
  gap: 4px;
  padding: 14px 16px;
  border: 1px solid rgba(148, 163, 184, .18);
  border-radius: var(--usp-radius-lg);
  background: rgba(255, 255, 255, .86);
  box-shadow: var(--usp-shadow-sm);
}

.snapshot-metric::before {
  position: absolute;
  top: 0;
  right: 0;
  left: 0;
  height: 3px;
  background: #3b82f6;
  content: '';
}

.snapshot-metric[data-tone='success']::before { background: #16a34a; }
.snapshot-metric[data-tone='warning']::before { background: #f59e0b; }
.snapshot-metric[data-tone='danger']::before { background: #dc2626; }

.snapshot-metric > span {
  color: var(--usp-color-text-secondary);
  font-size: 13px;
}

.snapshot-metric strong {
  font-size: clamp(25px, 2.4vw, 36px);
  line-height: 1.05;
  font-variant-numeric: tabular-nums;
}

.risk-distribution {
  display: grid;
  gap: var(--usp-space-3);
  padding: 14px 16px;
  border-radius: var(--usp-radius-lg);
  background: rgba(255, 255, 255, .74);
  box-shadow: var(--usp-shadow-sm);
}

.distribution-title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--usp-space-3);
}

.distribution-title small {
  font-size: 11px;
}

.distribution-list {
  display: grid;
  gap: 10px;
}

.distribution-row {
  display: grid;
  grid-template-columns: 88px minmax(70px, 1fr) 32px;
  align-items: center;
  gap: 10px;
  font-size: 12px;
}

.distribution-label {
  display: flex;
  align-items: center;
  gap: 7px;
}

.distribution-label i {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: #64748b;
}

.distribution-label i[data-tone='danger'],
.distribution-bar[data-tone='danger'] { background: #dc2626; }
.distribution-label i[data-tone='warning'],
.distribution-bar[data-tone='warning'] { background: #f59e0b; }
.distribution-label i[data-tone='success'],
.distribution-bar[data-tone='success'] { background: #16a34a; }

.distribution-track {
  overflow: hidden;
  height: 7px;
  border-radius: 999px;
  background: rgba(148, 163, 184, .18);
}

.distribution-bar {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #64748b;
}

.distribution-empty {
  margin: 0;
  color: var(--usp-color-text-secondary);
  font-size: 12px;
}

.snapshot-unavailable {
  display: grid;
  gap: 4px;
  padding: 12px 14px;
  border-radius: var(--usp-radius-lg);
  background: rgba(255, 255, 255, .76);
}

@media (max-width: 1180px) {
  .snapshot-body {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 820px) {
  .snapshot-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .snapshot-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
