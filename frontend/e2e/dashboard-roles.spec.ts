import { expect, test, type Page } from '@playwright/test'
import { DEMO_PASSWORD } from '../src/mocks/fixtures/data'

interface AccountExpectation {
  username: string
  password: string
  title: string
  visibleActions: string[]
  hiddenActions?: string[]
}

const accounts: AccountExpectation[] = [
  {
    username: 'admin',
    password: DEMO_PASSWORD,
    title: '系统管理工作台',
    visibleActions: ['空间档案', '巡检组织', '专业复核', '风险与优先级'],
  },
  {
    username: 'manager',
    password: 'demo123',
    title: '社区巡检工作台',
    visibleActions: ['辖区楼栋', '巡检任务', '公众反馈'],
    hiddenActions: ['风险与优先级'],
  },
  {
    username: 'government',
    password: 'demo123',
    title: '区域风险工作台',
    visibleActions: ['区域楼栋', '公众反馈', '风险与优先级', '楼栋报告'],
  },
  {
    username: 'expert',
    password: 'demo123',
    title: '专业复核工作台',
    visibleActions: ['专业复核', '楼栋档案', '风险与优先级'],
  },
]

async function login(page: Page, account: AccountExpectation): Promise<void> {
  await page.goto('/console/login')
  await page.getByPlaceholder('请输入用户名').fill(account.username)
  await page.getByPlaceholder('请输入密码').fill(account.password)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/\/console\/?$/)
}

for (const account of accounts) {
  test(`${account.username} 看到与职责匹配的工作台`, async ({ page }) => {
    await login(page, account)

    await expect(page.getByRole('heading', { name: account.title })).toBeVisible()
    await expect(page.getByText('打开完整空间地图')).toBeVisible()

    for (const action of account.visibleActions) {
      await expect(page.getByRole('button', { name: new RegExp(action) }).first()).toBeVisible()
    }
    for (const action of account.hiddenActions ?? []) {
      await expect(page.getByRole('button', { name: new RegExp(action) })).toHaveCount(0)
    }
  })
}

test('社区管理人员待办直接进入带筛选条件的巡检页', async ({ page }) => {
  const manager = accounts.find((account) => account.username === 'manager')!
  await login(page, manager)

  await page.getByRole('button', { name: /跟进进行中巡检/ }).click()
  await expect(page).toHaveURL(/\/console\/inspections\?status=IN_PROGRESS$/)
})
