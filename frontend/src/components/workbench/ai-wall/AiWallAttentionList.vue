<script setup lang="ts">
import type { AiDashboardBuilding } from '@/shared/api/endpoints/ai-dashboard'

defineProps<{ items: AiDashboardBuilding[] }>()
const emit = defineEmits<{
  focus: [building: AiDashboardBuilding]
  open: [building: AiDashboardBuilding]
}>()

function attentionLabel(level: AiDashboardBuilding['aiAttentionLevel']): string {
  if (level === 'HIGH') return '高关注'
  if (level === 'MEDIUM') return '中关注'
  if (level === 'LOW') return '一般关注'
  return '常规'
}

function suggestion(item: AiDashboardBuilding): string {
  if (item.pendingReviewCount > 0) return '建议：优先人工复核'
  if (item.aiAttentionReasons.includes('风险结果已过期')) return '建议：补充巡检并更新风险'
  if (item.aiAttentionReasons.includes('暂无正式风险结果')) return '建议：完成正式风险评估'
  if (item.findings.length > 0) return '建议：核对 AI 病害证据'
  if (item.aiAttentionReasons.includes('正式高风险')) return '建议：核对治理优先级'
  return '建议：结合档案与巡检继续核查'
}
</script>

<template>
  <section class="attention-panel">
    <header><div><span>✦ AI</span><strong>AI 重点关注</strong></div><small>点击聚焦地图</small></header>
    <div v-if="items.length" class="attention-items">
      <article v-for="(item, index) in items.slice(0, 7)" :key="item.buildingId" :data-level="item.aiAttentionLevel">
        <button type="button" class="focus" @click="emit('focus', item)">
          <span class="rank">{{ String(index + 1).padStart(2, '0') }}</span>
          <span class="copy">
            <strong>{{ item.communityName || '未命名小区' }} · {{ item.buildingName }}</strong>
            <small>{{ item.aiAttentionReasons.slice(0, 2).join(' · ') || 'AI 已完成辅助分析' }}</small>
            <em>{{ suggestion(item) }}</em>
          </span>
          <span class="level">{{ attentionLabel(item.aiAttentionLevel) }}</span>
        </button>
        <button type="button" class="detail" @click="emit('open', item)">详情</button>
      </article>
    </div>
    <p v-else class="empty">当前没有需要额外关注的 AI 对象。</p>
  </section>
</template>

<style scoped lang="scss">
.attention-panel{display:grid;gap:8px;padding:12px;border:1px solid rgba(113,224,205,.15);border-radius:14px;background:rgba(5,28,31,.8);color:#effcf9;box-shadow:0 14px 36px rgba(0,0,0,.16);backdrop-filter:blur(14px)}.attention-panel>header{display:flex;align-items:center;justify-content:space-between;gap:8px}.attention-panel>header>div{display:flex;align-items:center;gap:6px}.attention-panel>header span{color:#8cf0d9;font-size:9px;font-weight:900}.attention-panel>header strong{font-size:12px}.attention-panel>header small{color:#769b96;font-size:8px}.attention-items{display:grid;gap:5px;min-height:0;overflow:auto}.attention-items article{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:4px;border-radius:10px;background:rgba(255,255,255,.04)}.attention-items article[data-level='HIGH']{box-shadow:inset 2px 0 0 rgba(255,110,105,.7)}.attention-items article[data-level='MEDIUM']{box-shadow:inset 2px 0 0 rgba(242,196,90,.65)}.focus{display:grid;grid-template-columns:24px minmax(0,1fr) auto;align-items:center;gap:7px;padding:8px;border:0;background:transparent;color:inherit;text-align:left;cursor:pointer}.rank{color:#6e9992;font-size:8px;font-weight:900}.copy{display:grid;min-width:0;gap:1px}.copy strong,.copy small,.copy em{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.copy strong{font-size:9px}.copy small{color:#88aaa5;font-size:8px}.copy em{color:#b9d6d1;font-size:7px;font-style:normal}.level{padding:3px 5px;border-radius:999px;background:rgba(255,255,255,.06);color:#a9cbc5;font-size:7px;font-weight:800}.attention-items article[data-level='HIGH'] .level{background:rgba(255,102,100,.12);color:#ffaca7}.attention-items article[data-level='MEDIUM'] .level{background:rgba(245,196,78,.1);color:#f4d691}.detail{align-self:center;margin-right:6px;padding:4px 6px;border:1px solid rgba(139,222,208,.13);border-radius:8px;background:rgba(255,255,255,.03);color:#8db1ab;font-size:7px;cursor:pointer}.empty{margin:0;padding:18px 8px;color:#789d97;font-size:9px;text-align:center}
</style>
