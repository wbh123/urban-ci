import { describe, expect, it } from 'vitest'
import { routes } from './routes'
import spatialMapSource from '@/pages/console/ConsoleSpatialMapPage.vue?raw'

function consoleChildren() {
  return routes.find((route) => route.path === '/console')?.children || []
}

describe('R4-3 unified building detail route', () => {
  it('registers a dedicated building detail page for all console read roles', () => {
    const detail = consoleChildren().find((route) => route.path === 'buildings/:buildingId')

    expect(detail?.name).toBe('console-building-detail')
    expect(detail?.meta?.allowedRoles).toEqual([
      'EXPERT',
      'PROFESSIONAL_REVIEWER',
      'COMMUNITY_MANAGER',
      'GOVERNMENT_MANAGER',
      'ADMIN',
    ])
  })

  it('keeps the legacy assessment url as a compatibility redirect to the unified detail assessment tab', () => {
    const legacy = consoleChildren().find((route) => route.path === 'buildings/:buildingId/assessment')

    expect(typeof legacy?.redirect).toBe('function')
    const redirect = legacy?.redirect as ((to: { params: Record<string, string> }) => unknown)
    expect(redirect({ params: { buildingId: 'b-1' } })).toEqual({
      name: 'console-building-detail',
      params: { buildingId: 'b-1' },
      query: { tab: 'assessment' },
    })
  })

  it('opens the unified building detail from the spatial map instead of the legacy assessment-only page', () => {
    expect(spatialMapSource).toContain("router.push(`/console/buildings/${selectedProjection.feature.id}`)")
    expect(spatialMapSource).not.toContain("router.push(`/console/buildings/${selectedProjection.feature.id}/assessment`)")
  })
})
