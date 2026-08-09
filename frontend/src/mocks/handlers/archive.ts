import { http } from 'msw'
import { db, type MockCommunityWrite } from '../fixtures/data'
import { errorResponse, okResponse, requireAuth } from './helpers'

interface MockBuildingWrite {
  communityId?: string
  buildingCode?: string
  buildingName?: string
  address?: string
  constructionYear?: number
  floorCount?: number
  hasElevator?: boolean
  hasIllegalModification?: boolean
  hasGroundFloorBusiness?: boolean
  status?: 'ACTIVE' | 'INACTIVE'
}

interface MockLocationWrite {
  longitude?: number
  latitude?: number
  formattedAddress?: string
  provider?: 'AMAP' | 'MANUAL' | 'IMPORT' | 'MOCK'
  coordinateSystem?: 'GCJ02' | 'WGS84' | 'BD09' | 'UNKNOWN'
  matchLevel?: string
  mock?: boolean
  metadata?: Record<string, unknown>
}

function communityRow(community: (typeof db.communities)[number]) {
  const metadata = db.communityMetadata.get(community.communityId)
  return {
    id: community.communityId,
    communityCode: metadata?.communityCode ?? `COM-${community.communityId.slice(0, 8)}`,
    communityName: community.communityName,
    administrativeRegion: metadata?.administrativeRegion ?? '湖南省株洲市',
    address: metadata?.address ?? community.address ?? community.formattedAddress ?? '',
    status: metadata?.status ?? 'ACTIVE',
  }
}

function page<T>(content: T[]) {
  return {
    content,
    page: {
      page: 0,
      size: Math.max(content.length, 20),
      totalElements: content.length,
      totalPages: content.length > 0 ? 1 : 0,
    },
  }
}

function defaultCoordinateSystem(provider: MockLocationWrite['provider']) {
  if (provider === 'AMAP') return 'GCJ02' as const
  return 'UNKNOWN' as const
}

export const archiveHandlers = [
  http.get('/api/v1/communities', ({ request }) => {
    const unauth = requireAuth(request)
    if (unauth) return unauth
    return okResponse(page(db.communities.map(communityRow)))
  }),

  http.get('/api/v1/communities/:communityId', ({ request, params }) => {
    const unauth = requireAuth(request)
    if (unauth) return unauth
    const communityId = params.communityId as string
    const community = db.communities.find((c) => c.communityId === communityId)
    if (!community) return errorResponse('COMMUNITY_NOT_FOUND', '小区不存在。', 404)
    const metadata = db.communityMetadata.get(community.communityId)
    return okResponse({
      id: community.communityId,
      communityCode: metadata?.communityCode ?? `COM-${community.communityId.slice(0, 8)}`,
      communityName: community.communityName,
      administrativeRegion: metadata?.administrativeRegion ?? '湖南省株洲市',
      address: metadata?.address ?? community.address ?? community.formattedAddress ?? '',
      status: metadata?.status ?? 'ACTIVE',
    })
  }),

  http.post('/api/v1/communities', async ({ request }) => {
    const unauth = requireAuth(request)
    if (unauth) return unauth
    const body = (await request.json().catch(() => ({}))) as MockCommunityWrite
    if (!body.communityCode?.trim() || !body.communityName?.trim()) {
      return errorResponse('VALIDATION_ERROR', '小区编码和名称不能为空。', 400)
    }
    const id = crypto.randomUUID()
    db.communities.push({
      communityId: id,
      communityName: body.communityName.trim(),
      address: body.address?.trim() ?? '',
    })
    db.communityMetadata.set(id, { ...body })
    return okResponse({
      id,
      communityCode: body.communityCode.trim(),
      communityName: body.communityName.trim(),
      administrativeRegion: body.administrativeRegion ?? null,
      address: body.address ?? null,
      status: body.status ?? 'ACTIVE',
    }, 201)
  }),

  http.post('/api/v1/buildings', async ({ request }) => {
    const unauth = requireAuth(request)
    if (unauth) return unauth
    const body = (await request.json().catch(() => ({}))) as MockBuildingWrite
    if (!body.communityId || !db.communities.some((item) => item.communityId === body.communityId)) {
      return errorResponse('COMMUNITY_NOT_FOUND', '所属小区不存在。', 404)
    }
    if (!body.buildingCode?.trim()) {
      return errorResponse('VALIDATION_ERROR', '楼栋编码不能为空。', 400)
    }
    const id = crypto.randomUUID()
    const record = {
      id,
      communityId: body.communityId,
      buildingCode: body.buildingCode.trim(),
      buildingName: body.buildingName?.trim() || body.buildingCode.trim(),
      constructionYear: body.constructionYear ?? new Date().getFullYear(),
      floorCount: body.floorCount ?? 1,
      residentCount: 0,
      status: body.status ?? 'ACTIVE' as const,
      createdAt: new Date().toISOString(),
    }
    db.buildings.push(record)
    return okResponse({
      ...record,
      address: body.address ?? null,
      hasElevator: body.hasElevator ?? false,
      hasIllegalModification: body.hasIllegalModification ?? false,
      hasGroundFloorBusiness: body.hasGroundFloorBusiness ?? false,
    }, 201)
  }),

  http.post('/api/v1/map/places/search', async ({ request }) => {
    const unauth = requireAuth(request)
    if (unauth) return unauth
    const body = (await request.json().catch(() => ({}))) as { keyword?: string }
    const keyword = body.keyword?.trim()
    if (!keyword) return errorResponse('MAP_KEYWORD_REQUIRED', '搜索关键词不能为空。', 400)
    return okResponse([
      {
        providerObjectId: `mock-${encodeURIComponent(keyword)}`,
        name: keyword,
        formattedAddress: `湖南省株洲市${keyword}`,
        province: '湖南省',
        city: '株洲市',
        district: '天元区',
        adcode: '430211',
        citycode: '0733',
        longitude: 113.13396,
        latitude: 27.82767,
        provider: 'MOCK',
        coordinateSystem: 'UNKNOWN',
        mock: true,
      },
    ])
  }),

  http.post('/api/v1/map/reverse-geocoding/preview', async ({ request }) => {
    const unauth = requireAuth(request)
    if (unauth) return unauth
    const body = (await request.json().catch(() => ({}))) as MockLocationWrite
    if (typeof body.longitude !== 'number' || typeof body.latitude !== 'number') {
      return errorResponse('MAP_COORDINATE_INVALID', '经纬度不能为空。', 400)
    }
    return okResponse({
      formattedAddress: `Mock 地址 ${body.longitude.toFixed(5)},${body.latitude.toFixed(5)}`,
      province: '湖南省',
      city: '株洲市',
      district: '天元区',
      adcode: '430211',
      citycode: '0733',
      longitude: body.longitude,
      latitude: body.latitude,
      provider: 'MOCK',
      coordinateSystem: 'UNKNOWN',
      nearestPoiId: null,
      nearestPoiName: null,
      mock: true,
    })
  }),

  http.post('/api/v1/map/community-boundary-candidates/preview', async ({ request }) => {
    const unauth = requireAuth(request)
    if (unauth) return unauth
    const body = (await request.json().catch(() => ({}))) as { communityId?: string; communityName?: string }
    if (!body.communityId || !body.communityName?.trim()) {
      return errorResponse('VALIDATION_ERROR', '小区标识和名称不能为空。', 400)
    }
    return okResponse({
      available: true,
      provider: 'AMAP',
      reasonCode: null,
      message: 'Mock 高德候选边界，仅用于人工预览。',
      coordinateSystem: 'GCJ02',
      sourceType: 'AMAP_AOI',
      sourceId: `mock-aoi-${body.communityId}`,
      name: body.communityName.trim(),
      address: `湖南省株洲市${body.communityName.trim()}`,
      geometry: {
        type: 'Polygon',
        coordinates: [[
          [113.128, 27.824],
          [113.139, 27.824],
          [113.139, 27.833],
          [113.128, 27.833],
          [113.128, 27.824],
        ]],
      },
    })
  }),

  http.put('/api/v1/buildings/:buildingId/location', async ({ request, params }) => {
    const unauth = requireAuth(request)
    if (unauth) return unauth
    const buildingId = String(params.buildingId)
    if (!db.buildings.some((item) => item.id === buildingId)) {
      return errorResponse('BUILDING_NOT_FOUND', '楼栋不存在。', 404)
    }
    const body = (await request.json().catch(() => ({}))) as MockLocationWrite
    if (typeof body.longitude !== 'number' || typeof body.latitude !== 'number') {
      return errorResponse('MAP_COORDINATE_INVALID', '经纬度不能为空。', 400)
    }
    const provider = body.provider ?? 'MOCK'
    const stored = {
      buildingId,
      longitude: body.longitude,
      latitude: body.latitude,
      formattedAddress: body.formattedAddress ?? '',
      provider,
      coordinateSystem: body.coordinateSystem ?? defaultCoordinateSystem(provider),
      matchLevel: body.matchLevel ?? 'MOCK_PREVIEW',
      metadata: body.metadata ?? {},
      updatedAt: new Date().toISOString(),
    }
    db.buildingLocations.set(buildingId, stored)
    return okResponse(stored)
  }),

  http.get('/api/v1/buildings/:buildingId/location', ({ request, params }) => {
    const unauth = requireAuth(request)
    if (unauth) return unauth
    const buildingId = String(params.buildingId)
    const stored = db.buildingLocations.get(buildingId)
    if (!stored) return errorResponse('BUILDING_LOCATION_NOT_FOUND', '楼栋尚未保存地图位置。', 404)
    return okResponse(stored)
  }),
]