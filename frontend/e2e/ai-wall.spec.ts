import { test, expect, type Page } from '@playwright/test'

async function loginAsAdmin(page: Page) {
  await page.goto('/console/login')
  await page.getByPlaceholder('请输入用户名').fill('admin')
  await page.getByPlaceholder('请输入密码').fill('urban_safe_admin_password')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByRole('button', { name: '我已了解', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '我已了解', exact: true }).click()
  await expect(page).toHaveURL(/\/console\/?$/)
}

test.describe('AI 城市建筑安全态势大屏', () => {
  test('管理员可从原管理总览进入 AI 态势大屏并看到五类地图图层', async ({ page }) => {
    await loginAsAdmin(page)

    const wallEntry = page.getByRole('button', { name: 'AI 态势大屏', exact: true })
    await expect(wallEntry).toBeVisible()
    await wallEntry.click()

    await expect(page.getByText('城安智序 · AI 城市建筑安全智能研判中心', { exact: true })).toBeVisible()

    const layerSwitch = page.getByRole('region', { name: '地图展示模式' })
    await expect(layerSwitch).toBeVisible()
    await expect(layerSwitch.getByRole('button', { name: '风险', exact: true })).toBeVisible()
    await expect(layerSwitch.getByRole('button', { name: 'AI 病害', exact: true })).toBeVisible()
    await expect(layerSwitch.getByRole('button', { name: 'AI 关注', exact: true })).toBeVisible()
    await expect(layerSwitch.getByRole('button', { name: '待复核', exact: true })).toBeVisible()
    await expect(layerSwitch.getByRole('button', { name: '治理优先级', exact: true })).toBeVisible()

    const priority = layerSwitch.getByRole('button', { name: '治理优先级', exact: true })
    await priority.click()
    await expect(priority).toHaveAttribute('data-active', 'true')

    await expect(page.getByRole('button', { name: '全屏', exact: true })).toBeVisible()
  })
})
