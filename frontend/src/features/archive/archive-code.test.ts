import { describe, expect, it } from 'vitest'
import { suggestBuildingCode, suggestCommunityCode } from './archive-code'

describe('archive code suggestions', () => {
  it('suggests readable date based community and building codes', () => {
    const now = new Date('2026-08-08T12:00:00Z')
    expect(suggestCommunityCode(now, () => 'A1B2')).toBe('COMM-20260808-A1B2')
    expect(suggestBuildingCode(now, () => 'C3D4')).toBe('BLDG-20260808-C3D4')
  })

  it('normalizes random suffixes to uppercase alphanumeric text', () => {
    expect(suggestCommunityCode(new Date('2026-01-02T00:00:00Z'), () => ' a-1 '))
      .toBe('COMM-20260102-A1')
  })
})
