<script setup lang="ts">
import { computed } from 'vue'
import { buildAiInspectionSummary, type AiInspectionFindingInput } from '@/shared/ai/ai-inspection-summary'

const props = defineProps<{
  detections: AiInspectionFindingInput[]
  imageCount?: number
  compact?: boolean
}>()

const summary = computed(() => buildAiInspectionSummary(props.detections))
</script>

<template>
  <section class="ai-inspection-summary" :class="{ 'is-compact': compact }">
    <header>
      <div>
        <strong>✦ AI 巡检摘要</strong>
        <small v-if="imageCount != null">本次巡检共 {{ imageCount }} 张照片</small>
        <small v-else>基于当前已选择的 AI 视觉识别结果</small>
      </div>
      <span>{{ summary.total }} 处疑似病害</span>
    </header>

    <div v-if="summary.findings.length" class="finding-list">
      <span class="label">AI发现</span>
      <div>
        <el-tag v-for="item in summary.findings" :key="item.name" effect="plain" round>
          {{ item.name }} ×{{ item.count }}
        </el-tag>
      </div>
    </div>
    <p v-else class="empty-copy">当前结果未发现明确疑似病害。</p>

    <div class="suggestion">
      <span>建议</span>
      <p>{{ summary.suggestion }}</p>
    </div>
  </section>
</template>

<style scoped lang="scss">
.ai-inspection-summary {
  display: grid;
  gap: 12px;
  padding: 14px 16px;
  border: 1px solid var(--usp-color-border);
  border-radius: var(--usp-radius-xl);
  background: var(--usp-color-surface);
}
.ai-inspection-summary header { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.ai-inspection-summary header > div { display: grid; gap: 3px; }
.ai-inspection-summary header strong { color: var(--usp-color-text-primary); }
.ai-inspection-summary header small,
.ai-inspection-summary header > span,
.empty-copy { color: var(--usp-color-text-secondary); font-size: 12px; }
.finding-list { display: grid; gap: 7px; }
.finding-list > div { display: flex; flex-wrap: wrap; gap: 7px; }
.label,
.suggestion > span { color: var(--usp-color-text-tertiary); font-size: 12px; font-weight: 700; }
.suggestion { padding-top: 10px; border-top: 1px solid var(--usp-color-border); }
.suggestion p { margin: 5px 0 0; color: var(--usp-color-text-secondary); line-height: 1.6; }
.is-compact { padding: 12px; border-radius: var(--usp-radius-lg); }
@media (max-width: 640px) {
  .ai-inspection-summary header { flex-direction: column; }
}
</style>
