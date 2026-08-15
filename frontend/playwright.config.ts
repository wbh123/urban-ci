import { defineConfig, devices } from '@playwright/test'

/**
 * 端到端测试固定在 Mock 模式下运行：webServer 以 VITE_API_MODE=mock 启动 Vite，
 * 应用启动层会启用 Mock Service Worker，因此不依赖真实后端服务。
 *
 * 默认复用本地前端端口 5173；需要并行运行或持续集成环境隔离时，可通过
 * PLAYWRIGHT_PORT / PLAYWRIGHT_HOST / PLAYWRIGHT_BASE_URL 覆盖。
 */
const playwrightPort = Number.parseInt(
  process.env.PLAYWRIGHT_PORT ?? process.env.URBAN_SAFE_FRONTEND_PORT ?? '5173',
  10,
)
const playwrightHost = process.env.PLAYWRIGHT_HOST ?? '127.0.0.1'
const baseURL =
  process.env.PLAYWRIGHT_BASE_URL ?? `http://${playwrightHost}:${playwrightPort}`

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: 'list',
  use: {
    baseURL,
    trace: 'on-first-retry',
  },
  webServer: {
    command: `VITE_API_MODE=mock npm run dev -- --host ${playwrightHost} --port ${playwrightPort} --strictPort`,
    url: baseURL,
    reuseExistingServer: !process.env.CI,
    timeout: 60_000,
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
})