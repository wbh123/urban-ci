import { describe, expect, it } from 'vitest'
import source from './ConsoleReviewDetailPage.vue?raw'

describe('ConsoleReviewDetailPage AI overlay selection', () => {
  it('uses normalized drawable detections and falls back to persisted geometry', () => {
    expect(source).toContain("import { resolveReviewOverlayDetections } from '@/pages/console/reviewDetectionOverlay'")
    expect(source).toContain('const overlayDetections = computed(() => resolveReviewOverlayDetections(')
    expect(source).toContain(':detections="overlayDetections"')
    expect(source).not.toContain(':detections="structured?.detections?.length ? structured.detections : task.detections"')
  })
})
