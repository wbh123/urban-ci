import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  listCommunities: vi.fn(),
  getCommunity: vi.fn(),
  listBuildings: vi.fn(),
  getBuilding: vi.fn(),
}))

vi.mock('@/shared/api/endpoints/communities', () => ({
  listCommunities: mocks.listCommunities,
  getCommunity: mocks.getCommunity,
}))
vi.mock('@/shared/api/endpoints/buildings', () => ({
  listBuildings: mocks.listBuildings,
  getBuilding: mocks.getBuilding,
}))

import { useSpatialObjectSelector } from './useSpatialObjectSelector'

function community(id: string, name = `小区-${id}`) {
  return { id, communityName: name, status: 'ACTIVE' }
}

function building(id: string, communityId: string, code = id.toUpperCase()) {
  return { id, communityId, buildingCode: code, buildingName: `楼栋-${id}`, status: 'ACTIVE' }
}

describe('useSpatialObjectSelector', () => {
  beforeEach(() => {
    vi.useRealTimers()
    vi.clearAllMocks()
    mocks.listCommunities.mockResolvedValue({ content: [] })
    mocks.listBuildings.mockResolvedValue({ content: [] })
  })

  it('searches communities and buildings on the server with a 20-row result cap', async () => {
    vi.useFakeTimers()
    mocks.listCommunities.mockResolvedValue({ content: [community('c1', '幸福家园')] })
    mocks.listBuildings.mockResolvedValue({ content: [building('b1', 'c1', 'A1')] })

    const selector = useSpatialObjectSelector()
    const pending = selector.search('幸福')
    await vi.advanceTimersByTimeAsync(300)
    await pending

    expect(mocks.listCommunities).toHaveBeenCalledWith({
      keyword: '幸福',
      status: 'ACTIVE',
      page: 0,
      size: 20,
    })
    expect(mocks.listBuildings).toHaveBeenCalledWith({ keyword: '幸福', page: 0, size: 20 })
    expect(selector.searchCommunityResults.value.map((item) => item.id)).toEqual(['c1'])
    expect(selector.searchBuildingResults.value.map((item) => item.id)).toEqual(['b1'])
  })

  it('ignores stale search responses when a newer query finishes first', async () => {
    vi.useFakeTimers()
    let resolveOldCommunities!: (value: unknown) => void
    let resolveOldBuildings!: (value: unknown) => void

    mocks.listCommunities
      .mockImplementationOnce(() => new Promise((resolve) => { resolveOldCommunities = resolve }))
      .mockResolvedValueOnce({ content: [community('c-new', '新结果')] })
    mocks.listBuildings
      .mockImplementationOnce(() => new Promise((resolve) => { resolveOldBuildings = resolve }))
      .mockResolvedValueOnce({ content: [building('b-new', 'c-new')] })

    const selector = useSpatialObjectSelector()
    const oldPending = selector.search('旧')
    await vi.advanceTimersByTimeAsync(300)
    const newPending = selector.search('新')
    await vi.advanceTimersByTimeAsync(300)
    await newPending

    resolveOldCommunities({ content: [community('c-old', '旧结果')] })
    resolveOldBuildings({ content: [building('b-old', 'c-old')] })
    await oldPending

    expect(selector.searchCommunityResults.value.map((item) => item.id)).toEqual(['c-new'])
    expect(selector.searchBuildingResults.value.map((item) => item.id)).toEqual(['b-new'])
  })

  it('restores a community option directly from its id', async () => {
    const targetCommunity = community('c1', '武汉花园')
    mocks.getCommunity.mockResolvedValue(targetCommunity)

    const selector = useSpatialObjectSelector()
    const resolved = await selector.resolveCommunity('c1')

    expect(mocks.getCommunity).toHaveBeenCalledWith('c1')
    expect(resolved?.id).toBe('c1')
    expect(selector.communities.value.some((item) => item.id === 'c1')).toBe(true)
  })

  it('restores a building path and its parent community from a building id', async () => {
    const targetBuilding = building('b1', 'c1', '1')
    const targetCommunity = community('c1', '武汉花园')
    mocks.getBuilding.mockResolvedValue(targetBuilding)
    mocks.getCommunity.mockResolvedValue(targetCommunity)
    mocks.listBuildings.mockResolvedValue({ content: [targetBuilding] })

    const selector = useSpatialObjectSelector()
    const resolved = await selector.resolveBuildingPath('b1')

    expect(mocks.getBuilding).toHaveBeenCalledWith('b1')
    expect(mocks.getCommunity).toHaveBeenCalledWith('c1')
    expect(mocks.listBuildings).toHaveBeenCalledWith({ communityId: 'c1', page: 0, size: 100 })
    expect(resolved.building?.id).toBe('b1')
    expect(resolved.community?.id).toBe('c1')
    expect(selector.communities.value.some((item) => item.id === 'c1')).toBe(true)
    expect(selector.buildings.value.some((item) => item.id === 'b1')).toBe(true)
  })

  it('clears search results without issuing a request for blank input', async () => {
    const selector = useSpatialObjectSelector()
    selector.searchCommunityResults.value = [community('c1') as never]
    selector.searchBuildingResults.value = [building('b1', 'c1') as never]

    await selector.search('   ')

    expect(mocks.listCommunities).not.toHaveBeenCalled()
    expect(mocks.listBuildings).not.toHaveBeenCalled()
    expect(selector.searchCommunityResults.value).toEqual([])
    expect(selector.searchBuildingResults.value).toEqual([])
  })
})
