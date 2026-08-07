import { setupWorker } from 'msw/browser'
import { handlers } from './handlers'

/** 浏览器端 Mock Worker，仅在 mock 模式下由 bootstrap 启动。 */
export const worker = setupWorker(...handlers)
