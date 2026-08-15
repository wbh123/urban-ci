import { describe, expect, it } from 'vitest'
import legacyWallSource from '@/components/workbench/WorkbenchDataWall.vue?raw'
import legacyMapSource from '@/components/workbench/WorkbenchDataWallMap.vue?raw'
import aiWallSource from './WorkbenchAiDataWall.vue?raw'
import aiMapSource from './AiWallMap.vue?raw'

describe('AI 大屏与旧数据大屏隔离', () => {
  it('旧大屏恢复为正式风险历史实现，不继续承载 AI 聚合逻辑', () => {
    expect(legacyWallSource).not.toContain('getAiDashboardBuildings')
    expect(legacyWallSource).not.toContain('AiWallActivityFeed')
    expect(legacyWallSource).not.toContain('AiWallBuildingDrawer')
    expect(legacyWallSource).not.toContain('aiOverview')
    expect(legacyWallSource).toContain('WorkbenchDataWallMap')
    expect(legacyWallSource).toContain('城市建筑安全治理态势中心')
  })

  it('旧地图不暴露 AI 图层与 AI 楼栋事件', () => {
    expect(legacyMapSource).not.toContain('AiDashboardBuilding')
    expect(legacyMapSource).not.toContain('AiDashboardLayerMode')
    expect(legacyMapSource).not.toContain('buildingSelected')
    expect(legacyMapSource).not.toContain('AI_DEFECT')
    expect(legacyMapSource).toContain('城市建筑安全空间态势地图')
  })

  it('新版 AI 大屏独立持有 AI 数据、图层与地图实现', () => {
    expect(aiWallSource).toContain('getAiDashboardBuildings')
    expect(aiWallSource).toContain('AiWallBuildingDrawer')
    expect(aiWallSource).toContain("import AiWallMap from './AiWallMap.vue'")
    expect(aiMapSource).toContain('AiDashboardLayerMode')
    expect(aiMapSource).toContain('buildingSelected')
  })
})
