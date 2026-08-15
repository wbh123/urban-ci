import { expect, test, type Page } from '@playwright/test'
import { DEMO_PASSWORD, DEMO_USERNAME } from '../src/mocks/fixtures/data'

async function loginAsAdmin(page: Page): Promise<void> {
  await page.goto('/console/login')
  await page.getByPlaceholder('请输入用户名').fill(DEMO_USERNAME)
  await page.getByPlaceholder('请输入密码').fill(DEMO_PASSWORD)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByRole('button', { name: '我已了解', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '我已了解', exact: true }).click()
  await expect(page).toHaveURL(/\/console\/?$/)
}

async function selectFirstCommunity(page: Page): Promise<void> {
  const communityField = page
    .locator('.spatial-selector .spatial-selector__field')
    .filter({ hasText: '小区' })
    .first()
  const communitySelect = communityField.locator('.el-select__wrapper')
  await expect(communitySelect).toBeVisible()
  await communitySelect.click()
  const firstOption = page.locator('.el-select-dropdown:visible .el-select-dropdown__item').first()
  await expect(firstOption).toBeVisible()
  await firstOption.click()
}

test.describe('R4-1 高德候选边界', () => {
  test('候选只预览，人工采用并保存后仍保持待确认', async ({ page }) => {
    await loginAsAdmin(page)
    await page.goto('/console/spatial-archive')
    await expect(page.getByRole('heading', { name: '空间档案' })).toBeVisible()

    await selectFirstCommunity(page)
    const previewButton = page.getByRole('button', { name: '查询高德候选边界' })
    await expect(previewButton).toBeEnabled()
    await previewButton.click()
    await expect(page.getByText('仅预览', { exact: true })).toBeVisible()
    await expect(page.getByText(/尚未采用、不会保存/)).toBeVisible()

    await page.getByRole('button', { name: '采用为草稿' }).click()
    await expect(page.getByText('已采用为草稿', { exact: true })).toBeVisible()
    await expect(page.getByText('高德候选草稿', { exact: true })).toBeVisible()
    await expect(page.getByText(/尚未保存/)).toBeVisible()

    await page.getByRole('button', { name: '保存为新版本' }).click()
    await expect(page.getByText('边界已保存并进入待确认状态。')).toBeVisible()
    await expect(page.getByText('待确认', { exact: true })).toBeVisible()
    await expect(page.getByText('AMAP_AOI', { exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: '确认边界' })).toBeEnabled()
  })
})
