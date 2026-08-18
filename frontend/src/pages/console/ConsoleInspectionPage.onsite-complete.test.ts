import { describe, expect, it } from 'vitest'
import source from './ConsoleInspectionPage.vue?raw'

describe('ConsoleInspectionPage onsite completion', () => {
  it('exposes onsite-completed tasks as waiting for backoffice confirmation', () => {
    expect(source).toContain('ONSITE_COMPLETED')
    expect(source).toContain('待后台确认')
  })

  it('keeps final completion in the management console', () => {
    expect(source).toContain('确认任务完成')
    expect(source).toContain("transitionInspectionTask(task.taskId, 'complete')")
  })
})
