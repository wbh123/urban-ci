<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listCommunities, type CommunityListRow } from '@/shared/api/endpoints/communities'
import { listBuildings, type BuildingListRow } from '@/shared/api/endpoints/buildings'
import { getMapRuntimeConfig, type MapRuntimeConfig } from '@/shared/api/endpoints/map'
import {
  getBuildingBoundary,
  getCommunityBoundary,
  rejectBuildingBoundary,
  rejectCommunityBoundary,
  upsertBuildingBoundary,
  upsertCommunityBoundary,
  verifyBuildingBoundary,
  verifyCommunityBoundary,
  type SpatialBoundaryView,
  type SpatialGeoJsonGeometry,
} from '@/shared/api/endpoints/spatial'
import { toAppError } from '@/shared/api/error'
import { createSpatialBoundaryEditor } from '@/shared/map/spatial-boundary-editor'

type EntityType = 'COMMUNITY' | 'BUILDING'

const communities = ref<CommunityListRow[]>([])
const buildings = ref<BuildingListRow[]>([])
const selectedCommunityId = ref('')
const selectedBuildingId = ref('')
const entityType = ref<EntityType>('COMMUNITY')
const currentBoundary = ref<SpatialBoundaryView | null>(null)
const runtimeConfig = ref<MapRuntimeConfig | null>(null)
const mapContainer = ref<HTMLElement | null>(null)
const loading = ref(false)
const buildingLoading = ref(false)
const boundaryLoading = ref(false)
const saving = ref(false)
const reviewLoading = ref(false)
const mapReady = ref(false)
const errorMessage = ref('')
const notice = ref('')
const remark = ref('')
const dirtyGeometry = ref<SpatialGeoJsonGeometry | null>(null)
const editor = createSpatialBoundaryEditor()

const selectedEntityId = computed(() => entityType.value === 'COMMUNITY' ? selectedCommunityId.value : selectedBuildingId.value)
const selectedEntityName = computed(() => entityType.value === 'COMMUNITY'
  ? communities.value.find((item) => item.id === selectedCommunityId.value)?.communityName ?? '未选择小区'
  : buildings.value.find((item) => item.id === selectedBuildingId.value)?.buildingName ?? '未选择楼栋')
const statusLabel = computed(() => ({ UNVERIFIED: '待确认', VERIFIED: '已确认', REJECTED: '已驳回' } as Record<string, string>)[currentBoundary.value?.status ?? ''] ?? '暂无边界')
const statusType = computed<'success' | 'warning' | 'danger' | 'info'>(() => {
  if (currentBoundary.value?.status === 'VERIFIED') return 'success'
  if (currentBoundary.value?.status === 'UNVERIFIED') return 'warning'
  if (currentBoundary.value?.status === 'REJECTED') return 'danger'
  return 'info'
})
const canReview = computed(() => currentBoundary.value?.status === 'UNVERIFIED')

onMounted(loadWorkspace)
onBeforeUnmount(() => editor.destroy())

async function loadWorkspace(): Promise<void> {
  loading.value = true; errorMessage.value = ''; notice.value = ''
  try {
    const [communityPage, config] = await Promise.all([
      listCommunities({ status: 'ACTIVE', size: 100 }),
      getMapRuntimeConfig(),
    ])
    communities.value = communityPage.content ?? []
    runtimeConfig.value = config
    selectedCommunityId.value = communities.value[0]?.id ?? ''
    await loadBuildings()
    await nextTick()
    if (mapContainer.value && config.enabled && config.mode === 'LIVE' && config.jsApiKey) {
      mapReady.value = await editor.mount(mapContainer.value, config, { onChange: (geometry) => { dirtyGeometry.value = geometry } })
    }
    await loadCurrentBoundary()
  } catch (error) {
    errorMessage.value = toAppError(error).message
  } finally { loading.value = false }
}

async function loadBuildings(): Promise<void> {
  buildings.value = []; selectedBuildingId.value = ''
  if (!selectedCommunityId.value) return
  buildingLoading.value = true
  try {
    const page = await listBuildings({ communityId: selectedCommunityId.value, size: 100 })
    buildings.value = page.content ?? []
    selectedBuildingId.value = buildings.value[0]?.id ?? ''
  } finally { buildingLoading.value = false }
}

async function handleCommunityChange(): Promise<void> {
  await loadBuildings()
  if (entityType.value === 'BUILDING' && !selectedBuildingId.value) entityType.value = 'COMMUNITY'
  await loadCurrentBoundary()
}
async function handleBuildingChange(): Promise<void> { if (entityType.value === 'BUILDING') await loadCurrentBoundary() }
async function handleEntityTypeChange(): Promise<void> { await loadCurrentBoundary() }

async function loadCurrentBoundary(): Promise<void> {
  boundaryLoading.value = true; notice.value = ''; currentBoundary.value = null; dirtyGeometry.value = null
  const id = selectedEntityId.value
  if (!id) { editor.clear(); boundaryLoading.value = false; return }
  try {
    currentBoundary.value = entityType.value === 'COMMUNITY' ? await getCommunityBoundary(id) : await getBuildingBoundary(id)
    remark.value = currentBoundary.value.remark ?? ''
    editor.loadGeometry(currentBoundary.value.displayGeometry)
  } catch (error) {
    const appError = toAppError(error)
    if (appError.isNotFound) {
      currentBoundary.value = null; remark.value = ''; editor.clear()
    } else {
      notice.value = appError.message
    }
  } finally { boundaryLoading.value = false }
}

function startDraw(): void { notice.value = ''; editor.startDraw() }
function startEdit(): void {
  if (!currentBoundary.value) { notice.value = '当前对象尚无边界，请先绘制新边界。'; return }
  notice.value = ''; editor.startEdit()
}

async function saveBoundary(): Promise<void> {
  const id = selectedEntityId.value
  const geometry = editor.exportGeometry()
  if (!id || !geometry) { notice.value = '请先选择对象并绘制有效的 Polygon 边界。'; return }
  saving.value = true; notice.value = ''
  const expectedVersion = currentBoundary.value?.version ?? 0
  const payload = {
    expectedVersion,
    sourceType: currentBoundary.value ? 'MANUAL_EDIT' as const : 'MANUAL_DRAW' as const,
    sourceProvider: 'INTERNAL_EDITOR',
    sourceCoordinateSystem: 'GCJ02' as const,
    sourceGeometry: geometry,
    displayCoordinateSystem: 'GCJ02' as const,
    displayGeometry: geometry,
    remark: remark.value.trim() || null,
  }
  try {
    currentBoundary.value = entityType.value === 'COMMUNITY'
      ? await upsertCommunityBoundary(id, payload)
      : await upsertBuildingBoundary(id, payload)
    dirtyGeometry.value = currentBoundary.value.displayGeometry
    editor.loadGeometry(currentBoundary.value.displayGeometry)
    ElMessage.success('边界已保存并进入待确认状态。')
  } catch (error) {
    const appError = toAppError(error)
    if (appError.isConflict) {
      notice.value = '边界已被其他用户修改，已为你重新载入服务器最新版本，请确认后再编辑。'
      await loadCurrentBoundary()
    } else notice.value = appError.message
  } finally { saving.value = false }
}

async function reviewBoundary(action: 'VERIFY' | 'REJECT'): Promise<void> {
  const boundary = currentBoundary.value; const id = selectedEntityId.value
  if (!boundary || !id || boundary.status !== 'UNVERIFIED') return
  if (action === 'REJECT' && !remark.value.trim()) { notice.value = '驳回边界时请填写原因。'; return }
  try {
    await ElMessageBox.confirm(action === 'VERIFY' ? '确认该边界可进入正式地图展示？' : '确认驳回该边界？', action === 'VERIFY' ? '确认边界' : '驳回边界', { type: action === 'VERIFY' ? 'success' : 'warning' })
  } catch { return }
  reviewLoading.value = true; notice.value = ''
  const input = { expectedVersion: boundary.version, remark: remark.value.trim() || null }
  try {
    if (entityType.value === 'COMMUNITY') {
      currentBoundary.value = action === 'VERIFY' ? await verifyCommunityBoundary(id, input) : await rejectCommunityBoundary(id, input)
    } else {
      currentBoundary.value = action === 'VERIFY' ? await verifyBuildingBoundary(id, input) : await rejectBuildingBoundary(id, input)
    }
    ElMessage.success(action === 'VERIFY' ? '边界已确认，可进入正式地图。' : '边界已驳回。')
  } catch (error) {
    const appError = toAppError(error)
    if (appError.isConflict) {
      notice.value = '边界已被其他用户修改，当前审核未提交，已重新载入最新版本。'
      await loadCurrentBoundary()
    } else notice.value = appError.message
  } finally { reviewLoading.value = false }
}
</script>

<template>
  <section class="archive-page">
    <header class="page-head"><div><h1>空间档案</h1><p>维护来源可追溯、带版本并需要人工确认的小区与楼栋边界。</p></div><el-button @click="loadWorkspace">刷新目录</el-button></header>
    <el-alert v-if="errorMessage || notice" :title="errorMessage || notice" :type="errorMessage ? 'error' : 'warning'" :closable="false" show-icon />

    <div class="archive-grid">
      <el-card shadow="never" class="archive-controls">
        <template #header><strong>边界对象</strong></template>
        <el-form label-position="top" v-loading="loading">
          <el-form-item label="小区"><el-select v-model="selectedCommunityId" filterable style="width:100%" @change="handleCommunityChange"><el-option v-for="item in communities" :key="item.id" :label="item.communityName" :value="item.id" /></el-select></el-form-item>
          <el-form-item label="维护层级"><el-radio-group v-model="entityType" @change="handleEntityTypeChange"><el-radio-button value="COMMUNITY">小区边界</el-radio-button><el-radio-button value="BUILDING" :disabled="!selectedBuildingId">楼栋边界</el-radio-button></el-radio-group></el-form-item>
          <el-form-item v-if="entityType === 'BUILDING'" label="楼栋"><el-select v-model="selectedBuildingId" filterable :loading="buildingLoading" style="width:100%" @change="handleBuildingChange"><el-option v-for="item in buildings" :key="item.id" :label="`${item.buildingCode} · ${item.buildingName}`" :value="item.id" /></el-select></el-form-item>
        </el-form>
        <div class="boundary-state"><div><small>当前对象</small><strong>{{ selectedEntityName }}</strong></div><el-tag :type="statusType">{{ statusLabel }}</el-tag></div>
        <dl class="version-info"><div><dt>版本</dt><dd>{{ currentBoundary?.version ?? 0 }}</dd></div><div><dt>展示坐标</dt><dd>{{ currentBoundary?.displayCoordinateSystem ?? 'GCJ02' }}</dd></div><div><dt>来源</dt><dd>{{ currentBoundary?.sourceType ?? '待绘制' }}</dd></div></dl>
        <el-form-item label="维护/审核说明"><el-input v-model="remark" type="textarea" :rows="4" maxlength="1000" show-word-limit /></el-form-item>
        <div class="action-stack"><el-button :disabled="!mapReady" @click="startDraw">绘制新边界</el-button><el-button :disabled="!mapReady || !currentBoundary" @click="startEdit">编辑当前边界</el-button><el-button type="primary" :loading="saving" :disabled="!mapReady || boundaryLoading" @click="saveBoundary">保存为新版本</el-button></div>
        <el-divider>人工审核</el-divider>
        <div class="review-actions"><el-button type="success" :loading="reviewLoading" :disabled="!canReview" @click="reviewBoundary('VERIFY')">确认边界</el-button><el-button type="danger" plain :loading="reviewLoading" :disabled="!canReview" @click="reviewBoundary('REJECT')">驳回边界</el-button></div>
        <p class="hint">任何绘制或编辑保存后都会进入“待确认”；只有已确认边界会出现在正式地图。</p>
      </el-card>

      <el-card shadow="never" class="editor-card">
        <template #header><div class="editor-head"><strong>边界编辑器</strong><span v-if="dirtyGeometry">当前编辑：{{ dirtyGeometry.type }}</span></div></template>
        <div ref="mapContainer" class="editor-map" />
        <div v-if="runtimeConfig && (!runtimeConfig.enabled || runtimeConfig.mode !== 'LIVE')" class="editor-unavailable"><strong>地图服务当前不可用</strong><span>空间档案不会使用模拟地图替代正式编辑环境。</span></div>
        <div v-else-if="boundaryLoading" class="editor-busy">正在加载边界…</div>
      </el-card>
    </div>
  </section>
</template>

<style scoped lang="scss">
.archive-page{display:grid;gap:var(--usp-space-4)}.page-head,.boundary-state,.editor-head,.review-actions{display:flex;align-items:center;justify-content:space-between;gap:var(--usp-space-3)}.page-head h1{margin:0}.page-head p,.hint{margin:4px 0 0;color:var(--usp-color-text-secondary)}.archive-grid{display:grid;grid-template-columns:minmax(320px,380px) minmax(0,1fr);gap:var(--usp-space-4)}.archive-controls{min-width:0}.boundary-state{padding:12px;border-radius:8px;background:var(--usp-color-bg)}.boundary-state div{display:grid;gap:3px}.boundary-state small,.version-info dt{color:var(--usp-color-text-secondary)}.version-info{display:grid;grid-template-columns:repeat(3,1fr);gap:8px}.version-info div{padding:10px;border:1px solid var(--usp-color-border);border-radius:8px}.version-info dt,.version-info dd{margin:0}.version-info dd{margin-top:4px;font-weight:700}.action-stack{display:grid;grid-template-columns:1fr 1fr;gap:8px}.action-stack .el-button{margin:0}.action-stack .el-button:last-child{grid-column:1/-1}.review-actions .el-button{flex:1;margin:0}.hint{font-size:12px;line-height:1.6}.editor-card{position:relative;min-width:0}.editor-map{height:680px}.editor-head span{color:var(--usp-color-text-secondary);font-size:12px}.editor-unavailable,.editor-busy{position:absolute;inset:58px 0 0;display:grid;place-content:center;justify-items:center;gap:8px;background:rgba(248,250,252,.94);text-align:center}@media(max-width:1100px){.archive-grid{grid-template-columns:1fr}.editor-map{height:560px}}@media(max-width:640px){.version-info{grid-template-columns:1fr}.action-stack{grid-template-columns:1fr}.action-stack .el-button:last-child{grid-column:auto}}
</style>
