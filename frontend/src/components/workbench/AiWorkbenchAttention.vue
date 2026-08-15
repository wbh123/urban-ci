<script setup lang="ts">
import { computed } from 'vue'
import type { AiDashboardOverview } from '@/shared/api/endpoints/ai-dashboard'

const props = withDefaults(defineProps<{
  overview: AiDashboardOverview
  canReview?: boolean
  canManageInspection?: boolean
}>(), {
  canReview: false,
  canManageInspection: false,
})

const emit = defineEmits<{
  openReview: []
  openRisk: []
  openInspection: []
  openArchive: []
}>()

const inspectionAttentionCount = computed(() => (
  props.overview.metrics.inspectionAttentionCount
  ?? props.overview.attention.filter((item) => item.findings.length > 0).length
))
const dataIssueCount = computed(() => (
  props.overview.metrics.dataIssueCount
  ?? props.overview.attention.filter((item) => item.aiAttentionReasons.some((reason) => (
    reason === '风险结果已过期'
    || reason === '暂无正式风险结果'
    || reason === '档案资料不足'
  ))).length
))

function openReview(): void {
  if (props.canReview) emit('openReview')
  else emit('openRisk')
}

function openInspection(): void {
  if (props.canManageInspection) emit('openInspection')
  else emit('openRisk')
}
</script>

<template>
  <section class="attention-panel" aria-label="待人工处理">
    <header>
      <div><span>需要人接手</span><strong>待人工处理</strong></div>
      <small>AI 只给出辅助发现与排序，不自动形成专业结论</small>
    </header>

    <div class="attention-grid">
      <button type="button" data-tone="review" @click="openReview">
        <span>AI 待复核</span><strong>{{ overview.metrics.pendingReviewCount }}</strong>
        <small>{{ canReview ? '进入 AI 人工复核中心' : '查看相关风险对象' }} →</small>
      </button>
      <button type="button" data-tone="risk" @click="emit('openRisk')">
        <span>高风险楼栋</span><strong>{{ overview.metrics.highRiskCount }}</strong>
        <small>查看正式风险与优先级 →</small>
      </button>
      <button type="button" data-tone="inspection" @click="openInspection">
        <span>巡检异常关注</span><strong>{{ inspectionAttentionCount }}</strong>
        <small>{{ canManageInspection ? '进入巡检管理' : '查看 AI 关注对象' }} →</small>
      </button>
      <button type="button" data-tone="archive" @click="emit('openArchive')">
        <span>资料缺失 / 过期</span><strong>{{ dataIssueCount }}</strong>
        <small>补充空间与楼栋档案 →</small>
      </button>
    </div>
  </section>
</template>

<style scoped lang="scss">
.attention-panel{display:grid;gap:10px}.attention-panel>header{display:flex;align-items:end;justify-content:space-between;gap:14px}.attention-panel>header>div{display:grid;gap:2px}.attention-panel>header span{color:#176354;font-size:10px;font-weight:900;letter-spacing:.05em}.attention-panel>header strong{font-size:16px}.attention-panel>header small{color:var(--usp-color-text-secondary);font-size:11px}.attention-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px}.attention-grid button{display:grid;gap:5px;min-height:108px;padding:13px;border:1px solid var(--usp-color-border);border-radius:var(--usp-radius-xl);background:var(--usp-color-surface);text-align:left;box-shadow:var(--usp-shadow-sm);cursor:pointer}.attention-grid button>span{color:var(--usp-color-text-secondary);font-size:11px;font-weight:700}.attention-grid button>strong{font-size:26px;line-height:1}.attention-grid button>small{align-self:end;color:var(--usp-color-text-tertiary);font-size:10px}.attention-grid button[data-tone='review'] strong{color:#8a5d12}.attention-grid button[data-tone='risk'] strong{color:var(--usp-color-danger)}.attention-grid button[data-tone='inspection'] strong{color:#176354}.attention-grid button[data-tone='archive'] strong{color:#5b6472}
@media(max-width:980px){.attention-grid{grid-template-columns:repeat(2,1fr)}}@media(max-width:560px){.attention-panel>header{align-items:flex-start;flex-direction:column}.attention-grid{grid-template-columns:1fr 1fr}}
</style>
