<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import {
  getMapRuntimeConfig,
  previewArchiveReverseGeocoding,
  searchArchivePlaces,
  toAppError,
  type ArchiveCoordinateSystem,
  type ArchiveProvider,
  type MapPlaceCandidate,
  type MapRuntimeConfig,
} from '@/shared/api'
import { createArchivePointPicker, type ArchiveMapPoint } from '@/shared/map/archive-point-picker'
import { useAppStore } from '@/stores/app'

export interface ArchiveLocationSelection {
  longitude: number
  latitude: number
  formattedAddress: string
  provider: ArchiveProvider
  coordinateSystem: ArchiveCoordinateSystem
  matchLevel: string
  mock: boolean
  name?: string
  metadata: Record<string, unknown>
}

const props = withDefaults(defineProps<{
  keyword?: string
  region?: string
}>(), {
  keyword: '',
  region: '',
})

const emit = defineEmits<{
  select: [selection: ArchiveLocationSelection]
}>()

const appStore = useAppStore()
const pointPicker = createArchivePointPicker()
const runtimeConfig = ref<MapRuntimeConfig | null>(null)
const mapContainer = ref<HTMLElement | null>(null)
const mapReady = ref(false)
const searchKeyword = ref(props.keyword)
const searching = ref(false)
const candidates = ref<MapPlaceCandidate[]>([])

onMounted(async () => {
  try {
    runtimeConfig.value = await getMapRuntimeConfig()
    if (mapContainer.value) {
      mapReady.value = await pointPicker.mount(mapContainer.value, runtimeConfig.value, {
        onSelect: (point) => { void chooseMapPoint(point) },
      })
    }
  } catch (error) {
    appStore.notify(`地图服务暂不可用：${toAppError(error).message}`, 'warning')
  }
})

onBeforeUnmount(() => pointPicker.destroy())

async function search(): Promise<void> {
  const keyword = searchKeyword.value.trim()
  if (!keyword) {
    appStore.notify('请输入小区、楼栋或完整地址。', 'warning')
    return
  }
  searching.value = true
  try {
    candidates.value = await searchArchivePlaces({
      keyword,
      region: props.region.trim() || undefined,
      cityLimit: Boolean(props.region.trim()),
      pageSize: 8,
    })
    if (!candidates.value.length) appStore.notify('没有找到匹配地点，可继续手工填写。', 'info')
  } catch (error) {
    appStore.notify(`地点搜索失败：${toAppError(error).message}`, 'error')
  } finally {
    searching.value = false
  }
}

function chooseCandidate(candidate: MapPlaceCandidate): void {
  const selection: ArchiveLocationSelection = {
    longitude: candidate.longitude,
    latitude: candidate.latitude,
    formattedAddress: candidate.formattedAddress ?? '',
    provider: candidate.provider,
    coordinateSystem: candidate.coordinateSystem,
    matchLevel: candidate.mock ? 'MOCK_PREVIEW' : 'PLACE_SEARCH',
    mock: candidate.mock,
    name: candidate.name,
    metadata: {
      providerObjectId: candidate.providerObjectId,
      adcode: candidate.adcode,
      citycode: candidate.citycode,
    },
  }
  pointPicker.setPoint(selection)
  emit('select', selection)
}

async function chooseMapPoint(point: ArchiveMapPoint): Promise<void> {
  try {
    const result = await previewArchiveReverseGeocoding(point)
    emit('select', {
      longitude: result.longitude,
      latitude: result.latitude,
      formattedAddress: result.formattedAddress ?? '',
      provider: result.provider,
      coordinateSystem: result.coordinateSystem,
      matchLevel: result.mock ? 'MOCK_PREVIEW' : 'REVERSE_GEOCODING',
      mock: result.mock,
      name: result.nearestPoiName ?? undefined,
      metadata: {
        adcode: result.adcode,
        citycode: result.citycode,
        nearestPoiId: result.nearestPoiId,
        nearestPoiName: result.nearestPoiName,
      },
    })
  } catch (error) {
    const fallback = runtimeConfig.value?.mode === 'MOCK'
    emit('select', {
      longitude: point.longitude,
      latitude: point.latitude,
      formattedAddress: '',
      provider: fallback ? 'MOCK' : 'MANUAL',
      coordinateSystem: 'GCJ02',
      matchLevel: 'MANUAL_POINT',
      mock: fallback,
      metadata: {},
    })
    appStore.notify(`坐标已保留，地址识别失败：${toAppError(error).message}`, 'warning')
  }
}
</script>

<template>
  <section class="archive-location-picker">
    <div class="search-row">
      <el-input v-model="searchKeyword" clearable placeholder="搜索小区、楼栋或完整地址" @keyup.enter="search" />
      <el-button :loading="searching" @click="search">搜索地点</el-button>
    </div>
    <div v-if="candidates.length" class="candidate-list">
      <button
        v-for="candidate in candidates"
        :key="candidate.providerObjectId || `${candidate.name}-${candidate.longitude}-${candidate.latitude}`"
        type="button"
        class="candidate-item"
        @click="chooseCandidate(candidate)"
      >
        <span><strong>{{ candidate.name }}</strong><small>{{ candidate.formattedAddress || '地址待补充' }}</small></span>
        <el-tag size="small" effect="plain" round>选择</el-tag>
      </button>
    </div>
    <div class="map-shell">
      <div ref="mapContainer" class="map-canvas" aria-label="地图点选区域" />
      <div v-if="!mapReady" class="map-fallback">
        地图暂未加载，可使用地点搜索或手工填写坐标。
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
.archive-location-picker { display: grid; gap: var(--usp-space-3); }
.search-row { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: var(--usp-space-2); }
.candidate-list { display: grid; gap: var(--usp-space-2); max-height: 200px; overflow: auto; }
.candidate-item { display: flex; justify-content: space-between; gap: 12px; width: 100%; padding: 11px 13px; border: 1px solid var(--usp-color-border); border-radius: var(--usp-radius-lg); background: var(--usp-color-surface); color: inherit; text-align: left; cursor: pointer; box-shadow: var(--usp-shadow-sm); }
.candidate-item:hover { border-color: rgba(40,122,106,.38); background: var(--usp-color-primary-light); }
.candidate-item span { min-width: 0; display: grid; gap: 4px; }
.candidate-item small { color: var(--usp-color-text-secondary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.map-shell { position: relative; min-height: 260px; border: 1px solid var(--usp-color-border); border-radius: var(--usp-radius-xl); overflow: hidden; box-shadow: var(--usp-shadow-sm); }
.map-canvas { width: 100%; height: 280px; }
.map-fallback { position: absolute; inset: 0; display: grid; place-items: center; padding: 24px; text-align: center; color: var(--usp-color-text-secondary); background: var(--usp-color-surface); }
@media (max-width: 640px) { .search-row { grid-template-columns: 1fr; } }
</style>
