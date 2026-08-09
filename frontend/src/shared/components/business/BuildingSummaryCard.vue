<script setup lang="ts">
import { computed } from 'vue'
import type { BuildingSummaryView } from './building-business'

const props = defineProps<{ summary: BuildingSummaryView }>()

const spatialLabel = computed(() => {
  if (props.summary.spatialStatus === 'VERIFIED') return '空间档案已确认'
  if (props.summary.spatialStatus === 'UNVERIFIED') return '空间档案待确认'
  if (props.summary.spatialStatus === 'REJECTED') return '空间档案已驳回'
  return '尚无空间边界'
})

const spatialType = computed<'success' | 'warning' | 'danger' | 'info'>(() => {
  if (props.summary.spatialStatus === 'VERIFIED') return 'success'
  if (props.summary.spatialStatus === 'UNVERIFIED') return 'warning'
  if (props.summary.spatialStatus === 'REJECTED') return 'danger'
  return 'info'
})

function displayNumber(value: number | undefined, unit: string): string {
  return Number.isFinite(value) ? `${value} ${unit}` : '—'
}
</script>

<template>
  <el-card shadow="never" class="building-summary-card">
    <template #header>
      <div class="summary-head">
        <div>
          <div class="eyebrow">{{ summary.communityName }}</div>
          <div class="title-row">
            <strong>{{ summary.buildingName }}</strong>
            <el-tag effect="plain">{{ summary.buildingCode }}</el-tag>
          </div>
        </div>
        <el-tag :type="spatialType">{{ spatialLabel }}</el-tag>
      </div>
    </template>

    <dl class="summary-grid">
      <div><dt>地址</dt><dd>{{ summary.address || '—' }}</dd></div>
      <div><dt>建成年份</dt><dd>{{ summary.constructionYear ?? '—' }}</dd></div>
      <div><dt>楼层</dt><dd>{{ displayNumber(summary.floorCount, '层') }}</dd></div>
      <div><dt>居民</dt><dd>{{ displayNumber(summary.residentCount, '人') }}</dd></div>
    </dl>

    <slot name="actions" />
  </el-card>
</template>

<style scoped lang="scss">
.building-summary-card { min-width: 0; }
.summary-head,.title-row{display:flex;align-items:center;justify-content:space-between;gap:var(--usp-space-3)}
.summary-head>div{min-width:0}.eyebrow{color:var(--usp-color-text-secondary);font-size:12px;margin-bottom:4px}.title-row{justify-content:flex-start}.title-row strong{font-size:20px}.summary-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:var(--usp-space-3);margin:0}.summary-grid div{min-width:0}.summary-grid dt{font-size:12px;color:var(--usp-color-text-secondary)}.summary-grid dd{margin:4px 0 0;font-weight:600;overflow-wrap:anywhere}@media(max-width:640px){.summary-head{align-items:flex-start;flex-direction:column}.summary-grid{grid-template-columns:1fr}}
</style>
