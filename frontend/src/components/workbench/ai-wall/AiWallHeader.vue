<script setup lang="ts">
defineProps<{
  generatedAt?: string | null
  fullscreen?: boolean
  degraded?: boolean
}>()

defineEmits<{
  fullscreen: []
}>()

function displayTime(value?: string | null): string {
  if (!value) return '数据时间待更新'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return `数据更新 ${date.toLocaleString('zh-CN')}`
}
</script>

<template>
  <header class="ai-wall-header">
    <div class="brand-block">
      <strong>城安智序 · AI 城市建筑安全智能研判中心</strong>
    </div>
    <div class="header-status">
      <span v-if="degraded" class="degraded">AI 聚合暂不可用 · 风险地图继续工作</span>
      <span>{{ displayTime(generatedAt) }}</span>
      <button v-if="!fullscreen" type="button" @click="$emit('fullscreen')">全屏</button>
    </div>
  </header>
</template>

<style scoped lang="scss">
.ai-wall-header{position:relative;z-index:35;display:flex;min-height:70px;align-items:center;justify-content:center;padding:8px 16px;background:transparent;color:#effffb;box-shadow:none;backdrop-filter:none}.brand-block{position:relative;display:flex;min-width:0;align-items:center;justify-content:center;text-align:center}.brand-block strong{overflow:hidden;max-width:min(980px,72vw);font-size:28px;font-weight:900;line-height:1.15;letter-spacing:.035em;text-overflow:ellipsis;white-space:nowrap;text-shadow:0 2px 18px rgba(0,0,0,.52)}.header-status{position:absolute;top:50%;right:0;display:flex;align-items:center;justify-content:flex-end;gap:8px;color:#8fb9b2;font-size:9px;transform:translateY(-50%)}.header-status button{height:29px;padding:0 10px;border:1px solid rgba(136,230,213,.18);border-radius:999px;background:rgba(5,27,30,.6);color:#d9f8f1;font-size:9px;font-weight:800;cursor:pointer;backdrop-filter:blur(10px)}.degraded{padding:5px 7px;border-radius:999px;background:rgba(245,184,76,.12);color:#f7d991;backdrop-filter:blur(8px)}:global(.data-wall .building-focus-switch){z-index:60!important;pointer-events:auto!important}:global(.data-wall .building-focus-switch button){pointer-events:auto!important}@media(max-width:1180px){.ai-wall-header{min-height:86px;align-content:start;flex-direction:column;gap:7px;padding:6px 12px}.brand-block strong{max-width:92vw;font-size:24px}.header-status{position:static;justify-content:center;flex-wrap:wrap;transform:none}}@media(max-width:700px){.brand-block strong{font-size:20px;white-space:normal}.header-status{font-size:8px}}
</style>
