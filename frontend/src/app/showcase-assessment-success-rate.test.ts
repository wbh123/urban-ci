import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const source = readFileSync(
  resolve(process.cwd(), '../scripts/dev/calculate-showcase-assessments.sh'),
  'utf8',
)

describe('showcase assessment generation quality gate', () => {
  it('fails showcase generation when assessment success rate is too low', () => {
    expect(source).toContain('SHOWCASE_MIN_ASSESSMENT_SUCCESS_RATE')
    expect(source).toContain('min_success_rate=')
    expect(source).toContain('success_rate=')
    expect(source).toContain('评分成功率低于展示数据最低要求')
    expect(source).toContain('exit 2')
  })
})
