import { describe, expect, it } from 'vitest'
import source from './KnowledgeQaPanel.vue?raw'

describe('KnowledgeQaPanel AI-first contract', () => {
  it('uses business-first scope selection and answer ordering', () => {
    expect(source).toContain('AI 知识助手')
    expect(source).toContain('SpatialObjectSelector')
    expect(source).toContain('引用依据')
    expect(source).toContain('专业技术详情')
    expect(source).not.toContain('限定社区范围的 UUID')
    expect(source).not.toContain('限定楼栋范围的 UUID')
  })
})
