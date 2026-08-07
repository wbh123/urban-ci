#!/usr/bin/env node
/**
 * 从后端聚合 OpenAPI 契约生成 TypeScript 类型。
 *
 * 事实来源：backend-java/model/src/main/resources/openapi-interface.yaml
 * 该文件通过 $ref 聚合各细粒度 openapi-*.yaml，openapi-typescript 会就地解析外部引用。
 *
 * 生成产物：src/shared/api/generated/schema.d.ts
 * 该文件由脚本生成，禁止人工修改；后端契约变化后重新执行本脚本。
 */
import { execFileSync } from 'node:child_process'
import { existsSync, mkdirSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const frontendRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const repoRoot = resolve(frontendRoot, '..')
const spec = resolve(
  repoRoot,
  'backend-java/model/src/main/resources/openapi-interface.yaml',
)
const outDir = resolve(frontendRoot, 'src/shared/api/generated')
const outFile = resolve(outDir, 'schema.d.ts')
const bin = resolve(frontendRoot, 'node_modules/.bin/openapi-typescript')

if (!existsSync(spec)) {
  console.error(`[api:generate] 找不到 OpenAPI 契约: ${spec}`)
  process.exit(1)
}
if (!existsSync(bin)) {
  console.error(`[api:generate] 找不到 openapi-typescript 可执行文件: ${bin}`)
  process.exit(1)
}

mkdirSync(outDir, { recursive: true })

execFileSync(process.execPath, [bin, spec, '-o', outFile], {
  cwd: frontendRoot,
  stdio: 'inherit',
})

console.log(`[api:generate] 已生成类型: ${outFile}`)
console.log('[api:generate] 提示: 该文件由脚本生成，禁止人工修改。')
