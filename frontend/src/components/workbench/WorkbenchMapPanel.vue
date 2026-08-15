<script setup lang="ts">
type MapMode = 'GLOBAL' | 'AREA' | 'REVIEW'

const props = withDefaults(defineProps<{
  mode: MapMode
  enableRisk: boolean
  highRiskCount?: number
  reviewCount?: number
}>(), {
  highRiskCount: undefined,
  reviewCount: undefined,
})

const emit = defineEmits<{
  open: []
}>()

const modeCopy: Record<MapMode, { title: string; description: string }> = {
  GLOBAL: {
    title: '全局空间态势',
    description: '查看小区、楼栋与当前授权范围内的空间治理信息。',
  },
  AREA: {
    title: '辖区空间视图',
    description: '聚焦辖区楼栋、空间档案与巡检组织。',
  },
  REVIEW: {
    title: '复核空间视图',
    description: '从空间位置进入楼栋档案并开展专业复核。',
  },
}
</script>

<template>
  <button type="button" class="map-panel" :data-mode="props.mode" @click="emit('open')">
    <div class="map-copy">
      <span class="map-kicker">空间态势</span>
      <strong>{{ modeCopy[props.mode].title }}</strong>
      <p>{{ modeCopy[props.mode].description }}</p>
      <div v-if="enableRisk && (highRiskCount != null || reviewCount != null)" class="map-stats">
        <span v-if="highRiskCount != null"><b>{{ highRiskCount }}</b> 高风险</span>
        <span v-if="reviewCount != null"><b>{{ reviewCount }}</b> 待复核</span>
      </div>
      <span class="map-action">打开空间地图 <b>→</b></span>
    </div>

    <div class="map-miniature" aria-hidden="true">
      <span class="road road--one" />
      <span class="road road--two" />
      <span class="building building--one" />
      <span class="building building--two" />
      <span class="building building--three" />
      <span class="building building--four" />
      <span class="map-pulse" />
    </div>
  </button>
</template>

<style scoped lang="scss">
.map-panel {
  position: relative;
  overflow: hidden;
  display: grid;
  width: 100%;
  min-height: 166px;
  grid-template-columns: minmax(0, 1.15fr) minmax(170px, .85fr);
  align-items: stretch;
  padding: 0;
  border: 1px solid rgba(40, 122, 106, .14);
  border-radius: var(--usp-radius-xl);
  background: linear-gradient(135deg, #f8fbfa, #edf7f4 58%, #eaf3f7);
  color: var(--usp-color-text);
  text-align: left;
  box-shadow: var(--usp-shadow-sm);
  cursor: pointer;
  transition: transform .16s ease, border-color .16s ease, box-shadow .16s ease;
}

.map-panel:hover {
  transform: translateY(-2px);
  border-color: rgba(40, 122, 106, .32);
  box-shadow: var(--usp-shadow-md);
}

.map-copy {
  position: relative;
  z-index: 2;
  display: grid;
  align-content: center;
  gap: 5px;
  padding: 18px 20px;
}

.map-kicker {
  color: var(--usp-color-primary);
  font-size: 10px;
  font-weight: 900;
  letter-spacing: .09em;
}

.map-copy > strong {
  font-size: 19px;
}

.map-copy p {
  max-width: 520px;
  margin: 0;
  color: var(--usp-color-text-secondary);
  font-size: 12px;
  line-height: 1.55;
}

.map-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 2px;
  color: var(--usp-color-text-secondary);
  font-size: 11px;
}

.map-stats span {
  display: inline-flex;
  align-items: baseline;
  gap: 4px;
}

.map-stats b {
  color: var(--usp-color-danger);
  font-size: 16px;
}

.map-action {
  margin-top: 2px;
  color: var(--usp-color-primary-strong);
  font-size: 12px;
  font-weight: 900;
}

.map-action b {
  display: inline-block;
  transition: transform .16s ease;
}

.map-panel:hover .map-action b { transform: translateX(3px); }

.map-miniature {
  position: relative;
  overflow: hidden;
  min-height: 166px;
  background:
    linear-gradient(rgba(100, 116, 139, .07) 1px, transparent 1px),
    linear-gradient(90deg, rgba(100, 116, 139, .07) 1px, transparent 1px),
    radial-gradient(circle at 58% 45%, rgba(40, 122, 106, .14), transparent 34%);
  background-size: 24px 24px, 24px 24px, auto;
}

.map-miniature::before {
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, rgba(248, 251, 250, .98), transparent 36%);
  content: '';
}

.road,
.building,
.map-pulse { position: absolute; display: block; }

.road {
  height: 11px;
  border: 3px solid rgba(255, 255, 255, .92);
  border-right: 0;
  border-left: 0;
  background: rgba(148, 163, 184, .13);
}

.road--one { top: 44px; right: -28px; width: 92%; transform: rotate(-12deg); }
.road--two { right: 4px; bottom: 32px; width: 76%; transform: rotate(22deg); }

.building {
  border: 1.5px solid rgba(14, 116, 144, .38);
  border-radius: 7px;
  background: rgba(14, 116, 144, .13);
  box-shadow: 0 6px 14px rgba(15, 23, 42, .05);
}

.building--one { top: 18px; right: 18px; width: 54px; height: 36px; transform: rotate(7deg); }
.building--two { top: 64px; right: 86px; width: 62px; height: 42px; transform: rotate(-5deg); }
.building--three { right: 22px; bottom: 18px; width: 68px; height: 38px; transform: rotate(8deg); }
.building--four { right: 118px; bottom: 12px; width: 48px; height: 32px; transform: rotate(-9deg); }

.map-panel[data-mode='GLOBAL'] .building--two {
  border-color: rgba(220, 38, 38, .5);
  background: rgba(220, 38, 38, .14);
}

.map-pulse {
  top: 52%;
  right: 38%;
  width: 10px;
  height: 10px;
  border: 2px solid #dc2626;
  border-radius: 50%;
  background: rgba(220, 38, 38, .16);
  box-shadow: 0 0 0 7px rgba(220, 38, 38, .07);
}

@media (max-width: 720px) {
  .map-panel { grid-template-columns: 1fr; }
  .map-miniature { display: none; }
}
</style>
