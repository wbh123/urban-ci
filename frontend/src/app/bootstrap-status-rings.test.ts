import { describe, expect, it } from 'vitest'
import source from './bootstrap?raw'

describe('application bootstrap status ring integration', () => {
  it('installs the workbench concentric status overlay with the application Pinia instance', () => {
    expect(source).toContain('installWorkbenchStatusRingOverlay')
    expect(source).toContain('installWorkbenchStatusRingOverlay(pinia)')
  })
})
