import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({ apiGet: vi.fn() }))
vi.mock('../client', () => ({ apiGet: mocks.apiGet }))

import { getAiRuntimeSummary } from './ai-runtime'

describe('AI runtime summary endpoint', () => {
  beforeEach(() => vi.clearAllMocks())

  it('uses a dedicated business-safe runtime path', async () => {
    mocks.apiGet.mockResolvedValue({})
    await getAiRuntimeSummary()
    expect(mocks.apiGet).toHaveBeenCalledWith('/api/v1/ai-runtime/summary')
  })
})
