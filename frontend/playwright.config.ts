import { defineConfig, devices } from '@playwright/test'

/**
 * 端到端测试固定在 Mock 模式下运行：webServer 以 VITE_API_MODE=mock 启动 Vite，
 * 应用启动层会启用 Mock Service Worker，因此不依赖真实后端服务。
 * 使用独立端口 5174，避免与日常开发的 5173 冲突。
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: 'list',
  use: {
    baseURL: 'http://localhost:5174',
    trace: 'on-first-retry',
  },
  webServer: {
    command: 'VITE_API_MODE=mock npm run dev -- --port 5174 --strictPort',
    url: 'http://localhost:5174',
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
