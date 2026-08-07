<script setup lang="ts">
import type { RenewalPriorityRow } from '@/shared/api'
defineProps<{ rows: RenewalPriorityRow[] }>()
const emit = defineEmits<{ open: [buildingId: string] }>()
function priorityClass(level: string): string { return `priority-${level.toLowerCase()}` }
function reasons(row: RenewalPriorityRow): string[] { return (row.mainReasons ?? []).slice(0, 3) }
</script>
<template>
  <div class="ranking-table-wrap">
    <table class="ranking-table">
      <thead><tr><th>正式排名</th><th>楼栋</th><th>优先级分</th><th>优先级</th><th>风险分</th><th>风险等级</th><th>置信度</th><th>完整度</th><th>排序解释</th><th>居民数</th><th>复核建议</th><th>结果状态</th><th>排名范围键</th></tr></thead>
      <tbody>
        <tr v-for="row in rows" :key="row.buildingId" :data-building-id="row.buildingId">
          <td class="ranking-number">{{ row.ranking }}</td>
          <td><button type="button" class="building-link" @click="emit('open', row.buildingId)">{{ row.buildingName || row.buildingCode }}</button><small>{{ row.communityName }} · {{ row.buildingCode }}</small></td>
          <td>{{ row.priorityScore }}</td><td><span class="level-pill" :class="priorityClass(row.priorityLevel)">{{ row.priorityLevel }}</span></td>
          <td>{{ row.riskScore }}</td><td>{{ row.riskLevel }}</td><td>{{ row.confidenceScore }}</td><td>{{ row.completenessScore ?? '--' }}</td>
          <td class="reason-cell"><span v-if="!reasons(row).length">暂无主要因素</span><span v-for="reason in reasons(row)" :key="reason" class="reason-tag">{{ reason }}</span></td>
          <td>{{ row.residentCount }}</td>
          <td><span v-if="row.needProfessionalInspection">专业检测</span><span v-else-if="row.needManualReview">人工复核</span><span v-else>常态跟踪</span></td>
          <td><span class="status-pill" :class="{ stale: row.status === 'STALE' }">{{ row.status === 'CURRENT' ? '当前有效' : '需重新计算' }}</span></td>
          <td>{{ row.rankingScopeKey }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
<style scoped lang="scss">
.ranking-table-wrap { overflow-x: auto; }.ranking-table { width: 100%; min-width: 1520px; border-collapse: collapse; background: #fff; }th, td { padding: 12px 10px; border-bottom: 1px solid #ebeef2; text-align: left; white-space: nowrap; }th { color: #475467; background: #f8fafb; font-size: 13px; }.ranking-number { font-size: 18px; font-weight: 800; }.building-link { display: block; padding: 0; border: 0; background: transparent; color: #176354; font: inherit; font-weight: 700; cursor: pointer; }td small { display: block; margin-top: 4px; color: #667085; }.level-pill { display: inline-block; padding: 3px 9px; border-radius: 999px; background: #eef2f6; }.priority-p1 { background: #fee4e2; color: #b42318; }.priority-p2 { background: #fef0c7; color: #b54708; }.priority-p3 { background: #dcfae6; color: #067647; }.reason-cell { max-width: 340px; white-space: normal; }.reason-tag { display: inline-block; margin: 2px 4px 2px 0; padding: 3px 7px; border-radius: 6px; background: #eef6f4; color: #176354; font-size: 12px; }.status-pill { padding: 3px 8px; border-radius: 999px; background: #dcfae6; color: #067647; }.status-pill.stale { background: #fef0c7; color: #b54708; }
</style>
