export const AI_CONFIDENCE_PERCENT_THRESHOLD = 0.4

export function shouldDisplayAiConfidence(confidence?: number | null): boolean {
  return typeof confidence === 'number'
    && Number.isFinite(confidence)
    && confidence >= AI_CONFIDENCE_PERCENT_THRESHOLD
}

export function formatAiConfidence(confidence?: number | null): string | null {
  if (!shouldDisplayAiConfidence(confidence)) return null
  return `${Math.round(Number(confidence) * 100)}%`
}

export function formatAiDetectionLabel(name?: string | null, confidence?: number | null): string {
  const normalizedName = (name ?? '').trim() || '疑似表观异常'
  const percentage = formatAiConfidence(confidence)
  return percentage ? `${normalizedName} ${percentage}` : normalizedName
}