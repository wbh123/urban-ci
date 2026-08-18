import { describe, expect, it } from 'vitest'
import tasksSource from './MobileTasksPage.vue?raw'
import taskDetailSource from './MobileTaskDetailPage.vue?raw'
import disposalSource from './MobileDisposalPage.vue?raw'

describe('mobile demo pagination pressure relief', () => {
  it('loads inspection tasks in 20-row pages and exposes a load-more action', () => {
    expect(tasksSource).toContain('const PAGE_SIZE = 20')
    expect(tasksSource).toContain('page: currentPage.value')
    expect(tasksSource).toContain('size: PAGE_SIZE')
    expect(tasksSource).toContain('加载更多')
  })

  it('loads disposal work orders in 20-row pages and exposes a load-more action', () => {
    expect(disposalSource).toContain('const PAGE_SIZE = 20')
    expect(disposalSource).toContain('page: currentPage.value')
    expect(disposalSource).toContain('size: PAGE_SIZE')
    expect(disposalSource).toContain('加载更多')
  })

  it('loads a task detail through the existing single-task endpoint instead of fetching all tasks', () => {
    expect(taskDetailSource).toContain('getInspectionTask(taskId.value)')
    expect(taskDetailSource).not.toContain('const tasks = await api.listInspectionTasks()')
  })
})
