/// <reference types="node" />

import { describe, expect, it } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

function source(relativePath: string): string {
  const path = fileURLToPath(new URL(relativePath, import.meta.url))
  return existsSync(path) ? readFileSync(path, 'utf8') : ''
}

const panelSource = source('../../features/knowledge/KnowledgeQaPanel.vue')
const consolePageSource = source('../console/ConsoleKnowledgePage.vue')
const mockSource = source('../../mocks/handlers/knowledge.ts')

describe('internal knowledge QA user experience', () => {
  it('shows evidence sufficiency, controlled refusal, versions and citations', () => {
    expect(panelSource).toContain('evidenceSufficient')
    expect(panelSource).toContain("status === 'REFUSED'")
    expect(panelSource).toContain('documentVersion')
    expect(panelSource).toContain('sectionTitle')
    expect(panelSource).toContain('pageNumber')
    expect(panelSource).toContain('disclaimer')
  })

  it('lets administrators register reviewed document chunks with a SHA-256 checksum', () => {
    expect(consolePageSource).toContain('createKnowledgeDocument')
    expect(consolePageSource).toContain('crypto.subtle.digest')
    expect(consolePageSource).toContain('roleScope')
    expect(consolePageSource).toContain('documentVersion')
  })

  it('provides answered and refused mock scenarios without inventing formal conclusions', () => {
    expect(mockSource).toContain("status: 'ANSWERED'")
    expect(mockSource).toContain("status: 'REFUSED'")
    expect(mockSource).toContain('documentVersion')
    expect(mockSource).toContain('不作为正式房屋安全鉴定结论')
  })
})
