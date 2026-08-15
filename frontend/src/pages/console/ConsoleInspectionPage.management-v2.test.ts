import { describe, expect, it } from 'vitest'
import source from './ConsoleInspectionPage.vue?raw'

describe('ConsoleInspectionPage management layout', () => {
  it('removes the old next-step hint and permanent split creation layout', () => {
    expect(source).not.toContain('next-step-hint')
    expect(source).not.toContain('<el-row')
    expect(source).toContain('inspection-task-table')
  })

  it('provides shared search and filters for inspection tasks', () => {
    expect(source).toContain('AppFilterBar')
    expect(source).toContain('AppQueryField')
    expect(source).toContain('taskKeyword')
    expect(source).toContain('statusFilter')
    expect(source).toContain('typeFilter')
    expect(source).toContain('dateRange')
  })

  it('uses the shared spatial selector and a creation drawer', () => {
    expect(source).toContain('SpatialObjectSelector')
    expect(source).toContain('<el-drawer')
    expect(source).toContain('创建巡检任务')
  })

  it('paginates task rows with the shared pager', () => {
    expect(source).toContain('AppTablePager')
    expect(source).toContain('pageSize')
    expect(source).toContain('20')
  })

  it('provides a real inspection detail drawer with records, uploaded images and AI summary', () => {
    expect(source).toContain('查看详情')
    expect(source).toContain('巡检详情')
    expect(source).toContain('巡检记录')
    expect(source).toContain('InspectionImageGallery')
    expect(source).toContain('AiInspectionSummary')
    expect(source).not.toContain('show-technical-route')
  })

  it('can display the rich local accuracy result while keeping the original image evidence visible', () => {
    expect(source).toContain('@result-selected="displayDetailInference"')
    expect(source).toContain('getInspectionImageRichResult')
    expect(source).toContain('AiDetectionOverlay')
    expect(source).toContain('detailImageUrl')
  })
})