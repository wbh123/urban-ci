import { test, expect, type Page } from '@playwright/test'

async function login(page: Page, username: string, password: string) {
  await page.goto('/console/login')
  await page.getByPlaceholder('请输入用户名').fill(username)
  await page.getByPlaceholder('请输入密码').fill(password)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  if (username !== 'wrong') {
    await expect(page.getByRole('button', { name: '我已了解', exact: true })).toBeVisible()
    await page.getByRole('button', { name: '我已了解', exact: true }).click()
    await expect(page).not.toHaveURL(/\/console\/login/)
  }
}

/**
 * 登录是异步的：点击登录并确认风险提示后，必须先等 token 持久化到 localStorage，再执行整页跳转。
 * 否则整页加载时新文档没有会话，路由守卫会把用户重定向回登录页——
 * 这是 CI 压力下“管理员可以直接进入巡检管理”偶发失败的根因
 * （此前 toHaveURL(/\/console\/inspections/) 误匹配了 redirect 查询参数）。
 */
async function expectSessionPersisted(page: Page): Promise<void> {
  await expect.poll(async () => page.evaluate(() => localStorage.getItem('urban-safe-token'))).toBeTruthy()
}

test.describe('巡检管理访问基线 (Mock 模式)', () => {
  test('社区管理员从角色工作台进入巡检管理', async ({ page }) => {
    await login(page, 'manager', 'demo123')
    await expect(page).toHaveURL(/\/console\/?$/)
    await expect(page.getByRole('heading', { name: '社区巡检工作台' })).toBeVisible()
    await expectSessionPersisted(page)

    await page.getByRole('menuitem', { name: '巡检管理' }).click()
    await expect(page).toHaveURL(/\/console\/inspections/)
    await expect(page.getByRole('heading', { name: '巡检管理' })).toBeVisible()
  })

  test('管理员可以直接进入巡检管理', async ({ page }) => {
    await login(page, 'admin', 'urban_safe_admin_password')
    await expectSessionPersisted(page)
    await page.goto('/console/inspections')
    await expect(page).toHaveURL(/\/console\/inspections/)
    await expect(page.getByRole('heading', { name: '巡检管理' })).toBeVisible()
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
    await expect(page.getByRole('button', { name: '我已了解', exact: true })).toBeVisible()
    await page.getByRole('button', { name: '我已了解', exact: true }).click()
    await expect(page).toHaveURL(/\/mobile\/tasks/)
    await expectSessionPersisted(page)

    await page.goto('/console/inspections')
    await expect(page).toHaveURL(/\/client-mismatch\?expected=CONSOLE/)
  })
})