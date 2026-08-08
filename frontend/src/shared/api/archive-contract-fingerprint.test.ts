import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const generateSource = readFileSync(
  resolve(process.cwd(), 'scripts/generate-api.mjs'),
  'utf8',
)
const checkSource = readFileSync(
  resolve(process.cwd(), 'scripts/check-api-contract.mjs'),
  'utf8',
)

describe('archive OpenAPI drift guard', () => {
  it('generate-api refreshes the independent archive contract fingerprint', () => {
    expect(generateSource).toContain('archive/openapi-archive.yaml')
    expect(generateSource).toContain('archive-contract.gitblob')
    expect(generateSource).toContain('endpoints/archive.ts')
  })

  it('api:check rejects archive contract drift', () => {
    expect(checkSource).toContain('archive/openapi-archive.yaml')
    expect(checkSource).toContain('archive-contract.gitblob')
    expect(checkSource).toContain('archive adapter')
    expect(checkSource).toContain('actualArchiveSha')
  })
})
