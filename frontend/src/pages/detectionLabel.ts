import { formatAiDetectionLabel } from '@/shared/ai/ai-display'

export function formatDetectionLabel(className: string, confidence: number | null | undefined): string {
  return formatAiDetectionLabel(className, confidence)
}
