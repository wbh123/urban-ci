import { describe, expect, it } from 'vitest'
import { ensureMockServer } from '@/tests/setup'

describe('MockAuthHandlerTest', () => {
  it('提供 mock health 端点', async () => {
    await ensureMockServer()

    const response = await fetch('http://localhost/api/v1/mock/health')
    const body = await response.json()

    expect(response.status).toBe(200)
    expect(body.success).toBe(true)
    expect(body.data).toMatchObject({ mode: 'mock', status: 'UP' })
  })

  it('演示账号能稳定登录', async () => {
    await ensureMockServer()

    const response = await fetch('http://localhost/api/v1/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: 'inspector', password: 'demo123' }),
    })
    const body = await response.json()

    expect(response.status).toBe(200)
    expect(body.success).toBe(true)
    expect(body.data.accessToken).toBe('mock-access-token:inspector')
    expect(body.data.user.roles).toContain('PROPERTY_INSPECTOR')
  })

  it('错误密码返回明确认证失败', async () => {
    await ensureMockServer()

    const response = await fetch('http://localhost/api/v1/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: 'inspector', password: 'wrong' }),
    })
    const body = await response.json()

    expect(response.status).toBe(401)
    expect(body.success).toBe(false)
    expect(body.error.code).toBe('BAD_CREDENTIALS')
  })

  it('提供 AI 推理复核队列 mock 数据', async () => {
    await ensureMockServer()

    const response = await fetch('http://localhost/api/v1/ai-inferences?page=0&size=100', {
      headers: { Authorization: 'Bearer mock-access-token:expert' },
    })
    const body = await response.json()

    expect(response.status).toBe(200)
    expect(body.success).toBe(true)
    expect(body.data.content[0]).toMatchObject({
      requestCode: 'AI-DEMO-001',
      status: 'SUCCEEDED',
      reviewStatus: 'UNREVIEWED',
    })
  })


  it('提供 AI 推理图片内容 mock 数据', async () => {
    await ensureMockServer()

    const response = await fetch('http://localhost/api/v1/assets/images/80000000-0000-0000-0000-000000000001/content', {
      headers: { Authorization: 'Bearer mock-access-token:expert' },
    })
    const body = new Uint8Array(await response.arrayBuffer())

    expect(response.status).toBe(200)
    expect(response.headers.get('content-type')).toBe('image/png')
    expect(body.slice(0, 8)).toEqual(Uint8Array.from([137, 80, 78, 71, 13, 10, 26, 10]))
  })


  it('提供城市更新优先级排行榜 mock 数据', async () => {
    await ensureMockServer()

    const response = await fetch('http://localhost/api/v1/renewal-priorities?scopeType=ALL&page=0&size=20', {
      headers: { Authorization: 'Bearer mock-access-token:government' },
    })
    const body = await response.json()

    expect(response.status).toBe(200)
    expect(body.success).toBe(true)
    expect(body.data.content[0]).toMatchObject({
      ranking: 1,
      buildingCode: 'B-001',
      priorityLevel: 'P1',
      status: 'CURRENT',
    })
  })


  it('提供 AI 推理创建和重试 mock 数据', async () => {
    await ensureMockServer()

    const createResponse = await fetch('http://localhost/api/v1/ai-inferences', {
      method: 'POST',
      headers: { Authorization: 'Bearer mock-access-token:inspector', 'Content-Type': 'application/json' },
      body: JSON.stringify({ assetId: '80000000-0000-0000-0000-000000000001', mode: 'MOCK' }),
    })
    const createBody = await createResponse.json()

    expect(createResponse.status).toBe(201)
    expect(createBody.success).toBe(true)
    expect(createBody.data.assetId).toBe('80000000-0000-0000-0000-000000000001')

    const retryResponse = await fetch(`http://localhost/api/v1/ai-inferences/${createBody.data.inferenceId}/retry`, {
      method: 'POST',
      headers: { Authorization: 'Bearer mock-access-token:inspector', 'Content-Type': 'application/json' },
      body: JSON.stringify({}),
    })
    const retryBody = await retryResponse.json()

    expect(retryResponse.status).toBe(200)
    expect(retryBody.success).toBe(true)
    expect(retryBody.data.attemptNo).toBe(createBody.data.attemptNo + 1)
  })

  it('提供评分详情摘要历史计算和规则激活 mock 数据', async () => {
    await ensureMockServer()

    const headers = { Authorization: 'Bearer mock-access-token:government' }
    const current = await (await fetch('http://localhost/api/v1/assessments/buildings/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa/current', { headers })).json()
    const summary = await (await fetch('http://localhost/api/v1/assessments/buildings/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa/summary', { headers })).json()
    const history = await (await fetch('http://localhost/api/v1/assessments/buildings/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa/history', { headers })).json()
    const calculate = await (await fetch('http://localhost/api/v1/assessments/buildings/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa/calculate', {
      method: 'POST',
      headers: { ...headers, 'Content-Type': 'application/json' },
      body: JSON.stringify({ force: false }),
    })).json()
    const rules = await (await fetch('http://localhost/api/v1/assessment-rules', { headers })).json()
    const activate = await (await fetch('http://localhost/api/v1/assessment-rules/41000000-0000-0000-0000-000000000004/activate', {
      method: 'POST',
      headers,
    })).json()

    expect(current.data.freshness).toBe('CURRENT')
    expect(summary.data).not.toHaveProperty('priority')
    expect(history.data.content[0].assessmentType).toBe('RISK')
    expect(calculate.data.reused).toBe(true)
    expect(rules.data.content).toHaveLength(3)
    expect(activate.data.activeRule.versionCode).toBe('COMPLETENESS-V1.1')
  })

})
