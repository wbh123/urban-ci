import { http } from 'msw'
import { okResponse, errorResponse, requireAuth, scenarioOf } from './helpers'
import { BUILDING_ID, COMMUNITY_ID, TASK_ID } from '../fixtures/data'
import type { AiInferenceTask } from '@/shared/api'

const now = (): string => new Date().toISOString()

const disclaimer = '系统结果仅用于风险筛查与辅助决策，不作为正式房屋安全鉴定结论。'

const tasks: AiInferenceTask[] = [
  {
    inferenceId: '90000000-0000-0000-0000-000000000001',
    requestCode: 'AI-DEMO-001',
    status: 'SUCCEEDED',
    mode: 'MOCK',
    providerCode: 'FAST_API',
    capabilityType: 'VISION_INFERENCE',
    modelId: 'mock-visual-inspection-v1',
    modelName: '表观病害 MOCK 识别模型',
    modelVersion: '1.0.0',
    license: 'DEMO_ONLY',
    assetId: '80000000-0000-0000-0000-000000000001',
    inspectionTaskId: TASK_ID,
    inspectionRecordId: '70000000-0000-0000-0000-000000000001',
    buildingId: BUILDING_ID,
    communityId: COMMUNITY_ID,
    requestedBy: 'expert',
    attemptNo: 1,
    reviewStatus: 'UNREVIEWED',
    durationMs: 420,
    requestedAt: now(),
    startedAt: now(),
    completedAt: now(),
    createdAt: now(),
    imageWidth: 1280,
    imageHeight: 960,
    qualityStatus: 'ACCEPTABLE',
    applicability: 'VISUAL_DEFECT_SCREENING',
    summary: { detectionCount: 2, classCounts: { CRACK: 1, SPALLING: 1 }, summary: '发现两处疑似表观病害。' },
    structuredResult: {
      requestId: 'mock-request-1',
      providerCode: 'FAST_API',
      modelCode: 'mock-visual-inspection-v1',
      modelVersion: '1.0.0',
      capabilityType: 'VISION_INFERENCE',
      status: 'SUCCEEDED',
      summary: '发现墙体裂缝和饰面脱落候选区域，需人工核验。',
      detections: [],
      riskSignals: [
        {
          code: 'VISIBLE_CRACK',
          level: 'MEDIUM',
          description: '墙面存在一处疑似裂缝。',
          confidence: 0.82,
        },
      ],
      recommendations: ['补拍带标尺近景照片', '由专业人员核验裂缝位置和宽度'],
      confidence: 0.82,
      warnings: ['模拟结果仅用于业务链路验证'],
      rawResponseReference: 'fast-api:mock-request-1',
      durationMs: 420,
    },
    rawResponseReference: 'fast-api:mock-request-1',
    warnings: ['MOCK_RESULT_REQUIRES_REVIEW'],
    detections: [
      {
        sequence: 1,
        classCode: 'CRACK',
        className: '墙体裂缝',
        confidence: 0.82,
        boundingBox: { x: 0.18, y: 0.22, width: 0.28, height: 0.12, coordinateType: 'NORMALIZED_XYWH' },
      },
      {
        sequence: 2,
        classCode: 'SPALLING',
        className: '饰面脱落',
        confidence: 0.76,
        boundingBox: { x: 0.56, y: 0.48, width: 0.2, height: 0.18, coordinateType: 'NORMALIZED_XYWH' },
      },
    ],
    fallbackUsed: false,
    fallbackProviderCode: null,
    fallbackReason: null,
    latestReview: null,
    resultAvailable: true,
    detectionCount: 2,
    assessmentEligibility: 'REVIEW_REQUIRED',
    eligibleForFormalAssessment: false,
    evidenceReliability: 'SIMULATED',
    assessmentNote: 'MOCK 推理结果仅用于演示，需人工复核后才能作为筛查辅助证据。',
    disclaimer,
  },
  {
    inferenceId: '90000000-0000-0000-0000-000000000002',
    requestCode: 'AI-DEMO-002',
    status: 'FAILED',
    mode: 'MOCK',
    providerCode: 'DIFY',
    capabilityType: 'WORKFLOW',
    modelId: 'mock-visual-inspection-v1',
    modelName: '表观病害 MOCK 识别模型',
    modelVersion: '1.0.0',
    workflowCode: 'AI-DIFY-WORKFLOW-001',
    workflowVersion: 'image-analysis-v1.0.0',
    license: 'DEMO_ONLY',
    assetId: '80000000-0000-0000-0000-000000000002',
    buildingId: BUILDING_ID,
    communityId: COMMUNITY_ID,
    requestedBy: 'expert',
    attemptNo: 1,
    reviewStatus: 'UNREVIEWED',
    errorCode: 'AI_PROVIDER_NOT_CONFIGURED',
    errorMessage: '人工智能提供者尚未完成配置。',
    requestedAt: now(),
    createdAt: now(),
    fallbackUsed: false,
    resultAvailable: false,
    detectionCount: 0,
    assessmentEligibility: 'EXCLUDED',
    eligibleForFormalAssessment: false,
    evidenceReliability: 'NOT_USABLE',
    assessmentNote: '失败任务不会进入正式评分。',
    disclaimer,
  },
]

function pageOf(items: AiInferenceTask[], page: number, size: number) {
  const start = page * size
  const content = items.slice(start, start + size)
  return {
    content,
    page: {
      page,
      size,
      totalElements: items.length,
      totalPages: Math.ceil(items.length / size),
    },
  }
}

function findTask(inferenceId: string): AiInferenceTask | undefined {
  return tasks.find((task) => task.inferenceId === inferenceId)
}

export const aiInferenceHandlers = [
  http.post('/api/v1/ai-inferences', async ({ request }) => {
    const unauthenticated = requireAuth(request)
    if (unauthenticated) return unauthenticated
    const body = (await request.json().catch(() => ({}))) as {
      assetId?: string
      mode?: AiInferenceTask['mode']
      modelId?: string
      providerCode?: AiInferenceTask['providerCode']
      capabilityType?: AiInferenceTask['capabilityType']
      prompt?: string
    }
    if (!body.assetId) return errorResponse('BAD_REQUEST', '图片资产不能为空。', 400)
    const providerCode = body.providerCode ?? 'FAST_API'
    const capabilityType = body.capabilityType ?? 'VISION_INFERENCE'
    const created: AiInferenceTask = {
      ...tasks[0],
      inferenceId: `90000000-0000-0000-0000-${String(tasks.length + 1).padStart(12, '0')}`,
      requestCode: `AI-DEMO-${String(tasks.length + 1).padStart(3, '0')}`,
      assetId: body.assetId,
      mode: body.mode ?? 'MOCK',
      modelId: body.modelId ?? tasks[0].modelId,
      providerCode,
      capabilityType,
      workflowCode: capabilityType === 'WORKFLOW' ? body.modelId : null,
      workflowVersion: capabilityType === 'WORKFLOW' ? 'image-analysis-v1.0.0' : null,
      structuredResult: {
        ...(tasks[0].structuredResult!),
        providerCode,
        capabilityType,
        modelCode: body.modelId ?? tasks[0].modelId,
        summary: body.prompt || tasks[0].structuredResult!.summary,
        rawResponseReference: `${providerCode.toLowerCase()}:mock-run`,
      },
      rawResponseReference: `${providerCode.toLowerCase()}:mock-run`,
      attemptNo: 1,
      reviewStatus: 'UNREVIEWED',
      latestReview: null,
      fallbackUsed: false,
      fallbackProviderCode: null,
      fallbackReason: null,
      requestedAt: now(),
      startedAt: now(),
      completedAt: now(),
      createdAt: now(),
    }
    tasks.unshift(created)
    return okResponse(created, 201)
  }),

  http.get('/api/v1/ai-inferences', ({ request }) => {
    const unauthenticated = requireAuth(request)
    if (unauthenticated) return unauthenticated
    const scenario = scenarioOf(request)
    if (scenario === 'server-error') return errorResponse('MOCK_AI_QUEUE_ERROR', '模拟复核队列加载失败。', 500)
    const url = new URL(request.url)
    const page = Number(url.searchParams.get('page') ?? '0')
    const size = Number(url.searchParams.get('size') ?? '20')
    const status = url.searchParams.get('status')
    const mode = url.searchParams.get('mode')
    const providerCode = url.searchParams.get('providerCode')
    const capabilityType = url.searchParams.get('capabilityType')
    const filtered = tasks.filter((task) => {
      if (status && task.status !== status) return false
      if (mode && task.mode !== mode) return false
      if (providerCode && task.providerCode !== providerCode) return false
      if (capabilityType && task.capabilityType !== capabilityType) return false
      return true
    })
    return okResponse(pageOf(filtered, Number.isFinite(page) ? page : 0, Number.isFinite(size) && size > 0 ? size : 20))
  }),

  http.get('/api/v1/ai-inferences/:inferenceId', ({ request, params }) => {
    const unauthenticated = requireAuth(request)
    if (unauthenticated) return unauthenticated
    const task = findTask(String(params.inferenceId))
    if (!task) return errorResponse('AI_INFERENCE_NOT_FOUND', '推理任务不存在。', 404)
    return okResponse(task)
  }),

  http.post('/api/v1/ai-inferences/:inferenceId/retry', ({ request, params }) => {
    const unauthenticated = requireAuth(request)
    if (unauthenticated) return unauthenticated
    const task = findTask(String(params.inferenceId))
    if (!task) return errorResponse('AI_INFERENCE_NOT_FOUND', '推理任务不存在。', 404)
    task.status = 'SUCCEEDED'
    task.errorCode = undefined
    task.errorMessage = undefined
    task.attemptNo += 1
    task.completedAt = now()
    task.resultAvailable = true
    task.detectionCount = task.detections?.length ?? task.detectionCount
    task.fallbackUsed = false
    return okResponse(task)
  }),

  http.post('/api/v1/ai-inferences/:inferenceId/review', async ({ request, params }) => {
    const unauthenticated = requireAuth(request)
    if (unauthenticated) return unauthenticated
    const task = findTask(String(params.inferenceId))
    if (!task) return errorResponse('AI_INFERENCE_NOT_FOUND', '推理任务不存在。', 404)
    const body = (await request.json().catch(() => ({}))) as { reviewStatus?: AiInferenceTask['reviewStatus']; comment?: string }
    if (!body.reviewStatus) return errorResponse('BAD_REQUEST', '复核状态不能为空。', 400)
    task.reviewStatus = body.reviewStatus
    task.latestReview = {
      reviewStatus: body.reviewStatus,
      comment: body.comment,
      reviewedBy: 'mock-expert',
      reviewedAt: now(),
    }
    return okResponse({ inferenceId: task.inferenceId, reviewStatus: task.reviewStatus, reviewedAt: task.latestReview.reviewedAt })
  }),
]
