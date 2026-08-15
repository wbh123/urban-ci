import { ref } from 'vue'
import {
  getCommunity,
  listCommunities,
  type CommunityListRow,
  type CommunityResponse,
} from '@/shared/api/endpoints/communities'
import {
  getBuilding,
  listBuildings,
  type BuildingListRow,
  type BuildingResponse,
} from '@/shared/api/endpoints/buildings'
import { toAppError } from '@/shared/api/error'

export interface SpatialCommunityOption {
  id: string
  communityName?: string | null
  address?: string | null
  administrativeRegion?: string | null
  status?: string | null
}

export interface SpatialBuildingOption {
  id: string
  communityId: string
  buildingCode?: string | null
  buildingName?: string | null
  status?: string | null
}

export interface SpatialObjectSelection {
  level: 'COMMUNITY' | 'BUILDING' | null
  communityId: string
  buildingId: string
  community: SpatialCommunityOption | null
  building: SpatialBuildingOption | null
}

export interface SpatialResolvedBuildingPath {
  community: SpatialCommunityOption | null
  building: SpatialBuildingOption | null
}

export interface SpatialObjectSelectorOptions {
  communityStatus?: string
  searchDebounceMs?: number
}

const SEARCH_RESULT_SIZE = 20
const COMMUNITY_BUILDING_CACHE_SIZE = 100

function toCommunityOption(value: CommunityListRow | CommunityResponse): SpatialCommunityOption {
  return {
    id: value.id,
    communityName: value.communityName,
    address: value.address,
    administrativeRegion: value.administrativeRegion,
    status: value.status,
  }
}

function toBuildingOption(value: BuildingListRow | BuildingResponse): SpatialBuildingOption {
  return {
    id: value.id,
    communityId: value.communityId ?? '',
    buildingCode: value.buildingCode,
    buildingName: value.buildingName,
    status: value.status,
  }
}

export function useSpatialObjectSelector(options: SpatialObjectSelectorOptions = {}) {
  const communityStatus = options.communityStatus ?? 'ACTIVE'
  const debounceMs = options.searchDebounceMs ?? 300

  const communities = ref<SpatialCommunityOption[]>([])
  const buildings = ref<SpatialBuildingOption[]>([])
  const searchCommunityResults = ref<SpatialCommunityOption[]>([])
  const searchBuildingResults = ref<SpatialBuildingOption[]>([])
  const communityLoading = ref(false)
  const buildingLoading = ref(false)
  const searchLoading = ref(false)
  const searchAttempted = ref(false)
  const resolving = ref(false)
  const errorMessage = ref('')

  const buildingCache = new Map<string, SpatialBuildingOption[]>()
  let searchSequence = 0
  let searchTimer: ReturnType<typeof setTimeout> | null = null
  let scheduledSearchResolve: (() => void) | null = null

  function mergeCommunity(option: SpatialCommunityOption): void {
    const index = communities.value.findIndex((item) => item.id === option.id)
    if (index >= 0) communities.value[index] = option
    else communities.value = [...communities.value, option]
  }

  function mergeBuilding(option: SpatialBuildingOption): void {
    const index = buildings.value.findIndex((item) => item.id === option.id)
    if (index >= 0) buildings.value[index] = option
    else buildings.value = [...buildings.value, option]
  }

  async function loadCommunities(keyword = ''): Promise<SpatialCommunityOption[]> {
    communityLoading.value = true
    errorMessage.value = ''
    try {
      const page = await listCommunities({
        ...(keyword.trim() ? { keyword: keyword.trim() } : {}),
        status: communityStatus,
        page: 0,
        size: SEARCH_RESULT_SIZE,
      })
      const result = (page.content ?? []).map(toCommunityOption)
      communities.value = result
      return result
    } catch (error) {
      errorMessage.value = toAppError(error).message
      return []
    } finally {
      communityLoading.value = false
    }
  }

  async function loadBuildings(
    communityId: string,
    keyword = '',
    force = false,
  ): Promise<SpatialBuildingOption[]> {
    const normalizedCommunityId = communityId.trim()
    if (!normalizedCommunityId) {
      buildings.value = []
      return []
    }

    const normalizedKeyword = keyword.trim()
    if (!normalizedKeyword && !force) {
      const cached = buildingCache.get(normalizedCommunityId)
      if (cached) {
        buildings.value = cached
        return cached
      }
    }

    buildingLoading.value = true
    errorMessage.value = ''
    try {
      const page = await listBuildings({
        communityId: normalizedCommunityId,
        ...(normalizedKeyword ? { keyword: normalizedKeyword } : {}),
        page: 0,
        size: normalizedKeyword ? SEARCH_RESULT_SIZE : COMMUNITY_BUILDING_CACHE_SIZE,
      })
      const result = (page.content ?? []).map(toBuildingOption)
      buildings.value = result
      if (!normalizedKeyword) buildingCache.set(normalizedCommunityId, result)
      return result
    } catch (error) {
      errorMessage.value = toAppError(error).message
      return []
    } finally {
      buildingLoading.value = false
    }
  }

  function clearSearchResults(): void {
    searchCommunityResults.value = []
    searchBuildingResults.value = []
    searchLoading.value = false
  }

  function cancelScheduledSearch(): void {
    if (searchTimer !== null) {
      clearTimeout(searchTimer)
      searchTimer = null
    }
    if (scheduledSearchResolve) {
      scheduledSearchResolve()
      scheduledSearchResolve = null
    }
  }

  function search(keyword: string): Promise<void> {
    const normalizedKeyword = keyword.trim()
    const sequence = ++searchSequence
    cancelScheduledSearch()

    if (!normalizedKeyword) {
      clearSearchResults()
      searchAttempted.value = false
      errorMessage.value = ''
      return Promise.resolve()
    }

    searchAttempted.value = true
    searchLoading.value = true
    return new Promise((resolve) => {
      scheduledSearchResolve = resolve
      searchTimer = setTimeout(async () => {
        searchTimer = null
        scheduledSearchResolve = null
        const [communityResult, buildingResult] = await Promise.allSettled([
          listCommunities({
            keyword: normalizedKeyword,
            status: communityStatus,
            page: 0,
            size: SEARCH_RESULT_SIZE,
          }),
          listBuildings({ keyword: normalizedKeyword, page: 0, size: SEARCH_RESULT_SIZE }),
        ])

        if (sequence !== searchSequence) {
          resolve()
          return
        }

        errorMessage.value = ''
        if (communityResult.status === 'fulfilled') {
          searchCommunityResults.value = (communityResult.value.content ?? []).map(toCommunityOption)
        } else {
          searchCommunityResults.value = []
          errorMessage.value = toAppError(communityResult.reason).message
        }
        if (buildingResult.status === 'fulfilled') {
          searchBuildingResults.value = (buildingResult.value.content ?? []).map(toBuildingOption)
        } else {
          searchBuildingResults.value = []
          if (!errorMessage.value) errorMessage.value = toAppError(buildingResult.reason).message
        }

        const knownCommunityIds = new Set([
          ...communities.value.map((item) => item.id),
          ...searchCommunityResults.value.map((item) => item.id),
        ])
        const missingParentIds = [...new Set(
          searchBuildingResults.value
            .map((item) => item.communityId)
            .filter((id) => id && !knownCommunityIds.has(id)),
        )]
        if (missingParentIds.length > 0) {
          const parents = await Promise.allSettled(missingParentIds.map((id) => getCommunity(id)))
          if (sequence !== searchSequence) {
            resolve()
            return
          }
          parents.forEach((result) => {
            if (result.status === 'fulfilled') mergeCommunity(toCommunityOption(result.value))
          })
        }

        searchLoading.value = false
        resolve()
      }, debounceMs)
    })
  }

  async function resolveCommunity(communityId: string): Promise<SpatialCommunityOption | null> {
    const normalizedCommunityId = communityId.trim()
    if (!normalizedCommunityId) return null

    const existing = communities.value.find((item) => item.id === normalizedCommunityId)
      ?? searchCommunityResults.value.find((item) => item.id === normalizedCommunityId)
    if (existing) {
      mergeCommunity(existing)
      return existing
    }

    resolving.value = true
    errorMessage.value = ''
    try {
      const community = toCommunityOption(await getCommunity(normalizedCommunityId))
      mergeCommunity(community)
      return community
    } catch (error) {
      errorMessage.value = toAppError(error).message
      return null
    } finally {
      resolving.value = false
    }
  }

  async function resolveBuildingPath(buildingId: string): Promise<SpatialResolvedBuildingPath> {
    const normalizedBuildingId = buildingId.trim()
    if (!normalizedBuildingId) return { community: null, building: null }

    resolving.value = true
    errorMessage.value = ''
    try {
      const rawBuilding = await getBuilding(normalizedBuildingId)
      const building = toBuildingOption(rawBuilding)
      if (!building.communityId) return { community: null, building }

      const community = await resolveCommunity(building.communityId)
      await loadBuildings(building.communityId)
      mergeBuilding(building)
      const cached = buildingCache.get(building.communityId) ?? []
      if (!cached.some((item) => item.id === building.id)) {
        buildingCache.set(building.communityId, [...cached, building])
      }

      return { community, building }
    } catch (error) {
      errorMessage.value = toAppError(error).message
      return { community: null, building: null }
    } finally {
      resolving.value = false
    }
  }

  function clear(): void {
    ++searchSequence
    cancelScheduledSearch()
    communities.value = []
    buildings.value = []
    clearSearchResults()
    searchAttempted.value = false
    errorMessage.value = ''
  }

  return {
    communities,
    buildings,
    searchCommunityResults,
    searchBuildingResults,
    communityLoading,
    buildingLoading,
    searchLoading,
    searchAttempted,
    resolving,
    errorMessage,
    loadCommunities,
    loadBuildings,
    search,
    resolveCommunity,
    resolveBuildingPath,
    clearSearchResults,
    clear,
  }
}
