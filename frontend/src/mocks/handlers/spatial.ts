import { http } from 'msw'
import { errorResponse, okResponse, requireAuth } from './helpers'
import { BUILDING_ID, COMMUNITY_ID } from '../fixtures/data'
import type {
  SpatialBoundaryReviewInput,
  SpatialBoundaryView,
  SpatialBoundaryWriteInput,
} from '@/shared/api/endpoints/spatial'

type EntityType = 'COMMUNITY' | 'BUILDING'

const boundaries = new Map<string, SpatialBoundaryView>()

function key(entityType: EntityType, entityId: string): string {
  return `${entityType}:${entityId}`
}

function getBoundary(entityType: EntityType, entityId: string) {
  return boundaries.get(key(entityType, entityId))
}

function writeBoundary(entityType: EntityType, entityId: string, input: SpatialBoundaryWriteInput) {
  const current = getBoundary(entityType, entityId)
  const expectedVersion = current?.version ?? 0
  if (input.expectedVersion !== expectedVersion) {
    return errorResponse('SPATIAL_BOUNDARY_VERSION_CONFLICT', '边界已被其他用户修改。', 409)
  }
  const now = new Date().toISOString()
  const next: SpatialBoundaryView = {
    id: current?.id ?? crypto.randomUUID(),
    entityType,
    entityId,
    sourceType: input.sourceType,
    sourceProvider: input.sourceProvider ?? null,
    sourceObjectId: input.sourceObjectId ?? null,
    sourceCoordinateSystem: input.sourceCoordinateSystem,
    sourceGeometry: input.sourceGeometry,
    displayCoordinateSystem: input.displayCoordinateSystem,
    displayGeometry: input.displayGeometry,
    status: 'UNVERIFIED',
    version: expectedVersion + 1,
    verifiedBy: null,
    verifiedAt: null,
    remark: input.remark ?? null,
    createdAt: current?.createdAt ?? now,
    updatedAt: now,
  }
  boundaries.set(key(entityType, entityId), next)
  return okResponse(next)
}

function reviewBoundary(entityType: EntityType, entityId: string, input: SpatialBoundaryReviewInput, action: 'VERIFY' | 'REJECT') {
  const current = getBoundary(entityType, entityId)
  if (!current) return errorResponse('SPATIAL_BOUNDARY_NOT_FOUND', '空间边界不存在。', 404)
  if (input.expectedVersion !== current.version) {
    return errorResponse('SPATIAL_BOUNDARY_VERSION_CONFLICT', '边界已被其他用户修改。', 409)
  }
  const now = new Date().toISOString()
  const next: SpatialBoundaryView = {
    ...current,
    status: action === 'VERIFY' ? 'VERIFIED' : 'REJECTED',
    version: current.version + 1,
    verifiedBy: action === 'VERIFY' ? 'mock-admin' : null,
    verifiedAt: action === 'VERIFY' ? now : null,
    remark: input.remark ?? current.remark ?? null,
    updatedAt: now,
  }
  boundaries.set(key(entityType, entityId), next)
  return okResponse(next)
}

/** 为空间地图视口查询提供演示 GeoJSON 数据 */
function makeCommunityFeature(id: string, name: string, lng: number, lat: number) {
  const offset = 0.005
  return {
    type: 'Feature' as const,
    id,
    geometry: {
      type: 'Polygon' as const,
      coordinates: [[
        [lng - offset, lat - offset],
        [lng + offset, lat - offset],
        [lng + offset, lat + offset],
        [lng - offset, lat + offset],
        [lng - offset, lat - offset],
      ]],
    },
    properties: {
      entityType: 'COMMUNITY' as const,
      entityId: id,
      entityCode: `COM-${id.slice(0, 8)}`,
      name,
      communityId: null,
      status: 'VERIFIED' as const,
      version: 1,
      coordinateSystem: 'GCJ02' as const,
      sourceType: 'AMAP_AOI' as const,
    },
  }
}

function makeBuildingFeature(id: string, code: string, name: string, communityId: string, lng: number, lat: number) {
  const offset = 0.003
  return {
    type: 'Feature' as const,
    id,
    geometry: {
      type: 'Polygon' as const,
      coordinates: [[
        [lng - offset, lat - offset],
        [lng + offset, lat - offset],
        [lng + offset, lat + offset],
        [lng - offset, lat + offset],
        [lng - offset, lat - offset],
      ]],
    },
    properties: {
      entityType: 'BUILDING' as const,
      entityId: id,
      entityCode: code,
      name,
      communityId,
      status: 'VERIFIED' as const,
      version: 1,
      coordinateSystem: 'GCJ02' as const,
      sourceType: 'AMAP_AOI' as const,
    },
  }
}

const spatialCommunities = [
  makeCommunityFeature(COMMUNITY_ID, '示范小区', 113.13396, 27.82767),
  makeCommunityFeature('55555555-5555-5555-5555-555555555555', '安居小区', 113.145, 27.835),
]

const spatialBuildings = [
  makeBuildingFeature(BUILDING_ID, 'B-001', '1号楼', COMMUNITY_ID, 113.131, 27.826),
  makeBuildingFeature('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'B-002', '2号楼', COMMUNITY_ID, 113.134, 27.827),
  makeBuildingFeature('cccccccc-cccc-cccc-cccc-cccccccccccc', 'B-003', '3号楼', COMMUNITY_ID, 113.136, 27.828),
  makeBuildingFeature('dddddddd-dddd-dddd-dddd-dddddddddddd', 'B-004', '4号楼', '55555555-5555-5555-5555-555555555555', 113.143, 27.834),
]

function handlersFor(entityType: EntityType, path: 'communities' | 'buildings') {
  return [
    http.get(`/api/v1/spatial/${path}/:entityId/boundary`, ({ request, params }) => {
      const unauth = requireAuth(request)
      if (unauth) return unauth
      const current = getBoundary(entityType, String(params.entityId))
      return current ? okResponse(current) : errorResponse('SPATIAL_BOUNDARY_NOT_FOUND', '空间边界不存在。', 404)
    }),
    http.put(`/api/v1/spatial/${path}/:entityId/boundary`, async ({ request, params }) => {
      const unauth = requireAuth(request)
      if (unauth) return unauth
      const input = (await request.json()) as SpatialBoundaryWriteInput
      return writeBoundary(entityType, String(params.entityId), input)
    }),
    http.post(`/api/v1/spatial/${path}/:entityId/boundary/verify`, async ({ request, params }) => {
      const unauth = requireAuth(request)
      if (unauth) return unauth
      const input = (await request.json()) as SpatialBoundaryReviewInput
      return reviewBoundary(entityType, String(params.entityId), input, 'VERIFY')
    }),
    http.post(`/api/v1/spatial/${path}/:entityId/boundary/reject`, async ({ request, params }) => {
      const unauth = requireAuth(request)
      if (unauth) return unauth
      const input = (await request.json()) as SpatialBoundaryReviewInput
      return reviewBoundary(entityType, String(params.entityId), input, 'REJECT')
    }),
  ]
}

export const spatialHandlers = [
  ...handlersFor('COMMUNITY', 'communities'),
  ...handlersFor('BUILDING', 'buildings'),

  // 空间地图视口查询：返回 GeoJSON FeatureCollection
  http.get('/api/v1/spatial/communities', ({ request }) => {
    const unauth = requireAuth(request)
    if (unauth) return unauth
    return okResponse({ type: 'FeatureCollection', features: spatialCommunities })
  }),

  http.get('/api/v1/spatial/buildings', ({ request }) => {
    const unauth = requireAuth(request)
    if (unauth) return unauth
    return okResponse({ type: 'FeatureCollection', features: spatialBuildings })
  }),

  // 风险地图仪表盘数据
  http.get('/api/v1/dashboard/risk-map', ({ request }) => {
    const unauth = requireAuth(request)
    if (unauth) return unauth
    const now = new Date().toISOString()
    return okResponse({
      scopeKey: 'ALL',
      generatedAt: now,
      buildings: [
        {
          buildingId: BUILDING_ID,
          buildingCode: 'B-001',
          buildingName: '1号楼',
          communityId: COMMUNITY_ID,
          communityName: '示范小区',
          longitude: 113.131,
          latitude: 27.826,
          riskScore: 78.2,
          riskLevel: 'HIGH',
          confidenceScore: 72.4,
          completenessScore: 84.1,
          priorityScore: 86.5,
          priorityLevel: 'P1',
          ranking: 1,
          freshness: 'CURRENT' as const,
          needManualReview: true,
        },
        {
          buildingId: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
          buildingCode: 'B-002',
          buildingName: '2号楼',
          communityId: COMMUNITY_ID,
          communityName: '示范小区',
          longitude: 113.134,
          latitude: 27.827,
          riskScore: 45.5,
          riskLevel: 'MEDIUM',
          confidenceScore: 68.0,
          completenessScore: 76.2,
          priorityScore: 61.8,
          priorityLevel: 'P3',
          ranking: 2,
          freshness: 'CURRENT' as const,
          needManualReview: false,
        },
        {
          buildingId: 'cccccccc-cccc-cccc-cccc-cccccccccccc',
          buildingCode: 'B-003',
          buildingName: '3号楼',
          communityId: COMMUNITY_ID,
          communityName: '示范小区',
          longitude: 113.136,
          latitude: 27.828,
          riskScore: 92.1,
          riskLevel: 'VERY_HIGH',
          confidenceScore: 85.3,
          completenessScore: 62.4,
          priorityScore: 94.2,
          priorityLevel: 'P1',
          ranking: 1,
          freshness: 'CURRENT' as const,
          needManualReview: true,
        },
        {
          buildingId: 'dddddddd-dddd-dddd-dddd-dddddddddddd',
          buildingCode: 'B-004',
          buildingName: '4号楼',
          communityId: '55555555-5555-5555-5555-555555555555',
          communityName: '安居小区',
          longitude: 113.143,
          latitude: 27.834,
          riskScore: 25.3,
          riskLevel: 'LOW',
          confidenceScore: 55.0,
          completenessScore: 90.1,
          priorityScore: 12.4,
          priorityLevel: 'P5',
          ranking: 4,
          freshness: 'CURRENT' as const,
          needManualReview: false,
        },
      ],
      disclaimer: '系统结果仅用于风险筛查与辅助决策，不作为正式房屋安全鉴定结论。',
    })
  }),
]
