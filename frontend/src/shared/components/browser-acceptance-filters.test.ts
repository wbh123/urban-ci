import { describe, expect, it } from 'vitest'
import filterBarSource from './AppFilterBar.vue?raw'
import selectorSource from './SpatialObjectSelector.vue?raw'
import selectorComposableSource from '@/shared/composables/useSpatialObjectSelector.ts?raw'
import archiveSource from '@/pages/console/ConsoleArchiveManagementPage.vue?raw'
import feedbackSource from '@/pages/console/ConsoleFeedbackPage.vue?raw'

describe('比赛验收收口 S1', () => {
  it('公共筛选器提供统一字段宽度语义并保持操作区右对齐', () => {
    expect(filterBarSource).toContain('app-filter-field')
    expect(filterBarSource).toContain('--filter-field-width')
    expect(filterBarSource).toContain('margin-left: auto')
    expect(feedbackSource).toContain('AppFilterField')
  })

  it('空间搜索区分未搜索、无结果和真实错误', () => {
    expect(selectorComposableSource).toContain('searchAttempted')
    expect(selectorSource).toContain('输入小区、楼栋名称或楼栋编号开始搜索')
    expect(selectorSource).toContain('未找到匹配的空间对象')
    expect(selectorSource).not.toContain('未找到有权限访问的空间对象')
  })

  it('小区查询条件与结果整体和 AI 档案提示在桌面端使用两栏布局', () => {
    expect(archiveSource).toContain('community-ai-row')
    expect(archiveSource).toContain('community-ai-row__directory')
    expect(archiveSource).toContain('community-directory-card')
    expect(archiveSource).toContain('community-ai-row__ai')
    expect(archiveSource).toContain('grid-template-columns:minmax(0,1.75fr) minmax(300px,.85fr)')
  })
})
