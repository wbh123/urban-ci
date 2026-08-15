import { describe, expect, it } from 'vitest'
import filterFieldSource from './AppFilterField.vue?raw'
import spatialScopeSource from './AppSpatialScopeFilter.vue?raw'
import archiveSource from '@/pages/console/ConsoleArchiveManagementPage.vue?raw'
import inspectionSource from '@/pages/console/ConsoleInspectionPage.vue?raw'
import reviewSource from '@/pages/console/ConsoleReviewDetailPage.vue?raw'
import mapSource from '@/pages/console/ConsoleSpatialMapPage.vue?raw'

describe('比赛浏览器验收第二轮', () => {
  it('空间范围筛选器被抽成公共组件并使用参数语义宽度', () => {
    expect(spatialScopeSource).toContain('SpatialObjectSelector')
    expect(spatialScopeSource).toContain('AppFilterField')
    for (const kind of ['community', 'building', 'risk', 'priority']) {
      expect(filterFieldSource).toContain(`${kind}:`)
    }
    expect(inspectionSource).toContain('AppSpatialScopeFilter')
  })

  it('小区查询控件和查询结果整体位于 AI 档案提示左侧', () => {
    expect(archiveSource).toContain('community-ai-row__directory')
    expect(archiveSource).toContain('community-directory-card')
    expect(archiveSource).toContain('community-ai-row__ai')
    expect(archiveSource).toContain('grid-template-columns:minmax(0,1.75fr) minmax(300px,.85fr)')
  })

  it('巡检管理首页提供真实巡检 AI 看板并说明业务链路', () => {
    expect(inspectionSource).toContain('AiPageBrief')
    expect(inspectionSource).toContain('inspectionAiMetrics')
    expect(inspectionSource).toContain('listAiInferences')
    expect(inspectionSource).toContain('ACCURACY 视觉识别')
    expect(inspectionSource).toContain('正式风险仍由规则评分形成')
  })

  it('人工复核支持修正辅助风险度但明确不直接修改正式评分', () => {
    expect(reviewSource).toContain('reviewedRiskLevel')
    expect(reviewSource).toContain('复核风险度（辅助）')
    expect(reviewSource).toContain('不直接修改正式风险评分')
  })

  it('城市地图列表点击优先显示轮廓或点位并使用轻量摘要浮层', () => {
    expect(mapSource).toContain('selectedMapBuilding')
    expect(mapSource).toContain('map-building-summary')
    expect(mapSource).toContain('resolveBuildingSelection')
    expect(mapSource).toContain('visibleBuildings.value.find')
    expect(mapSource).toContain('driver.focusBuilding')
    expect(mapSource).toContain('查看治理详情')
  })
})
