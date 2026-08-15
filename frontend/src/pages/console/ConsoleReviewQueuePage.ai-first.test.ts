import { describe, expect, it } from 'vitest'
import source from './ConsoleReviewQueuePage.vue?raw'

describe('P2 AI human review center', () => {
  it('prioritizes review decisions instead of model metadata', () => {
    expect(source).toContain('AI 人工复核中心')
    for (const label of ['对象', 'AI 发现', '可信程度', '证据数量', '提交时间', '复核状态']) {
      expect(source).toContain(label)
    }
    expect(source).not.toContain('label="请求编号"')
    expect(source).not.toContain('label="模型"')
  })
})
