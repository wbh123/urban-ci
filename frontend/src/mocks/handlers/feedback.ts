import { HttpResponse, http } from 'msw'
import { db } from '../fixtures/data'
import { errorResponse, okResponse, requireAuth } from './helpers'
import type {
  CreateFeedbackPayload,
  FeedbackChannel,
  FeedbackImage,
  FeedbackStatus,
  UpdateFeedbackStatusPayload,
} from '@/shared/api'

type MockFeedbackImage = FeedbackImage & { file: File }

type MockFeedbackReport = Omit<CreateFeedbackPayload, 'feedbackChannel'> & {
  reportId: string
  reportCode: string
  trackingSecret: string
  status: FeedbackStatus
  feedbackChannel: FeedbackChannel
  submittedAt: string
  communityName: string
  buildingName?: string
  handlingSummary?: string
  images: MockFeedbackImage[]
  events: Array<{
    eventType: string
    fromStatus?: FeedbackStatus
    toStatus?: FeedbackStatus
    message: string
    createdAt: string
  }>
}

const MAX_IMAGE_COUNT = 6
const MAX_IMAGE_SIZE = 10 * 1024 * 1024
const SUPPORTED_IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp'])
const reports: MockFeedbackReport[] = []

function createReport(payload: CreateFeedbackPayload, channel: FeedbackChannel): MockFeedbackReport {
  const community = db.communities.find((item) => item.communityId === payload.communityId)
  const building = db.buildings.find((item) => item.id === payload.buildingId)
  const now = new Date().toISOString()
  const reportCode = `FB-${now.slice(0, 10).replaceAll('-', '')}-${Math.random().toString(36).slice(2, 10).toUpperCase()}`
  return {
    ...payload,
    reportId: crypto.randomUUID(),
    reportCode,
    trackingSecret: Math.random().toString(36).slice(2, 14),
    status: 'SUBMITTED',
    feedbackChannel: channel,
    submittedAt: now,
    communityName: community?.communityName || '未知小区',
    buildingName: building?.buildingName,
    images: [],
    events: [
      {
        eventType: 'CREATED',
        toStatus: 'SUBMITTED',
        message: '反馈已提交，等待工作人员受理。',
        createdAt: now,
      },
    ],
  }
}

function seedReport(
  reportId: string,
  reportCode: string,
  status: FeedbackStatus,
  payload: CreateFeedbackPayload,
  handlingSummary: string,
): MockFeedbackReport {
  const report = createReport(payload, 'INTERNAL')
  return {
    ...report,
    reportId,
    reportCode,
    trackingSecret: `mock-${reportCode.toLowerCase()}`,
    status,
    submittedAt: '2026-08-17T06:00:00.000Z',
    handlingSummary,
    events: [
      {
        eventType: 'CREATED',
        toStatus: 'SUBMITTED',
        message: '反馈已提交，等待工作人员受理。',
        createdAt: '2026-08-17T05:30:00.000Z',
      },
      {
        eventType: 'STATUS_CHANGED',
        fromStatus: 'ACCEPTED',
        toStatus: status,
        message: handlingSummary,
        createdAt: '2026-08-17T06:00:00.000Z',
      },
    ],
  }
}

reports.push(
  seedReport(
    '77777777-7777-7777-7777-777777777701',
    'FB-MOCK-PROCESSING',
    'PROCESSING',
    {
      communityId: '11111111-1111-1111-1111-111111111111',
      buildingId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
      reportType: 'OTHER',
      description: '地下车库导向标识松动，已完成重新固定',
      urgency: 'NORMAL',
      contactConsent: false,
      locationText: '1号楼地下车库入口',
    },
    '已重新固定导向标识并清理周边松动物。',
  ),
  seedReport(
    '77777777-7777-7777-7777-777777777702',
    'FB-MOCK-RESOLVED',
    'RESOLVED',
    {
      communityId: '11111111-1111-1111-1111-111111111111',
      buildingId: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
      reportType: 'WALL_CRACK',
      description: '外墙局部开裂，已完成修补并等待现场复验',
      urgency: 'HIGH',
      contactConsent: false,
      locationText: '2号楼东侧外墙',
    },
    '已完成裂缝修补并留存整改记录，等待现场复验。',
  ),
)

function imageMetadata(image: MockFeedbackImage): FeedbackImage {
  return {
    assetId: image.assetId,
    originalFilename: image.originalFilename,
    contentType: image.contentType,
    fileSize: image.fileSize,
    createdAt: image.createdAt,
  }
}

function createdPayload(report: MockFeedbackReport) {
  return {
    reportId: report.reportId,
    reportCode: report.reportCode,
    trackingSecret: report.trackingSecret,
    status: report.status,
    feedbackChannel: report.feedbackChannel,
    submittedAt: report.submittedAt,
    maxImageCount: MAX_IMAGE_COUNT,
    disclaimer: '反馈内容将作为巡检和治理线索，不代表正式房屋安全鉴定结论。',
  }
}

function publicPayload(report: MockFeedbackReport) {
  return {
    reportId: report.reportId,
    reportCode: report.reportCode,
    reportType: report.reportType,
    description: report.description,
    status: report.status,
    urgency: report.urgency,
    feedbackChannel: report.feedbackChannel,
    reporterName: report.reporterName,
    contactPhone: report.contactPhone
      ? `${report.contactPhone.slice(0, 3)}****${report.contactPhone.slice(-4)}`
      : undefined,
    contactEmail: report.contactEmail && report.contactEmail.includes('@')
      ? `${report.contactEmail.slice(0, 1)}***${report.contactEmail.slice(report.contactEmail.indexOf('@'))}`
      : report.contactEmail,
    locationText: report.locationText,
    handlingSummary: report.handlingSummary,
    submittedAt: report.submittedAt,
    communityName: report.communityName,
    buildingName: report.buildingName,
    events: report.events,
    images: report.images.map(imageMetadata),
    imageCount: report.images.length,
    maxImageCount: MAX_IMAGE_COUNT,
    disclaimer: '反馈内容将作为巡检和治理线索，不代表正式房屋安全鉴定结论。',
  }
}

function managementPayload(report: MockFeedbackReport) {
  return {
    reportId: report.reportId,
    reportCode: report.reportCode,
    reportType: report.reportType,
    description: report.description,
    status: report.status,
    urgency: report.urgency,
    feedbackChannel: report.feedbackChannel,
    reporterName: report.reporterName,
    contactPhone: report.contactPhone,
    contactEmail: report.contactEmail,
    locationText: report.locationText,
    handlingSummary: report.handlingSummary,
    submittedAt: report.submittedAt,
    communityId: report.communityId,
    buildingId: report.buildingId,
    communityName: report.communityName,
    buildingName: report.buildingName,
    imageCount: report.images.length,
  }
}

function recommendationPayload(report: MockFeedbackReport) {
  const requiredTypes = new Set(['WALL_CRACK', 'SURFACE_FALLING', 'ILLEGAL_MODIFICATION', 'FIRE_ACCESS'])
  const reasons: string[] = []
  if (report.urgency === 'HIGH' || report.urgency === 'URGENT') {
    reasons.push(`紧急程度为 ${report.urgency}，建议通过现场复检确认整改效果`)
  }
  if (requiredTypes.has(report.reportType)) {
    reasons.push(`问题类型 ${report.reportType} 涉及结构、坠落、违规改造或消防等现场核实事项`)
  }
  const recommendedDecision = reasons.length ? 'REQUIRED' : 'WAIVED'
  if (!reasons.length) {
    reasons.push('当前结构化信息未发现高紧急程度、重点问题类型或明显现场风险信号，可由人工结合整改证据判断是否免复检')
  }
  return {
    reportId: report.reportId,
    reportCode: report.reportCode,
    recommendedDecision,
    reasons,
    source: 'STRUCTURED_RULES',
    disclaimer: '系统复检建议仅用于辅助决策，不替代现场人员和管理人员判断；最终决策及人工覆盖理由将留痕。',
    formalRiskChanged: false,
  }
}

function findPublicReport(reportCode: string, trackingSecret: string | null): MockFeedbackReport | undefined {
  return reports.find(
    (item) => item.reportCode === reportCode && item.trackingSecret === trackingSecret,
  )
}

export const feedbackHandlers = [
  http.get('/api/v1/public/feedback/communities', () =>
    okResponse(
      db.communities.map((item) => ({
        communityId: item.communityId,
        communityName: item.communityName,
        address: item.address,
      })),
    ),
  ),

  http.get('/api/v1/public/feedback/communities/:communityId/buildings', ({ params }) => {
    const communityId = String(params.communityId)
    return okResponse(
      db.buildings
        .filter((item) => item.communityId === communityId)
        .map((item) => ({
          buildingId: item.id,
          buildingCode: item.buildingCode,
          buildingName: item.buildingName,
        })),
    )
  }),

  http.post('/api/v1/public/feedback/reports', async ({ request }) => {
    const payload = (await request.json()) as CreateFeedbackPayload
    if (!payload.communityId || !payload.description || payload.description.length < 10) {
      return errorResponse('FEEDBACK_FIELD_INVALID', '请完整填写小区和问题描述。', 400)
    }
    const report = createReport(payload, 'WEB')
    reports.unshift(report)
    return okResponse(createdPayload(report), 201)
  }),

  http.post('/api/v1/public/feedback/reports/:reportCode/images', async ({ request, params }) => {
    const formData = await request.formData()
    const secret = String(formData.get('trackingSecret') || '')
    const file = formData.get('file')
    const report = findPublicReport(String(params.reportCode), secret)
    if (!report) return errorResponse('FEEDBACK_TRACKING_NOT_FOUND', '工单编号或查询凭证不正确。', 404)
    if (!(file instanceof File)) return errorResponse('ASSET_FILE_REQUIRED', '请选择图片。', 400)
    if (report.images.length >= MAX_IMAGE_COUNT) {
      return errorResponse('FEEDBACK_IMAGE_LIMIT_REACHED', '每个反馈最多上传 6 张图片。', 409)
    }
    if (!SUPPORTED_IMAGE_TYPES.has(file.type)) {
      return errorResponse('ASSET_TYPE_UNSUPPORTED', '仅支持 JPEG、PNG、WebP。', 400)
    }
    if (file.size > MAX_IMAGE_SIZE) {
      return errorResponse('ASSET_TOO_LARGE', '图片不能超过 10MB。', 400)
    }
    const image: MockFeedbackImage = {
      assetId: crypto.randomUUID(),
      originalFilename: file.name,
      contentType: file.type as FeedbackImage['contentType'],
      fileSize: file.size,
      createdAt: new Date().toISOString(),
      file,
    }
    report.images.push(image)
    return okResponse({
      ...imageMetadata(image),
      imageCount: report.images.length,
      remainingSlots: MAX_IMAGE_COUNT - report.images.length,
    }, 201)
  }),

  http.get('/api/v1/public/feedback/reports/:reportCode/images', ({ request, params }) => {
    const secret = new URL(request.url).searchParams.get('trackingSecret')
    const report = findPublicReport(String(params.reportCode), secret)
    if (!report) return errorResponse('FEEDBACK_TRACKING_NOT_FOUND', '工单编号或查询凭证不正确。', 404)
    return okResponse(report.images.map(imageMetadata))
  }),

  http.get('/api/v1/public/feedback/reports/:reportCode/images/:assetId/content', ({ request, params }) => {
    const secret = new URL(request.url).searchParams.get('trackingSecret')
    const report = findPublicReport(String(params.reportCode), secret)
    if (!report) return errorResponse('FEEDBACK_TRACKING_NOT_FOUND', '工单编号或查询凭证不正确。', 404)
    const image = report.images.find((item) => item.assetId === String(params.assetId))
    if (!image) return errorResponse('FEEDBACK_IMAGE_NOT_FOUND', '图片不存在或不属于当前反馈。', 404)
    return new HttpResponse(image.file, {
      status: 200,
      headers: {
        'Content-Type': image.contentType,
        'Cache-Control': 'no-store',
      },
    })
  }),

  http.get('/api/v1/public/feedback/reports/:reportCode', ({ request, params }) => {
    const secret = new URL(request.url).searchParams.get('trackingSecret')
    const report = findPublicReport(String(params.reportCode), secret)
    if (!report) return errorResponse('FEEDBACK_TRACKING_NOT_FOUND', '工单编号或查询凭证不正确。', 404)
    return okResponse(publicPayload(report))
  }),

  http.get('/api/v1/feedback/reports', ({ request }) => {
    const unauth = requireAuth(request)
    if (unauth) return unauth
    const url = new URL(request.url)
    const status = url.searchParams.get('status')
    const channel = url.searchParams.get('feedbackChannel')
    const page = Number(url.searchParams.get('page') || 0)
    const size = Number(url.searchParams.get('size') || 20)
    const filtered = reports.filter(
      (item) => (!status || item.status === status) && (!channel || item.feedbackChannel === channel),
    )
    return okResponse({
      content: filtered.slice(page * size, page * size + size).map(managementPayload),
      page: {
        page,
        size,
        totalElements: filtered.length,
        totalPages: Math.ceil(filtered.length / size),
      },
    })
  }),

  http.get('/api/v1/feedback/reports/:reportId/images', ({ request, params }) => {
    const unauth = requireAuth(request)
    if (unauth) return unauth
    const report = reports.find((item) => item.reportId === String(params.reportId))
    if (!report) return errorResponse('FEEDBACK_REPORT_NOT_FOUND', '反馈工单不存在。', 404)
    return okResponse(report.images.map(imageMetadata))
  }),

  http.get('/api/v1/feedback/reports/:reportId/reinspection/recommendation', ({ request, params }) => {
    const unauth = requireAuth(request)
    if (unauth) return unauth
    const report = reports.find((item) => item.reportId === String(params.reportId))
    if (!report) return errorResponse('FEEDBACK_REPORT_NOT_FOUND', '反馈工单不存在。', 404)
    return okResponse(recommendationPayload(report))
  }),

  http.post('/api/v1/feedback/reports/manual', async ({ request }) => {
    const unauth = requireAuth(request)
    if (unauth) return unauth
    const payload = (await request.json()) as CreateFeedbackPayload
    const report = createReport(payload, payload.feedbackChannel || 'PHONE')
    reports.unshift(report)
    return okResponse(createdPayload(report), 201)
  }),

  http.post('/api/v1/feedback/reports/:reportId/status', async ({ request, params }) => {
    const unauth = requireAuth(request)
    if (unauth) return unauth
    const reportId = String(params.reportId)
    const report = reports.find((item) => item.reportId === reportId)
    if (!report) return errorResponse('FEEDBACK_REPORT_NOT_FOUND', '反馈工单不存在。', 404)
    const payload = (await request.json()) as UpdateFeedbackStatusPayload
    const fromStatus = report.status
    report.status = payload.status
    report.handlingSummary = payload.handlingSummary
    report.events.push({
      eventType: 'STATUS_CHANGED',
      fromStatus,
      toStatus: payload.status,
      message: payload.message || '反馈状态已更新。',
      createdAt: new Date().toISOString(),
    })
    return okResponse({ reportId: report.reportId, fromStatus, status: report.status })
  }),
]
