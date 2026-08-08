#!/usr/bin/env node
/**
 * 从后端聚合 OpenAPI 契约生成 TypeScript 类型，并刷新独立 R2 空间契约指纹。
 *
 * 主事实来源：backend-java/model/src/main/resources/openapi-interface.yaml
 * R2 空间事实来源：backend-java/model/src/main/resources/spatial/openapi-spatial.yaml
 *
 * 生成产物：src/shared/api/generated/schema.d.ts
 * 空间适配器：src/shared/api/endpoints/spatial.ts（契约变化时需人工审阅）
 * 空间指纹：src/shared/api/generated/spatial-contract.gitblob
 */
import { execFileSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const frontendRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const repoRoot = resolve(frontendRoot, '..')
const spec = resolve(
  repoRoot,
  'backend-java/model/src/main/resources/openapi-interface.yaml',
)
const spatialSpec = resolve(
  repoRoot,
  'backend-java/model/src/main/resources/spatial/openapi-spatial.yaml',
)
const outDir = resolve(frontendRoot, 'src/shared/api/generated')
const outFile = resolve(outDir, 'schema.d.ts')
const spatialFingerprint = resolve(outDir, 'spatial-contract.gitblob')
const bin = resolve(frontendRoot, 'node_modules/.bin/openapi-typescript')

if (!existsSync(spec)) {
  console.error(`[api:generate] 找不到 OpenAPI 契约: ${spec}`)
  process.exit(1)
}
if (!existsSync(spatialSpec)) {
  console.error(`[api:generate] 找不到 R2 空间 OpenAPI 契约: ${spatialSpec}`)
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

const spatialContent = readFileSync(spatialSpec)
const header = Buffer.from(`blob ${spatialContent.length}\0`, 'utf8')
const spatialSha = createHash('sha1').update(header).update(spatialContent).digest('hex')
writeFileSync(spatialFingerprint, `${spatialSha}\n`, 'utf8')

console.log(`[api:generate] 已生成类型: ${outFile}`)
console.log(`[api:generate] 已刷新 R2 空间契约指纹: ${spatialFingerprint}`)
console.log('[api:generate] 若空间契约有变化，请同时审阅 endpoints/spatial.ts。')
