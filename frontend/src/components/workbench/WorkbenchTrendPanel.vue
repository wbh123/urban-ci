<script setup lang="ts">
defineProps<{
  title: string
  enableRisk: boolean
}>()
</script>

<template>
  <el-card class="trend-panel" shadow="never">
    <template #header>
      <div class="trend-header">
        <div>
          <strong>{{ title }}</strong>
          <span>仅展示当前账号有权查看的业务维度</span>
        </div>
        <el-tag v-if="enableRisk" type="warning" effect="plain">含风险维度</el-tag>
        <el-tag v-else type="info" effect="plain">业务维度</el-tag>
      </div>
    </template>

    <div class="trend-content">
      <div class="trend-row">
        <span class="trend-marker" />
        <div>
          <strong>业务状态变化</strong>
          <p>趋势数据直接来自后续业务统计接口，不使用前端模拟数量替代真实数据。</p>
        </div>
      </div>
      <div v-if="enableRisk" class="trend-row">
        <span class="trend-marker" />
        <div>
          <strong>风险变化</strong>
          <p>只有具备风险读取或风险复核权限的账号才显示该维度。</p>
        </div>
      </div>
    </div>
  </el-card>
</template>

<style scoped lang="scss">
.trend-panel {
  border-radius: var(--usp-radius-lg);
}

.trend-header,
.trend-row {
  display: flex;
  align-items: center;
}

.trend-header {
  justify-content: space-between;
  gap: var(--usp-space-3);
}

.trend-header > div {
  display: grid;
  gap: 4px;
}

.trend-header span,
.trend-row p {
  color: var(--usp-color-text-secondary);
}

.trend-content {
  display: grid;
  gap: var(--usp-space-4);
}

.trend-row {
  align-items: flex-start;
  gap: var(--usp-space-3);
  padding: var(--usp-space-3);
  border-radius: var(--usp-radius-md);
  background: var(--usp-color-surface-muted, #f8fafc);
}

.trend-row p {
  margin: 5px 0 0;
  line-height: 1.6;
}

.trend-marker {
  width: 10px;
  height: 10px;
  margin-top: 6px;
  border-radius: 50%;
  background: var(--usp-color-primary);
  flex: 0 0 auto;
}
</style>
