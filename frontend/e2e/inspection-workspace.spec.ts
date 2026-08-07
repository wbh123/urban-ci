import { test, expect } from '@playwright/test'

/** Mock 模式演示账号凭据，与 Mock 处理器保持一致。 */
const DEMO_USER = 'admin'
const DEMO_PASS = 'urban_safe_admin_password'

/** 在登录表单中填入凭据并点击登录。 */
async function login(page: import('@playwright/test').Page) {
  await page.getByPlaceholder('用户名').fill(DEMO_USER)
  await page.getByPlaceholder('密码').fill(DEMO_PASS)
  await page.getByRole('button', { name: '登录' }).click()
}

test.describe('巡检工作台 (Mock 模式)', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/workspace')
  })

  test('Mock 模式加载地图运行配置', async ({ page }) => {
    await login(page)
    // 地图模式显示 MOCK
    await expect(page.getByText('地图模式：MOCK')).toBeVisible()
  })

  test('Mock 模式加载小区列表', async ({ page }) => {
    await login(page)
    // 小区卡片出现
    await expect(page.getByText('示范小区')).toBeVisible()
    await expect(page.getByText('安居小区')).toBeVisible()
  })

  test('接口错误时页面不白屏', async ({ page }) => {
    // 未登录直接点击按钮不存在（登录表单），改用错误凭据登录触发 401
    await page.getByPlaceholder('用户名').fill('wrong')
    await page.getByPlaceholder('密码').fill('wrong')
    await page.getByRole('button', { name: '登录' }).click()
    // 页面不白屏，标题仍在
    await expect(page.locator('h1')).toBeVisible()
    // 错误提示显示
    await expect(page.getByText(/用户名或密码错误/)).toBeVisible()
  })

  test('工作台基础结构显示正常', async ({ page }) => {
    await login(page)
    // 等待数据加载
    await expect(page.getByText('数据加载完成')).toBeVisible()
    // 四块面板标题都存在
    const h2 = page.locator('.panel h2')
    await expect(h2.filter({ hasText: '小区地图' }).first()).toBeVisible()
    await expect(h2.filter({ hasText: '创建巡检任务' }).first()).toBeVisible()
    await expect(h2.filter({ hasText: '巡检任务' }).first()).toBeVisible()
    await expect(h2.filter({ hasText: '现场记录与图片' }).first()).toBeVisible()
  })
})
