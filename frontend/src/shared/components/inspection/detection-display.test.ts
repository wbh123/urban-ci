import { describe, expect, it } from 'vitest'
import type { AiInferenceTask } from '@/shared/api'
import { selectDrawableDetections } from './detection-display'

function task(): AiInferenceTask {
  return {
    inferenceId: 'inference-1',
    requestCode: 'REQ-1',
    status: 'SUCCEEDED',
    mode: 'REAL',
    modelId: 'AI-VISION-LOCAL-001',
    modelName: 'Vision',
    modelVersion: '1.1.0',
    license: 'Apache-2.0',
    assetId: 'asset-1',
    attemptNo: 1,
    reviewStatus: 'UNREVIEWED',
    resultAvailable: true,
    detectionCount: 1,
    assessmentEligibility: 'REVIEW_REQUIRED',
    eligibleForFormalAssessment: false,
    evidenceReliability: 'MODEL_UNREVIEWED',
    assessmentNote: '',
    disclaimer: '',
    detections: [{
      sequence: 1,
      classCode: 'CRACK',
      className: '裂缝',
      confidence: 0.9,
      boundingBox: { x: 0.1, y: 0.2, width: 0.3, height: 0.4, coordinateType: 'NORMALIZED_XYWH' },
      segmentation: { type: 'POLYGON', points: [[0.1, 0.2], [0.4, 0.2], [0.4, 0.6]] },
    }],
    structuredResult: {
      requestId: 'REQ-1',
      providerCode: 'FAST_API',
      modelCode: 'AI-VISION-LOCAL-001',
      capabilityType: 'VISION_INFERENCE',
      status: 'SUCCEEDED',
      summary: 'semantic-only',
      detections: [{ classCode: 'CRACK', className: '裂缝', confidence: 0.9 }],
      riskSignals: [],
      recommendations: [],
      warnings: [],
      durationMs: 100,
    },
  }
}

describe('selectDrawableDetections', () => {
  it('prefers rich top-level detections with geometry over semantic structured detections', () => {
    const result = selectDrawableDetections(task())
    expect(result).toHaveLength(1)
    expect(result[0]?.boundingBox).toBeTruthy()
    expect(result[0]?.segmentation?.points.length).toBeGreaterThanOrEqual(3)
  })
})
