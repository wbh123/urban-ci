import { describe, expect, it } from 'vitest'
import source from '@/components/workbench/ai-wall/AiWallMap.vue?raw'

describe('GIS AI 展示层兼容', () => {
  it('继续复用现有高德 GIS 驱动而不是重写地图底座', () => {
    expect(source).toContain('createSpatialAmapDriver')
    expect(source).toContain('useSpatialMapStore')
    expect(source).toContain('driver.focusBuilding')
  })

  it('在 AI 大屏地图适配层处理 AI 模式和外部聚焦', () => {
    expect(source).toContain('layerMode')
    expect(source).toContain('AI_DEFECT')
    expect(source).toContain('AI_ATTENTION')
    expect(source).toContain('focusBuildingId')
    expect(source).toContain('decorateBuildingPoint')
  })
})
