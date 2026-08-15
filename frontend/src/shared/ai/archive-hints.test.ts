import { describe, expect, it } from 'vitest'
import { buildArchiveHints } from './archive-hints'

describe('archive AI hints', () => {
  it('guides the user to select a community first', () => {
    const hints = buildArchiveHints({})
    expect(hints[0]).toMatchObject({ action: 'SELECT_COMMUNITY', level: 'INFO' })
  })

  it('guides the user to select or create a building after choosing a community', () => {
    const hints = buildArchiveHints({ community: { id: 'c-1', communityName: '测试小区' } as never })
    expect(hints[0]).toMatchObject({ action: 'SELECT_BUILDING', level: 'INFO' })
  })

  it('lists missing core building archive fields as an attention hint', () => {
    const hints = buildArchiveHints({
      community: { id: 'c-1', communityName: '测试小区' } as never,
      building: {
        id: 'b-1',
        buildingCode: 'A1',
        buildingName: 'A栋',
        constructionYear: null,
        floorCount: null,
        residentCount: null,
      } as never,
    })

    expect(hints[0]).toMatchObject({ action: 'COMPLETE_ARCHIVE', level: 'ATTENTION' })
    expect(hints[0]?.detail).toContain('建成年份')
    expect(hints[0]?.detail).toContain('层数')
    expect(hints[0]?.detail).toContain('居民数')
  })

  it('suggests spatial archive and inspection when core archive fields are complete', () => {
    const hints = buildArchiveHints({
      community: { id: 'c-1', communityName: '测试小区' } as never,
      building: {
        id: 'b-1',
        buildingCode: 'A1',
        buildingName: 'A栋',
        constructionYear: 2008,
        floorCount: 18,
        residentCount: 240,
      } as never,
    })

    expect(hints.map((item) => item.action)).toEqual(['COMPLETE_SPATIAL', 'CREATE_INSPECTION'])
  })
})
