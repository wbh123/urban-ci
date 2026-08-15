import { describe, expect, it } from 'vitest'
import source from './ConsoleKnowledgePage.vue?raw'

describe('ConsoleKnowledgePage AI-first contract', () => {
  it('presents the knowledge service as an AI governance assistant', () => {
    expect(source).toContain('AI 知识助手')
    expect(source).toContain('证据不足时拒答')
    expect(source).toContain('KnowledgeQaPanel')
  })
})
