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

  it('loads the persisted default vision model', async () => {
    mocks.apiGet.mockResolvedValue({
      autoInferenceOnUpload: false,
      intelligentWorkflowEnabled: true,
      knowledgeQaEnabled: true,
      modelId: 'AI-VISION-LOCAL-001',
      providerCode: 'FAST_API',
      capabilityType: 'VISION_INFERENCE',
    })

    const settings = await getAiAutomationSettings()

    expect(mocks.apiGet).toHaveBeenCalledWith('/api/v1/ai-governance/automation-settings')
    expect(settings.modelId).toBe('AI-VISION-LOCAL-001')
  })

  it('updates switches together with the selected default vision model', async () => {
    mocks.apiPut.mockResolvedValue({
      autoInferenceOnUpload: true,
      intelligentWorkflowEnabled: false,
      knowledgeQaEnabled: false,
      modelId: 'AI-CRACK-HF-UNET-001',
      providerCode: 'FAST_API',
      capabilityType: 'VISION_INFERENCE',
    })

    await updateAiAutomationSettings({
      autoInferenceOnUpload: true,
      intelligentWorkflowEnabled: false,
      knowledgeQaEnabled: false,
      modelId: 'AI-CRACK-HF-UNET-001',
    })

    expect(mocks.apiPut).toHaveBeenCalledWith('/api/v1/ai-governance/automation-settings', {
      autoInferenceOnUpload: true,
      intelligentWorkflowEnabled: false,
      knowledgeQaEnabled: false,
      modelId: 'AI-CRACK-HF-UNET-001',
    })
  })
})
