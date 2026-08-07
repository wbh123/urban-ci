import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const phase2Path = resolve(
  process.cwd(),
  '../backend-java/model/src/main/resources/phase2/openapi-phase2.yaml',
)
const phase2 = readFileSync(phase2Path, 'utf8')

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
  it('地图接口通过成功响应 schema 描述统一外壳和 data', () => {
    expectSchemaRef('/api/v1/map/runtime-config', 'MapRuntimeConfigSuccessResponse')
    expectSchemaRef('/api/v1/map/geocoding/preview', 'GeocodingResultSuccessResponse')
    expectSchemaRef('/api/v1/map/communities', 'CommunityPointListSuccessResponse')
    expectSchemaRef('/api/v1/communities/{communityId}/location', 'CommunityLocationSuccessResponse')
  })

  it('巡检任务与记录通过成功响应 schema 描述统一外壳和 data', () => {
    expectSchemaRef('/api/v1/inspection-tasks', 'InspectionTaskListSuccessResponse')
    expectSchemaRef('/api/v1/inspection-tasks/{taskId}', 'InspectionTaskSuccessResponse')
    expectSchemaRef('/api/v1/inspection-records', 'InspectionRecordListSuccessResponse')
  })

  it('第二阶段声明长期使用的正式 data 类型', () => {
    for (const schema of [
      'MapRuntimeConfig',
      'CommunityPoint',
      'GeocodingResult',
      'CommunityLocation',
      'InspectionTask',
      'InspectionRecord',
    ]) {
      expect(phase2).toContain(`    ${schema}:`)
    }
  })
})
