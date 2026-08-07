#!/usr/bin/env node
/** 契约漂移检测：主业务与空间契约均重新生成并与已提交声明比较。 */
import { execFileSync } from 'node:child_process'
import { existsSync, mkdirSync, readFileSync, rmSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const frontendRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const repoRoot = resolve(frontendRoot, '..')
const bin = resolve(frontendRoot, 'node_modules/.bin/openapi-typescript')
const tmpDir = resolve(frontendRoot, 'node_modules/.tmp')
const contracts = [
  {
    name: '主业务',
    spec: resolve(repoRoot, 'backend-java/model/src/main/resources/openapi-interface.yaml'),
    committed: resolve(frontendRoot, 'src/shared/api/generated/schema.d.ts'),
    tmp: resolve(tmpDir, 'schema.d.ts.check'),
  },
  {
    name: '空间',
    spec: resolve(
      repoRoot,
      'backend-java/model/src/main/resources/spatial/openapi-spatial.yaml',
    ),
    committed: resolve(frontendRoot, 'src/shared/api/generated/spatial-schema.d.ts'),
    tmp: resolve(tmpDir, 'spatial-schema.d.ts.check'),
  },
]

if (!existsSync(bin)) {
  console.error(`[api:check] 找不到 openapi-typescript 可执行文件: ${bin}`)
  process.exit(1)
}
mkdirSync(tmpDir, { recursive: true })

for (const contract of contracts) {
  if (!existsSync(contract.spec)) {
    console.error(`[api:check] 找不到${contract.name} OpenAPI 契约: ${contract.spec}`)
    process.exit(1)
  }
  if (!existsSync(contract.committed)) {
    console.error(`[api:check] 找不到已提交的${contract.name}生成类型: ${contract.committed}`)
    console.error('[api:check] 请先执行 npm run api:generate。')
    process.exit(1)
  }

  execFileSync(process.execPath, [bin, contract.spec, '-o', contract.tmp], {
    cwd: frontendRoot,
  })
  const committedContent = readFileSync(contract.committed, 'utf8')
  const generatedContent = readFileSync(contract.tmp, 'utf8')
  rmSync(contract.tmp, { force: true })
  if (committedContent !== generatedContent) {
    console.error(`[api:check] ${contract.name}生成类型与 OpenAPI 契约不一致（已漂移）。`)
    console.error('[api:check] 请执行 npm run api:generate 重新生成并提交。')
    process.exit(1)
  }
}

console.log('[api:check] 主业务与空间生成类型均与 OpenAPI 契约一致。')
