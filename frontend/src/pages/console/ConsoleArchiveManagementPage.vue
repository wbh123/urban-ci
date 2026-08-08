<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  createBuilding,
  createCommunity,
  getMapRuntimeConfig,
  listBuildings,
  listCommunities,
  previewArchiveReverseGeocoding,
  saveArchiveBuildingLocation,
  saveCommunityLocation,
  searchArchivePlaces,
  toAppError,
  type ArchiveProvider,
  type BuildingListRow,
  type CommunityListRow,
  type CreateBuildingRequest,
  type CreateCommunityRequest,
  type MapPlaceCandidate,
  type MapRuntimeConfig,
} from '@/shared/api'
import AppEmpty from '@/shared/components/AppEmpty.vue'
import AppError from '@/shared/components/AppError.vue'
import AppLoading from '@/shared/components/AppLoading.vue'
import { createArchivePointPicker, type ArchiveMapPoint } from '@/shared/map/archive-point-picker'
import { useAuthStore } from '@/stores/auth'

type CreationType = 'COMMUNITY' | 'BUILDING'
type SpatialEntityType = 'COMMUNITY' | 'BUILDING'

interface LocationDraft {
  longitude: number | null
  latitude: number | null
  formattedAddress: string
  provider: ArchiveProvider
  matchLevel: string
  mock: boolean
  metadata: Record<string, unknown>
}

const router = useRouter()
const auth = useAuthStore()
const pointPicker = createArchivePointPicker()

const communities = ref<CommunityListRow[]>([])
const buildings = ref<BuildingListRow[]>([])
const selectedCommunityId = ref('')
const selectedBuildingId = ref('')
const runtimeConfig = ref<MapRuntimeConfig | null>(null)
const loading = ref(false)
const buildingLoading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const notice = ref('')

const drawerVisible = ref(false)
const creationType = ref<CreationType>('COMMUNITY')
const drawerNotice = ref('')
const discoveryKeyword = ref('')
const searchingPlaces = ref(false)
const placeCandidates = ref<MapPlaceCandidate[]>([])
const mapContainer = ref<HTMLElement | null>(null)
const mapReady = ref(false)

const communityForm = ref<CreateCommunityRequest>(newCommunityForm())
const buildingForm = ref<CreateBuildingRequest>(newBuildingForm())
const locationDraft = ref<LocationDraft>(newLocationDraft())

const selectedCommunity = computed(
  () => communities.value.find((item) => item.id === selectedCommunityId.value) ?? null,
)
const selectedBuilding = computed(
  () => buildings.value.find((item) => item.id === selectedBuildingId.value) ?? null,
)
const canCreateCommunity = computed(() => auth.hasAnyRole(['ADMIN', 'GOVERNMENT_MANAGER']))
const targetRegion = computed(() => {
  if (creationType.value === 'COMMUNITY') return communityForm.value.administrativeRegion ?? ''
  return selectedCommunity.value?.administrativeRegion ?? ''
})
const drawerTitle = computed(() => creationType.value === 'COMMUNITY' ? '新增小区' : '新增楼栋')
const hasLocation = computed(() =>
  locationDraft.value.longitude !== null && locationDraft.value.latitude !== null,
)

onMounted(loadWorkspace)
onBeforeUnmount(() => pointPicker.destroy())

function newCommunityForm(): CreateCommunityRequest {
  return {
    communityCode: '',
    communityName: '',
    status: 'ACTIVE',
  }
}

function newBuildingForm(): CreateBuildingRequest {
  return {
    communityId: selectedCommunityId.value,
    buildingCode: '',
    buildingName: '',
    hasElevator: false,
    hasIllegalModification: false,
    hasGroundFloorBusiness: false,
    status: 'ACTIVE',
  }
}

function newLocationDraft(): LocationDraft {
  return {
    longitude: null,
    latitude: null,
    formattedAddress: '',
    provider: 'MANUAL',
    matchLevel: 'MANUAL_POINT',
    mock: false,
    metadata: {},
  }
}

async function loadWorkspace(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const [communityPage, config] = await Promise.all([
      listCommunities({ size: 100 }),
      getMapRuntimeConfig(),
    ])
    communities.value = communityPage.content ?? []
    runtimeConfig.value = config
    selectedCommunityId.value = communities.value[0]?.id ?? ''
    await loadBuildings()
  } catch (error) {
    errorMessage.value = toAppError(error).message
  } finally {
    loading.value = false
  }
}

async function loadCommunities(preferredCommunityId?: string): Promise<void> {
  const page = await listCommunities({ size: 100 })
  communities.value = page.content ?? []
  const preferred = preferredCommunityId
    ?? (communities.value.some((item) => item.id === selectedCommunityId.value)
      ? selectedCommunityId.value
      : '')
  selectedCommunityId.value = communities.value.some((item) => item.id === preferred)
    ? preferred
    : communities.value[0]?.id ?? ''
  await loadBuildings()
}

async function loadBuildings(preferredBuildingId?: string): Promise<void> {
  buildings.value = []
  selectedBuildingId.value = ''
  if (!selectedCommunityId.value) return
  buildingLoading.value = true
  try {
    const page = await listBuildings({ communityId: selectedCommunityId.value, size: 100 })
    buildings.value = page.content ?? []
    selectedBuildingId.value = buildings.value.some((item) => item.id === preferredBuildingId)
      ? preferredBuildingId ?? ''
      : buildings.value[0]?.id ?? ''
  } catch (error) {
    notice.value = toAppError(error).message
  } finally {
    buildingLoading.value = false
  }
}

async function selectCommunity(row: CommunityListRow): Promise<void> {
  selectedCommunityId.value = row.id
  notice.value = ''
  await loadBuildings()
}

function selectBuilding(row: BuildingListRow): void {
  selectedBuildingId.value = row.id
}

function resetCreationState(): void {
  drawerNotice.value = ''
  discoveryKeyword.value = ''
  placeCandidates.value = []
  locationDraft.value = newLocationDraft()
  pointPicker.destroy()
  mapReady.value = false
}

function openCommunityDrawer(): void {
  if (!canCreateCommunity.value) {
    ElMessage.warning('当前角色可维护已授权小区内的楼栋，但无权新建小区。')
    return
  }
  resetCreationState()
  creationType.value = 'COMMUNITY'
  communityForm.value = newCommunityForm()
  drawerVisible.value = true
}

function openBuildingDrawer(): void {
  if (!selectedCommunityId.value) {
    ElMessage.warning('请先选择小区，再新增楼栋。')
    return
  }
  resetCreationState()
  creationType.value = 'BUILDING'
  buildingForm.value = newBuildingForm()
  buildingForm.value.communityId = selectedCommunityId.value
  drawerVisible.value = true
}

async function mountPicker(): Promise<void> {
  pointPicker.destroy()
  mapReady.value = false
  if (!mapContainer.value || !runtimeConfig.value) return
  try {
    mapReady.value = await pointPicker.mount(mapContainer.value, runtimeConfig.value, {
      onSelect: (point) => { void handleMapPoint(point) },
    })
  } catch (error) {
    drawerNotice.value = toAppError(error).message
  }
}

function closeCreationDrawer(): void {
  pointPicker.destroy()
  mapReady.value = false
}

async function searchPlaces(): Promise<void> {
  const keyword = discoveryKeyword.value.trim()
  if (!keyword) {
    drawerNotice.value = '请输入小区、楼栋或地址关键词。'
    return
  }
  searchingPlaces.value = true
  drawerNotice.value = ''
  try {
    placeCandidates.value = await searchArchivePlaces({
      keyword,
      region: targetRegion.value || undefined,
      cityLimit: Boolean(targetRegion.value),
      pageSize: 8,
    })
    if (!placeCandidates.value.length) drawerNotice.value = '没有找到匹配地点，可继续手工填写。'
  } catch (error) {
    drawerNotice.value = toAppError(error).message
  } finally {
    searchingPlaces.value = false
  }
}

function choosePlaceCandidate(candidate: MapPlaceCandidate): void {
  locationDraft.value = {
    longitude: candidate.longitude,
    latitude: candidate.latitude,
    formattedAddress: candidate.formattedAddress ?? '',
    provider: candidate.provider,
    matchLevel: candidate.mock ? 'MOCK_PREVIEW' : 'PLACE_SEARCH',
    mock: candidate.mock,
    metadata: {
      providerObjectId: candidate.providerObjectId,
      adcode: candidate.adcode,
      citycode: candidate.citycode,
    },
  }
  prefillAddress(candidate.formattedAddress ?? '')
  if (creationType.value === 'COMMUNITY') {
    if (!communityForm.value.communityName.trim()) communityForm.value.communityName = candidate.name
    if (!communityForm.value.administrativeRegion) {
      communityForm.value.administrativeRegion = regionLabel(candidate)
    }
  } else if (!buildingForm.value.buildingName?.trim()) {
    buildingForm.value.buildingName = candidate.name
  }
  pointPicker.setPoint({ longitude: candidate.longitude, latitude: candidate.latitude })
}

async function handleMapPoint(point: ArchiveMapPoint): Promise<void> {
  locationDraft.value = {
    longitude: point.longitude,
    latitude: point.latitude,
    formattedAddress: '',
    provider: runtimeConfig.value?.mode === 'MOCK' ? 'MOCK' : 'AMAP',
    matchLevel: runtimeConfig.value?.mode === 'MOCK' ? 'MOCK_PREVIEW' : 'REVERSE_GEOCODING',
    mock: runtimeConfig.value?.mode === 'MOCK',
    metadata: {},
  }
  drawerNotice.value = ''
  try {
    const result = await previewArchiveReverseGeocoding(point)
    locationDraft.value = {
      longitude: result.longitude,
      latitude: result.latitude,
      formattedAddress: result.formattedAddress ?? '',
      provider: result.provider,
      matchLevel: result.mock ? 'MOCK_PREVIEW' : 'REVERSE_GEOCODING',
      mock: result.mock,
      metadata: {
        adcode: result.adcode,
        citycode: result.citycode,
        nearestPoiId: result.nearestPoiId,
        nearestPoiName: result.nearestPoiName,
      },
    }
    prefillAddress(result.formattedAddress ?? '')
  } catch (error) {
    drawerNotice.value = `坐标已保留，但地址识别失败：${toAppError(error).message}`
  }
}

function prefillAddress(address: string): void {
  if (!address) return
  if (creationType.value === 'COMMUNITY') communityForm.value.address = address
  else buildingForm.value.address = address
}

function regionLabel(candidate: MapPlaceCandidate): string {
  return [...new Set([candidate.province, candidate.city, candidate.district].filter(Boolean))].join('')
}

function currentLocationPayload() {
  if (!hasLocation.value) return null
  const draft = locationDraft.value
  return {
    longitude: draft.longitude as number,
    latitude: draft.latitude as number,
    ...(draft.formattedAddress ? { formattedAddress: draft.formattedAddress } : {}),
    provider: draft.provider,
    ...(draft.matchLevel ? { matchLevel: draft.matchLevel } : {}),
    mock: draft.mock,
    metadata: draft.metadata,
  }
}

async function submitCreation(): Promise<void> {
  drawerNotice.value = ''
  if (creationType.value === 'COMMUNITY') {
    if (!communityForm.value.communityCode.trim() || !communityForm.value.communityName.trim()) {
      drawerNotice.value = '请填写小区编码和小区名称。'
      return
    }
  } else if (!buildingForm.value.buildingCode.trim()) {
    drawerNotice.value = '请填写楼栋编码。'
    return
  }

  saving.value = true
  try {
    if (creationType.value === 'COMMUNITY') await submitCommunity()
    else await submitBuilding()
  } catch (error) {
    drawerNotice.value = toAppError(error).message
  } finally {
    saving.value = false
  }
}

async function submitCommunity(): Promise<void> {
  const created = await createCommunity({
    ...communityForm.value,
    communityCode: communityForm.value.communityCode.trim(),
    communityName: communityForm.value.communityName.trim(),
    address: communityForm.value.address?.trim() || undefined,
    administrativeRegion: communityForm.value.administrativeRegion?.trim() || undefined,
  })
  const payload = currentLocationPayload()
  let locationFailed = false
  if (payload) {
    try {
      await saveCommunityLocation(created.id, payload)
    } catch {
      locationFailed = true
    }
  }
  await loadCommunities(created.id)
  drawerVisible.value = false
  if (locationFailed) ElMessage.warning('档案已创建，但地图位置保存失败，可稍后补录')
  else ElMessage.success('小区档案已创建。')
}

async function submitBuilding(): Promise<void> {
  const created = await createBuilding({
    ...buildingForm.value,
    communityId: selectedCommunityId.value,
    buildingCode: buildingForm.value.buildingCode.trim(),
    buildingName: buildingForm.value.buildingName?.trim() || undefined,
    address: buildingForm.value.address?.trim() || undefined,
  })
  const payload = currentLocationPayload()
  let locationFailed = false
  if (payload) {
    try {
      await saveArchiveBuildingLocation(created.id, payload)
    } catch {
      locationFailed = true
    }
  }
  await loadBuildings(created.id)
  drawerVisible.value = false
  if (locationFailed) ElMessage.warning('档案已创建，但地图位置保存失败，可稍后补录')
  else ElMessage.success('楼栋档案已创建。')
}

function goToSpatialArchive(entityType: SpatialEntityType, entityId: string): void {
  void router.push({
    name: 'console-spatial-archive',
    query: {
      entityType,
      entityId,
      communityId: selectedCommunityId.value,
    },
  })
}
</script>

<template>
  <section class="archive-management-page">
    <header class="page-head">
      <div>
        <p class="eyebrow">Archive Management</p>
        <h1>小区与楼栋管理</h1>
        <p>先建立业务档案，再按需补充地图中心点和空间边界。</p>
      </div>
      <div class="page-actions">
        <el-button @click="loadWorkspace">刷新</el-button>
        <el-button :disabled="!canCreateCommunity" @click="openCommunityDrawer">新增小区</el-button>
        <el-button type="primary" :disabled="!selectedCommunityId" @click="openBuildingDrawer">新增楼栋</el-button>
      </div>
    </header>

    <AppLoading :visible="loading" inline text="加载档案目录中…" />
    <AppError v-if="errorMessage" :message="errorMessage" @retry="loadWorkspace" />

    <template v-if="!loading && !errorMessage">
      <el-alert v-if="notice" :title="notice" type="info" :closable="false" show-icon />
      <div class="directory-grid">
        <el-card shadow="never" class="directory-card">
          <template #header>
            <div class="card-head">
              <div><strong>小区目录</strong><span>{{ communities.length }} 个</span></div>
              <el-button
                v-if="selectedCommunity"
                link
                type="primary"
                @click="goToSpatialArchive('COMMUNITY', selectedCommunity.id)"
              >进入空间档案</el-button>
            </div>
          </template>
          <el-table
            v-if="communities.length"
            :data="communities"
            stripe
            highlight-current-row
            @row-click="selectCommunity"
          >
            <el-table-column prop="communityCode" label="编码" width="130" />
            <el-table-column prop="communityName" label="小区" min-width="150" />
            <el-table-column prop="administrativeRegion" label="行政区域" min-width="160" />
            <el-table-column prop="address" label="地址" min-width="200" show-overflow-tooltip />
          </el-table>
          <AppEmpty v-else description="当前范围暂无小区档案" />
        </el-card>

        <el-card shadow="never" class="directory-card">
          <template #header>
            <div class="card-head">
              <div>
                <strong>楼栋目录</strong>
                <span>{{ selectedCommunity?.communityName || '未选择小区' }} · {{ buildings.length }} 栋</span>
              </div>
              <el-button
                v-if="selectedBuilding"
                link
                type="primary"
                @click="goToSpatialArchive('BUILDING', selectedBuilding.id)"
              >进入空间档案</el-button>
            </div>
          </template>
          <AppLoading :visible="buildingLoading" inline text="加载楼栋中…" />
          <el-table
            v-if="!buildingLoading && buildings.length"
            :data="buildings"
            stripe
            highlight-current-row
            @row-click="selectBuilding"
          >
            <el-table-column prop="buildingCode" label="编码" width="130" />
            <el-table-column prop="buildingName" label="楼栋" min-width="150" />
            <el-table-column prop="address" label="地址" min-width="210" show-overflow-tooltip />
            <el-table-column prop="constructionYear" label="建成年份" width="110" />
          </el-table>
          <AppEmpty
            v-else-if="!buildingLoading"
            :description="selectedCommunityId ? '当前小区暂无楼栋，可直接新增' : '请先选择小区'"
          />
        </el-card>
      </div>
    </template>

    <el-drawer
      v-model="drawerVisible"
      :title="drawerTitle"
      size="min(760px, 94vw)"
      destroy-on-close
      @opened="mountPicker"
      @closed="closeCreationDrawer"
    >
      <el-form label-position="top" class="create-form">
        <template v-if="creationType === 'COMMUNITY'">
          <div class="form-grid">
            <el-form-item label="小区编码" required>
              <el-input v-model="communityForm.communityCode" placeholder="例如 COM-001" />
            </el-form-item>
            <el-form-item label="小区名称" required>
              <el-input v-model="communityForm.communityName" placeholder="请输入小区名称" />
            </el-form-item>
          </div>
          <el-form-item label="行政区域">
            <el-input v-model="communityForm.administrativeRegion" placeholder="例如 株洲市芦淞区" />
          </el-form-item>
          <el-form-item label="详细地址">
            <el-input v-model="communityForm.address" placeholder="可由地图候选或地图点选自动填写" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="communityForm.remark" type="textarea" :rows="2" />
          </el-form-item>
        </template>

        <template v-else>
          <el-alert
            :title="`所属小区：${selectedCommunity?.communityName || '未选择'}`"
            type="info"
            :closable="false"
            class="scope-alert"
          />
          <div class="form-grid">
            <el-form-item label="楼栋编码" required>
              <el-input v-model="buildingForm.buildingCode" placeholder="例如 B-01" />
            </el-form-item>
            <el-form-item label="楼栋名称">
              <el-input v-model="buildingForm.buildingName" placeholder="例如 1 栋" />
            </el-form-item>
          </div>
          <el-form-item label="详细地址">
            <el-input v-model="buildingForm.address" placeholder="可由地图候选或地图点选自动填写" />
          </el-form-item>
          <div class="form-grid three">
            <el-form-item label="建成年份">
              <el-input-number v-model="buildingForm.constructionYear" :min="1800" :max="2100" controls-position="right" />
            </el-form-item>
            <el-form-item label="楼层数">
              <el-input-number v-model="buildingForm.floorCount" :min="1" :max="200" controls-position="right" />
            </el-form-item>
            <el-form-item label="结构类型">
              <el-input v-model="buildingForm.structureType" placeholder="可选" />
            </el-form-item>
          </div>
          <div class="boolean-grid">
            <el-checkbox v-model="buildingForm.hasElevator">有电梯</el-checkbox>
            <el-checkbox v-model="buildingForm.hasIllegalModification">存在违规改造</el-checkbox>
            <el-checkbox v-model="buildingForm.hasGroundFloorBusiness">底层经营</el-checkbox>
          </div>
        </template>

        <el-divider content-position="left">地图辅助定位</el-divider>
        <div class="discovery-search">
          <el-input
            v-model="discoveryKeyword"
            clearable
            placeholder="搜索小区、楼栋或完整地址"
            @keyup.enter="searchPlaces"
          />
          <el-button :loading="searchingPlaces" @click="searchPlaces">搜索地点</el-button>
        </div>

        <div v-if="placeCandidates.length" class="candidate-list">
          <button
            v-for="candidate in placeCandidates"
            :key="`${candidate.providerObjectId || candidate.name}-${candidate.longitude}-${candidate.latitude}`"
            type="button"
            class="candidate-item"
            @click="choosePlaceCandidate(candidate)"
          >
            <strong>{{ candidate.name }}</strong>
            <span>{{ candidate.formattedAddress || '暂无详细地址' }}</span>
            <small>{{ candidate.longitude.toFixed(6) }}, {{ candidate.latitude.toFixed(6) }}</small>
          </button>
        </div>

        <div class="map-block">
          <div ref="mapContainer" class="point-map" aria-label="地图点选区域" />
          <div v-if="!mapReady" class="map-fallback">
            <strong>地图点选</strong>
            <span>
              {{ runtimeConfig?.mode === 'MOCK'
                ? '当前为 Mock 模式，可使用地点搜索或手工坐标完成建档。'
                : '地图未加载，可继续使用地点搜索或手工坐标。' }}
            </span>
          </div>
        </div>

        <div class="coordinate-grid">
          <el-form-item label="经度">
            <el-input-number v-model="locationDraft.longitude" :min="-180" :max="180" :precision="6" controls-position="right" />
          </el-form-item>
          <el-form-item label="纬度">
            <el-input-number v-model="locationDraft.latitude" :min="-90" :max="90" :precision="6" controls-position="right" />
          </el-form-item>
        </div>
        <p class="location-hint">
          地图位置为可选信息；即使第三方地图服务暂不可用，也不会阻断小区或楼栋业务档案创建。
        </p>
        <el-alert v-if="drawerNotice" :title="drawerNotice" type="warning" :closable="false" show-icon />
      </el-form>

      <template #footer>
        <div class="drawer-footer">
          <el-button @click="drawerVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="submitCreation">
            {{ creationType === 'COMMUNITY' ? '创建小区' : '创建楼栋' }}
          </el-button>
        </div>
      </template>
    </el-drawer>
  </section>
</template>

<style scoped lang="scss">
.archive-management-page { display: grid; gap: 18px; }
.page-head { display: flex; justify-content: space-between; align-items: flex-end; gap: 18px; }
.page-head h1 { margin: 4px 0; font-size: 32px; color: var(--usp-color-text-primary); }
.page-head p { margin: 0; color: var(--usp-color-text-secondary); }
.eyebrow { color: #287a6a !important; font-size: 12px; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
.page-actions, .card-head, .card-head > div, .drawer-footer { display: flex; align-items: center; gap: 10px; }
.card-head { justify-content: space-between; }
.card-head span { color: var(--usp-color-text-secondary); font-size: 13px; }
.directory-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 18px; }
.directory-card { min-width: 0; }
.create-form { display: grid; gap: 4px; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.form-grid.three { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.boolean-grid { display: flex; flex-wrap: wrap; gap: 18px; margin-bottom: 8px; }
.scope-alert { margin-bottom: 12px; }
.discovery-search { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 10px; }
.candidate-list { display: grid; gap: 8px; max-height: 220px; overflow: auto; margin: 12px 0; }
.candidate-item { display: grid; gap: 4px; width: 100%; padding: 10px 12px; border: 1px solid var(--usp-color-border, #d0d5dd); border-radius: 8px; background: var(--usp-color-surface, #fff); text-align: left; cursor: pointer; }
.candidate-item:hover { border-color: #409eff; }
.candidate-item span, .candidate-item small, .location-hint { color: var(--usp-color-text-secondary); }
.map-block { position: relative; min-height: 260px; margin-top: 12px; overflow: hidden; border: 1px solid var(--usp-color-border, #d0d5dd); border-radius: 10px; }
.point-map { position: absolute; inset: 0; }
.map-fallback { position: absolute; inset: 0; display: grid; place-content: center; gap: 6px; padding: 24px; background: var(--usp-color-surface-muted, #f7f8fa); text-align: center; color: var(--usp-color-text-secondary); }
.coordinate-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; margin-top: 12px; }
.coordinate-grid :deep(.el-input-number) { width: 100%; }
.location-hint { margin: 0 0 12px; font-size: 13px; line-height: 1.6; }
.drawer-footer { justify-content: flex-end; }
@media (max-width: 1100px) { .directory-grid { grid-template-columns: 1fr; } }
@media (max-width: 720px) { .page-head { align-items: flex-start; flex-direction: column; } .page-actions { flex-wrap: wrap; } .form-grid, .form-grid.three, .coordinate-grid { grid-template-columns: 1fr; } }
</style>
