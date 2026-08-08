#!/usr/bin/env node
/**
 * 契约漂移检测：依据当前后端 OpenAPI 重新生成类型，并与已提交的生成文件比较。
 *
 * 主聚合契约继续做完整 TypeScript 生成对比；R2 空间契约保持独立，通过 Git Blob
 * 指纹强制前端 spatial adapter 与契约同步。空间契约发生任何字节级变化后，必须审阅
 * frontend/src/shared/api/endpoints/spatial.ts，并执行 npm run api:generate 更新指纹。
 */
import { execFileSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, rmSync } from 'node:fs'
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
const committed = resolve(frontendRoot, 'src/shared/api/generated/schema.d.ts')
const spatialFingerprint = resolve(
  frontendRoot,
  'src/shared/api/generated/spatial-contract.gitblob',
)
const tmp = resolve(frontendRoot, 'node_modules/.tmp/schema.d.ts.check')
const bin = resolve(frontendRoot, 'node_modules/.bin/openapi-typescript')

if (!existsSync(spec)) {
  console.error(`[api:check] 找不到 OpenAPI 契约: ${spec}`)
  process.exit(1)
}
if (!existsSync(committed)) {
  console.error(`[api:check] 找不到已提交的生成类型: ${committed}`)
  console.error('[api:check] 请先执行 npm run api:generate。')
  process.exit(1)
}
if (!existsSync(spatialSpec) || !existsSync(spatialFingerprint)) {
  console.error('[api:check] 找不到 R2 空间契约或其前端指纹。')
  process.exit(1)
}

mkdirSync(dirname(tmp), { recursive: true })
execFileSync(process.execPath, [bin, spec, '-o', tmp], { cwd: frontendRoot })

const committedContent = readFileSync(committed, 'utf8')
const generatedContent = readFileSync(tmp, 'utf8')
rmSync(tmp, { force: true })

if (committedContent !== generatedContent) {
  console.error('[api:check] 生成类型与 OpenAPI 契约不一致（已漂移）。')
  console.error('[api:check] 请执行 npm run api:generate 重新生成并提交。')
  process.exit(1)
}

function gitBlobSha(content) {
  const header = Buffer.from(`blob ${content.length}\0`, 'utf8')
  return createHash('sha1').update(header).update(content).digest('hex')
}

const spatialContent = readFileSync(spatialSpec)
const expectedSpatialSha = readFileSync(spatialFingerprint, 'utf8').trim()
const actualSpatialSha = gitBlobSha(spatialContent)
if (actualSpatialSha !== expectedSpatialSha) {
  console.error('[api:check] R2 空间 OpenAPI 与前端 spatial adapter 指纹不一致（已漂移）。')
  console.error('[api:check] 请审阅 spatial.ts 后执行 npm run api:generate 更新指纹。')
  process.exit(1)
}

console.log('[api:check] 主 OpenAPI 类型与 R2 空间契约指纹均一致。')
