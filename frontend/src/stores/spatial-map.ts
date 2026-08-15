import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  querySpatialBuildings,
  querySpatialCommunities,
  type SpatialBboxQuery,
  type SpatialGeoJsonFeature,
} from '@/shared/api/endpoints/spatial'
import {
  getRiskMap,
  type DashboardBuilding,
  type RiskScopeType,
} from '@/shared/api/endpoints/reports'

export type MapRiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'VERY_HIGH'

export interface SpatialBuildingProjection {
  feature: SpatialGeoJsonFeature
  risk?: DashboardBuilding
}

export const useSpatialMapStore = defineStore('spatial-map', () => {
  const viewport = ref<SpatialBboxQuery | null>(null)
  const communityFeatures = ref<SpatialGeoJsonFeature[]>([])
  const buildingFeatures = ref<SpatialGeoJsonFeature[]>([])
  const riskRows = ref<DashboardBuilding[]>([])
  const selectedCommunityId = ref<string | null>(null)
  const selectedBuildingIds = ref<string[]>([])
  const riskLevels = ref<MapRiskLevel[]>([])
  const searchKeyword = ref('')
  const loading = ref(false)
  const errorMessage = ref('')
  let requestSequence = 0

  const riskByBuildingId = computed(() => {
    const result = new Map<string, DashboardBuilding>()
    riskRows.value.forEach((row) => result.set(row.buildingId, row))
    return result
  })

  const visibleRiskBuildings = computed<DashboardBuilding[]>(() => {
    const keyword = searchKeyword.value.trim().toLocaleLowerCase()
    const allowedRiskLevels = new Set(riskLevels.value)

    return riskRows.value.filter((risk) => {
      if (selectedCommunityId.value && risk.communityId !== selectedCommunityId.value) return false
      if (
        allowedRiskLevels.size > 0
        && (!risk.riskLevel || !allowedRiskLevels.has(risk.riskLevel as MapRiskLevel))
      ) {
        return false
      }
      if (!keyword) return true
      return [risk.buildingName, risk.buildingCode, risk.communityName]
        .filter(Boolean)
        .join(' ')
        .toLocaleLowerCase()
        .includes(keyword)
    })
  })

  const visibleBuildings = computed<SpatialBuildingProjection[]>(() => {
    const keyword = searchKeyword.value.trim().toLocaleLowerCase()
    const allowedRiskLevels = new Set(riskLevels.value)

    return buildingFeatures.value
      .map((feature) => ({
        feature,
        risk: riskByBuildingId.value.get(feature.id),
      }))
      .filter(({ feature, risk }) => {
        if (
          selectedCommunityId.value
          && feature.properties.communityId !== selectedCommunityId.value
          && risk?.communityId !== selectedCommunityId.value
        ) {
          return false
        }
        if (
          allowedRiskLevels.size > 0
          && (!risk?.riskLevel || !allowedRiskLevels.has(risk.riskLevel as MapRiskLevel))
        ) {
          return false
        }
        if (!keyword) return true
        const haystack = [
          feature.properties.name,
          feature.properties.entityCode,
          risk?.buildingName,
          risk?.buildingCode,
          risk?.communityName,
        ]
          .filter(Boolean)
          .join(' ')
          .toLocaleLowerCase()
        return haystack.includes(keyword)
      })
  })

  async function loadViewport(nextViewport: SpatialBboxQuery): Promise<void> {
    const requestId = ++requestSequence
    viewport.value = { ...nextViewport }
    loading.value = true
    errorMessage.value = ''

    const buildingQuery = selectedCommunityId.value
      ? { ...nextViewport, communityId: selectedCommunityId.value }
      : nextViewport
    const riskScope: RiskScopeType = selectedCommunityId.value ? 'COMMUNITY' : 'ALL'
    const scopeId = selectedCommunityId.value ?? undefined

    try {
      const [communities, buildings, risk] = await Promise.all([
        querySpatialCommunities(nextViewport),
        querySpatialBuildings(buildingQuery),
        getRiskMap(riskScope, scopeId).catch(() => null),
      ])
      if (requestId !== requestSequence) return

      communityFeatures.value = communities.features
      buildingFeatures.value = buildings.features
      riskRows.value = risk?.buildings ?? []
    } catch (error) {
      if (requestId !== requestSequence) return
      errorMessage.value = error instanceof Error ? error.message : String(error)
      throw error
    } finally {
      if (requestId === requestSequence) loading.value = false
    }
  }

  function selectCommunity(communityId: string | null): void {
    if (selectedCommunityId.value === communityId) return
    selectedCommunityId.value = communityId
    selectedBuildingIds.value = []
  }

  function toggleBuilding(buildingId: string): void {
    selectedBuildingIds.value = selectedBuildingIds.value.includes(buildingId)
      ? selectedBuildingIds.value.filter((id) => id !== buildingId)
      : [...selectedBuildingIds.value, buildingId]
  }

  function selectSingleBuilding(buildingId: string | null): void {
    selectedBuildingIds.value = buildingId ? [buildingId] : []
  }

  function clearBuildingSelection(): void {
    selectedBuildingIds.value = []
  }

  function setRiskLevels(levels: readonly MapRiskLevel[]): void {
    riskLevels.value = [...new Set(levels)]
  }

  function setSearchKeyword(keyword: string): void {
    searchKeyword.value = keyword
  }

  return {
    viewport,
    communityFeatures,
    buildingFeatures,
    riskRows,
    selectedCommunityId,
    selectedBuildingIds,
    riskLevels,
    searchKeyword,
    loading,
    errorMessage,
    visibleRiskBuildings,
    visibleBuildings,
    loadViewport,
    selectCommunity,
    toggleBuilding,
    selectSingleBuilding,
    clearBuildingSelection,
    setRiskLevels,
    setSearchKeyword,
  }
})