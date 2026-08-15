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

  it('uses precision profile for local REAL FastAPI vision by default', async () => {
    mocks.apiGet.mockResolvedValue({
      content: [
        {
          modelId: 'AI-VISION-LOCAL-001',
          providerCode: 'FAST_API',
          capabilityType: 'VISION_INFERENCE',
        },
      ],
    })
    mocks.apiPost.mockResolvedValue({ inferenceId: 'inference-vision' })

    await createAiInference({
      assetId: 'asset-vision',
      mode: 'REAL',
      modelId: 'AI-VISION-LOCAL-001',
    })

    expect(mocks.apiPost).toHaveBeenCalledWith('/api/v1/ai-inferences', {
      assetId: 'asset-vision',
      mode: 'REAL',
      modelId: 'AI-VISION-LOCAL-001',
      providerCode: 'FAST_API',
      capabilityType: 'VISION_INFERENCE',
      inferenceProfile: 'PRECISION',
    })
  })

  it('preserves an explicit FAST profile for diagnostic callers', async () => {
    mocks.apiPost.mockResolvedValue({ inferenceId: 'inference-fast' })

    await createAiInference({
      assetId: 'asset-fast',
      mode: 'REAL',
      modelId: 'AI-VISION-LOCAL-001',
      providerCode: 'FAST_API',
      capabilityType: 'VISION_INFERENCE',
      inferenceProfile: 'FAST',
    })

    expect(mocks.apiGet).not.toHaveBeenCalled()
    expect(mocks.apiPost).toHaveBeenCalledWith('/api/v1/ai-inferences', {
      assetId: 'asset-fast',
      mode: 'REAL',
      modelId: 'AI-VISION-LOCAL-001',
      providerCode: 'FAST_API',
      capabilityType: 'VISION_INFERENCE',
      inferenceProfile: 'FAST',
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
