<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { loadBuildingDetail, type BuildingDetailModel } from '@/features/building-detail/building-detail-loader'
import BuildingSummaryCard from '@/shared/components/business/BuildingSummaryCard.vue'

const route = useRoute()
const loading = ref(false)
const errorMessage = ref('')
const model = ref<BuildingDetailModel | null>(null)
const buildingId = computed(() => String(route.params.buildingId ?? ''))

onMounted(load)
watch(buildingId, () => { void load() })

async function load(): Promise<void> {
  if (!buildingId.value) return
  loading.value = true
  errorMessage.value = ''
  model.value = null
  try {
    model.value = await loadBuildingDetail(buildingId.value)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : String(error)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="building-detail-page">
    <header class="page-head">
      <div>
        <h1>楼栋统一档案</h1>
        <p>集中查看楼栋档案、巡检、辅助分析、正式评分、处置和报告。</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </header>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon />
    <div v-if="loading" class="page-state">正在读取楼栋业务数据…</div>
    <BuildingSummaryCard v-else-if="model" :summary="model.summary" />
    <el-empty v-else-if="!errorMessage" description="暂无楼栋档案数据" />
  </section>
</template>

<style scoped lang="scss">
.building-detail-page { display: grid; gap: var(--usp-space-4); }
.page-head { display: flex; align-items: center; justify-content: space-between; gap: var(--usp-space-4); }
.page-head h1 { margin: 0; }
.page-head p { margin: 4px 0 0; color: var(--usp-color-text-secondary); }
.page-state { padding: var(--usp-space-6); text-align: center; color: var(--usp-color-text-secondary); }
@media (max-width: 640px) { .page-head { align-items: flex-start; flex-direction: column; } }
</style>
