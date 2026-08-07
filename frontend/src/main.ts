import '@/shared/styles/index.scss'
import { bootstrap, renderBootstrapError } from '@/app/bootstrap'

bootstrap().catch((error) => {
  console.error('[bootstrap] 应用启动失败:', error)
  renderBootstrapError(error)
})
