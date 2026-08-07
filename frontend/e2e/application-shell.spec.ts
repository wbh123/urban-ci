import { test, expect } from '@playwright/test'

test.describe('应用壳', () => {
  test('打开首页', async ({ page }) => {
    await page.goto('/')
    await expect(page.locator('h1')).toContainText('城安智序')
    await expect(page.locator('.home-card')).toHaveCount(3)
  })

  test('进入工作台（未登录状态显示登录表单）', async ({ page }) => {
    await page.goto('/workspace')
    await expect(page.locator('h1')).toContainText('巡检地图')
    // 登录表单可见
    await expect(page.getByPlaceholder('用户名')).toBeVisible()
    await expect(page.getByPlaceholder('密码')).toBeVisible()
  })

  test('从首页导航到工作台', async ({ page }) => {
    await page.goto('/')
    await page.getByRole('button', { name: '进入工作台' }).click()
    await expect(page).toHaveURL(/\/workspace/)
  })

  test('404 页面', async ({ page }) => {
    await page.goto('/nonexistent-path')
    await expect(page.locator('.el-result')).toContainText('404')
  })
})
