import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const generateSource = readFileSync(
  fileURLToPath(new URL('../../../scripts/generate-api.mjs', import.meta.url)),
  'utf8',
)
const checkSource = readFileSync(
  fileURLToPath(new URL('../../../scripts/check-api-contract.mjs', import.meta.url)),
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
