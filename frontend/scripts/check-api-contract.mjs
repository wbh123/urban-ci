#!/usr/bin/env node
/**
 * 契约漂移检测：依据当前后端 OpenAPI 重新生成类型，并与已提交的生成文件比较。
 *
 * 若二者不一致，说明后端契约已变更但前端生成类型未同步，直接以非零状态退出。
 * 用于 CI 与本地 `npm run check`，防止生成类型与契约脱节。
 */
import { execFileSync } from 'node:child_process'
import { existsSync, mkdirSync, readFileSync, rmSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const frontendRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const repoRoot = resolve(frontendRoot, '..')
const spec = resolve(
  repoRoot,
  'backend-java/model/src/main/resources/openapi-interface.yaml',
)
const committed = resolve(frontendRoot, 'src/shared/api/generated/schema.d.ts')
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

console.log('[api:check] 生成类型与 OpenAPI 契约一致。')
