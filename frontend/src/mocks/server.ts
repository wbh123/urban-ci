import { setupServer } from 'msw/node'
import { handlers } from './handlers'

/** Node 端 Mock 服务，用于单元/组件测试。 */
export const server = setupServer(...handlers)
