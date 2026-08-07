import { describe, expect, it } from 'vitest'
import { ensureMockServer } from '@/tests/setup'

describe('AiGovernanceMockTest', () => {
  it('管理员状态接口只返回受控配置状态与近七日统计', async () => {
    await ensureMockServer()

    const response = await fetch('http://localhost/api/v1/ai-governance/status', {
      headers: { Authorization: 'Bearer mock-access-token' },
    })
    const body = await response.json()

    expect(response.status).toBe(200)
    expect(body.data.statisticsWindow).toBe('LAST_7_DAYS')
    expect(body.data.providers).toHaveLength(3)
    expect(body.data.providers.every(
      (item: { connectivityStatus: string }) => item.connectivityStatus === 'NOT_PROBED',
    )).toBe(true)
    expect(JSON.stringify(body.data)).not.toContain('apiKey')
    expect(JSON.stringify(body.data)).not.toContain('weightPath')
  })

  it('自动识别开关可查询和更新且不返回密钥', async () => {
    await ensureMockServer()
    const headers = {
      Authorization: 'Bearer mock-access-token',
      'Content-Type': 'application/json',
    }

    const before = await fetch('http://localhost/api/v1/ai-governance/automation-settings', {
      headers,
    })
    expect((await before.json()).data.autoInferenceOnUpload).toBe(false)

    const updated = await fetch('http://localhost/api/v1/ai-governance/automation-settings', {
      method: 'PUT',
      headers,
      body: JSON.stringify({ autoInferenceOnUpload: true }),
    })
    const body = await updated.json()

    expect(updated.status).toBe(200)
    expect(body.data.autoInferenceOnUpload).toBe(true)
    expect(body.data.modelId).toBe('AI-DIFY-WORKFLOW-001')
    expect(JSON.stringify(body.data)).not.toContain('apiKey')
  })
})
