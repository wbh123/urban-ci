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

test.describe('小区与楼栋可视化建档闭环', () => {
  test('自动编码、重复提示、地图候选和创建流程可以连续完成', async ({ page }) => {
    await loginAsAdmin(page)
    await page.goto('/console/archive-management')
    await expect(page.getByRole('heading', { name: '小区与楼栋管理' })).toBeVisible()

    await page.getByRole('button', { name: '新增小区' }).click()
    const communityDrawer = page.locator('.el-drawer').filter({ hasText: '新增小区' })
    await expect(communityDrawer.getByLabel('小区编码')).toHaveValue(/^COMM-\d{8}-[A-Z0-9]+$/)

    await communityDrawer.getByLabel('小区名称').fill('示范小区')
    await expect(communityDrawer.getByText('发现疑似重复小区')).toBeVisible()
    await communityDrawer.getByLabel('小区名称').fill('')

    const communitySearch = communityDrawer.getByPlaceholder('搜索小区、楼栋或完整地址')
    await communitySearch.fill('新城花园')
    await communityDrawer.getByRole('button', { name: '搜索地点' }).click()
    await communityDrawer.locator('.candidate-item').filter({ hasText: '新城花园' }).click()
    await expect(communityDrawer.getByLabel('小区名称')).toHaveValue('新城花园')
    await expect(communityDrawer.getByLabel('详细地址')).not.toHaveValue('')
    await communityDrawer.getByRole('button', { name: '确认创建' }).click()
    await expect(page.getByText('小区档案已创建。')).toBeVisible()
    await expect(page.getByText('新城花园', { exact: true }).first()).toBeVisible()

    await page.getByRole('button', { name: '新增楼栋' }).click()
    const buildingDrawer = page.locator('.el-drawer').filter({ hasText: '新增楼栋' })
    await expect(buildingDrawer.getByLabel('楼栋编码')).toHaveValue(/^BLDG-\d{8}-[A-Z0-9]+$/)

    const buildingSearch = buildingDrawer.getByPlaceholder('搜索小区、楼栋或完整地址')
    await buildingSearch.fill('新城花园 1号楼')
    await buildingDrawer.getByRole('button', { name: '搜索地点' }).click()
    await buildingDrawer.locator('.candidate-item').filter({ hasText: '新城花园 1号楼' }).click()
    await expect(buildingDrawer.getByLabel('楼栋名称')).toHaveValue('新城花园 1号楼')
    await buildingDrawer.getByRole('button', { name: '确认创建' }).click()
    await expect(page.getByText('楼栋档案已创建。')).toBeVisible()
    await expect(page.getByText('新城花园 1号楼', { exact: true }).first()).toBeVisible()
  })

  test('完全不依赖地图也可以手工建档', async ({ page }) => {
    await loginAsAdmin(page)
    await page.goto('/console/archive-management')
    await page.getByRole('button', { name: '新增小区' }).click()

    const drawer = page.locator('.el-drawer').filter({ hasText: '新增小区' })
    await drawer.getByLabel('小区编码').fill('COM-MANUAL-E2E')
    await drawer.getByLabel('小区名称').fill('纯手工测试小区')
    await drawer.getByLabel('详细地址').fill('手工地址1号')
    await drawer.getByRole('button', { name: '确认创建' }).click()

    await expect(page.getByText('小区档案已创建。')).toBeVisible()
    await expect(page.getByText('纯手工测试小区', { exact: true }).first()).toBeVisible()
  })
})
