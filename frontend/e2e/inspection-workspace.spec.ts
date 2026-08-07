import { test, expect, type Page } from '@playwright/test'

async function login(page: Page, username: string, password: string) {
  await page.goto('/console/login')
  await page.getByPlaceholder('请输入用户名').fill(username)
  await page.getByPlaceholder('请输入密码').fill(password)
  await page.getByRole('button', { name: '登录', exact: true }).click()
}

test.describe('巡检组织管理访问基线 (Mock 模式)', () => {
  test('社区管理员登录后进入巡检组织管理', async ({ page }) => {
    await login(page, 'manager', 'demo123')
    await expect(page).toHaveURL(/\/console\/inspections/)
    // 硬导航后懒加载页面 + MSW mock 启动在 CI 压力下可能超过默认 5s 窗口，
    // 断言条件不变（标题必须可见），仅放宽就绪等待窗口。
    await expect(page.getByRole('heading', { name: '巡检组织管理' })).toBeVisible({ timeout: 15_000 })
  })

  test('管理员可以直接进入巡检组织管理', async ({ page }) => {
    await login(page, 'admin', 'urban_safe_admin_password')
    await page.goto('/console/inspections')
    await expect(page).toHaveURL(/\/console\/inspections/)
    // 全页 goto 会重新执行 mock bootstrap 并加载懒加载页面块，
    // CI 负载下曾间歇性超过默认 5s 窗口，故放宽到 15s；断言本身不变。
    await expect(page.getByRole('heading', { name: '巡检组织管理' })).toBeVisible({ timeout: 15_000 })
  })

  test('错误凭据不会进入受保护页面', async ({ page }) => {
    await login(page, 'wrong', 'wrong')
    await expect(page).toHaveURL(/\/console\/login/)
    await expect(page.getByText(/用户名或密码错误/)).toBeVisible()
  })

  test('移动巡检人员不能通过电脑端巡检管理路由', async ({ page }) => {
    await page.goto('/mobile/login')
    await page.getByPlaceholder('请输入用户名').fill('inspector')
    await page.getByPlaceholder('请输入密码').fill('demo123')
    await page.getByRole('button', { name: '登录', exact: true }).click()
    await expect(page).toHaveURL(/\/mobile\/tasks/)

    await page.goto('/console/inspections')
    await expect(page).toHaveURL(/\/client-mismatch\?expected=CONSOLE/)
  })
})
