import { expect, test, type Page } from '@playwright/test'

async function login(
  page: Page,
  entry: '/console/login' | '/mobile/login',
  username: string,
  password: string,
) {
  await page.goto(entry)
  await page.getByPlaceholder('请输入用户名').fill(username)
  await page.getByPlaceholder('请输入密码').fill(password)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByRole('button', { name: '我已了解', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '我已了解', exact: true }).click()
  await expect(page).not.toHaveURL(/\/(console|mobile)\/login/)
}

test.describe('公众反馈复检人工决策', () => {
  test('电脑管理端展示真实系统建议后再由工作人员做最终决定', async ({ page }) => {
    await login(page, '/console/login', 'admin', 'urban_safe_admin_password')
    await page.goto('/console/feedback')
    await expect(page.getByRole('heading', { name: '公众反馈管理' })).toBeVisible()

    const processingRow = page.getByRole('row').filter({
      hasText: '地下车库导向标识松动，已完成重新固定',
    })
    await expect(processingRow).toBeVisible()
    await processingRow.getByRole('button', { name: '提交整改', exact: true }).click()

    const statusDrawer = page.getByLabel('处理公众反馈 · 处置与整改')
    await expect(statusDrawer.getByText('系统复检建议', { exact: true })).toBeVisible()
    await expect(statusDrawer.getByText('可考虑免现场复检', { exact: true })).toBeVisible()
    await expect(statusDrawer.getByText('人工最终决定', { exact: true })).toBeVisible()
    await expect(statusDrawer.getByRole('radio', { name: '无需复检，直接闭环' })).toBeChecked()
    await statusDrawer.getByRole('button', { name: '取消', exact: true }).click()

    const resolvedRow = page.getByRole('row').filter({
      hasText: '外墙局部开裂，已完成修补并等待现场复验',
    })
    await expect(resolvedRow).toBeVisible()
    await resolvedRow.getByRole('button', { name: '人工确认无需复检', exact: true }).click()

    const waiverDialog = page.getByLabel('人工确认无需现场复检')
    await expect(waiverDialog).toBeVisible()
    await expect(waiverDialog.getByText('系统建议', { exact: true })).toBeVisible()
    await expect(waiverDialog.getByText('建议现场复检', { exact: true })).toBeVisible()
    await expect(waiverDialog.getByText(/问题类型 WALL_CRACK/)).toBeVisible()
  })

  test('移动处置端同样在人工决策前展示结构化复检建议', async ({ page }) => {
    await login(page, '/mobile/login', 'disposer', 'demo123')
    await expect(page).toHaveURL(/\/mobile\/disposal/)
    await expect(page.getByRole('heading', { name: '问题处置' })).toBeVisible()

    const processingCard = page.locator('.work-card').filter({
      hasText: '地下车库导向标识松动，已完成重新固定',
    })
    await expect(processingCard).toBeVisible()
    await processingCard.getByRole('button', { name: '提交整改完成', exact: true }).click()

    const actionDrawer = page.getByLabel('更新处置进度')
    await expect(actionDrawer).toBeVisible()
    await expect(actionDrawer.getByText('系统复检建议', { exact: true })).toBeVisible()
    await expect(actionDrawer.getByText('可考虑免现场复检', { exact: true })).toBeVisible()
    await expect(actionDrawer.getByText('人工最终决定', { exact: true })).toBeVisible()
    await expect(actionDrawer.getByRole('radio', { name: '无需复检，直接闭环' })).toBeChecked()

    await page.keyboard.press('Escape')
    await expect(actionDrawer).toBeHidden()

    const resolvedCard = page.locator('.work-card').filter({
      hasText: '外墙局部开裂，已完成修补并等待现场复验',
    })
    await expect(resolvedCard).toBeVisible()
    await resolvedCard.getByRole('button', { name: '人工确认无需复检', exact: true }).click()

    const waiverDialog = page.getByLabel('人工确认无需现场复检')
    await expect(waiverDialog).toBeVisible()
    await expect(waiverDialog.getByText('系统建议', { exact: true })).toBeVisible()
    await expect(waiverDialog.getByText('建议现场复检', { exact: true })).toBeVisible()
    await expect(waiverDialog.getByText(/问题类型 WALL_CRACK/)).toBeVisible()
  })
})
