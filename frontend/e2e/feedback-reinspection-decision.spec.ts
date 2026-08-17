import { expect, test, type Page } from '@playwright/test'

async function loginAdmin(page: Page) {
  await page.goto('/console/login')
  await page.getByPlaceholder('请输入用户名').fill('admin')
  await page.getByPlaceholder('请输入密码').fill('urban_safe_admin_password')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByRole('button', { name: '我已了解', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '我已了解', exact: true }).click()
  await expect(page).not.toHaveURL(/\/console\/login/)
}

test.describe('公众反馈复检人工决策', () => {
  test('电脑管理端展示真实系统建议后再由工作人员做最终决定', async ({ page }) => {
    await loginAdmin(page)
    await page.goto('/console/feedback')
    await expect(page.getByRole('heading', { name: '公众反馈管理' })).toBeVisible()

    const processingRow = page.getByRole('row').filter({
      hasText: '地下车库导向标识松动，已完成重新固定',
    })
    await expect(processingRow).toBeVisible()
    await processingRow.getByRole('button', { name: '提交整改', exact: true }).click()

    await expect(page.getByText('系统复检建议', { exact: true })).toBeVisible()
    await expect(page.getByText('可考虑免现场复检', { exact: true })).toBeVisible()
    await expect(page.getByText('人工最终决定', { exact: true })).toBeVisible()
    await expect(page.getByRole('radio', { name: '无需复检，直接闭环' })).toBeChecked()
    await page.getByRole('button', { name: '取消', exact: true }).click()

    const resolvedRow = page.getByRole('row').filter({
      hasText: '外墙局部开裂，已完成修补并等待现场复验',
    })
    await expect(resolvedRow).toBeVisible()
    await resolvedRow.getByRole('button', { name: '人工确认无需复检', exact: true }).click()

    await expect(page.getByText('人工确认无需现场复检', { exact: true })).toBeVisible()
    await expect(page.getByText('系统建议', { exact: true })).toBeVisible()
    await expect(page.getByText('建议现场复检', { exact: true })).toBeVisible()
    await expect(page.getByText(/问题类型 WALL_CRACK/)).toBeVisible()
  })
})
