import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({ apiGet: vi.fn() }))

vi.mock('../client', () => ({ apiGet: mocks.apiGet }))

import {
  getAiDashboardActivity,
  getAiDashboardBuildings,
  getAiDashboardOverview,
} from './ai-dashboard'

describe('AI dashboard read-model endpoints', () => {
  beforeEach(() => vi.clearAllMocks())

  it('uses dedicated read-only aggregate paths', async () => {
    mocks.apiGet.mockResolvedValue({})

    await getAiDashboardOverview()
    await getAiDashboardActivity(12)
    await getAiDashboardBuildings()

    expect(mocks.apiGet).toHaveBeenNthCalledWith(1, '/api/v1/ai-dashboard/overview')
    expect(mocks.apiGet).toHaveBeenNthCalledWith(2, '/api/v1/ai-dashboard/activity', { limit: 12 })
    expect(mocks.apiGet).toHaveBeenNthCalledWith(3, '/api/v1/ai-dashboard/buildings')
  })
})
