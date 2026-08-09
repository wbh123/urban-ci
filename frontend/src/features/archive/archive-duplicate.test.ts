import { describe, expect, it } from 'vitest'
import { findArchiveDuplicates } from './archive-duplicate'

describe('archive duplicate hints', () => {
  it('finds exact normalized name, code and address matches without blocking creation', () => {
    const rows = [
      { id: '1', code: 'COM-001', name: '示范 花园', address: '湖南省株洲市天元区示范路1号' },
      { id: '2', code: 'COM-002', name: '另一个小区', address: '其他路2号' },
    ]

    const matches = findArchiveDuplicates({
      code: 'com-001',
      name: '示范花园',
      address: ' 湖南省株洲市天元区示范路1号 ',
    }, rows)

    expect(matches).toHaveLength(1)
    expect(matches[0]?.id).toBe('1')
    expect(matches[0]?.reasons).toEqual(['CODE', 'NAME', 'ADDRESS'])
  })

  it('does not flag empty fields or partial text as duplicates', () => {
    const rows = [{ id: '1', code: 'B-01', name: '一号楼', address: '示范路1号' }]

    expect(findArchiveDuplicates({ name: '一号' }, rows)).toEqual([])
    expect(findArchiveDuplicates({ name: '', address: '' }, rows)).toEqual([])
  })
})
