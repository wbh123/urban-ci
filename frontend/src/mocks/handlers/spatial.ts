import { http } from 'msw'
import { errorResponse, okResponse, requireAuth } from './helpers'
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
]
