import { describe, expect, it } from 'vitest'
import { ensureMockServer } from '@/tests/setup'

describe('AiOrchestrationMockTest', () => {
  it('指定 Dify 工作流后返回可追溯结构化结果且不发生静默降级', async () => {
    await ensureMockServer()

    const response = await fetch('http://localhost/api/v1/ai-inferences', {
      method: 'POST',
      headers: {
        Authorization: 'Bearer mock-access-token:inspector',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        assetId: '80000000-0000-0000-0000-000000000001',
        mode: 'REAL',
        modelId: 'AI-DIFY-WORKFLOW-001',
        providerCode: 'DIFY',
        capabilityType: 'WORKFLOW',
        prompt: '分析图片并给出人工复核建议',
      }),
    })
    const body = await response.json()

    expect(response.status).toBe(201)
    expect(body.data).toMatchObject({
      providerCode: 'DIFY',
      capabilityType: 'WORKFLOW',
      fallbackUsed: false,
    })
    expect(body.data.structuredResult).toMatchObject({
      providerCode: 'DIFY',
      capabilityType: 'WORKFLOW',
      modelCode: 'AI-DIFY-WORKFLOW-001',
    })
    expect(body.data.structuredResult.recommendations.length).toBeGreaterThan(0)
  })

  it('可按提供者与能力筛选人工智能任务', async () => {
    await ensureMockServer()

    const response = await fetch(
      'http://localhost/api/v1/ai-inferences?providerCode=DIFY&capabilityType=WORKFLOW&page=0&size=20',
      { headers: { Authorization: 'Bearer mock-access-token:expert' } },
    )
    const body = await response.json()

    expect(response.status).toBe(200)
    expect(body.data.content.length).toBeGreaterThan(0)
    expect(body.data.content.every(
      (task: { providerCode: string; capabilityType: string }) =>
        task.providerCode === 'DIFY' && task.capabilityType === 'WORKFLOW',
    )).toBe(true)
  })
})
