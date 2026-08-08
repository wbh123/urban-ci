import { describe, expect, it } from 'vitest'
import source from './ConsoleLayout.vue?raw'

describe('ConsoleLayout full-width route mode', () => {
  it('lets selected routes escape the standard content max width', () => {
    expect(source).toContain("route.meta.fullWidth")
    expect(source).toContain('console-main--wide')
    expect(source).toContain('max-width: none')
  })
})
