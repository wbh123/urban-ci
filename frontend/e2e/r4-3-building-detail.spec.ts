import { expect, test } from '@playwright/test'

const buildingId = 'b-1'
const communityId = 'c-1'

function json(body: unknown) {
  return {
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(body),
  }
}

test('地图楼栋 Polygon → 业务 Drawer → 统一楼栋详情', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('urban-safe-token', 'e2e-token')
    localStorage.setItem('urban-safe-user', JSON.stringify({
      id: 'admin-1',
      username: 'admin',
      realName: '测试管理员',
      roles: ['ADMIN'],
      permissions: [],
    }))

    const browserWindow = window as typeof window & {
      AMap?: unknown
      __e2ePolygonClicks?: Array<() => void>
    }
    browserWindow.__e2ePolygonClicks = []

    class FakePolygon {
      on(event: string, handler: () => void) {
        if (event === 'click') browserWindow.__e2ePolygonClicks?.push(handler)
      }
      setOptions() {}
      setMap() {}
    }

    class FakeMap {
      on(event: string, handler: () => void) {
        if (event === 'complete') setTimeout(handler, 0)
      }
      add() {}
      getZoom() { return 16 }
      getBounds() {
        return {
          getSouthWest: () => ({ getLng: () => 113, getLat: () => 27 }),
          getNorthEast: () => ({ getLng: () => 114, getLat: () => 28 }),
        }
      }
      destroy() {}
    }

    browserWindow.AMap = {
      Map: FakeMap,
      Polygon: FakePolygon,
      plugin: (_plugins: string[], done: () => void) => done(),
    }
  })

  await page.route('**/api/v1/**', async (route) => {
    const requestUrl = new URL(route.request().url())
    const path = requestUrl.pathname

    if (path === '/api/v1/map/runtime-config') {
      await route.fulfill(json({
        enabled: true,
        mode: 'LIVE',
        jsApiKey: 'e2e-key',
        serviceHost: '/_AMapService',
        defaultZoom: 16,
        defaultCenter: { longitude: 113.5, latitude: 27.5 },
      }))
      return
    }

    if (path === '/api/v1/spatial/communities') {
      await route.fulfill(json({
        type: 'FeatureCollection',
        features: [{
          type: 'Feature',
          id: communityId,
          geometry: {
            type: 'Polygon',
            coordinates: [[[113, 27], [114, 27], [114, 28], [113, 27]]],
          },
          properties: {
            entityType: 'COMMUNITY',
            entityId: communityId,
            entityCode: 'C-001',
            name: '示范小区',
            status: 'VERIFIED',
            version: 1,
            coordinateSystem: 'GCJ02',
            sourceType: 'MANUAL_DRAW',
          },
        }],
      }))
      return
    }

    if (path === '/api/v1/spatial/buildings') {
      await route.fulfill(json({
        type: 'FeatureCollection',
        features: [{
          type: 'Feature',
          id: buildingId,
          geometry: {
            type: 'Polygon',
            coordinates: [[[113.2, 27.2], [113.3, 27.2], [113.3, 27.3], [113.2, 27.2]]],
          },
          properties: {
            entityType: 'BUILDING',
            entityId: buildingId,
            entityCode: 'B-001',
            name: '1号楼',
            communityId,
            status: 'VERIFIED',
            version: 1,
            coordinateSystem: 'GCJ02',
            sourceType: 'MANUAL_DRAW',
          },
        }],
      }))
      return
    }

    if (path === '/api/v1/dashboard/risk-map') {
      await route.fulfill(json({
        scopeKey: 'ALL',
        generatedAt: '2026-08-09T04:00:00Z',
        buildings: [{
          buildingId,
          buildingCode: 'B-001',
          buildingName: '1号楼',
          communityId,
          communityName: '示范小区',
          riskScore: 72.5,
          riskLevel: 'HIGH',
          confidenceScore: 81,
          completenessScore: 88,
          priorityScore: 76,
          priorityLevel: 'P1',
          freshness: 'CURRENT',
          needManualReview: true,
        }],
        disclaimer: '仅用于治理辅助。',
      }))
      return
    }

    if (path === `/api/v1/buildings/${buildingId}`) {
      await route.fulfill(json({
        id: buildingId,
        communityId,
        buildingCode: 'B-001',
        buildingName: '1号楼',
        address: '示范路1号',
        constructionYear: 2008,
        floorCount: 18,
        residentCount: 360,
      }))
      return
    }

    if (path === `/api/v1/communities/${communityId}`) {
      await route.fulfill(json({ id: communityId, communityName: '示范小区' }))
      return
    }

    if (path === `/api/v1/spatial/buildings/${buildingId}/boundary`) {
      await route.fulfill(json({ status: 'VERIFIED' }))
      return
    }

    if (path === '/api/v1/inspection-tasks') {
      await route.fulfill(json([]))
      return
    }

    if (path === '/api/v1/ai-inferences') {
      await route.fulfill(json({ content: [], page: { page: 0, size: 50, totalElements: 0, totalPages: 0 } }))
      return
    }

    if (path === `/api/v1/assessments/buildings/${buildingId}/current`) {
      await route.fulfill(json({
        buildingId,
        buildingCode: 'B-001',
        buildingName: '1号楼',
        communityId,
        communityName: '示范小区',
        freshness: 'CURRENT',
        completeness: { completenessScore: 88, missingItems: [] },
        risk: {
          riskScore: 72.5,
          riskLevel: 'HIGH',
          confidenceScore: 81,
          needManualReview: true,
          recommendations: ['安排专业人员复核高风险因素'],
          assessedAt: '2026-08-09T04:00:00Z',
          topFactors: [],
          excludedEvidence: [],
          dimensionScores: [],
        },
        renewalPriorities: [{
          priorityScore: 76,
          priorityLevel: 'P1',
          generatedAt: '2026-08-09T04:00:00Z',
        }],
        inputSummary: {},
        disclaimer: '正式评分仅作为治理研判依据。',
      }))
      return
    }

    if (path === '/api/v1/risk-reports') {
      await route.fulfill(json({ content: [], page: { page: 0, size: 20, totalElements: 0, totalPages: 0 } }))
      return
    }

    if (path === '/api/v1/feedback/reports') {
      await route.fulfill(json({ content: [], page: { page: 1, size: 100, totalElements: 0, totalPages: 0 } }))
      return
    }

    await route.fulfill(json({}))
  })

  await page.goto('/console/map')
  await expect(page.getByText('1号楼', { exact: true }).first()).toBeVisible()

  await page.waitForFunction(() => {
    const clicks = (window as typeof window & { __e2ePolygonClicks?: Array<() => void> }).__e2ePolygonClicks
    return Array.isArray(clicks) && clicks.length >= 2
  })
  await page.evaluate(() => {
    const clicks = (window as typeof window & { __e2ePolygonClicks?: Array<() => void> }).__e2ePolygonClicks
    clicks?.at(-1)?.()
  })

  await expect(page.getByText('楼栋空间详情')).toBeVisible()
  await expect(page.getByText('高风险', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '打开统一楼栋详情' })).toBeVisible()

  await page.getByRole('button', { name: '打开统一楼栋详情' }).click()

  await expect(page).toHaveURL(new RegExp(`/console/buildings/${buildingId}$`))
  await expect(page.getByRole('heading', { name: '楼栋统一详情' })).toBeVisible()
  await expect(page.getByText('楼栋业务生命周期')).toBeVisible()
  await expect(page.getByText('风险与更新优先级')).toBeVisible()
  await expect(page.getByRole('tab', { name: '正式评分' })).toBeVisible()
})
