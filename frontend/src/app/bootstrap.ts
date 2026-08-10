import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from '@/App.vue'
import { router } from './router'
import { configureInterceptors, getApiMode } from '@/shared/api'
import { useAuthStore } from '@/stores/auth'
import { installWorkbenchStatusRingOverlay } from '@/components/workbench/workbench-status-ring-overlay'

interface UnhandledRequestPrinter { warning: () => void }

export function handleMockUnhandledRequest(request: Request, print: UnhandledRequestPrinter): void {
  const url = new URL(request.url)
  if (url.pathname.startsWith('/api/')) {
    throw new Error(`[mock] 未配置 API mock 处理器：${request.method} ${url.pathname}`)
  }
  print.warning()
}

async function assertMockHealth(): Promise<void> {
  const response = await fetch('/api/v1/mock/health')
  if (!response.ok) {
    throw new Error(`mock health HTTP ${response.status}`)
  }
  const payload = (await response.json().catch(() => null)) as { success?: boolean; data?: { status?: string } } | null
  if (!payload?.success || payload.data?.status !== 'UP') {
    throw new Error('mock health 响应无效')
  }
}

/** 仅在 mock 模式下启动 Mock Service Worker；real 模式直接调用真实后端。 */
export async function enableMockWorker(): Promise<void> {
  try {
    const { worker } = await import('@/mocks/browser')
    await worker.start({
      onUnhandledRequest: handleMockUnhandledRequest,
      serviceWorker: {
        url: `${import.meta.env.BASE_URL}mockServiceWorker.js`,
      },
    })
    await assertMockHealth()
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error)
    throw new Error(`Mock 服务启动失败：${message}`)
  }
}

export function renderBootstrapError(error: unknown): void {
  const root = document.getElementById('app')
  if (!root) return
  const message = error instanceof Error ? error.message : String(error)
  root.innerHTML = '<main class="bootstrap-error"><h1></h1><p></p></main>'
  root.querySelector('h1')!.textContent = '应用启动失败'
  root.querySelector('p')!.textContent = message
}

/**
 * 应用启动层：统一注册 Pinia、Router 与单一组件库，
 * 装配 API 拦截器（令牌注入与 401 清理），并按需启动 Mock。
 */
export async function bootstrap(): Promise<void> {
  const app = createApp(App)
  const pinia = createPinia()
  app.use(pinia)

  const authStore = useAuthStore()
  configureInterceptors({
    tokenGetter: () => authStore.accessToken,
    onUnauthorized: () => authStore.clearSession(),
  })

  app.use(router)
  app.use(ElementPlus)

  if (getApiMode() === 'mock') {
    await enableMockWorker()
  }

  // 会话恢复：用本地令牌尝试拉取当前用户，失败已由拦截器清理。
  await authStore.restore().catch(() => {
    /* 恢复失败已清理会话 */
  })

  app.mount('#app')
  installWorkbenchStatusRingOverlay(pinia)
}
