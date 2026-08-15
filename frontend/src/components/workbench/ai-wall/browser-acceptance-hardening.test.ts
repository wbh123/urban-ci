import { describe, expect, it } from 'vitest'
import wallSource from './WorkbenchAiDataWall.vue?raw'
import headerSource from './AiWallHeader.vue?raw'
import layoutSource from '@/layouts/ConsoleLayout.vue?raw'
import pageHeaderSource from '@/shared/components/layout/AppPageHeader.vue?raw'

describe('比赛验收收口 S0', () => {
  it('地图清空楼栋选择后同步关闭大屏楼栋抽屉和聚焦状态', () => {
    expect(wallSource).toContain("import { storeToRefs } from 'pinia'")
    expect(wallSource).toContain("import { useSpatialMapStore } from '@/stores/spatial-map'")
    expect(wallSource).toContain('const { selectedBuildingIds } = storeToRefs(spatialMapStore)')
    expect(wallSource).toContain('watch(selectedBuildingIds')
    expect(wallSource).toContain('selectedBuilding.value = null')
    expect(wallSource).toContain('focusBuildingId.value = null')
  })

  it('大屏不再显示重复的返回 AI 工作台按钮', () => {
    expect(headerSource).not.toContain('返回 AI 工作台')
    expect(headerSource).not.toContain('exit: []')
  })

  it('电脑管理端只保留真实页面标题栏', () => {
    expect(layoutSource).not.toContain('<el-header')
    expect(layoutSource).not.toContain('console-header__actions')
    expect(layoutSource).toContain('--usp-console-header-offset: 0px')
  })

  it('真实页面标题栏默认承载 AI Runtime 和用户菜单', () => {
    expect(pageHeaderSource).toContain('showUserMenu: true')
    expect(pageHeaderSource).toContain('props.showUserMenu && runtime !== null')
    expect(pageHeaderSource).toContain('<AiRuntimeBadge')
    expect(pageHeaderSource).toContain('<ConsoleUserMenu')
  })
})
