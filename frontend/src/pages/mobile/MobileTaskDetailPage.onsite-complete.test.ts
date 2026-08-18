import { describe, expect, it } from 'vitest'
import source from './MobileTaskDetailPage.vue?raw'

describe('MobileTaskDetailPage onsite completion', () => {
  it('submits onsite completion without closing the inspection task', () => {
    expect(source).toContain('现场巡查完毕')
    expect(source).toContain("transition('onsite-complete')")
    expect(source).toContain('现场作业已提交，等待后台确认')
    expect(source).not.toContain("@click=\"transition('complete')\"")
  })
})
