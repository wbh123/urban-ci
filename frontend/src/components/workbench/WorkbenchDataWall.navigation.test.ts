import { describe, expect, it } from 'vitest'
import source from './WorkbenchDataWall.vue?raw'

describe('WorkbenchDataWall local-detail navigation contract', () => {
  it('opens side building rows in an in-wall detail overlay instead of navigating away', () => {
    expect(source).toContain('selectedWallBuilding')
    expect(source).toContain('showBuildingDetail(row)')
    expect(source).toContain('class="wall-detail-overlay"')
  })

  it('allows cross-page navigation only from the explicit detail-overlay action', () => {
    expect(source.match(/emit\('openRisk'\)/g)).toHaveLength(1)
    expect(source).toContain('class="detail-navigation"')
    expect(source).not.toContain('@click="emit(\'openRisk\')" class="rank-row"')
    expect(source).not.toContain('<el-button round @click="emit(\'openRisk\')">风险详情</el-button>')
  })

  it('makes review-required rows open the same local detail instead of acting as passive navigation targets', () => {
    expect(source).toContain('@click="showBuildingDetail(row)"')
    expect(source).toContain('详情遮罩')
  })
})
