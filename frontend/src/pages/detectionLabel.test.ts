import { describe, expect, it } from 'vitest'
import { formatDetectionLabel } from './detectionLabel'

describe('formatDetectionLabel', () => {
  it('hides confidence below 40 percent but keeps the class label', () => {
    expect(formatDetectionLabel('裂缝', 0.39)).toBe('裂缝')
  })

  it('shows confidence at or above 40 percent', () => {
    expect(formatDetectionLabel('裂缝', 0.4)).toBe('裂缝 40%')
    expect(formatDetectionLabel('剥落', 0.726)).toBe('剥落 73%')
  })
})
