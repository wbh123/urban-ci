<script setup lang="ts">
import type { AiDashboardLayerMode } from '@/shared/api/endpoints/ai-dashboard'

withDefaults(defineProps<{ aiAvailable?: boolean }>(), { aiAvailable: true })
const model = defineModel<AiDashboardLayerMode>({ required: true })

const items: Array<{ value: AiDashboardLayerMode; label: string; requiresAi?: boolean }> = [
  { value: 'RISK', label: '风险' },
  { value: 'AI_DEFECT', label: 'AI 病害', requiresAi: true },
  { value: 'AI_ATTENTION', label: 'AI 关注', requiresAi: true },
  { value: 'REVIEW', label: '待复核', requiresAi: true },
  { value: 'PRIORITY', label: '治理优先级' },
]
</script>

<template>
  <section class="layer-switch" aria-label="地图展示模式">
    <div class="layer-buttons">
      <button
        v-for="item in items"
        :key="item.value"
        type="button"
        :data-active="model === item.value"
        :disabled="Boolean(item.requiresAi && !aiAvailable)"
        @click="model = item.value"
      >{{ item.label }}</button>
    </div>
    <small v-if="!aiAvailable">AI 图层暂不可用，已保留正式风险与治理优先级地图。</small>
    <small v-else-if="model === 'AI_ATTENTION'">AI 关注只用于前端治理排序，不是正式风险等级。</small>
    <small v-else-if="model === 'AI_DEFECT'">AI 病害来自已落库视觉候选，仍需人工复核。</small>
    <small v-else-if="model === 'REVIEW'">仅突出仍需人工确认的 AI 结果。</small>
  </section>
</template>

<style scoped lang="scss">
.layer-switch{display:grid;justify-items:center;gap:4px}.layer-buttons{display:inline-flex;padding:3px;border:1px solid rgba(111,221,203,.16);border-radius:999px;background:rgba(4,25,28,.82);backdrop-filter:blur(12px)}.layer-buttons button{min-width:57px;height:25px;padding:0 8px;border:0;border-radius:999px;background:transparent;color:#7ea59f;font-size:8px;font-weight:800;cursor:pointer}.layer-buttons button[data-active='true']{background:rgba(79,207,183,.16);color:#c5fff2;box-shadow:inset 0 0 0 1px rgba(127,239,219,.12)}.layer-buttons button:disabled{cursor:not-allowed;opacity:.35}.layer-switch small{padding:3px 7px;border-radius:999px;background:rgba(4,25,28,.68);color:#789d97;font-size:7px;backdrop-filter:blur(8px)}@media(max-width:700px){.layer-buttons{max-width:100%;overflow:auto}.layer-buttons button{min-width:52px}}
</style>
