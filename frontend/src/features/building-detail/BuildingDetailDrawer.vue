<script setup lang="ts">
import BuildingSummaryCard from '@/shared/components/business/BuildingSummaryCard.vue'
import RiskSummaryPanel from '@/shared/components/business/RiskSummaryPanel.vue'
import type { BuildingDetailModel } from './building-detail-loader'

const props = defineProps<{
  model: BuildingDetailModel
  modelValue: boolean
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'open-full', buildingId: string): void
}>()

function close(value: boolean): void {
  emit('update:modelValue', value)
}

function openFull(): void {
  emit('open-full', props.model.summary.buildingId)
}
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    size="520px"
    title="楼栋详情"
    :append-to-body="false"
    @update:model-value="close"
  >
    <div class="building-detail-drawer">
      <BuildingSummaryCard :summary="model.summary" compact />
      <RiskSummaryPanel :summary="model.risk" />
      <el-alert
        v-if="model.warnings.length"
        type="warning"
        :closable="false"
        show-icon
        title="部分业务数据暂时不可用"
        :description="model.warnings.map((item) => item.message).join('；')"
      />
      <div class="drawer-actions">
        <el-button
          type="primary"
          data-action="open-full-detail"
          @click="openFull"
        >
          查看完整档案
        </el-button>
      </div>
    </div>
  </el-drawer>
</template>

<style scoped lang="scss">
.building-detail-drawer{display:grid;gap:var(--usp-space-4)}
.drawer-actions{display:flex;justify-content:flex-end}
</style>
