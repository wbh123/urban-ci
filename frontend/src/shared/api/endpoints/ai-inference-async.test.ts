import { describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  apiGet: vi.fn(),
  createAccuracy: vi.fn(),
  poll: vi.fn(),
  listModels: vi.fn(),
  createBase: vi.fn(),
}))

vi.mock('../client', () => ({ apiGet: mocks.apiGet }))
vi.mock('./ai-inference', () => ({
  createAiAccuracyExecution: mocks.createAccuracy,
  getAiInferenceExecution: mocks.poll,
  listAiModels: mocks.listModels,
  createAiInference: mocks.createBase,
}))

import { createAiInference } from './ai-inference-async'

describe('ACCURACY async routing', () => {
  it('queues and reads rich result for local REAL vision', async () => {
    mocks.listModels.mockResolvedValue({ content: [{
      modelId: 'AI-VISION-LOCAL-001',
      providerCode: 'FAST_API',
      capabilityType: 'VISION_INFERENCE',
    }] })
    mocks.createAccuracy.mockResolvedValue({ taskId: 'task-1' })
    mocks.poll.mockResolvedValue({ status: 'SUCCEEDED', inferenceId: 'result-1' })
    mocks.apiGet.mockResolvedValue({ inferenceId: 'result-1' })

    const result = await createAiInference({
      assetId: 'asset-1', mode: 'REAL', modelId: 'AI-VISION-LOCAL-001',
    })

    expect(mocks.createAccuracy).toHaveBeenCalled()
    expect(mocks.poll).toHaveBeenCalledWith('task-1')
    expect(mocks.apiGet).toHaveBeenCalledWith('/api/v1/ai-inferences/result-1/rich-result')
    expect(result.inferenceId).toBe('result-1')
  })
})
