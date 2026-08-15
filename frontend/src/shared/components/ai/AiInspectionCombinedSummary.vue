<script setup lang="ts">
import type { InspectionAiCombinedSummary } from '@/shared/api'

defineProps<{
  summary: InspectionAiCombinedSummary
}>()
</script>

<template>
  <section class="combined-summary" aria-label="AI 巡检综合总结">
    <header>
      <div>
        <strong>✦ AI 巡检综合总结</strong>
        <small>结合巡检员记录与已完成的视觉识别结果，不会重新执行图片识别。</small>
      </div>
      <el-tag :type="summary.mode === 'AI' ? 'success' : 'warning'" effect="plain" round>
        {{ summary.mode === 'AI' ? 'AI 综合' : '基础摘要' }}
      </el-tag>
    </header>

    <div class="summary-grid">
      <article><span>现场描述</span><p>{{ summary.fieldDescription }}</p></article>
      <article><span>AI 视觉发现</span><p>{{ summary.visualFindings }}</p></article>
      <article><span>相互印证 / 冲突</span><p>{{ summary.agreement }}</p></article>
      <article><span>重点位置</span><p>{{ summary.keyLocations }}</p></article>
      <article><span>建议补充证据</span><p>{{ summary.evidenceGaps }}</p></article>
      <article><span>人工复核建议</span><p>{{ summary.reviewSuggestion }}</p></article>
    </div>
    <small class="disclaimer">{{ summary.disclaimer }}</small>
  </section>
</template>

<style scoped lang="scss">
.combined-summary{display:grid;gap:12px;padding:14px;border:1px solid color-mix(in srgb,var(--usp-color-primary) 22%,var(--usp-color-border));border-radius:var(--usp-radius-xl);background:color-mix(in srgb,var(--usp-color-primary) 4%,var(--usp-color-surface))}.combined-summary>header{display:flex;align-items:center;justify-content:space-between;gap:12px}.combined-summary>header>div{display:grid;gap:2px}.combined-summary>header strong{font-size:15px}.combined-summary>header small,.disclaimer{color:var(--usp-color-text-secondary);font-size:11px;line-height:1.5}.summary-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:9px}.summary-grid article{display:grid;gap:5px;padding:10px 11px;border:1px solid var(--usp-color-border);border-radius:var(--usp-radius-lg);background:var(--usp-color-surface)}.summary-grid span{color:var(--usp-color-text-tertiary);font-size:11px;font-weight:800}.summary-grid p{margin:0;color:var(--usp-color-text-secondary);font-size:12px;line-height:1.6}@media(max-width:720px){.summary-grid{grid-template-columns:1fr}.combined-summary>header{align-items:flex-start;flex-direction:column}}
</style>
