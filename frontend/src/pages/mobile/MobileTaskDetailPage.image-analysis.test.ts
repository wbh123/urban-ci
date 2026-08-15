import { describe, expect, it } from 'vitest'
import source from './MobileTaskDetailPage.vue?raw'

describe('MobileTaskDetailPage image analysis workflow', () => {
  it('keeps local preview before upload and loads the shared server image gallery afterwards', () => {
    expect(source).toContain('URL.createObjectURL(file)')
    expect(source).toContain('InspectionImageGallery')
    expect(source).toContain(':task-id="taskId"')
  })

  it('tracks upload-triggered analysis as a background execution instead of waiting for inference', () => {
    expect(source).toContain('automatic?.triggered && automatic.executionTaskId')
    expect(source).toContain('trackExecution')
    expect(source).toContain('后台分析')
  })

  it('allows manual single or batch analysis through the shared gallery when automatic analysis is disabled', () => {
    expect(source).toContain('editable')
    expect(source).toContain('分析全部未分析图片')
  })
})
