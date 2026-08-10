import type { DashboardBuilding } from '@/shared/api/endpoints/reports'
import type { SpatialBboxQuery } from '@/shared/api/endpoints/spatial'

export type RiskStatusTone = 'low' | 'medium' | 'high' | 'very-high' | 'unknown'
export type PriorityStatusTone = 'p1' | 'p2' | 'p3' | 'p4' | 'unknown'
export type FreshnessTone = 'current' | 'stale' | 'no-result'

export interface ProjectedStatusMarker {
  building: DashboardBuilding
  x: number
  y: number
  riskTone: RiskStatusTone
  priorityTone: PriorityStatusTone
  freshnessTone: FreshnessTone
}

export function riskStatusTone(level?: string): RiskStatusTone {
  if (level === 'LOW') return 'low'
  if (level === 'MEDIUM') return 'medium'
  if (level === 'HIGH') return 'high'
  if (level === 'VERY_HIGH') return 'very-high'
  return 'unknown'
}

export function priorityStatusTone(level?: string): PriorityStatusTone {
  if (level === 'P1') return 'p1'
  if (level === 'P2') return 'p2'
  if (level === 'P3') return 'p3'
  if (level === 'P4') return 'p4'
  return 'unknown'
}

export function freshnessTone(freshness?: string): FreshnessTone {
  if (freshness === 'STALE') return 'stale'
  if (freshness === 'NO_RESULT') return 'no-result'
  return 'current'
}

function mercatorY(latitude: number): number {
  const bounded = Math.max(-85, Math.min(85, latitude)) * Math.PI / 180
  return Math.log(Math.tan(Math.PI / 4 + bounded / 2))
}

export function projectStatusMarker(
  longitude: number,
  latitude: number,
  viewport: SpatialBboxQuery,
  width: number,
  height: number,
): { x: number; y: number } | null {
  if (
    !Number.isFinite(longitude)
    || !Number.isFinite(latitude)
    || !Number.isFinite(width)
    || !Number.isFinite(height)
    || width <= 0
    || height <= 0
    || viewport.east <= viewport.west
    || viewport.north <= viewport.south
    || longitude < viewport.west
    || longitude > viewport.east
    || latitude < viewport.south
    || latitude > viewport.north
  ) return null

  const x = ((longitude - viewport.west) / (viewport.east - viewport.west)) * width
  const northY = mercatorY(viewport.north)
  const southY = mercatorY(viewport.south)
  const currentY = mercatorY(latitude)
  const denominator = northY - southY
  if (!Number.isFinite(denominator) || denominator <= 0) return null
  const y = ((northY - currentY) / denominator) * height
  return Number.isFinite(x) && Number.isFinite(y) ? { x, y } : null
}

export function projectDashboardStatusMarkers(
  buildings: readonly DashboardBuilding[],
  viewport: SpatialBboxQuery | null,
  width: number,
  height: number,
): ProjectedStatusMarker[] {
  if (!viewport || viewport.zoom >= 17) return []
  return buildings.flatMap((building) => {
    const longitude = Number(building.longitude)
    const latitude = Number(building.latitude)
    if (
      building.longitude === null
      || building.longitude === undefined
      || building.latitude === null
      || building.latitude === undefined
      || !Number.isFinite(longitude)
      || !Number.isFinite(latitude)
    ) return []
    const projected = projectStatusMarker(longitude, latitude, viewport, width, height)
    if (!projected) return []
    return [{
      building,
      ...projected,
      riskTone: riskStatusTone(building.riskLevel),
      priorityTone: priorityStatusTone(building.priorityLevel),
      freshnessTone: freshnessTone(building.freshness),
    }]
  })
}

export function riskStatusLabel(level?: string): string {
  return ({ LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险', VERY_HIGH: '极高风险' } as Record<string, string>)[level ?? ''] ?? '待评估'
}

export function priorityStatusLabel(level?: string): string {
  return ({ P1: 'P1 优先', P2: 'P2 优先', P3: 'P3 优先', P4: 'P4 优先' } as Record<string, string>)[level ?? ''] ?? '暂无优先级'
}
