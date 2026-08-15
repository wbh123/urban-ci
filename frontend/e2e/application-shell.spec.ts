import { test, expect, type Page } from '@playwright/test'

async function login(
  page: Page,
  entry: '/console/login' | '/mobile/login',
  username: string,
  password: string,
  acceptRiskNotice = true,
) {
  await page.goto(entry)
  await page.getByPlaceholder('请输入用户名').fill(username)
  await page.getByPlaceholder('请输入密码').fill(password)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  if (acceptRiskNotice) {
    await expect(page.getByRole('button', { name: '我已了解', exact: true })).toBeVisible()
    await page.getByRole('button', { name: '我已了解', exact: true }).click()
  }
  await expect(page).not.toHaveURL(/\/(console|mobile)\/login/)
}

test.describe('应用壳与角色入口', () => {
  test('首页保留公众、移动作业和电脑管理三个入口', async ({ page }) => {
    await page.goto('/')
    await expect(page.getByRole('heading', { name: '城安智序' })).toBeVisible()
    await expect(page.getByRole('button', { name: /公众反馈端/ })).toBeVisible()
    await expect(page.getByRole('button', { name: /移动作业端/ })).toBeVisible()
    await expect(page.getByRole('button', { name: /电脑审核管理端/ })).toBeVisible()
  })

  test('公众无需登录即可提交和查询反馈', async ({ page }) => {
    await page.goto('/citizen')
    await expect(page).toHaveURL(/\/citizen/)
    await page.goto('/citizen/report')
    await expect(page).toHaveURL(/\/citizen\/report/)
    await page.goto('/citizen/track')
    await expect(page).toHaveURL(/\/citizen\/track/)
  })

  test('管理员可进入电脑管理端', async ({ page }) => {
    await login(page, '/console/login', 'admin', 'urban_safe_admin_password')
    await expect(page).toHaveURL(/\/console\/?$/)
    await expect(page.getByRole('menuitem', { name: '管理总览' })).toBeVisible()
  })

  test('社区管理员默认进入社区巡检工作台', async ({ page }) => {
    await login(page, '/console/login', 'manager', 'demo123')
    await expect(page).toHaveURL(/\/console\/?$/)
    await expect(page.getByRole('heading', { name: '社区巡检工作台' })).toBeVisible()
    await expect(page.getByRole('menuitem', { name: '巡检管理' })).toBeVisible()
  })

  test('专家默认进入专业复核工作台', async ({ page }) => {
    await login(page, '/console/login', 'expert', 'demo123')
    await expect(page).toHaveURL(/\/console\/?$/)
    await expect(page.getByRole('heading', { name: '专业复核工作台' })).toBeVisible()
    await expect(page.getByRole('menuitem', { name: 'AI 人工复核' })).toBeVisible()
  })

  test('住建管理人员可进入电脑端风险入口', async ({ page }) => {
    await login(page, '/console/login', 'government', 'demo123')
    await expect(page).toHaveURL(/\/console\/?$/)
    await expect(page.getByRole('menuitem', { name: '风险总览与报告' })).toBeVisible()
  })

  test('巡检人员默认进入移动端巡检任务', async ({ page }) => {
    await login(page, '/mobile/login', 'inspector', 'demo123')
    await expect(page).toHaveURL(/\/mobile\/tasks/)
    await expect(page.getByText('巡检', { exact: true }).first()).toBeVisible()
  })

  test('问题处置人员默认进入移动端处置页面', async ({ page }) => {
    await login(page, '/mobile/login', 'disposer', 'demo123')
    await expect(page).toHaveURL(/\/mobile\/disposal/)
    await expect(page.getByText('处置', { exact: true }).first()).toBeVisible()
  })

  test('移动角色从电脑登录入口登录会被入口隔离', async ({ page }) => {
    await login(page, '/console/login', 'inspector', 'demo123', false)
    await expect(page).toHaveURL(/\/client-mismatch\?expected=CONSOLE/)
  })

  test('旧工作台地址重定向到电脑端巡检管理并要求登录', async ({ page }) => {
    await page.goto('/workspace')
    await expect(page).toHaveURL(/\/console\/login\?redirect=/)
    await expect(page.getByRole('heading', { name: '电脑审核管理端登录' })).toBeVisible()
  })

  test('404 页面仍可访问', async ({ page }) => {
    await page.goto('/nonexistent-path')
    await expect(page.locator('.el-result')).toContainText('404')
  })
})