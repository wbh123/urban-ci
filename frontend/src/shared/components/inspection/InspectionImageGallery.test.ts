import { describe, expect, it } from 'vitest'
import source from './InspectionImageGallery.vue?raw'

describe('InspectionImageGallery', () => {
  it('loads images independently from artificial intelligence state', () => {
    expect(source).toContain('listImages')
    expect(source).toContain('listInspectionTaskExecutions')
    expect(source).toContain('listInspectionTaskInferences')
    expect(source).toContain('现场图片')
  })

  it('supports manual single and batch accuracy submissions without a long browser request', () => {
    expect(source).toContain('submitInspectionImageAnalysis')
    expect(source).toContain("'MANUAL_SINGLE'")
    expect(source).toContain("'MANUAL_BATCH'")
    expect(source).toContain('getInspectionImageExecution')
    expect(source).toContain('POLL_INTERVAL_MS')
  })

  it('resumes short polling for queued or running tasks restored from the database', () => {
    expect(source).toContain('resumeActivePolling')
    expect(source).toContain('isActiveExecution')
    expect(source).toContain('pollingGenerations')
    expect(source).toContain("status === 'RETRY_WAIT'")
    expect(source).toContain("execution.status === 'CANCELLED'")
  })

  it('renders the unified image analysis states and technical fallback route', () => {
    expect(source).toContain('未分析')
    expect(source).toContain('排队中')
    expect(source).toContain('AI 分析中')
    expect(source).toContain('已完成')
    expect(source).toContain('分析失败')
    expect(source).toContain('已自动回退')
  })

  it('automatically loads rich completed results and draws annotations on the image card', () => {
    expect(source).toContain('getInspectionImageRichResult')
    expect(source).toContain('selectDrawableDetections')
    expect(source).toContain('AiDetectionOverlay')
    expect(source).toContain('loadSucceededRichResults')
    expect(source).toContain('未检测到病害候选')
  })
})
