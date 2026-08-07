#!/usr/bin/env node
/**
 * 从后端 OpenAPI 契约生成 TypeScript 类型。
 *
 * 主业务契约继续生成 schema.d.ts；R2 空间契约采用与 ai-governance 相同的独立细粒度
 * 契约方式，生成 spatial-schema.d.ts，避免为新增领域反复扩大历史聚合 YAML。
 */
import { execFileSync } from 'node:child_process'
import { existsSync, mkdirSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const frontendRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const repoRoot = resolve(frontendRoot, '..')
const bin = resolve(frontendRoot, 'node_modules/.bin/openapi-typescript')
const outDir = resolve(frontendRoot, 'src/shared/api/generated')
const contracts = [
  {
    spec: resolve(repoRoot, 'backend-java/model/src/main/resources/openapi-interface.yaml'),
    out: resolve(outDir, 'schema.d.ts'),
  },
  {
    spec: resolve(
      repoRoot,
      'backend-java/model/src/main/resources/spatial/openapi-spatial.yaml',
    ),
    out: resolve(outDir, 'spatial-schema.d.ts'),
  },
]

if (!existsSync(bin)) {
  console.error(`[api:generate] 找不到 openapi-typescript 可执行文件: ${bin}`)
  process.exit(1)
}
mkdirSync(outDir, { recursive: true })

for (const contract of contracts) {
  if (!existsSync(contract.spec)) {
    console.error(`[api:generate] 找不到 OpenAPI 契约: ${contract.spec}`)
    process.exit(1)
  }
  execFileSync(process.execPath, [bin, contract.spec, '-o', contract.out], {
    cwd: frontendRoot,
    stdio: 'inherit',
  })
  console.log(`[api:generate] 已生成类型: ${contract.out}`)
}

console.log('[api:generate] 提示: 生成文件禁止人工修改。')
