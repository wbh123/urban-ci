<script setup lang="ts">
type MapMode = 'GLOBAL' | 'AREA' | 'REVIEW'

const props = defineProps<{
  mode: MapMode
  enableRisk: boolean
}>()

const emit = defineEmits<{
  open: []
}>()

const modeCopy: Record<MapMode, { title: string; description: string }> = {
  GLOBAL: {
    title: '全局空间态势',
    description: '从统一空间地图查看小区、楼栋与当前授权范围内的业务信息。',
  },
  AREA: {
    title: '辖区空间视图',
    description: '以辖区楼栋和巡检组织为主，不越权加载未授权风险字段。',
  },
  REVIEW: {
    title: '复核空间视图',
    description: '从空间位置进入统一楼栋档案，结合现场证据完成专业复核。',
  },
}
</script>

<template>
  <el-card class="map-panel" shadow="never">
    <div class="map-preview" :data-mode="props.mode">
      <div class="map-grid" aria-hidden="true" />
      <div class="map-copy">
        <el-tag size="small" effect="dark">空间地图</el-tag>
        <h2>{{ modeCopy[props.mode].title }}</h2>
        <p>{{ modeCopy[props.mode].description }}</p>
        <div class="legend">
          <span>▢ 已确认小区边界</span>
          <span>▣ 已确认楼栋边界</span>
          <span v-if="enableRisk">● 风险分层</span>
        </div>
        <el-button type="primary" @click="emit('open')">打开完整空间地图</el-button>
      </div>
    </div>
  </el-card>
</template>

<style scoped lang="scss">
.map-panel {
  overflow: hidden;
  border-radius: var(--usp-radius-lg);
}

.map-preview {
  position: relative;
  min-height: 310px;
  overflow: hidden;
  border-radius: var(--usp-radius-md);
  background: linear-gradient(135deg, #eef4f8, #f8fafc);
}

.map-grid {
  position: absolute;
  inset: 0;
  opacity: .55;
  background-image:
    linear-gradient(rgba(100, 116, 139, .12) 1px, transparent 1px),
    linear-gradient(90deg, rgba(100, 116, 139, .12) 1px, transparent 1px);
  background-size: 34px 34px;
  transform: rotate(-4deg) scale(1.1);
}

.map-copy {
  position: relative;
  z-index: 1;
  display: grid;
  width: min(560px, calc(100% - 48px));
  gap: var(--usp-space-3);
  padding: 42px 32px;
}

.map-copy h2,
.map-copy p {
  margin: 0;
}

.map-copy p,
.legend {
  color: var(--usp-color-text-secondary);
  line-height: 1.7;
}

.legend {
  display: flex;
  flex-wrap: wrap;
  gap: var(--usp-space-3);
  font-size: 12px;
}

.map-copy .el-button {
  width: fit-content;
}
</style>
