<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import {
  previewCommunityBoundaryCandidate,
  type CommunityBoundaryCandidatePreview,
} from '@/shared/api/endpoints/archive'
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
import SpatialObjectSelector from '@/shared/components/SpatialObjectSelector.vue'
import AppPageHeader from '@/shared/components/layout/AppPageHeader.vue'
import type {
  SpatialBuildingOption,
  SpatialCommunityOption,
  SpatialObjectSelection,
} from '@/shared/composables/useSpatialObjectSelector'
import { createSpatialBoundaryEditor } from '@/shared/map/spatial-boundary-editor'
import { parseSpatialGeoJson } from '@/shared/map/spatial-geojson'

type EntityType = 'COMMUNITY' | 'BUILDING'

const route = useRoute()
const selectedCommunityId = ref('')
const selectedBuildingId = ref('')
const selectedCommunity = ref<SpatialCommunityOption | null>(null)
const selectedBuilding = ref<SpatialBuildingOption | null>(null)
const entityType = ref<EntityType>('COMMUNITY')
const currentBoundary = ref<SpatialBoundaryView | null>(null)
const runtimeConfig = ref<MapRuntimeConfig | null>(null)
const mapContainer = ref<HTMLElement | null>(null)
const loading = ref(false)
const boundaryLoading = ref(false)
const candidateLoading = ref(false)
const saving = ref(false)
const reviewLoading = ref(false)
const mapReady = ref(false)
const errorMessage = ref('')
const notice = ref('')
const remark = ref('')
const dirtyGeometry = ref<SpatialGeoJsonGeometry | null>(null)
const geoJsonInput = ref('')
const importedGeometry = ref(false)
const candidate = ref<CommunityBoundaryCandidatePreview | null>(null)
const candidateAdopted = ref(false)
const selectorRevision = ref(0)
const editor = createSpatialBoundaryEditor()

const preferredEntityType = queryValue(route.query.entityType)
const preferredEntityId = queryValue(route.query.entityId)
const preferredCommunityId = queryValue(route.query.communityId)

selectedCommunityId.value = preferredCommunityId
  || (preferredEntityType === 'COMMUNITY' ? preferredEntityId : '')
selectedBuildingId.value = preferredEntityType === 'BUILDING' ? preferredEntityId : ''
if (preferredEntityType === 'BUILDING') entityType.value = 'BUILDING'

const selectedEntityId = computed(() => entityType.value === 'COMMUNITY'
  ? selectedCommunityId.value
  : selectedBuildingId.value)
const selectedEntityName = computed(() => entityType.value === 'COMMUNITY'
  ? selectedCommunity.value?.communityName ?? '未选择小区'
  : selectedBuilding.value?.buildingName
    ?? selectedBuilding.value?.buildingCode
    ?? '未选择楼栋')
const statusLabel = computed(() => ({
  UNVERIFIED: '待确认',
  VERIFIED: '已确认',
  REJECTED: '已驳回',
} as Record<string, string>)[currentBoundary.value?.status ?? ''] ?? '暂无边界')
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
  loading.value = true
  errorMessage.value = ''
  notice.value = ''
  try {
    const config = await getMapRuntimeConfig()
    runtimeConfig.value = config
    await nextTick()
    if (mapContainer.value && config.enabled && config.mode === 'LIVE' && config.jsApiKey) {
      mapReady.value = await editor.mount(mapContainer.value, config, {
        onChange: (geometry) => { dirtyGeometry.value = geometry },
      })
      if (mapReady.value && currentBoundary.value) editor.loadGeometry(currentBoundary.value.displayGeometry)
    }
    await loadCurrentBoundary()
  } catch (error) {
    errorMessage.value = toAppError(error).message
  } finally {
    loading.value = false
  }
}

async function refreshWorkspace(): Promise<void> {
  selectorRevision.value += 1
  await loadCurrentBoundary()
}

async function handleSpatialSelection(selection: SpatialObjectSelection): Promise<void> {
  selectedCommunityId.value = selection.communityId
  selectedBuildingId.value = selection.buildingId
  selectedCommunity.value = selection.community
  selectedBuilding.value = selection.building
  if (selection.level === 'BUILDING') entityType.value = 'BUILDING'
  else if (!selection.buildingId) entityType.value = 'COMMUNITY'
  await loadCurrentBoundary()
}

async function handleEntityTypeChange(): Promise<void> {
  await loadCurrentBoundary()
}

function clearCandidateState(): void {
  editor.clearPreview()
  candidate.value = null
  candidateAdopted.value = false
}

async function loadCurrentBoundary(): Promise<void> {
  boundaryLoading.value = true
  notice.value = ''
  currentBoundary.value = null
  dirtyGeometry.value = null
  geoJsonInput.value = ''
  importedGeometry.value = false
  clearCandidateState()
  const id = selectedEntityId.value
  if (!id) {
    editor.clear()
    boundaryLoading.value = false
    return
  }
  try {
    currentBoundary.value = entityType.value === 'COMMUNITY'
      ? await getCommunityBoundary(id)
      : await getBuildingBoundary(id)
    remark.value = currentBoundary.value.remark ?? ''
    if (mapReady.value) editor.loadGeometry(currentBoundary.value.displayGeometry)
  } catch (error) {
    const appError = toAppError(error)
    if (appError.isNotFound) {
      currentBoundary.value = null
      remark.value = ''
      editor.clear()
    } else {
      notice.value = appError.message
    }
  } finally {
    boundaryLoading.value = false
  }
}

function startDraw(): void {
  notice.value = ''
  importedGeometry.value = false
  geoJsonInput.value = ''
  clearCandidateState()
  editor.startDraw()
}

function startEdit(): void {
  if (!currentBoundary.value && !dirtyGeometry.value) {
    notice.value = '当前对象尚无边界，请先绘制新边界。'
    return
  }
  notice.value = ''
  importedGeometry.value = false
  geoJsonInput.value = ''
  if (!candidateAdopted.value) clearCandidateState()
  editor.startEdit()
}

async function previewAmapCandidate(): Promise<void> {
  const community = selectedCommunity.value
  if (entityType.value !== 'COMMUNITY' || !community) return
  const communityName = community.communityName?.trim()
  if (!communityName) {
    ElNotification({
      title: '当前小区缺少名称，无法查询高德候选边界',
      message: '可继续使用人工绘制或 GeoJSON 导入。',
      type: 'warning',
      position: 'top-right',
    })
    return
  }

  candidateLoading.value = true
  notice.value = ''
  clearCandidateState()
  try {
    const result = await previewCommunityBoundaryCandidate({
      communityId: community.id,
      communityName,
      address: community.address ?? null,
      region: community.administrativeRegion ?? null,
    })
    candidate.value = result
    if (!result.available || !result.geometry) {
      ElNotification({
        title: '未获取到可用区域边界',
        message: '可继续使用人工绘制或 GeoJSON 导入。',
        type: 'warning',
        position: 'top-right',
      })
      return
    }
    if (mapReady.value) editor.previewGeometry(result.geometry)
    notice.value = '高德候选已叠加预览，但尚未采用、不会保存。请核对后再决定是否采用为草稿。'
  } catch (error) {
    ElNotification({
      title: '高德候选边界查询失败',
      message: `${toAppError(error).message} 可继续使用人工绘制或 GeoJSON 导入。`,
      type: 'error',
      position: 'top-right',
    })
  } finally {
    candidateLoading.value = false
  }
}

function adoptAmapCandidate(): void {
  if (!candidate.value?.available || !candidate.value.geometry) return
  candidateAdopted.value = true
  importedGeometry.value = false
  geoJsonInput.value = ''
  dirtyGeometry.value = candidate.value.geometry
  if (mapReady.value) editor.loadGeometry(candidate.value.geometry)
  notice.value = '高德候选已采用为编辑草稿，但尚未保存。保存后只会进入“待确认”，不会自动成为正式边界。'
}

async function cancelAmapCandidate(): Promise<void> {
  const wasAdopted = candidateAdopted.value
  clearCandidateState()
  if (wasAdopted) {
    await loadCurrentBoundary()
    notice.value = '已撤销采用，并恢复服务器当前边界；未产生任何保存。'
    return
  }
  notice.value = '已取消高德候选预览，当前正式边界和已有草稿未被候选查询修改。'
}

function importGeoJsonText(): void {
  notice.value = ''
  try {
    const geometry = parseSpatialGeoJson(geoJsonInput.value)
    dirtyGeometry.value = geometry
    importedGeometry.value = true
    clearCandidateState()
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
  clearCandidateState()
  await loadCurrentBoundary()
  ElMessage.info('已取消本次修改，并恢复服务器当前版本。')
}

async function saveBoundary(): Promise<void> {
  const id = selectedEntityId.value
  const geometry = dirtyGeometry.value ?? editor.exportGeometry()
  if (!id || !geometry) {
    notice.value = '请先选择对象并绘制或导入有效的 Polygon 边界。'
    return
  }
  saving.value = true
  notice.value = ''
  const expectedVersion = currentBoundary.value?.version ?? 0
  const payload = {
    expectedVersion,
    sourceType: candidateAdopted.value
      ? 'AMAP_AOI' as const
      : importedGeometry.value
        ? 'GEOJSON_IMPORT' as const
        : currentBoundary.value
          ? 'MANUAL_EDIT' as const
          : 'MANUAL_DRAW' as const,
    sourceProvider: candidateAdopted.value
      ? 'AMAP'
      : importedGeometry.value
        ? 'GEOJSON_IMPORT'
        : 'INTERNAL_EDITOR',
    sourceObjectId: candidateAdopted.value ? candidate.value?.sourceId ?? null : null,
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
    clearCandidateState()
    if (mapReady.value) editor.loadGeometry(currentBoundary.value.displayGeometry)
    ElMessage.success('边界已保存并进入待确认状态。')
  } catch (error) {
    const appError = toAppError(error)
    if (appError.isConflict) {
      notice.value = '边界已被其他用户修改，已为你重新载入服务器最新版本，请确认后再编辑。'
      await loadCurrentBoundary()
    } else {
      notice.value = appError.message
    }
  } finally {
    saving.value = false
  }
}

async function reviewBoundary(action: 'VERIFY' | 'REJECT'): Promise<void> {
  const boundary = currentBoundary.value
  const id = selectedEntityId.value
  if (!boundary || !id || boundary.status !== 'UNVERIFIED') return
  if (action === 'REJECT' && !remark.value.trim()) {
    notice.value = '驳回边界时请填写原因。'
    return
  }
  try {
    await ElMessageBox.confirm(
      action === 'VERIFY' ? '确认该边界可进入正式地图展示？' : '确认驳回该边界？',
      action === 'VERIFY' ? '确认边界' : '驳回边界',
      { type: action === 'VERIFY' ? 'success' : 'warning' },
    )
  } catch {
    return
  }
  reviewLoading.value = true
  notice.value = ''
  const input = { expectedVersion: boundary.version, remark: remark.value.trim() || null }
  try {
    if (entityType.value === 'COMMUNITY') {
      currentBoundary.value = action === 'VERIFY'
        ? await verifyCommunityBoundary(id, input)
        : await rejectCommunityBoundary(id, input)
    } else {
      currentBoundary.value = action === 'VERIFY'
        ? await verifyBuildingBoundary(id, input)
        : await rejectBuildingBoundary(id, input)
    }
    ElMessage.success(action === 'VERIFY' ? '边界已确认，可进入正式地图。' : '边界已驳回。')
  } catch (error) {
    const appError = toAppError(error)
    if (appError.isConflict) {
      notice.value = '边界已被其他用户修改，当前审核未提交，已重新载入最新版本。'
      await loadCurrentBoundary()
    } else {
      notice.value = appError.message
    }
  } finally {
    reviewLoading.value = false
  }
}
</script>

<template>
  <section class="archive-page">
    <AppPageHeader
      eyebrow="空间档案"
      title="空间档案"
      description="维护可追溯、带版本且需人工确认的小区与楼栋空间边界。"
      show-user-menu
    >
      <template #actions><el-button @click="refreshWorkspace">刷新目录</el-button></template>
    </AppPageHeader>

    <el-alert
      v-if="errorMessage || notice"
      :title="errorMessage || notice"
      :type="errorMessage ? 'error' : 'warning'"
      :closable="false"
      show-icon
    />

    <section class="archive-summary" aria-label="当前空间档案状态">
      <div class="archive-summary__entity"><span>当前对象</span><strong>{{ selectedEntityName }}</strong><small>{{ entityType === 'COMMUNITY' ? '小区边界' : '楼栋边界' }}</small></div>
      <div><span>确认状态</span><el-tag :type="statusType" effect="light">{{ statusLabel }}</el-tag></div>
      <div><span>当前版本</span><strong>v{{ currentBoundary?.version ?? 0 }}</strong></div>
      <div><span>数据来源</span><strong>{{ candidateAdopted ? '高德候选草稿' : currentBoundary?.sourceType ?? '待绘制' }}</strong></div>
      <div><span>展示坐标</span><strong>{{ currentBoundary?.displayCoordinateSystem ?? 'GCJ02' }}</strong></div>
    </section>

    <div class="archive-grid">
      <el-card shadow="never" class="archive-controls">
        <section class="control-section control-section--object">
          <div class="section-heading"><div><span>01</span><strong>选择边界对象</strong></div><small>可逐级选择，也可直接搜索楼栋并自动定位所属小区。</small></div>
          <div v-loading="loading" class="object-selector-block">
            <SpatialObjectSelector
              :key="selectorRevision"
              v-model:community-id="selectedCommunityId"
              v-model:building-id="selectedBuildingId"
              mode="both"
              @change="handleSpatialSelection"
            />
            <div class="maintenance-level-row">
              <span class="field-label">维护层级</span>
              <el-radio-group v-model="entityType" @change="handleEntityTypeChange">
                <el-radio-button value="COMMUNITY" :disabled="!selectedCommunityId">小区边界</el-radio-button>
                <el-radio-button value="BUILDING" :disabled="!selectedBuildingId">楼栋边界</el-radio-button>
              </el-radio-group>
            </div>
          </div>
        </section>

        <section class="control-section control-section--source">
          <div class="section-heading"><div><span>02</span><strong>边界来源与辅助</strong></div><small>高德候选与 GeoJSON 只生成草稿，不直接成为正式边界。</small></div>
          <template v-if="entityType === 'COMMUNITY'">
            <div class="inline-action-row">
              <el-button :loading="candidateLoading" :disabled="!selectedCommunityId" @click="previewAmapCandidate">查询高德候选边界</el-button>
              <span class="hint">查询结果仅预览，采用后仍需保存和人工确认。</span>
            </div>
            <div v-if="candidate" class="candidate-card">
              <div class="candidate-head">
                <div><strong>{{ candidate.name || selectedCommunity?.communityName }}</strong><small>{{ candidate.address || candidate.message }}</small></div>
                <el-tag :type="candidate.available ? 'warning' : 'info'">{{ candidateAdopted ? '已采用为草稿' : candidate.available ? '仅预览' : candidate.reasonCode || '不可用' }}</el-tag>
              </div>
              <div v-if="candidate.available && candidate.geometry" class="candidate-actions">
                <el-button type="primary" plain :disabled="candidateAdopted" @click="adoptAmapCandidate">采用为草稿</el-button>
                <el-button @click="cancelAmapCandidate">{{ candidateAdopted ? '撤销采用' : '取消预览' }}</el-button>
              </div>
            </div>
          </template>

          <div class="geojson-import-section">
            <label class="field-label" for="geojson-input">导入 GCJ-02 GeoJSON</label>
            <el-input
              id="geojson-input"
              v-model="geoJsonInput"
              type="textarea"
              :rows="5"
              placeholder="粘贴 Polygon、MultiPolygon 或 Feature GeoJSON"
            />
            <div class="geojson-import-actions">
              <el-button class="full-width-action" :disabled="!geoJsonInput.trim()" @click="importGeoJsonText">从文本导入并预览</el-button>
              <label class="file-action full-width-action">
                选择文件导入
                <input type="file" accept=".json,.geojson,application/json,application/geo+json" @change="handleGeoJsonFile" />
              </label>
            </div>
          </div>
        </section>

        <section class="control-section control-section--edit edit-save-section">
          <div class="section-heading"><div><span>03</span><strong>编辑与保存</strong></div><small>保存会生成新版本，并进入待确认状态。</small></div>
          <label class="field-label" for="archive-remark">维护/审核说明</label>
          <el-input id="archive-remark" v-model="remark" type="textarea" :rows="3" maxlength="1000" show-word-limit />
          <div class="edit-save-actions">
            <el-button class="full-width-action" :disabled="!mapReady" @click="startDraw">绘制新边界</el-button>
            <el-button class="full-width-action" :disabled="!mapReady || (!currentBoundary && !dirtyGeometry)" @click="startEdit">编辑当前草稿</el-button>
            <el-button class="full-width-action" type="primary" :loading="saving" :disabled="boundaryLoading || !selectedEntityId" @click="saveBoundary">保存为新版本</el-button>
            <el-button class="full-width-action" :disabled="boundaryLoading" @click="cancelEdit">取消本次修改</el-button>
          </div>
        </section>

        <section class="control-section control-section--review">
          <div class="section-heading"><div><span>04</span><strong>人工审核</strong></div><small>只有人工确认后的边界才进入正式地图展示。</small></div>
          <div class="review-actions">
            <el-button type="success" :loading="reviewLoading" :disabled="!canReview" @click="reviewBoundary('VERIFY')">确认边界</el-button>
            <el-button type="danger" plain :loading="reviewLoading" :disabled="!canReview" @click="reviewBoundary('REJECT')">驳回边界</el-button>
          </div>
        </section>
      </el-card>

      <el-card shadow="never" class="editor-card">
        <template #header>
          <div class="editor-head">
            <div><strong>边界编辑器</strong><small>地图用于绘制、校核与候选叠加，最终边界以保存版本为准。</small></div>
            <span v-if="dirtyGeometry">当前编辑：{{ dirtyGeometry.type }}</span>
          </div>
        </template>
        <div ref="mapContainer" class="editor-map" />
        <div v-if="runtimeConfig && (!runtimeConfig.enabled || runtimeConfig.mode !== 'LIVE')" class="editor-unavailable">
          <strong>地图服务当前不可用</strong><span>可继续在左侧导入 GCJ-02 GeoJSON 并保存空间档案。</span>
        </div>
        <div v-else-if="boundaryLoading" class="editor-busy">正在加载边界…</div>
      </el-card>
    </div>
  </section>
</template>

<style scoped lang="scss">
.archive-page { display: grid; gap: 14px; }
.archive-summary { display: grid; grid-template-columns: minmax(220px, 1.7fr) repeat(4, minmax(120px, .8fr)); gap: 10px; }
.archive-summary > div { display: grid; min-width: 0; align-content: center; gap: 4px; min-height: 72px; padding: 11px 13px; border: 1px solid var(--usp-color-border); border-radius: var(--usp-radius-xl); background: var(--usp-color-surface); box-shadow: var(--usp-shadow-sm); }
.archive-summary span,
.archive-summary small { color: var(--usp-color-text-secondary); font-size: 10px; font-weight: 700; }
.archive-summary strong { overflow: hidden; font-size: 15px; text-overflow: ellipsis; white-space: nowrap; }
.archive-summary__entity { background: linear-gradient(135deg, var(--usp-color-primary-soft), var(--usp-color-surface)) !important; }
.archive-summary__entity strong { color: var(--usp-color-primary-strong); font-size: 18px; }
.archive-grid { display: grid; grid-template-columns: minmax(380px, 445px) minmax(0, 1fr); gap: 14px; align-items: start; }
.archive-controls,
.editor-card { min-width: 0; border-radius: var(--usp-radius-xl); box-shadow: var(--usp-shadow-sm); }
.archive-controls :deep(.el-card__body) { display: grid; gap: 0; padding: 0; }
.control-section { display: grid; gap: 12px; padding: 16px 18px; border-bottom: 1px solid var(--usp-color-border); }
.control-section:last-child { border-bottom: 0; }
.section-heading { display: grid; gap: 4px; }
.section-heading > div { display: flex; align-items: center; gap: 8px; }
.section-heading > div > span { display: grid; place-items: center; width: 24px; height: 24px; border-radius: 999px; background: var(--usp-color-primary-soft); color: var(--usp-color-primary-strong); font-size: 10px; font-weight: 800; }
.section-heading strong { font-size: 14px; }
.section-heading small,
.hint { color: var(--usp-color-text-secondary); font-size: 11px; line-height: 1.55; }
.object-selector-block,
.geojson-import-section,
.edit-save-section { display: grid; gap: 10px; }
.maintenance-level-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding-top: 2px; }
.field-label { color: var(--usp-color-text); font-size: 13px; font-weight: 700; }
.inline-action-row { display: grid; grid-template-columns: auto 1fr; align-items: center; gap: 10px; }
.candidate-card { display: grid; gap: 10px; padding: 12px; border: 1px solid var(--usp-color-border); border-radius: var(--usp-radius-xl); background: var(--usp-color-surface-muted); }
.candidate-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 10px; }
.candidate-head > div { display: grid; min-width: 0; gap: 3px; }
.candidate-head small { overflow: hidden; color: var(--usp-color-text-secondary); text-overflow: ellipsis; white-space: nowrap; }
.candidate-actions,
.review-actions { display: flex; gap: 8px; }
.geojson-import-actions,
.edit-save-actions { display: grid; grid-template-columns: 1fr; gap: 8px; }
.full-width-action { width: 100%; margin: 0 !important; }
.file-action { display: flex; align-items: center; justify-content: center; box-sizing: border-box; min-height: 32px; padding: 7px 12px; border: 1px solid var(--usp-color-border); border-radius: var(--usp-radius-lg); background: var(--usp-color-surface); color: var(--usp-color-text); cursor: pointer; font-size: 13px; }
.file-action:hover { border-color: var(--usp-color-primary); color: var(--usp-color-primary); }
.file-action input { display: none; }
.review-actions .el-button { flex: 1; margin: 0; }
.archive-controls :deep(.el-input__wrapper),
.archive-controls :deep(.el-textarea__inner),
.archive-controls :deep(.el-radio-button__inner),
.archive-controls :deep(.el-button) { border-radius: var(--usp-radius-lg); }
.editor-card { position: relative; }
.editor-card :deep(.el-card__header) { padding: 13px 16px; }
.editor-card :deep(.el-card__body) { padding: 0; }
.editor-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.editor-head > div { display: grid; gap: 2px; }
.editor-head small { color: var(--usp-color-text-secondary); font-size: 11px; }
.editor-map { height: min(760px, calc(100vh - 185px)); min-height: 610px; border-radius: 0 0 var(--usp-radius-xl) var(--usp-radius-xl); overflow: hidden; }
.editor-unavailable,
.editor-busy { position: absolute; inset: 58px 0 0; display: grid; place-content: center; justify-items: center; gap: 8px; padding: 24px; border-radius: 0 0 var(--usp-radius-xl) var(--usp-radius-xl); background: rgba(248, 250, 252, .94); text-align: center; }

@media (max-width: 1240px) {
  .archive-summary { grid-template-columns: repeat(3, 1fr); }
  .archive-summary__entity { grid-column: span 2; }
  .archive-grid { grid-template-columns: minmax(350px, 410px) minmax(0, 1fr); }
}
@media (max-width: 1040px) {
  .archive-grid { grid-template-columns: 1fr; }
  .editor-map { height: 620px; min-height: 520px; }
}
@media (max-width: 720px) {
  .archive-summary { grid-template-columns: 1fr 1fr; }
  .archive-summary__entity { grid-column: 1 / -1; }
  .maintenance-level-row,
  .candidate-head,
  .editor-head { align-items: stretch; flex-direction: column; }
  .inline-action-row { grid-template-columns: 1fr; }
  .review-actions { align-items: stretch; flex-direction: column; }
}
</style>
