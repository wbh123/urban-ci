import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  apiPost: vi.fn(),
}))

vi.mock('../client', () => ({
  apiGet: vi.fn(),
  apiPost: mocks.apiPost,
  apiPut: vi.fn(),
}))

import { previewCommunityBoundaryCandidate } from './map'

describe('map boundary candidate endpoint', () => {
  beforeEach(() => vi.clearAllMocks())

  it('previews AMap community boundary without invoking spatial persistence endpoints', async () => {
    mocks.apiPost.mockResolvedValue({ available: false, provider: 'AMAP', reasonCode: 'DISABLED' })

    await previewCommunityBoundaryCandidate({
      communityName: '示范小区',
      address: '湖南省株洲市示范路1号',
      city: '株洲市',
    })

    expect(mocks.apiPost).toHaveBeenCalledTimes(1)
    expect(mocks.apiPost).toHaveBeenCalledWith('/api/v1/map/boundary-candidates/community', {
      communityName: '示范小区',
      address: '湖南省株洲市示范路1号',
      city: '株洲市',
    })
  })
})
