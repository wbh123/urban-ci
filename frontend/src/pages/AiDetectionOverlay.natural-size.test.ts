import { describe, expect, it } from 'vitest'
import source from './AiDetectionOverlay.vue?raw'

describe('AiDetectionOverlay natural image size fallback', () => {
  it('falls back to the loaded image natural size when persisted dimensions are missing', () => {
    expect(source).toContain('naturalWidth')
    expect(source).toContain('naturalHeight')
    expect(source).toContain('@load="handleImageLoad"')
    expect(source).toContain('props.imageWidth && props.imageWidth > 1')
    expect(source).toContain('props.imageHeight && props.imageHeight > 1')
  })

  it('redraws annotations after image dimensions become available', () => {
    expect(source).toContain('syncNaturalImageSize()')
    expect(source).toContain('requestAnimationFrame(draw)')
    expect(source).toContain('naturalImageW.value')
    expect(source).toContain('naturalImageH.value')
  })
})
