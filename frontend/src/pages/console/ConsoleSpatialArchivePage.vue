<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
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
import { parseSpatialGeoJson } from '@/shared/map/spatial-geojson'

type EntityType = 'COMMUNITY' | 'BUILDING'

const route = useRoute()
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
const geoJsonInput = ref('')
const importedGeometry = ref(false)
const editor = createSpatialBoundaryEditor()

const preferredEntityType = queryValue(route.query.entityType)
const preferredEntityId = queryValue(route.query.entityId)
const preferredCommunityId = queryValue(route.query.communityId)

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

function queryValue(value: unknown): string {
  if (Array.isArray(value)) return typeof value[0] === 'string' ? value[0] : ''
  return typeof value === 'string' ? value : ''
}

async function loadWorkspace(): Promise<void> {
  loading.value = true; errorMessage.value = ''; notice.value = ''
  try {
    const [communityPage, config] = await Promise.all([
      listCommunities({ status: 'ACTIVE', size: 100 }),
      getMapRuntimeConfig(),
    ])
    communities.value = communityPage.content ?? []
    runtimeConfig.value = config
    const requestedCommunityId = preferredCommunityId
      || (preferredEntityType === 'COMMUNITY' ? preferredEntityId : '')
    selectedCommunityId.value = communities.value.some((item) => item.id === requestedCommunityId)
      ? requestedCommunityId
      : communities.value[0]?.id ?? ''
    await loadBuildings(preferredEntityType === 'BUILDING' ? preferredEntityId : '')
    if (preferredEntityType === 'BUILDING' && selectedBuildingId.value === preferredEntityId) {
      entityType.value = 'BUILDING'
    } else if (preferredEntityType === 'COMMUNITY') {
      entityType.value = 'COMMUNITY'
    }
    await nextTick()
    if (mapContainer.value && config.enabled && config.mode === 'LIVE' && config.jsApiKey) {
      mapReady.value = await editor.mount(mapContainer.value, config, { onChange: (geometry) => { dirtyGeometry.value = geometry } })
    }
    await loadCurrentBoundary()
  } catch (error) {
    errorMessage.value = toAppError(error).message
  } finally { loading.value = false }
}

async function loadBuildings(preferredBuildingId = ''): Promise<void> {
  buildings.value = []; selectedBuildingId.value = ''
  if (!selectedCommunityId.value) return
  buildingLoading.value = true
  try {
    const page = await listBuildings({ communityId: selectedCommunityId.value, size: 100 })
    buildings.value = page.content ?? []
    selectedBuildingId.value = buildings.value.some((item) => item.id === preferredBuildingId)
      ? preferredBuildingId
      : buildings.value[0]?.id ?? ''
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
  geoJsonInput.value = ''; importedGeometry.value = false
  const id = selectedEntityId.value
  if (!id) { editor.clear(); boundaryLoading.value = false; return }
  try {
    currentBoundary.value = entityType.value === 'COMMUNITY' ? await getCommunityBoundary(id) : await getBuildingBoundary(id)
    remark.value = currentBoundary.value.remark ?? ''
    if (mapReady.value) editor.loadGeometry(currentBoundary.value.displayGeometry)
  } catch (error) {
    const appError = toAppError(error)
    if (appError.isNotFound) {
      currentBoundary.value = null; remark.value = ''; editor.clear()
    } else {
      notice.value = appError.message
    }
  } finally { boundaryLoading.value = false }
}

function startDraw(): void {
  notice.value = ''; importedGeometry.value = false; geoJsonInput.value = ''; editor.startDraw()
}
function startEdit(): void {
  if (!currentBoundary.value) { notice.value = '当前对象尚无边界，请先绘制新边界。'; return }
  notice.value = ''; importedGeometry.value = false; geoJsonInput.value = ''; editor.startEdit()
}

function importGeoJsonText(): void {
  notice.value = ''
  try {
    const geometry = parseSpatialGeoJson(geoJsonInput.value)
    dirtyGeometry.value = geometry
    importedGeometry.value = true
    if (mapReady.value) editor.loadGeometry(geometry)
    notice.value = 'GeoJSON 校验通过。保存后将作为 GCJ-02 边界进入待确认状态。'
  } catch (error) {
    notice.value = error instanceof Error ? error.message : String(error)
  }
}

async function handleGeoJsonFile(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  try {
    geoJsonInput.value = await file.text()
    importGeoJsonText()
  } catch (error) {
    notice.value = error instanceof Error ? error.message : String(error)
  } finally {
    input.value = ''
  }
}

async function cancelEdit(): Promise<void> {
  notice.value = ''
  geoJsonInput.value = ''
  importedGeometry.value = false
  await loadCurrentBoundary()
  ElMessage.info('已取消本次修改，并恢复服务器当前版本。')
}

async function saveBoundary(): Promise<void> {
  const id = selectedEntityId.value
  const geometry = dirtyGeometry.value ?? editor.exportGeometry()
  if (!id || !geometry) { notice.value = '请先选择对象并绘制或导入有效的 Polygon 边界。'; return }
  saving.value = true; notice.value = ''
  const expectedVersion = currentBoundary.value?.version ?? 0
  const payload = {
    expectedVersion,
    sourceType: importedGeometry.value ? 'GEOJSON_IMPORT' as const : currentBoundary.value ? 'MANUAL_EDIT' as const : 'MANUAL_DRAW' as const,
    sourceProvider: importedGeometry.value ? 'GEOJSON_IMPORT' : 'INTERNAL_EDITOR',
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
    importedGeometry.value = false
    geoJsonInput.value = ''
    if (mapReady.value) editor.loadGeometry(currentBoundary.value.displayGeometry)
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

        <el-divider>导入 GCJ-02 GeoJSON</el-divider>
        <el-form-item label="GeoJSON 文本">
          <el-input v-model="geoJsonInput" type="textarea" :rows="5" placeholder="粘贴 Polygon、MultiPolygon 或 Feature GeoJSON" />
        </el-form-item>
        <div class="import-actions">
          <el-button :disabled="!geoJsonInput.trim()" @click="importGeoJsonText">校验并载入</el-button>
          <label class="file-action">
            从文件导入
            <input type="file" accept=".json,.geojson,application/json,application/geo+json" @change="handleGeoJsonFile" />
          </label>
        </div>
        <p class="hint">导入内容按 GCJ-02 保存；地图不可用时仍可通过 GeoJSON 建档，不会自动伪造或转换边界。</p>

        <el-form-item label="维护/审核说明"><el-input v-model="remark" type="textarea" :rows="4" maxlength="1000" show-word-limit /></el-form-item>
        <div class="action-stack">
          <el-button :disabled="!mapReady" @click="startDraw">绘制新边界</el-button>
          <el-button :disabled="!mapReady || !currentBoundary" @click="startEdit">编辑当前边界</el-button>
          <el-button type="primary" :loading="saving" :disabled="boundaryLoading || !selectedEntityId" @click="saveBoundary">保存为新版本</el-button>
          <el-button :disabled="boundaryLoading" @click="cancelEdit">取消本次修改</el-button>
        </div>
        <el-divider>人工审核</el-divider>
        <div class="review-actions"><el-button type="success" :loading="reviewLoading" :disabled="!canReview" @click="reviewBoundary('VERIFY')">确认边界</el-button><el-button type="danger" plain :loading="reviewLoading" :disabled="!canReview" @click="reviewBoundary('REJECT')">驳回边界</el-button></div>
        <p class="hint">任何绘制或编辑保存后都会进入“待确认”；只有已确认边界会出现在正式地图。</p>
      </el-card>

      <el-card shadow="never" class="editor-card">
        <template #header><div class="editor-head"><strong>边界编辑器</strong><span v-if="dirtyGeometry">当前编辑：{{ dirtyGeometry.type }}</span></div></template>
        <div ref="mapContainer" class="editor-map" />
        <div v-if="runtimeConfig && (!runtimeConfig.enabled || runtimeConfig.mode !== 'LIVE')" class="editor-unavailable"><strong>地图服务当前不可用</strong><span>可继续在左侧导入 GCJ-02 GeoJSON 并保存空间档案。</span></div>
        <div v-else-if="boundaryLoading" class="editor-busy">正在加载边界…</div>
      </el-card>
    </div>
  </section>
</template>

<style scoped lang="scss">
.archive-page{display:grid;gap:var(--usp-space-4)}.page-head,.boundary-state,.editor-head,.review-actions{display:flex;align-items:center;justify-content:space-between;gap:var(--usp-space-3)}.page-head h1{margin:0}.page-head p,.hint{margin:4px 0 0;color:var(--usp-color-text-secondary)}.archive-grid{display:grid;grid-template-columns:minmax(340px,410px) minmax(0,1fr);gap:var(--usp-space-4)}.archive-controls{min-width:0}.boundary-state{padding:12px;border-radius:8px;background:var(--usp-color-bg)}.boundary-state div{display:grid;gap:3px}.boundary-state small,.version-info dt{color:var(--usp-color-text-secondary)}.version-info{display:grid;grid-template-columns:repeat(3,1fr);gap:8px}.version-info div{padding:10px;border:1px solid var(--usp-color-border);border-radius:8px}.version-info dt,.version-info dd{margin:0}.version-info dd{margin-top:4px;font-weight:700}.import-actions{display:flex;gap:8px;align-items:center;margin-bottom:8px}.file-action{display:inline-flex;align-items:center;justify-content:center;min-height:32px;padding:0 15px;border:1px solid var(--usp-color-border);border-radius:var(--usp-radius-sm);background:var(--usp-color-surface);cursor:pointer;font-size:14px}.file-action input{display:none}.action-stack{display:grid;grid-template-columns:1fr 1fr;gap:8px}.action-stack .el-button{margin:0}.review-actions .el-button{flex:1;margin:0}.hint{font-size:12px;line-height:1.6}.editor-card{position:relative;min-width:0}.editor-map{height:680px}.editor-head span{color:var(--usp-color-text-secondary);font-size:12px}.editor-unavailable,.editor-busy{position:absolute;inset:58px 0 0;display:grid;place-content:center;justify-items:center;gap:8px;padding:24px;background:rgba(248,250,252,.94);text-align:center}@media(max-width:1100px){.archive-grid{grid-template-columns:1fr}.editor-map{height:560px}}@media(max-width:640px){.version-info{grid-template-columns:1fr}.action-stack,.import-actions{grid-template-columns:1fr;flex-direction:column;align-items:stretch}}
</style>