import { describe, expect, it } from 'vitest'
import source from './AppPageHeader.vue?raw'

describe('console page AI runtime', () => {
  it('reuses the runtime context directly in the real page header', () => {
    expect(source).toContain('AI_RUNTIME_CONTEXT_KEY')
    expect(source).toContain('props.showUserMenu && runtime !== null')
    expect(source).toContain('showInlineAiRuntime')
    expect(source).not.toContain('inlineConsoleUser')
  })

  it('renders the runtime badge beside the page user menu without refetching', () => {
    expect(source).toContain('AiRuntimeBadge')
    expect(source).toContain('ConsoleUserMenu')
    expect(source).toContain('showUserMenu: true')
    expect(source).not.toContain('getAiRuntimeSummary')
  })
})
