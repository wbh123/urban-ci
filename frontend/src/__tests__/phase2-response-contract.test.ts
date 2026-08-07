import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const phase2Path = resolve(
  process.cwd(),
  '../backend-java/model/src/main/resources/phase2/openapi-phase2.yaml',
)
const aggregatePath = resolve(
  process.cwd(),
  '../backend-java/model/src/main/resources/openapi-interface.yaml',
)
const phase2 = readFileSync(phase2Path, 'utf8')
const aggregate = readFileSync(aggregatePath, 'utf8')

function pathSection(path: string): string {
  const marker = `  ${path}:`
  const start = phase2.indexOf(marker)
  expect(start, `OpenAPI 缺少路径 ${path}`).toBeGreaterThanOrEqual(0)
  const next = phase2.indexOf('\n  /api/', start + marker.length)
  return phase2.slice(start, next === -1 ? phase2.length : next)
}

function expectSchemaRef(path: string, schema: string): void {
  const section = pathSection(path)
  expect(section).toContain(`$ref: '#/components/schemas/${schema}'`)
}

describe('第二阶段正式响应契约', () => {
  it('地图接口通过 schema 描述正式响应，而不是只保留 example', () => {
    expectSchemaRef('/api/v1/map/runtime-config', 'MapRuntimeConfig')
    expectSchemaRef('/api/v1/map/geocoding/preview', 'GeocodingResult')
    expectSchemaRef('/api/v1/map/communities', 'CommunityPoint')
    expectSchemaRef('/api/v1/communities/{communityId}/location', 'CommunityLocation')
  })

  it('巡检任务与记录通过 schema 描述正式响应', () => {
    expectSchemaRef('/api/v1/inspection-tasks', 'InspectionTask')
    expectSchemaRef('/api/v1/inspection-tasks/{taskId}', 'InspectionTask')
    expectSchemaRef('/api/v1/inspection-records', 'InspectionRecord')
  })

  it('聚合 OpenAPI 导出所有长期使用的第二阶段响应类型', () => {
    for (const schema of [
      'MapRuntimeConfig',
      'CommunityPoint',
      'GeocodingResult',
      'CommunityLocation',
      'InspectionTask',
      'InspectionRecord',
    ]) {
      expect(aggregate).toContain(
        `${schema}:\n      $ref: './phase2/openapi-phase2.yaml#/components/schemas/${schema}'`,
      )
    }
  })
})
