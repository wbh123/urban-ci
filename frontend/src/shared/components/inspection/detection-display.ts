import type {
  AiDetection,
  AiInferenceTask,
  AiStructuredDetection,
} from '@/shared/api/endpoints/ai-inference'

export type DrawableDetection = AiDetection | AiStructuredDetection

function hasGeometry(detection: DrawableDetection): boolean {
  return Boolean(detection.boundingBox)
}

/**
 * 图上标注优先使用 rich-result 恢复出的顶层 Detection。
 * structuredResult 可能只包含语义项，没有 boundingBox；这种数据不能遮掉
 * 已经持久化的 SAM/检测框几何结果。
 */
export function selectDrawableDetections(inference: AiInferenceTask | null | undefined): DrawableDetection[] {
  if (!inference) return []
  const rich = (inference.detections ?? []).filter(hasGeometry)
  if (rich.length) return rich
  return (inference.structuredResult?.detections ?? []).filter(hasGeometry)
}
