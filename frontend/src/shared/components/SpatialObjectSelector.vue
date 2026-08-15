<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  useSpatialObjectSelector,
  type SpatialBuildingOption,
  type SpatialCommunityOption,
  type SpatialObjectSelection,
} from '@/shared/composables/useSpatialObjectSelector'

type SpatialSelectorMode = 'community' | 'building' | 'both'

interface Props {
  mode?: SpatialSelectorMode
  communityId?: string
  buildingId?: string
  disabled?: boolean
  clearable?: boolean
  showGlobalSearch?: boolean
  communityStatus?: string
}

const props = withDefaults(defineProps<Props>(), {
  mode: 'both',
  communityId: '',
  buildingId: '',
  disabled: false,
  clearable: true,
  showGlobalSearch: true,
  communityStatus: 'ACTIVE',
})

const emit = defineEmits<{
  'update:communityId': [value: string]
  'update:buildingId': [value: string]
  change: [value: SpatialObjectSelection]
}>()

const selector = useSpatialObjectSelector({ communityStatus: props.communityStatus })
const localCommunityId = ref(props.communityId)
const localBuildingId = ref(props.buildingId)
const searchSelection = ref('')
let syncSequence = 0

const showBuildingSelector = computed(() => props.mode !== 'community')
const selectedCommunity = computed(() => findCommunity(localCommunityId.value))
const selectedBuilding = computed(() => findBuilding(localBuildingId.value))
const busy = computed(() => (
  selector.communityLoading.value
  || selector.buildingLoading.value
  || selector.resolving.value
))
const searchEmptyCopy = computed(() => {
  if (selector.searchLoading.value) return '正在搜索…'
  if (!selector.searchAttempted.value) return '输入小区、楼栋名称或楼栋编号开始搜索'
  if (selector.errorMessage.value) return '空间对象搜索暂不可用，请稍后重试'
  return '未找到匹配的空间对象'
})

onMounted(async () => {
  await selector.loadCommunities()
  await syncFromProps(true)
})

watch(
  () => [props.communityId, props.buildingId] as const,
  ([communityId, buildingId]) => {
    if (communityId === localCommunityId.value && buildingId === localBuildingId.value) return
    void syncFromProps(false)
  },
)

function normalizeId(value: unknown): string {
  return typeof value === 'string' ? value : ''
}

function findCommunity(communityId: string): SpatialCommunityOption | null {
  if (!communityId) return null
  return selector.communities.value.find((item) => item.id === communityId)
    ?? selector.searchCommunityResults.value.find((item) => item.id === communityId)
    ?? null
}

function findBuilding(buildingId: string): SpatialBuildingOption | null {
  if (!buildingId) return null
  return selector.buildings.value.find((item) => item.id === buildingId)
    ?? selector.searchBuildingResults.value.find((item) => item.id === buildingId)
    ?? null
}

function currentLevel(): SpatialObjectSelection['level'] {
  if (localBuildingId.value) return 'BUILDING'
  if (localCommunityId.value && props.mode !== 'building') return 'COMMUNITY'
  return null
}

function emitSelection(): void {
  emit('change', {
    level: currentLevel(),
    communityId: localCommunityId.value,
    buildingId: localBuildingId.value,
    community: selectedCommunity.value,
    building: selectedBuilding.value,
  })
}

function emitIds(): void {
  emit('update:communityId', localCommunityId.value)
  emit('update:buildingId', localBuildingId.value)
}

async function syncFromProps(emitInitialChange: boolean): Promise<void> {
  const sequence = ++syncSequence
  const requestedCommunityId = props.communityId.trim()
  const requestedBuildingId = props.buildingId.trim()

  if (requestedBuildingId) {
    const resolved = await selector.resolveBuildingPath(requestedBuildingId)
    if (sequence !== syncSequence) return
    localCommunityId.value = resolved.community?.id ?? requestedCommunityId
    localBuildingId.value = resolved.building?.id ?? ''
    if (localCommunityId.value !== requestedCommunityId) emit('update:communityId', localCommunityId.value)
    if (localBuildingId.value !== requestedBuildingId) emit('update:buildingId', localBuildingId.value)
    if (emitInitialChange) emitSelection()
    return
  }

  localCommunityId.value = requestedCommunityId
  localBuildingId.value = ''
  if (requestedCommunityId) {
    await selector.resolveCommunity(requestedCommunityId)
    await selector.loadBuildings(requestedCommunityId)
  } else {
    await selector.loadBuildings('')
  }
  if (sequence !== syncSequence) return
  if (emitInitialChange) emitSelection()
}

async function handleCommunityChange(value: unknown): Promise<void> {
  localCommunityId.value = normalizeId(value)
  localBuildingId.value = ''
  emit('update:communityId', localCommunityId.value)
  emit('update:buildingId', '')
  selector.clearSearchResults()

  if (localCommunityId.value) {
    await selector.resolveCommunity(localCommunityId.value)
    await selector.loadBuildings(localCommunityId.value)
  } else {
    await selector.loadBuildings('')
  }
  emitSelection()
}

function handleBuildingChange(value: unknown): void {
  localBuildingId.value = normalizeId(value)
  emit('update:buildingId', localBuildingId.value)
  selector.clearSearchResults()
  emitSelection()
}

function handleCommunityRemoteSearch(keyword: string): void {
  void selector.loadCommunities(keyword)
}

function handleBuildingRemoteSearch(keyword: string): void {
  if (!localCommunityId.value) return
  void selector.loadBuildings(localCommunityId.value, keyword, true)
}

function handleGlobalSearch(keyword: string): void {
  void selector.search(keyword)
}

async function handleSearchSelection(value: unknown): Promise<void> {
  const token = normalizeId(value)
  if (!token) return
  const separator = token.indexOf(':')
  if (separator < 0) return
  const kind = token.slice(0, separator)
  const id = token.slice(separator + 1)

  if (kind === 'COMMUNITY') await selectSearchCommunity(id)
  if (kind === 'BUILDING') await selectSearchBuilding(id)
  searchSelection.value = ''
  selector.clearSearchResults()
}

async function selectSearchCommunity(communityId: string): Promise<void> {
  const community = await selector.resolveCommunity(communityId)
  if (!community) return
  localCommunityId.value = community.id
  localBuildingId.value = ''
  emitIds()
  await selector.loadBuildings(community.id)
  emitSelection()
}

async function selectSearchBuilding(buildingId: string): Promise<void> {
  const resolved = await selector.resolveBuildingPath(buildingId)
  if (!resolved.community || !resolved.building) return
  localCommunityId.value = resolved.community.id
  localBuildingId.value = resolved.building.id
  emitIds()
  emitSelection()
}

function communityNameFor(communityId: string): string {
  return findCommunity(communityId)?.communityName?.trim()
    || `小区 ${communityId.slice(0, 8)}`
}

function communityLabel(item: SpatialCommunityOption): string {
  return item.communityName?.trim() || `未命名小区 · ${item.id.slice(0, 8)}`
}

function buildingLabel(item: SpatialBuildingOption): string {
  const code = item.buildingCode?.trim()
  const name = item.buildingName?.trim()
  return [code, name].filter(Boolean).join(' · ') || `未命名楼栋 · ${item.id.slice(0, 8)}`
}
</script>

<template>
  <div class="spatial-selector" :class="{ 'is-disabled': disabled }">
    <div v-if="showGlobalSearch" class="spatial-selector__search">
      <label class="spatial-selector__label">空间对象搜索</label>
      <el-select
        v-model="searchSelection"
        class="spatial-selector__control"
        filterable
        remote
        clearable
        reserve-keyword
        :disabled="disabled"
        :loading="selector.searchLoading.value"
        :remote-method="handleGlobalSearch"
        placeholder="搜索小区、楼栋名称或楼栋编号"
        @change="handleSearchSelection"
      >
        <el-option-group
          v-if="selector.searchCommunityResults.value.length"
          label="搜索结果 · 小区"
        >
          <el-option
            v-for="item in selector.searchCommunityResults.value"
            :key="`community:${item.id}`"
            :label="communityLabel(item)"
            :value="`COMMUNITY:${item.id}`"
          >
            <div class="search-option">
              <strong>{{ communityLabel(item) }}</strong>
              <small>{{ item.administrativeRegion || item.address || '小区档案' }}</small>
            </div>
          </el-option>
        </el-option-group>

        <el-option-group
          v-if="mode !== 'community' && selector.searchBuildingResults.value.length"
          label="搜索结果 · 楼栋"
        >
          <el-option
            v-for="item in selector.searchBuildingResults.value"
            :key="`building:${item.id}`"
            :label="`${communityNameFor(item.communityId)} · ${buildingLabel(item)}`"
            :value="`BUILDING:${item.id}`"
          >
            <div class="search-option">
              <strong>{{ buildingLabel(item) }}</strong>
              <small>{{ communityNameFor(item.communityId) }}</small>
            </div>
          </el-option>
        </el-option-group>

        <template #empty>
          <div class="spatial-selector__empty">{{ searchEmptyCopy }}</div>
        </template>
      </el-select>
    </div>

    <div class="spatial-selector__cascade" :class="{ 'has-building': showBuildingSelector }">
      <div class="spatial-selector__field">
        <label class="spatial-selector__label">小区</label>
        <el-select
          v-model="localCommunityId"
          class="spatial-selector__control"
          filterable
          remote
          :clearable="clearable"
          :disabled="disabled"
          :loading="selector.communityLoading.value || selector.resolving.value"
          :remote-method="handleCommunityRemoteSearch"
          placeholder="选择或搜索小区"
          @change="handleCommunityChange"
        >
          <el-option
            v-for="item in selector.communities.value"
            :key="item.id"
            :label="communityLabel(item)"
            :value="item.id"
          />
        </el-select>
      </div>

      <div v-if="showBuildingSelector" class="spatial-selector__field">
        <label class="spatial-selector__label">楼栋</label>
        <el-select
          v-model="localBuildingId"
          class="spatial-selector__control"
          filterable
          remote
          :clearable="clearable"
          :disabled="disabled || !localCommunityId"
          :loading="selector.buildingLoading.value || selector.resolving.value"
          :remote-method="handleBuildingRemoteSearch"
          placeholder="选择或搜索楼栋"
          @change="handleBuildingChange"
        >
          <el-option
            v-for="item in selector.buildings.value"
            :key="item.id"
            :label="buildingLabel(item)"
            :value="item.id"
          />
        </el-select>
      </div>
    </div>

    <p v-if="selector.errorMessage.value" class="spatial-selector__error">
      {{ selector.errorMessage.value }}
    </p>
    <p v-else-if="mode === 'building' && localCommunityId && !localBuildingId && !busy" class="spatial-selector__hint">
      已选择小区，请继续选择楼栋。
    </p>
  </div>
</template>

<style scoped lang="scss">
.spatial-selector {
  display: grid;
  gap: 12px;
  width: 100%;
}

.spatial-selector__search,
.spatial-selector__field {
  display: grid;
  min-width: 0;
  gap: 6px;
}

.spatial-selector__label {
  color: var(--usp-color-text-secondary);
  font-size: 12px;
  font-weight: 700;
}

.spatial-selector__control {
  width: 100%;
}

.spatial-selector__cascade {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 10px;
}

.spatial-selector__cascade.has-building {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.search-option {
  display: grid;
  min-width: 0;
  gap: 2px;
  line-height: 1.25;
}

.search-option strong,
.search-option small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.search-option strong {
  color: var(--usp-color-text-primary);
  font-size: 13px;
}

.search-option small,
.spatial-selector__hint,
.spatial-selector__empty {
  color: var(--usp-color-text-secondary);
  font-size: 12px;
}

.spatial-selector__empty {
  padding: 12px 14px;
  text-align: center;
}

.spatial-selector__hint,
.spatial-selector__error {
  margin: -2px 0 0;
  line-height: 1.5;
}

.spatial-selector__error {
  color: var(--usp-color-danger);
  font-size: 12px;
}

@media (max-width: 720px) {
  .spatial-selector__cascade.has-building {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
