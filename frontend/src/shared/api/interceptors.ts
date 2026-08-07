import type { AxiosInstance, InternalAxiosRequestConfig, AxiosResponse } from 'axios'
import { generateRequestId } from './request-id'
import { AppError, toAppError } from './error'
import type { ApiEnvelope } from './types'

type TokenGetter = () => string
type UnauthorizedHandler = () => void

let tokenGetter: TokenGetter = () => ''
let unauthorizedHandler: UnauthorizedHandler = () => {}

/**
 * 注入运行时依赖，避免 API 层与 Pinia store 形成循环依赖。
 * 由应用启动层（bootstrap）在创建 store 之后调用一次。
 */
export function configureInterceptors(options: {
  tokenGetter?: TokenGetter
  onUnauthorized?: UnauthorizedHandler
} = {}): void {
  if (options.tokenGetter) tokenGetter = options.tokenGetter
  if (options.onUnauthorized) unauthorizedHandler = options.onUnauthorized
}

/** 仅供单元测试重置内部处理函数。 */
export function resetInterceptors(): void {
  tokenGetter = () => ''
  unauthorizedHandler = () => {}
}

export function installInterceptors(instance: AxiosInstance): void {
  instance.interceptors.request.use((config: InternalAxiosRequestConfig) => {
    const token = tokenGetter()
    if (token) {
      config.headers.set('Authorization', `Bearer ${token}`)
    }
    config.headers.set('X-UrbanSafe-Request-Id', generateRequestId())
    return config
  })

  instance.interceptors.response.use(
    (response: AxiosResponse<ApiEnvelope>) => {
      const body = response.data
      // 非统一外壳（如二进制内容）直接放行
      if (!body || typeof body !== 'object' || typeof body.success !== 'boolean') {
        return response
      }
      if (!body.success) {
        // 业务失败但仍以 2xx 返回的防御性处理
        const envError = body.error
        throw new AppError({
          message: envError?.message ?? '请求失败。',
          code: envError?.code ?? 'BUSINESS_ERROR',
          status: response.status,
          requestId: body.requestId,
          fieldErrors: envError?.fieldErrors ?? [],
        })
      }
      return response
    },
    (error: unknown) => {
      const appError = toAppError(error)
      if (appError.isUnauthorized) {
        unauthorizedHandler()
      }
      return Promise.reject(appError)
    },
  )
}
