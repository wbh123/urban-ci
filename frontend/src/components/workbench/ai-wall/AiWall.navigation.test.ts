import { describe, expect, it } from 'vitest'
import source from './WorkbenchAiDataWall.vue?raw'
import attentionSource from './AiWallAttentionList.vue?raw'
import drawerSource from './AiWallBuildingDrawer.vue?raw'

describe('AI situation wall navigation contract', () => {
  it('keeps attention selection inside the wall first and focuses the map', () => {
    expect(source).toContain('selectedBuilding')
    expect(source).toContain('focusBuildingId')
    expect(source).toContain('function focusBuilding(building: AiDashboardBuilding)')
    expect(attentionSource).toContain("emit('focus', item)")
  })

  it('uses the building drawer for one explicit building entry and separate governance actions', () => {
    expect(source).toContain('AiWallBuildingDrawer')
    expect(drawerSource).toContain('进入楼栋详情')
    expect(drawerSource).toContain('查看巡检')
    expect(drawerSource).toContain('进入人工复核')
    expect(drawerSource).not.toContain('查看 AI 研判</button>')
    expect(source).toContain("emit('openBuilding', $event)")
  })

  it('keeps review and inspection actions permission-aware', () => {
    expect(source).toContain(':can-review="canReview"')
    expect(source).toContain(':can-manage-inspection="canManageInspection"')
    expect(drawerSource).toContain('v-if="canManageInspection"')
    expect(drawerSource).toContain('v-if="canReview && building.pendingReviewCount > 0"')
  })
})
