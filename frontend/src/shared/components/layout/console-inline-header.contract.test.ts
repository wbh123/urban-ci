import { describe, expect, it } from 'vitest'
import layout from '../../../layouts/ConsoleLayout.vue?raw'
import pageHeader from './AppPageHeader.vue?raw'

describe('single console page header contract', () => {
  it('removes the standalone console top bar for every console route', () => {
    expect(layout).not.toContain('<el-header')
    expect(layout).not.toContain('inlineConsoleUser')
    expect(layout).toContain('--usp-console-header-offset: 0px')
  })

  it('lets the shared page header own page actions, AI runtime and user menu', () => {
    expect(pageHeader).toContain('showUserMenu?: boolean')
    expect(pageHeader).toContain('showUserMenu: true')
    expect(pageHeader).toContain('<AiRuntimeBadge')
    expect(pageHeader).toContain('<ConsoleUserMenu v-if="showUserMenu"')
  })
})
