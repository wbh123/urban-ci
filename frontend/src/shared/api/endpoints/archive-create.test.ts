import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  apiPost: vi.fn(),
  apiGet: vi.fn(),
}))

vi.mock('../client', () => mocks)

import { createCommunity } from './communities'
import { createBuilding } from './buildings'

describe('archive create endpoint adapters', () => {
  beforeEach(() => vi.clearAllMocks())

  it('creates a community through the existing community POST contract', async () => {
    mocks.apiPost.mockResolvedValue({ id: 'community-1' })
    const input = {
      communityCode: 'COM-001',
      communityName: '和平小区',
      administrativeRegion: '株洲市芦淞区',
      address: '建设南路 1 号',
      status: 'ACTIVE' as const,
    }

    await createCommunity(input)

    expect(mocks.apiPost).toHaveBeenCalledWith('/api/v1/communities', input)
  })

  it('creates a building through the existing building POST contract', async () => {
    mocks.apiPost.mockResolvedValue({ id: 'building-1' })
    const input = {
      communityId: 'community-1',
      buildingCode: 'B-01',
      buildingName: '1 栋',
      address: '建设南路 1 号',
      hasElevator: false,
      hasIllegalModification: false,
      hasGroundFloorBusiness: false,
      status: 'ACTIVE' as const,
    }

    await createBuilding(input)

    expect(mocks.apiPost).toHaveBeenCalledWith('/api/v1/buildings', input)
  })
})
