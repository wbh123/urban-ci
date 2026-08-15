import { describe, expect, it } from 'vitest'
import source from './SpatialObjectSelector.vue?raw'

describe('SpatialObjectSelector contract', () => {
  it('offers global search plus community and building cascading selectors', () => {
    expect(source).toContain('空间对象搜索')
    expect(source).toContain('搜索小区、楼栋名称或楼栋编号')
    expect(source).toContain('小区')
    expect(source).toContain('楼栋')
    expect(source).toContain('remote-method')
  })

  it('supports community, building and both modes with controlled ids', () => {
    expect(source).toContain("'community' | 'building' | 'both'")
    expect(source).toContain('communityId')
    expect(source).toContain('buildingId')
    expect(source).toContain("'update:communityId'")
    expect(source).toContain("'update:buildingId'")
    expect(source).toContain("'change'")
  })

  it('clears the building when the community changes and never auto-selects the first building', () => {
    expect(source).toContain("emit('update:buildingId', '')")
    expect(source).toContain('handleCommunityChange')
    expect(source).not.toContain('buildings.value[0]?.id')
  })

  it('can select a global building result and resolve its parent community', () => {
    expect(source).toContain('selectSearchBuilding')
    expect(source).toContain('resolveBuildingPath')
    expect(source).toContain('communityId')
    expect(source).toContain('buildingId')
  })

  it('groups search results into communities and buildings and displays building context', () => {
    expect(source).toContain('搜索结果 · 小区')
    expect(source).toContain('搜索结果 · 楼栋')
    expect(source).toContain('buildingCode')
    expect(source).toContain('communityNameFor')
  })
})
