/// <reference types="node" />

import { describe, expect, it } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const endpointPath = resolve(process.cwd(), 'src/shared/api/endpoints/knowledge.ts')
const endpointSource = existsSync(endpointPath) ? readFileSync(endpointPath, 'utf8') : ''

describe('knowledge QA endpoints', () => {
  it('uses the generated OpenAPI schema and the unified POST client', () => {
    expect(endpointSource).toContain("components['schemas']['KnowledgeQuestionRequest']")
    expect(endpointSource).toContain("components['schemas']['KnowledgeAnswerView']")
    expect(endpointSource).toContain("apiPost<KnowledgeAnswerView>('/api/v1/knowledge/questions'")
  })

  it('provides the administrator document registration endpoint', () => {
    expect(endpointSource).toContain("components['schemas']['KnowledgeDocumentCreateRequest']")
    expect(endpointSource).toContain("apiPost<KnowledgeDocumentView>('/api/v1/knowledge/documents'")
  })
})
