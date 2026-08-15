<script setup lang="ts">
import type { AiDashboardOverview } from '@/shared/api/endpoints/ai-dashboard'

const props = defineProps<{ overview: AiDashboardOverview }>()
const emit = defineEmits<{
  openWall: []
  openBuilding: [buildingId: string]
}>()
</script>

<template>
  <section class="ai-brief" aria-label="AI 今日简报">
    <div class="ai-brief__main">
      <div class="ai-brief__headline">
        <span class="ai-mark">✦ AI 今日</span>
        <strong>已处理 {{ props.overview.today.totalAnalyses }} 项城市建筑安全视觉分析任务</strong>
        <p>AI 负责发现、解释和辅助排序；正式风险评分仍由确定性规则与人工专业复核形成。</p>
      </div>

      <div class="ai-brief__facts">
        <div><strong>{{ props.overview.today.succeeded }}</strong><span>今日分析完成</span></div>
        <div><strong>{{ props.overview.metrics.aiAnalyzedImageCount }}</strong><span>累计识别图片</span></div>
        <div><strong>{{ props.overview.metrics.pendingReviewCount }}</strong><span>等待人工复核</span></div>
      </div>

      <button type="button" class="wall-entry" @click="emit('openWall')">
        <span>进入 AI 态势大屏</span><b>→</b>
      </button>
    </div>

    <aside class="ai-brief__attention">
      <header><span>AI 建议优先关注</span><small>仅辅助治理排序</small></header>
      <div v-if="props.overview.attention.length" class="attention-list">
        <button
          v-for="(item, index) in props.overview.attention.slice(0, 3)"
          :key="item.buildingId"
          type="button"
          @click="emit('openBuilding', item.buildingId)"
        >
          <span class="index">{{ String(index + 1).padStart(2, '0') }}</span>
          <span class="copy"><strong>{{ item.communityName || '未命名小区' }} · {{ item.buildingName }}</strong><small>{{ item.aiAttentionReasons.slice(0, 2).join(' · ') || 'AI 已完成辅助分析' }}</small></span>
          <span class="level" :data-level="item.aiAttentionLevel">{{ item.aiAttentionLevel === 'HIGH' ? '高关注' : item.aiAttentionLevel === 'MEDIUM' ? '中关注' : '关注' }}</span>
        </button>
      </div>
      <p v-else class="empty">当前没有需要额外关注的 AI 对象。</p>
    </aside>
  </section>
</template>

<style scoped lang="scss">
.ai-brief{display:grid;grid-template-columns:minmax(0,1.45fr) minmax(300px,.8fr);gap:14px;padding:16px;border:1px solid #cfe7df;border-radius:var(--usp-radius-xl);background:linear-gradient(145deg,#f5fbf9,#fff);box-shadow:var(--usp-shadow-sm)}
.ai-brief__main{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:14px 20px;align-items:center}.ai-brief__headline{grid-column:1/-1;display:grid;gap:4px}.ai-mark{color:#176354;font-size:11px;font-weight:900;letter-spacing:.05em}.ai-brief__headline strong{font-size:18px;line-height:1.35}.ai-brief__headline p{margin:0;color:var(--usp-color-text-secondary);font-size:12px;line-height:1.6}.ai-brief__facts{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px}.ai-brief__facts>div{display:grid;gap:3px;padding:10px 11px;border-radius:var(--usp-radius-lg);background:rgba(255,255,255,.78)}.ai-brief__facts strong{font-size:22px;color:#164e43}.ai-brief__facts span{color:var(--usp-color-text-secondary);font-size:11px}.wall-entry{display:flex;align-items:center;justify-content:space-between;gap:14px;min-width:158px;height:42px;padding:0 14px;border:0;border-radius:var(--usp-radius-lg);background:#176354;color:#fff;font-weight:800;cursor:pointer}.wall-entry b{font-size:17px}.ai-brief__attention{display:grid;align-content:start;gap:8px;padding-left:14px;border-left:1px solid #d9ebe5}.ai-brief__attention header{display:flex;align-items:center;justify-content:space-between;gap:10px}.ai-brief__attention header span{font-size:13px;font-weight:800}.ai-brief__attention header small{color:var(--usp-color-text-tertiary)}.attention-list{display:grid;gap:5px}.attention-list button{display:grid;grid-template-columns:28px minmax(0,1fr) auto;align-items:center;gap:8px;padding:8px;border:0;border-radius:var(--usp-radius-lg);background:rgba(255,255,255,.72);text-align:left;cursor:pointer}.index{color:#56837a;font-size:10px;font-weight:900}.copy{display:grid;min-width:0;gap:2px}.copy strong,.copy small{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.copy strong{font-size:11px}.copy small{color:var(--usp-color-text-secondary);font-size:10px}.level{padding:3px 6px;border-radius:999px;background:#edf4f2;color:#477269;font-size:9px;font-weight:800}.level[data-level='HIGH']{background:#fff1f0;color:#b42318}.level[data-level='MEDIUM']{background:#fffaeb;color:#b54708}.empty{margin:0;padding:12px;border-radius:var(--usp-radius-lg);background:rgba(255,255,255,.62);color:var(--usp-color-text-secondary);font-size:11px;text-align:center}
@media(max-width:980px){.ai-brief{grid-template-columns:1fr}.ai-brief__attention{padding:12px 0 0;border-top:1px solid #d9ebe5;border-left:0}}@media(max-width:640px){.ai-brief__main{grid-template-columns:1fr}.ai-brief__facts{grid-template-columns:repeat(3,1fr)}.wall-entry{width:100%}.ai-brief__facts strong{font-size:18px}}
</style>
