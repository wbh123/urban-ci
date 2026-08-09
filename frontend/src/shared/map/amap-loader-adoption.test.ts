import { describe, expect, it } from 'vitest'
import pointPickerSource from './archive-point-picker.ts?raw'
import spatialAmapSource from './spatial-amap.ts?raw'
import boundaryEditorSource from './spatial-boundary-editor.ts?raw'

describe('shared AMap loader adoption', () => {
  it('routes archive point picker and spatial maps through the shared loader', () => {
    for (const source of [pointPickerSource, spatialAmapSource, boundaryEditorSource]) {
      expect(source).toContain("from './amap-loader'")
      expect(source).not.toContain("document.createElement('script')")
    }
  })
})
