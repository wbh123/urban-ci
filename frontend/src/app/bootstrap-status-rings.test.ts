import { describe, expect, it } from 'vitest'
import source from './bootstrap?raw'

describe('application bootstrap map marker integration', () => {
  it('does not install the retired DOM status-ring overlay', () => {
    expect(source).not.toContain('installWorkbenchStatusRingOverlay')
    expect(source).not.toContain('workbench-status-ring-overlay')
  })
})
