<script setup lang="ts">
import { computed } from 'vue'
import type { AiDashboardBuilding } from '@/shared/api/endpoints/ai-dashboard'
import { formatAiDetectionLabel } from '@/shared/ai/ai-display'

const props = withDefaults(defineProps<{
  building: AiDashboardBuilding | null
  canReview?: boolean
  canManageInspection?: boolean
}>(), {
  canReview: false,
  canManageInspection: false,
})

const emit = defineEmits<{
  close: []
  openBuilding: [buildingId: string]
  openReview: []
  openInspection: [buildingId: string]
}>()

const evidenceTotal = computed(() => {
  const building = props.building
  if (!building) return 0
  return building.evidenceCounts.visual
    + building.evidenceCounts.inspection
    + building.evidenceCounts.archive
    + building.evidenceCounts.formalRisk
})

const reviewStatus = computed(() => {
  const building = props.building
  if (!building) return '当前无待复核'
  if (building.pendingReviewCount > 0) return `${building.pendingReviewCount} 项待复核`
  if (building.needManualReview) return '建议人工确认'
  return '当前无待复核'
})

const governanceSuggestion = computed(() => {
  const building = props.building
  if (!building) return ''
  if (building.pendingReviewCount > 0 || building.needManualReview) {
    return `存在 ${Math.max(building.pendingReviewCount, 1)} 项需要人工确认的结果，建议先完成人工复核，再进入正式治理决策。`
  }
  if (building.freshness === 'STALE') {
    return '正式风险结果已过期，建议先更新评估，再结合 AI 发现安排后续治理。'
  }
  if (building.findings.length > 0 && props.canManageInspection) {
    return '存在视觉病害候选，建议结合现场巡检核实具体位置、范围与严重程度。'
  }
  if (building.findings.length > 0) {
    return '存在视觉病害候选，建议由有权限的人员结合现场记录完成人工核实。'
  }
  return '当前未发现额外高关注候选；继续以正式风险结果和最新巡检记录作为治理依据。'
})

function riskLabel(level?: string | null): string {
  if (level === 'VERY_HIGH') return '极高风险'
  if (level === 'HIGH') return '高风险'
  if (level === 'MEDIUM') return '中风险'
  if (level === 'LOW') return '低风险'
  return '待评估'
}

function attentionLabel(level?: string): string {
  if (level === 'HIGH') return '高关注'
  if (level === 'MEDIUM') return '中关注'
  if (level === 'LOW') return '一般关注'
  return '常规'
}

function freshnessLabel(value?: string | null): string {
  if (value === 'CURRENT') return '当前有效'
  if (value === 'STALE') return '结果已过期'
  if (value === 'NO_RESULT') return '暂无正式结果'
  return '状态待确认'
}

function formatRiskScore(value?: number | null): string {
  return value == null ? '暂无分值' : `分值 ${value.toFixed(1)}`
}

function formatDate(value?: string | null): string {
  if (!value) return '暂无记录'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN')
}
</script>

<template>
  <aside v-if="building" class="building-drawer">
    <header>
      <div><span>楼栋 AI 治理摘要</span><strong>{{ building.communityName || '未命名小区' }} · {{ building.buildingName }}</strong></div>
      <button type="button" aria-label="关闭楼栋摘要" @click="emit('close')">×</button>
    </header>

    <div class="status-grid">
      <div>
        <small>正式风险</small>
        <strong>{{ riskLabel(building.riskLevel) }}</strong>
        <em>{{ formatRiskScore(building.riskScore) }}</em>
      </div>
      <div><small>AI 关注</small><strong>{{ attentionLabel(building.aiAttentionLevel) }}</strong><em>辅助治理排序</em></div>
      <div><small>治理优先级</small><strong>{{ building.priorityLevel || '—' }}</strong><em>正式更新顺序</em></div>
      <div><small>数据时效</small><strong>{{ freshnessLabel(building.freshness) }}</strong><em>{{ formatDate(building.latestAiAt) }}</em></div>
    </div>

    <section>
      <h4>✦ AI 最近发现</h4>
      <div v-if="building.findings.length" class="finding-list">
        <span v-for="item in building.findings" :key="`${item.classCode}-${item.className}`">
          {{ formatAiDetectionLabel(item.className, item.maxConfidence) }} ×{{ item.count }}
        </span>
      </div>
      <p v-else class="muted">最近一次 AI 分析没有可展示的病害候选。</p>
    </section>

    <section>
      <h4>AI 综合判断</h4>
      <p>{{ building.latestAiSummary || building.aiAttentionReasons.join('；') || '当前没有额外 AI 综合判断。' }}</p>
      <small class="inspection-time">最近巡检：{{ formatDate(building.latestInspectionAt) }}</small>
    </section>

    <section class="review-section">
      <div class="section-heading"><h4>人工复核</h4><strong>{{ reviewStatus }}</strong></div>
      <p v-if="building.pendingReviewCount > 0 || building.needManualReview">AI 候选仍需人工确认，复核结果不会被 AI 自动写成正式风险结论。</p>
      <p v-else>当前没有待人工确认的 AI 结果，正式风险结论仍以现有业务规则与人工记录为准。</p>
    </section>

    <section>
      <div class="section-heading"><h4>分析依据</h4><strong>共 {{ evidenceTotal }} 条</strong></div>
      <div class="evidence-grid">
        <div><span>视觉证据</span><strong>{{ building.evidenceCounts.visual }}</strong></div>
        <div><span>巡检</span><strong>{{ building.evidenceCounts.inspection }}</strong></div>
        <div><span>档案</span><strong>{{ building.evidenceCounts.archive }}</strong></div>
        <div><span>正式风险</span><strong>{{ building.evidenceCounts.formalRisk }}</strong></div>
      </div>
    </section>

    <section class="suggestion-section">
      <h4>下一步建议</h4>
      <p>{{ governanceSuggestion }}</p>
    </section>

    <footer>
      <button type="button" @click="emit('openBuilding', building.buildingId)">进入楼栋详情</button>
      <button v-if="canManageInspection" type="button" @click="emit('openInspection', building.buildingId)">查看巡检</button>
      <button v-if="canReview && building.pendingReviewCount > 0" type="button" class="primary" @click="emit('openReview')">进入人工复核</button>
    </footer>
  </aside>
</template>

<style scoped lang="scss">
.building-drawer{position:absolute;z-index:38;right:18px;bottom:94px;display:grid;width:min(380px,calc(100% - 36px));max-height:calc(100% - 190px);gap:11px;overflow:auto;padding:14px;border:1px solid rgba(126,232,214,.2);border-radius:16px;background:rgba(5,27,30,.92);color:#edfdf9;box-shadow:0 22px 58px rgba(0,0,0,.32);backdrop-filter:blur(18px)}.building-drawer>header{display:flex;align-items:flex-start;justify-content:space-between;gap:12px}.building-drawer>header>div{display:grid;gap:2px}.building-drawer>header span{color:#7fc7ba;font-size:8px;font-weight:800}.building-drawer>header strong{font-size:14px}.building-drawer>header button{width:26px;height:26px;border:0;border-radius:50%;background:rgba(255,255,255,.06);color:#c8ded9;font-size:16px;cursor:pointer}.status-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:6px}.status-grid>div{display:grid;gap:3px;padding:8px;border-radius:10px;background:rgba(255,255,255,.045)}.status-grid small{color:#789e98;font-size:7px}.status-grid strong{font-size:11px}.status-grid em{overflow:hidden;color:#6f948e;font-size:7px;font-style:normal;text-overflow:ellipsis;white-space:nowrap}.building-drawer section{display:grid;gap:6px}.building-drawer h4{margin:0;color:#bff7ea;font-size:10px}.building-drawer p{margin:0;color:#9abcb6;font-size:9px;line-height:1.6}.section-heading{display:flex;align-items:center;justify-content:space-between;gap:8px}.section-heading>strong{color:#9ddfd3;font-size:8px}.review-section,.suggestion-section{padding:9px;border:1px solid rgba(119,220,203,.1);border-radius:10px;background:rgba(255,255,255,.025)}.finding-list{display:flex;flex-wrap:wrap;gap:5px}.finding-list span{padding:4px 6px;border-radius:8px;background:rgba(244,190,85,.09);color:#f1d497;font-size:8px}.inspection-time{color:#789e98;font-size:7px}.evidence-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:5px}.evidence-grid>div{display:grid;gap:2px;padding:7px;border-radius:9px;background:rgba(255,255,255,.04)}.evidence-grid span{color:#779c96;font-size:7px}.evidence-grid strong{font-size:12px}.building-drawer footer{display:flex;flex-wrap:wrap;gap:5px;padding-top:3px}.building-drawer footer button{padding:6px 8px;border:1px solid rgba(122,219,202,.15);border-radius:9px;background:rgba(255,255,255,.045);color:#cceae4;font-size:8px;font-weight:700;cursor:pointer}.building-drawer footer button:first-child{border-color:rgba(96,222,199,.26);background:rgba(72,185,165,.1);color:#e4fff9}.building-drawer footer button.primary{background:rgba(79,210,183,.16);color:#c8fff3}.muted{color:#708f8a!important}@media(max-width:760px){.building-drawer{right:12px;bottom:86px;width:calc(100% - 24px);max-height:52%}.status-grid{grid-template-columns:repeat(2,1fr)}}
</style>