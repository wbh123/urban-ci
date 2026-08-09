import { expect, test, type Page, type Route } from '@playwright/test'
import { BUILDING_ID, DEMO_PASSWORD, DEMO_USERNAME } from '../src/mocks/fixtures/data'

async function loginAsAdmin(page: Page): Promise<void> {
  await page.goto('/console/login')
  await page.getByPlaceholder('请输入用户名').fill(DEMO_USERNAME)
  await page.getByPlaceholder('请输入密码').fill(DEMO_PASSWORD)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/\/console\/?$/)
}

async function enableBrowserMapHarness(page: Page): Promise<void> {
  await page.route('**/api/v1/map/runtime-config', async (route) => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        enabled: true,
        mode: 'LIVE',
        provider: 'AMAP',
        jsApiKey: 'playwright-map-key',
        serviceHost: '/_AMapService',
        securityJsCodeExposed: false,
        defaultCenter: { longitude: 113.13396, latitude: 27.82767 },
        defaultZoom: 16,
      }),
    })
  })

  await page.route('https://webapi.amap.com/maps?**', fulfillFakeAmap)
}

async function fulfillFakeAmap(route: Route): Promise<void> {
  const requestUrl = new URL(route.request().url())
  const callback = requestUrl.searchParams.get('callback')
  if (!callback) throw new Error('AMap callback missing')

  const script = `
    (() => {
      window.__urbanSafeE2ePolygons = [];
      class FakePolygon {
        constructor(options) { this.options = options; this.handlers = {}; window.__urbanSafeE2ePolygons.push(this); }
        on(event, handler) { this.handlers[event] = handler; }
        setOptions(options) { this.options = Object.assign({}, this.options, options); }
        setMap() {}
        emit(event) { if (this.handlers[event]) this.handlers[event](); }
      }
      class FakeMap {
        constructor() { this.handlers = {}; setTimeout(() => this.handlers.complete && this.handlers.complete(), 0); }
        add() {}
        on(event, handler) { this.handlers[event] = handler; }
        getZoom() { return 16; }
        getBounds() {
          return {
            getSouthWest: () => ({ getLng: () => 113.0, getLat: () => 27.0 }),
            getNorthEast: () => ({ getLng: () => 114.0, getLat: () => 28.0 }),
          };
        }
        destroy() {}
      }
      window.AMap = {
        Map: FakeMap,
        Polygon: FakePolygon,
        plugin: (_plugins, done) => done(),
      };
      window[${JSON.stringify(callback)}]();
    })();
  `

  await route.fulfill({ contentType: 'application/javascript', body: script })
}

test.describe('R4-3 楼栋统一详情闭环', () => {
  test('地图楼栋 Polygon 可以打开摘要抽屉并进入完整档案', async ({ page }) => {
    await loginAsAdmin(page)
    await enableBrowserMapHarness(page)
    await page.goto('/console/map')

    await expect.poll(async () => page.evaluate(() => (
      (window as unknown as { __urbanSafeE2ePolygons?: unknown[] }).__urbanSafeE2ePolygons?.length ?? 0
    ))).toBeGreaterThan(2)

    await page.evaluate(() => {
      const polygons = (window as unknown as {
        __urbanSafeE2ePolygons?: Array<{ emit: (event: string) => void }>
      }).__urbanSafeE2ePolygons ?? []
      polygons.at(-1)?.emit('click')
    })

    const drawer = page.locator('.el-drawer').filter({ hasText: '楼栋详情' })
    await expect(drawer).toBeVisible()
    await expect(drawer.getByRole('button', { name: '查看完整档案' })).toBeVisible()
    await drawer.getByRole('button', { name: '查看完整档案' }).click()

    await expect(page).toHaveURL(/\/console\/buildings\/[^/?]+$/)
    await expect(page.getByRole('heading', { name: '楼栋统一档案' })).toBeVisible()
    await expect(page.getByRole('tab', { name: '风险与优先级' })).toBeVisible()
  })

  test('旧评分地址兼容跳转到统一详情风险页', async ({ page }) => {
    await loginAsAdmin(page)
    await page.goto(`/console/buildings/${BUILDING_ID}/assessment?from=e2e`)

    await expect(page).toHaveURL(new RegExp(`/console/buildings/${BUILDING_ID}\\?.*tab=risk`))
    await expect(page.getByRole('heading', { name: '楼栋统一档案' })).toBeVisible()
    await expect(page.getByRole('tab', { name: '风险与优先级' })).toHaveAttribute('aria-selected', 'true')
  })
})
