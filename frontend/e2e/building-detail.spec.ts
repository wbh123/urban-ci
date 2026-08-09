import { expect, test, type Page } from '@playwright/test'
import { BUILDING_ID, DEMO_PASSWORD, DEMO_USERNAME } from '../src/mocks/fixtures/data'

async function loginAsAdmin(page: Page): Promise<void> {
  await page.goto('/console/login')
  await page.getByPlaceholder('请输入用户名').fill(DEMO_USERNAME)
  await page.getByPlaceholder('请输入密码').fill(DEMO_PASSWORD)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/\/console\/?$/)
}

/**
 * 启用地图实时模式用于 E2E 测试。
 * 通过 addInitScript 注入 Fake AMap 实现，避免依赖外部脚本；
 * 通过 localStorage 标记通知 MSW 处理器返回 LIVE 配置。
 */
async function enableLiveMapForE2E(page: Page): Promise<void> {
  // 注入 Fake AMap 实现，避免加载外部高德脚本
  await page.addInitScript(() => {
    ;(window as unknown as Record<string, unknown>).__urbanSafeE2ePolygons = []
    class FakePolygon {
      options: Record<string, unknown>
      handlers: Record<string, (() => void) | undefined> = {}
      constructor(options: Record<string, unknown>) {
        this.options = options
        ;((window as unknown as Record<string, unknown>).__urbanSafeE2ePolygons as Array<unknown>).push(this)
      }
      on(event: string, handler: () => void) { this.handlers[event] = handler }
      setOptions(options: Record<string, unknown>) { this.options = Object.assign({}, this.options, options) }
      setMap() {}
      emit(event: string) { if (this.handlers[event]) this.handlers[event]!() }
    }
    class FakeMap {
      handlers: Record<string, (() => void) | undefined> = {}
      constructor() { setTimeout(() => this.handlers.complete && this.handlers.complete!(), 0) }
      add() {}
      on(event: string, handler: () => void) { this.handlers[event] = handler }
      getZoom() { return 16 }
      getBounds() {
        return {
          getSouthWest: () => ({ getLng: () => 113.0, getLat: () => 27.0 }),
          getNorthEast: () => ({ getLng: () => 114.0, getLat: () => 28.0 }),
        }
      }
      destroy() {}
    }
    ;(window as unknown as Record<string, unknown>).AMap = {
      Map: FakeMap,
      Polygon: FakePolygon,
      plugin: (_plugins: string[], done: () => void) => done(),
    }
  })

  // 通知 MSW 处理器返回 LIVE 配置
  await page.evaluate(() => localStorage.setItem('e2e-map-live', '1'))
}

test.describe('R4-3 楼栋统一详情闭环', () => {
  test.skip(Boolean(process.env.CI), 'R4-3 浏览器闭环保留为本地验收项；持续集成继续执行类型、单元、构建与服务端硬门禁')

  test('地图楼栋 Polygon 可以打开摘要抽屉并进入完整档案', async ({ page }) => {
    await loginAsAdmin(page)
    await enableLiveMapForE2E(page)
    await page.goto('/console/map')
    await page.waitForLoadState('networkidle')

    // 轮询等待 FakePolygon 实例创建（地图渲染至少需要 3 个楼栋多边形）
    await expect.poll(async () => page.evaluate(() => (
      ((window as unknown as { __urbanSafeE2ePolygons?: unknown[] }).__urbanSafeE2ePolygons?.length ?? 0)
    ))).toBeGreaterThan(2)

    // 触发最后一个 polygon 的点击事件以打开楼栋详情抽屉
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
