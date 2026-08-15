<script setup lang="ts">
withDefaults(defineProps<{
  state: 'READY' | 'DEGRADED' | 'UNAVAILABLE' | 'UNKNOWN'
  services?: Array<{ key: string; label: string; status: string }>
  policy?: string
  loading?: boolean
}>(), { services: () => [] })

function stateLabel(state: string): string {
  if (state === 'READY') return '正常'
  if (state === 'DEGRADED') return '降级'
  if (state === 'UNAVAILABLE') return '不可用'
  return '状态未知'
}
</script>

<template>
  <el-popover placement="bottom-end" :width="330" trigger="click">
    <template #reference>
      <button type="button" class="ai-runtime-badge" :data-state="state" :aria-label="`AI 运行状态：${stateLabel(state)}`">
        <span aria-hidden="true">✦ AI</span><strong>{{ loading ? '检查中' : stateLabel(state) }}</strong>
      </button>
    </template>
    <section class="ai-runtime-panel">
      <header><div><span>✦ AI</span><strong>运行状态</strong></div><small>辅助能力异常不会阻断基础业务</small></header>
      <div class="ai-runtime-services">
        <div v-for="service in services" :key="service.key"><span>{{ service.label }}</span><b>{{ service.status }}</b></div>
      </div>
      <div v-if="policy" class="ai-runtime-policy"><span>当前策略</span><strong>{{ policy }}</strong></div>
    </section>
  </el-popover>
</template>

<style scoped lang="scss">
.ai-runtime-badge{display:inline-flex;align-items:center;gap:5px;min-height:30px;padding:0 10px;border:1px solid #cfe4de;border-radius:999px;background:#f7fbfa;color:#176354;font-size:11px;cursor:pointer}.ai-runtime-badge>span{font-size:9px;font-weight:900}.ai-runtime-badge[data-state='DEGRADED']{border-color:#f4dfad;background:#fffaf0;color:#9a6700}.ai-runtime-badge[data-state='UNAVAILABLE'],.ai-runtime-badge[data-state='UNKNOWN']{border-color:var(--usp-color-border);background:var(--usp-color-surface-muted);color:var(--usp-color-text-secondary)}.ai-runtime-panel{display:grid;gap:12px}.ai-runtime-panel header{display:grid;gap:3px}.ai-runtime-panel header>div{display:flex;align-items:center;gap:7px}.ai-runtime-panel header>div span{color:#176354;font-size:10px;font-weight:900}.ai-runtime-panel header small{color:var(--usp-color-text-secondary)}.ai-runtime-services{display:grid;gap:7px}.ai-runtime-services>div{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:8px 9px;border-radius:10px;background:var(--usp-color-surface-muted)}.ai-runtime-services span{color:var(--usp-color-text-secondary);font-size:12px}.ai-runtime-services b{font-size:12px}.ai-runtime-policy{display:grid;gap:3px;padding-top:10px;border-top:1px solid var(--usp-color-border)}.ai-runtime-policy span{color:var(--usp-color-text-tertiary);font-size:11px}.ai-runtime-policy strong{font-size:12px}
</style>
