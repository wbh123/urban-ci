import { describe, expect, it } from 'vitest'
import source from './AiPageBrief.vue?raw'

describe('AiPageBrief', () => {
  it('renders one compact business-facing AI brief with at most four real metrics', () => {
    expect(source).toContain('metrics.slice(0, 4)')
    expect(source).toContain('✦ AI')
    expect(source).toContain('summary')
    expect(source).toContain('suggestion')
  })

  it('suppresses the whole card when the caller marks it empty', () => {
    expect(source).toContain('v-if="!empty"')
  })

  it('does not expose model provider or token details', () => {
    expect(source).not.toContain('provider')
    expect(source).not.toContain('token')
    expect(source).not.toContain('modelCode')
  })
})
