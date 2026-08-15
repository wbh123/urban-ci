import { describe, expect, it } from 'vitest'
import source from './ConsoleSpatialArchivePage.vue?raw'

describe('ConsoleSpatialArchivePage stacked editing layout', () => {
  it('keeps the maintenance level label and switch on one row', () => {
    expect(source).toContain('maintenance-level-row')
    expect(source).toContain('维护层级')
    expect(source).toContain('entityType')
  })

  it('stacks the GCJ-02 GeoJSON label, textarea and full-width import actions', () => {
    expect(source).toContain('geojson-import-section')
    expect(source).toContain('导入 GCJ-02 GeoJSON')
    expect(source).toContain('geojson-import-actions')
    expect(source).toContain('full-width-action')
  })

  it('stacks edit/save fields and actions instead of squeezing them into one row', () => {
    expect(source).toContain('edit-save-section')
    expect(source).toContain('edit-save-actions')
    expect(source).toContain('full-width-action')
  })

  it('uses a floating notification when AMap has no usable community boundary', () => {
    expect(source).toContain('ElNotification')
    expect(source).toContain('未获取到可用区域边界')
    expect(source).toContain('可继续使用人工绘制或 GeoJSON 导入。')
  })
})
