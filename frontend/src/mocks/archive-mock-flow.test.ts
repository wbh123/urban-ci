import { afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest'
import {
  createBuilding,
  createCommunity,
  getArchiveBuildingLocation,
  listBuildings,
  listCommunities,
  previewArchiveReverseGeocoding,
  saveArchiveBuildingLocation,
  searchArchivePlaces,
} from '@/shared/api'
import { configureInterceptors, resetInterceptors } from '@/shared/api/interceptors'
import { ensureMockServer } from '@/tests/setup'
import { MOCK_TOKEN, resetDb } from './fixtures/data'

describe('Mock 模式可视化建档完整链路', () => {
  beforeAll(async () => {
    await ensureMockServer()
  })

  beforeEach(() => {
    resetDb()
    configureInterceptors({ tokenGetter: () => MOCK_TOKEN })
  })

  afterEach(() => {
    resetInterceptors()
    resetDb()
  })

  it('可查询并新增小区，新增结果立即进入目录', async () => {
    const before = await listCommunities({ size: 100 })
    const created = await createCommunity({
      communityCode: 'COM-MOCK-NEW',
      communityName: 'Mock 新建小区',
      administrativeRegion: '株洲市天元区',
      address: '测试路 8 号',
      status: 'ACTIVE',
    })
    const after = await listCommunities({ size: 100 })

    expect(before.content?.length).toBeGreaterThan(0)
    expect(created.id).toBeTruthy()
    expect(after.content).toEqual(expect.arrayContaining([
      expect.objectContaining({ id: created.id, communityName: 'Mock 新建小区' }),
    ]))
  })

  it('可在新建小区下新增楼栋并立即查询到', async () => {
    const community = await createCommunity({
      communityCode: 'COM-MOCK-BUILDING',
      communityName: 'Mock 楼栋小区',
      status: 'ACTIVE',
    })
    const building = await createBuilding({
      communityId: community.id,
      buildingCode: 'B-MOCK-01',
      buildingName: 'Mock 1 栋',
      hasElevator: false,
      hasIllegalModification: false,
      hasGroundFloorBusiness: false,
      status: 'ACTIVE',
    })
    const page = await listBuildings({ communityId: community.id, size: 100 })

    expect(building.id).toBeTruthy()
    expect(page.content).toEqual(expect.arrayContaining([
      expect.objectContaining({ id: building.id, buildingCode: 'B-MOCK-01' }),
    ]))
  })

  it('地点搜索与地图反查在 Mock 模式返回可编辑候选', async () => {
    const places = await searchArchivePlaces({ keyword: '示范小区', region: '株洲', pageSize: 8 })
    const reverse = await previewArchiveReverseGeocoding({ longitude: 113.13, latitude: 27.82 })

    expect(places.length).toBeGreaterThan(0)
    expect(places[0]).toMatchObject({ provider: 'MOCK', mock: true })
    expect(reverse).toMatchObject({
      longitude: 113.13,
      latitude: 27.82,
      provider: 'MOCK',
      mock: true,
    })
  })

  it('楼栋中心点可保存并重新读取', async () => {
    const saved = await saveArchiveBuildingLocation('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', {
      longitude: 113.14,
      latitude: 27.83,
      formattedAddress: 'Mock 新位置',
      provider: 'MOCK',
      matchLevel: 'MOCK_PREVIEW',
      mock: true,
    })
    const read = await getArchiveBuildingLocation('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa')

    expect(saved).toMatchObject({ longitude: 113.14, latitude: 27.83, provider: 'MOCK' })
    expect(read).toMatchObject({
      buildingId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
      formattedAddress: 'Mock 新位置',
    })
  })
})
