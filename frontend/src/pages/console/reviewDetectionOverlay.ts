import type {
  AiDetection,
  AiDetectionBox,
  AiSegmentation,
  AiStructuredDetection,
} from '@/shared/api/endpoints/ai-inference'

export type ReviewOverlayDetection = AiDetection | AiStructuredDetection

type LegacyStructuredDetection = AiStructuredDetection & {
  bbox?: Partial<AiDetectionBox> | null
  polygon?: number[][] | { points?: number[][] | null } | null
}

export function resolveReviewOverlayDetections(
  structuredDetections: readonly AiStructuredDetection[] = [],
  persistedDetections: readonly AiDetection[] = [],
): ReviewOverlayDetection[] {
  const normalizedStructured = structuredDetections
    .map(normalizeStructuredDetection)
    .filter(hasDrawableBoundingBox)

  if (normalizedStructured.length) return normalizedStructured
  return persistedDetections.filter(hasDrawableBoundingBox)
}

function normalizeStructuredDetection(detection: AiStructuredDetection): AiStructuredDetection {
  const legacy = detection as LegacyStructuredDetection
  const boundingBox = normalizeBoundingBox(detection.boundingBox ?? legacy.bbox)
  const segmentation = normalizeSegmentation(detection.segmentation, legacy.polygon)

  return {
    ...detection,
    ...(boundingBox ? { boundingBox } : {}),
    ...(segmentation ? { segmentation } : {}),
  }
}

function normalizeBoundingBox(box?: Partial<AiDetectionBox> | null): AiDetectionBox | undefined {
  if (!box) return undefined
  const x = Number(box.x)
  const y = Number(box.y)
  const width = Number(box.width)
  const height = Number(box.height)
  if (![x, y, width, height].every(Number.isFinite)) return undefined
  if (x < 0 || y < 0 || width <= 0 || height <= 0) return undefined
  if (x + width > 1 || y + height > 1) return undefined
  return { x, y, width, height, coordinateType: 'NORMALIZED_XYWH' }
}

function normalizeSegmentation(
  segmentation?: AiSegmentation | null,
  polygon?: LegacyStructuredDetection['polygon'],
): AiSegmentation | undefined {
  if (segmentation?.type === 'POLYGON' && validPoints(segmentation.points)) return segmentation
  const points = Array.isArray(polygon) ? polygon : polygon?.points
  if (!validPoints(points)) return undefined
  return { type: 'POLYGON', points }
}

function validPoints(points?: number[][] | null): points is number[][] {
  return Array.isArray(points)
    && points.length >= 3
    && points.every((point) => Array.isArray(point)
      && point.length >= 2
      && Number.isFinite(Number(point[0]))
      && Number.isFinite(Number(point[1])))
}

function hasDrawableBoundingBox(detection: ReviewOverlayDetection): boolean {
  return Boolean(normalizeBoundingBox(detection.boundingBox))
}
