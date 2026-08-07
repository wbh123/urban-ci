import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  apiGet: vi.fn(),
  apiPut: vi.fn(),
}))

vi.mock('../client', () => mocks)

import {
  getAiAutomationSettings,
  updateAiAutomationSettings,
} from './ai-governance'

describe('AI automation settings endpoints', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('loads the upload auto-inference switch', async () => {
    mocks.apiGet.mockResolvedValue({
      autoInferenceOnUpload: false,
      modelId: 'AI-DIFY-WORKFLOW-001',
      providerCode: 'DIFY',
      capabilityType: 'WORKFLOW',
    })

    await getAiAutomationSettings()

    expect(mocks.apiGet).toHaveBeenCalledWith('/api/v1/ai-governance/automation-settings')
  })

  it('updates the upload auto-inference switch', async () => {
    mocks.apiPut.mockResolvedValue({
      autoInferenceOnUpload: true,
      modelId: 'AI-DIFY-WORKFLOW-001',
      providerCode: 'DIFY',
      capabilityType: 'WORKFLOW',
    })

    await updateAiAutomationSettings(true)

    expect(mocks.apiPut).toHaveBeenCalledWith('/api/v1/ai-governance/automation-settings', {
      autoInferenceOnUpload: true,
    })
  })
})
