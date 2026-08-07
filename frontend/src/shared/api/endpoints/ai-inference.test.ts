import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
}))

vi.mock('../client', () => mocks)

import { createAiInference } from './ai-inference'

describe('AI inference endpoint routing', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('routes a Dify workflow model using its catalog metadata', async () => {
    mocks.apiGet.mockResolvedValue({
      content: [
        {
          modelId: 'urban-safe-dify-workflow',
          providerCode: 'DIFY',
          capabilityType: 'WORKFLOW',
        },
      ],
    })
    mocks.apiPost.mockResolvedValue({ inferenceId: 'inference-1' })

    await createAiInference({
      assetId: 'asset-1',
      mode: 'REAL',
      modelId: 'urban-safe-dify-workflow',
    })

    expect(mocks.apiGet).toHaveBeenCalledWith('/api/v1/ai-models')
    expect(mocks.apiPost).toHaveBeenCalledWith('/api/v1/ai-inferences', {
      assetId: 'asset-1',
      mode: 'REAL',
      modelId: 'urban-safe-dify-workflow',
      providerCode: 'DIFY',
      capabilityType: 'WORKFLOW',
    })
  })

  it('keeps explicitly supplied routing without querying the catalog', async () => {
    mocks.apiPost.mockResolvedValue({ inferenceId: 'inference-2' })

    await createAiInference({
      assetId: 'asset-2',
      mode: 'REAL',
      modelId: 'manual-model',
      providerCode: 'SPRING_AI',
      capabilityType: 'TEXT_GENERATION',
    })

    expect(mocks.apiGet).not.toHaveBeenCalled()
    expect(mocks.apiPost).toHaveBeenCalledWith('/api/v1/ai-inferences', {
      assetId: 'asset-2',
      mode: 'REAL',
      modelId: 'manual-model',
      providerCode: 'SPRING_AI',
      capabilityType: 'TEXT_GENERATION',
    })
  })
})
