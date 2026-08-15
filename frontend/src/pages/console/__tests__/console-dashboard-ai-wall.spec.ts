import { describe, expect, it } from 'vitest'
import dashboardSource from '../ConsoleDashboardPage.vue?raw'

describe('ConsoleDashboardPage AI wall mount contract', () => {
  it('mounts the new AI wall and leaves the legacy wall unmounted', () => {
    expect(dashboardSource).toContain(
      "import WorkbenchAiDataWall from '@/components/workbench/ai-wall/WorkbenchAiDataWall.vue'",
    )
    expect(dashboardSource).toContain('<WorkbenchAiDataWall')
    expect(dashboardSource).not.toContain("import WorkbenchDataWall from '@/components/workbench/WorkbenchDataWall.vue'")
    expect(dashboardSource).not.toContain('<WorkbenchDataWall')
  })
})
