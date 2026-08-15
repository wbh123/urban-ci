import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
}))

vi.mock('../client', () => mocks)

import {
  getInspectionImageRichResult,
  listInspectionTaskInferences,
  submitInspectionImageAnalysis,
} from './inspection-image-analysis'

describe('inspection image analysis api', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('submits local accuracy as an async manual single task', async () => {
    mocks.apiPost.mockResolvedValue({ taskId: 'exec-1', status: 'PENDING', assetId: 'asset-1' })

    await submitInspectionImageAnalysis('asset-1', 'MANUAL_SINGLE')

    expect(mocks.apiPost).toHaveBeenCalledWith('/api/v1/ai-inferences', expect.objectContaining({
      assetId: 'asset-1',
      mode: 'REAL',
      modelId: 'AI-VISION-LOCAL-001',
      providerCode: 'FAST_API',
      capabilityType: 'VISION_INFERENCE',
      inferenceProfile: 'ACCURACY',
      triggerType: 'MANUAL_SINGLE',
    }))
  })

  it('lists historical inference rows by inspection task', async () => {
    mocks.apiGet.mockResolvedValue({ content: [], page: { page: 0, size: 100, totalElements: 0, totalPages: 0 } })

    await listInspectionTaskInferences('task-1')

    expect(mocks.apiGet).toHaveBeenCalledWith('/api/v1/ai-inferences', {
      inspectionTaskId: 'task-1',
      page: 0,
      size: 100,
    })
  })

  it('keeps rich drawable detections when structured semantic detections lack geometry', async () => {
    mocks.apiGet.mockResolvedValue({
      inferenceId: 'inf-1',
      detections: [{
        sequence: 1,
        classCode: 'CRACK',
        className: '裂缝',
        confidence: 0.91,
        boundingBox: { x: 0.1, y: 0.2, width: 0.3, height: 0.4, coordinateType: 'NORMALIZED_XYWH' },
        segmentation: { type: 'POLYGON', points: [[0.1, 0.2], [0.4, 0.2], [0.4, 0.6]] },
      }],
      structuredResult: {
        detections: [{ classCode: 'CRACK', className: '裂缝', confidence: 0.91 }],
      },
    })

    const result = await getInspectionImageRichResult('inf-1')

    expect(result.structuredResult?.detections?.[0]?.boundingBox).toBeTruthy()
    expect(result.structuredResult?.detections?.[0]?.segmentation?.points).toHaveLength(3)
  })
})
