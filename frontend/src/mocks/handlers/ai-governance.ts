import { http } from 'msw'
import { okResponse, requireAuth } from './helpers'

let autoInferenceOnUpload = false

function automationSettings() {
  return {
    autoInferenceOnUpload,
    modelId: 'AI-DIFY-WORKFLOW-001',
    providerCode: 'DIFY',
    capabilityType: 'WORKFLOW',
    updatedAt: new Date().toISOString(),
  }
}

export const aiGovernanceHandlers = [
  http.get('/api/v1/ai-governance/status', ({ request }) => {
    const unauthenticated = requireAuth(request)
    if (unauthenticated) return unauthenticated
    return okResponse({
      generatedAt: new Date().toISOString(),
      statisticsWindow: 'LAST_7_DAYS',
      providers: [
        {
          providerCode: 'FAST_API',
          enabled: true,
          configured: true,
          configurationStatus: 'CONFIGURED',
          connectivityStatus: 'NOT_PROBED',
          capabilities: ['VISION_INFERENCE'],
          defaultFor: ['VISION_INFERENCE'],
          metrics7d: {
            totalTasks: 18,
            succeededTasks: 16,
            failedTasks: 2,
            reviewedTasks: 12,
            pendingReviewTasks: 4,
            averageDurationMs: 430,
            successRate: 88.89,
          },
        },
        {
          providerCode: 'DIFY',
          enabled: false,
          configured: false,
          configurationStatus: 'DISABLED',
          connectivityStatus: 'NOT_PROBED',
          capabilities: ['VISION_INFERENCE', 'WORKFLOW'],
          defaultFor: ['WORKFLOW'],
          metrics7d: {
            totalTasks: 2,
            succeededTasks: 0,
            failedTasks: 2,
            reviewedTasks: 0,
            pendingReviewTasks: 0,
            averageDurationMs: 0,
            successRate: 0,
          },
        },
        {
          providerCode: 'SPRING_AI',
          enabled: false,
          configured: false,
          configurationStatus: 'DISABLED',
          connectivityStatus: 'NOT_PROBED',
          capabilities: ['TEXT_GENERATION', 'VISION_INFERENCE'],
          defaultFor: ['TEXT_GENERATION'],
          metrics7d: {
            totalTasks: 0,
            succeededTasks: 0,
            failedTasks: 0,
            reviewedTasks: 0,
            pendingReviewTasks: 0,
            averageDurationMs: 0,
            successRate: 0,
          },
        },
      ],
      total7d: {
        totalTasks: 20,
        succeededTasks: 16,
        failedTasks: 4,
        reviewedTasks: 12,
        pendingReviewTasks: 4,
        averageDurationMs: 387,
        successRate: 80,
      },
      unassignedLegacyTasks7d: 0,
      healthSemantics: 'CONFIGURED 仅表示配置完整；connectivityStatus=NOT_PROBED 表示未主动调用外部服务。',
      disclaimer: '人工智能状态与统计仅用于运维和质量治理，不代表模型准确率、房屋危险概率或正式鉴定结论。',
    })
  }),
  http.get('/api/v1/ai-governance/automation-settings', ({ request }) => {
    const unauthenticated = requireAuth(request)
    if (unauthenticated) return unauthenticated
    return okResponse(automationSettings())
  }),
  http.put('/api/v1/ai-governance/automation-settings', async ({ request }) => {
    const unauthenticated = requireAuth(request)
    if (unauthenticated) return unauthenticated
    const body = await request.json() as { autoInferenceOnUpload?: boolean }
    autoInferenceOnUpload = body.autoInferenceOnUpload === true
    return okResponse(automationSettings())
  }),
]
