import { describe, expect, it } from 'vitest'
import type { AiDetection, AiStructuredDetection } from '@/shared/api/endpoints/ai-inference'
import { resolveReviewOverlayDetections } from './reviewDetectionOverlay'

describe('resolveReviewOverlayDetections', () => {
  it('normalizes legacy showcase bbox and polygon fields into drawable annotations', () => {
    const structured = [{
      classCode: 'CRACK',
      className: '裂缝',
      confidence: 0.82,
      bbox: { x: 0.1, y: 0.2, width: 0.3, height: 0.25 },
      polygon: [[0.1, 0.2], [0.4, 0.2], [0.4, 0.45], [0.1, 0.45]],
    }] as unknown as AiStructuredDetection[]

    const result = resolveReviewOverlayDetections(structured, [])

    expect(result).toHaveLength(1)
    expect(result[0]).toMatchObject({
      classCode: 'CRACK',
      boundingBox: {
        x: 0.1,
        y: 0.2,
        width: 0.3,
        height: 0.25,
        coordinateType: 'NORMALIZED_XYWH',
      },
      segmentation: {
        type: 'POLYGON',
        points: [[0.1, 0.2], [0.4, 0.2], [0.4, 0.45], [0.1, 0.45]],
      },
    })
  })

  it('falls back to persisted detections when structured candidates have no drawable box', () => {
    const structured = [{ classCode: 'CRACK', className: '裂缝', confidence: 0.82 }] as AiStructuredDetection[]
    const persisted: AiDetection[] = [{
      sequence: 1,
      classCode: 'CRACK',
      className: '裂缝',
      confidence: 0.82,
      boundingBox: {
        x: 0.12,
        y: 0.18,
        width: 0.28,
        height: 0.3,
        coordinateType: 'NORMALIZED_XYWH',
      },
    }]

    expect(resolveReviewOverlayDetections(structured, persisted)).toEqual(persisted)
  })
})
