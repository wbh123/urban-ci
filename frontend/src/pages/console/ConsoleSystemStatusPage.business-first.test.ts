import { describe, expect, it } from 'vitest'
import source from './ConsoleSystemStatusPage.vue?raw'

describe('ConsoleSystemStatusPage business-first contract', () => {
  it('puts business capability health before technical provider details', () => {
    expect(source).toContain('AI 运行状态')
    expect(source).toContain('业务能力概览')
    expect(source).toContain('本地视觉识别')
    expect(source).toContain('智能工作流')
    expect(source).toContain('知识服务')
    expect(source).toContain('专业技术详情')
  })
})
