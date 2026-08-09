import { http } from 'msw'
import { okResponse, errorResponse, requireAuth } from './helpers'
import { BUILDING_ID, COMMUNITY_ID } from '../fixtures/data'

const now = (): string => new Date().toISOString()
const disclaimer = '系统结果仅用于风险筛查与辅助决策，不作为正式房屋安全鉴定结论。'

const reportRows = [
  {
    reportId: '61000000-0000-0000-0000-000000000001',
    reportCode: 'RR-20260714-0001',
    buildingId: BUILDING_ID,
    buildingCode: 'B-001',
    buildingName: '1号楼',
    communityId: COMMUNITY_ID,
    communityName: '示范小区',
    reportStatus: 'GENERATED' as const,
    reportFormat: 'PDF' as const,
    templateVersion: 'RISK-V1.0',
    sourceChecksum: 'mock-source-checksum',
    riskLevel: 'HIGH',
    priorityLevel: 'P1',
    generatedAt: now(),
    createdAt: now(),
  },
]

export const reportHandlers = [
  http.get('/api/v1/risk-reports', ({ request }) => {
    const unauth = requireAuth(request)
    if (unauth) return unauth
    const url = new URL(request.url)
    const buildingId = url.searchParams.get('buildingId')
    const page = Number(url.searchParams.get('page') ?? '0')
    const size = Number(url.searchParams.get('size') ?? '20')
    const filtered = buildingId
      ? reportRows.filter((r) => r.buildingId === buildingId)
      : reportRows
    const safePage = Number.isFinite(page) ? page : 0
    const safeSize = Number.isFinite(size) && size > 0 ? size : 20
    return okResponse({
      content: filtered.slice(safePage * safeSize, safePage * safeSize + safeSize),
      page: {
        page: safePage,
        size: safeSize,
        totalElements: filtered.length,
        totalPages: Math.ceil(filtered.length / safeSize),
      },
    })
  }),
]
