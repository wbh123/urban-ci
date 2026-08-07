<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getBuildingAssessmentSummary, type BuildingAssessmentSummary } from '@/shared/api'
const route = useRoute(); const current = ref<BuildingAssessmentSummary | null>(null); const loading = ref(false); const buildingId = computed(() => String(route.params.buildingId || ''))
async function load(): Promise<void> { loading.value = true; try { current.value = await getBuildingAssessmentSummary(buildingId.value) } catch (error) { ElMessage.error(error instanceof Error ? error.message : '评分摘要加载失败') } finally { loading.value = false } }
function score(value?: number): string { return value == null ? '--' : Number(value).toFixed(2) }
const isStale = computed(() => current.value?.freshness === 'STALE')
onMounted(load)
</script>
<template>
  <section v-loading="loading" class="mobile-assessment">
    <header><small>只读评分摘要</small><h1>{{ current?.buildingName || '楼栋评分' }}</h1><p>{{ current?.communityName }} · {{ current?.buildingCode }}</p></header>
    <el-alert v-if="current" :title="current.disclaimer" type="warning" :closable="false" show-icon />
    <el-empty v-if="current?.freshness === 'NO_RESULT'" description="暂无评分，请联系管理人员计算" />
    <el-alert v-if="isStale" title="评分结果已过期，请联系管理人员重算后再用于处置参考" type="error" :closable="false" show-icon />
    <div v-if="current && current.freshness !== 'NO_RESULT'" class="mobile-score-grid"><article><span>完整度</span><strong>{{ score(current?.completeness?.completenessScore) }}</strong><small>{{ current?.completeness?.completenessLevel }}</small></article><article><span>风险筛查</span><strong>{{ score(current?.risk?.riskScore) }}</strong><small>{{ current?.risk?.riskLevel }}</small></article><article><span>判断置信度</span><strong>{{ score(current?.risk?.confidenceScore) }}</strong><small>{{ current?.risk?.confidenceLevel }}</small></article></div>
    <el-card v-if="current?.risk" shadow="never"><template #header><strong>现场资料补充提示</strong></template><ul><li v-for="item in current.completeness?.missingItems || []" :key="item">{{ item }}</li><li v-for="item in current.completeness?.suggestions || []" :key="`suggest-${item}`">{{ item }}</li><li v-for="item in current.risk.recommendations" :key="`risk-${item}`">{{ item }}</li></ul><p v-if="current.risk.confidenceScore < 60" class="warning-text">当前置信度低于 60，应补充资料或现场复核，不得把资料缺失理解为安全。</p></el-card>
  </section>
</template>
<style scoped lang="scss">.mobile-assessment { display: grid; gap: 14px; }header small { color: #176354; font-weight: 700; }header h1 { margin: 4px 0; font-size: 26px; }header p { margin: 0; color: #667085; }.mobile-score-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }.mobile-score-grid article { display: grid; gap: 7px; padding: 14px 10px; border: 1px solid #dce5e2; border-radius: 14px; background: #fff; text-align: center; }.mobile-score-grid strong { font-size: 25px; }.mobile-score-grid span, .mobile-score-grid small { color: #667085; }ul { margin: 0; padding-left: 20px; color: #475467; line-height: 1.8; }.warning-text { color: #b54708; font-weight: 700; line-height: 1.7; }@media (max-width: 460px) { .mobile-score-grid { grid-template-columns: 1fr; } }</style>
