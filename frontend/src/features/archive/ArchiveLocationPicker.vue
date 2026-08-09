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

const pointPicker = createArchivePointPicker()
const runtimeConfig = ref<MapRuntimeConfig | null>(null)
const mapContainer = ref<HTMLElement | null>(null)
const mapReady = ref(false)
const searchKeyword = ref(props.keyword)
const searching = ref(false)
const candidates = ref<MapPlaceCandidate[]>([])
const notice = ref('')

onMounted(async () => {
  try {
    runtimeConfig.value = await getMapRuntimeConfig()
    if (mapContainer.value) {
      mapReady.value = await pointPicker.mount(mapContainer.value, runtimeConfig.value, {
        onSelect: (point) => { void chooseMapPoint(point) },
      })
    }
  } catch (error) {
    notice.value = `地图不可用：${toAppError(error).message}。仍可使用手工坐标完成建档。`
  }
})

onBeforeUnmount(() => pointPicker.destroy())

async function search(): Promise<void> {
  const keyword = searchKeyword.value.trim()
  if (!keyword) {
    notice.value = '请输入小区、楼栋或完整地址。'
    return
  }
  searching.value = true
  notice.value = ''
  try {
    candidates.value = await searchArchivePlaces({
      keyword,
      region: props.region.trim() || undefined,
      cityLimit: Boolean(props.region.trim()),
      pageSize: 8,
    })
    if (!candidates.value.length) notice.value = '没有找到匹配地点，可继续手工填写。'
  } catch (error) {
    notice.value = `地点搜索不可用：${toAppError(error).message}。可继续手工填写。`
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
  notice.value = ''
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
    const mock = runtimeConfig.value?.mode === 'MOCK'
    emit('select', {
      longitude: point.longitude,
      latitude: point.latitude,
      formattedAddress: '',
      provider: mock ? 'MOCK' : 'MANUAL',
      coordinateSystem: mock ? 'GCJ02' : 'GCJ02',
      matchLevel: 'MANUAL_POINT',
      mock,
      metadata: {},
    })
    notice.value = `坐标已保留，但地址识别失败：${toAppError(error).message}`
  }
}
</script>

<template>
  <section class="archive-location-picker">
    <el-alert
      title="地图结果只用于候选和预填，最终仍需人工核对后确认创建。"
      type="info"
      :closable="false"
      show-icon
    />
    <div class="search-row">
      <el-input v-model="searchKeyword" clearable placeholder="搜索小区、楼栋或完整地址" @keyup.enter="search" />
      <el-button :loading="searching" @click="search">搜索地点</el-button>
    </div>
    <el-alert v-if="notice" :title="notice" type="warning" :closable="false" show-icon />
    <div v-if="candidates.length" class="candidate-list">
      <button
        v-for="candidate in candidates"
        :key="candidate.providerObjectId || `${candidate.name}-${candidate.longitude}-${candidate.latitude}`"
        type="button"
        class="candidate-item"
        @click="chooseCandidate(candidate)"
      >
        <span><strong>{{ candidate.name }}</strong><small>{{ candidate.formattedAddress || '地址待补充' }}</small></span>
        <el-tag size="small" effect="plain">{{ candidate.provider }}</el-tag>
      </button>
    </div>
    <div class="map-shell">
      <div ref="mapContainer" class="map-canvas" aria-label="地图点选区域" />
      <div v-if="!mapReady" class="map-fallback">
        {{ runtimeConfig?.mode === 'MOCK' ? 'Mock 模式不加载地图，可使用搜索候选或手工坐标。' : '地图未加载，可继续手工填写。' }}
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
.archive-location-picker { display: grid; gap: var(--usp-space-3); }
.search-row { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: var(--usp-space-2); }
.candidate-list { display: grid; gap: var(--usp-space-2); max-height: 200px; overflow: auto; }
.candidate-item { display: flex; justify-content: space-between; gap: 12px; width: 100%; padding: 10px 12px; border: 1px solid var(--usp-border-color); border-radius: var(--usp-radius-md); background: var(--usp-surface); color: inherit; text-align: left; cursor: pointer; }
.candidate-item span { min-width: 0; display: grid; gap: 4px; }
.candidate-item small { color: var(--usp-text-secondary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.map-shell { position: relative; min-height: 260px; border: 1px solid var(--usp-border-color); border-radius: var(--usp-radius-md); overflow: hidden; }
.map-canvas { width: 100%; height: 280px; }
.map-fallback { position: absolute; inset: 0; display: grid; place-items: center; padding: 24px; text-align: center; color: var(--usp-text-secondary); background: var(--usp-surface); }
@media (max-width: 640px) { .search-row { grid-template-columns: 1fr; } }
</style>
