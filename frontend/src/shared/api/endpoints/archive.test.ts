import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  apiPut: vi.fn(),
}))

vi.mock('../client', () => mocks)

import {
  getArchiveBuildingLocation,
  previewArchiveReverseGeocoding,
  saveArchiveBuildingLocation,
  searchArchivePlaces,
} from './archive'

describe('archive discovery endpoints', () => {
  beforeEach(() => vi.clearAllMocks())

  it('routes place search and reverse geocoding through archive discovery paths', async () => {
    mocks.apiPost.mockResolvedValue({})
    const search = { keyword: '和平小区', region: '株洲', cityLimit: true, pageSize: 8 }
    const point = { longitude: 113.12, latitude: 27.88 }

    await searchArchivePlaces(search)
    await previewArchiveReverseGeocoding(point)

    expect(mocks.apiPost).toHaveBeenCalledWith('/api/v1/map/places/search', search)
    expect(mocks.apiPost).toHaveBeenCalledWith('/api/v1/map/reverse-geocoding/preview', point)
  })

  it('reads and saves building center points with encoded ids', async () => {
    mocks.apiGet.mockResolvedValue({})
    mocks.apiPut.mockResolvedValue({})
    const input = {
      longitude: 113.12,
      latitude: 27.88,
      provider: 'MANUAL' as const,
    }

    await getArchiveBuildingLocation('building/a')
    await saveArchiveBuildingLocation('building/a', input)

    expect(mocks.apiGet).toHaveBeenCalledWith('/api/v1/buildings/building%2Fa/location')
    expect(mocks.apiPut).toHaveBeenCalledWith('/api/v1/buildings/building%2Fa/location', input)
  })
})
