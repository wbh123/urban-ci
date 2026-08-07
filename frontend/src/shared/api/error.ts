import { isAxiosError } from 'axios'
import type { ApiEnvelope } from './types'

export interface AppFieldError {
  field: string
  message: string
  rejectedValue?: string | null
}

export interface AppErrorOptions {
  message: string
  code?: string
  status?: number
  requestId?: string
  fieldErrors?: AppFieldError[]
}

/**
 * 项目统一的前端错误对象。后端错误码、HTTP 状态与 requestId 都保留其上，
 * 页面与组件只处理 AppError，不再各自解析 success/data/error。
 */
export class AppError extends Error {
  readonly code: string
  readonly status: number
  readonly requestId: string | undefined
  readonly fieldErrors: AppFieldError[]

  constructor(options: AppErrorOptions) {
    super(options.message)
    this.name = 'AppError'
    this.code = options.code ?? 'UNKNOWN'
    this.status = options.status ?? 0
    this.requestId = options.requestId
    this.fieldErrors = options.fieldErrors ?? []
  }

  get isNetwork(): boolean {
    return this.status === 0
  }
  get isUnauthorized(): boolean {
    return this.status === 401
  }
  get isForbidden(): boolean {
    return this.status === 403
  }
  get isNotFound(): boolean {
    return this.status === 404
  }
  get isConflict(): boolean {
    return this.status === 409
  }
  get isValidation(): boolean {
    return this.status === 400 || this.status === 422
  }
  get isServer(): boolean {
    return this.status >= 500
  }
}

export function mapStatusToCode(status: number): string {
  switch (status) {
    case 400:
      return 'BAD_REQUEST'
    case 401:
      return 'UNAUTHORIZED'
    case 403:
      return 'FORBIDDEN'
    case 404:
      return 'NOT_FOUND'
    case 409:
      return 'CONFLICT'
    case 415:
      return 'UNSUPPORTED_MEDIA_TYPE'
    case 422:
      return 'UNPROCESSABLE_ENTITY'
    case 500:
      return 'INTERNAL_ERROR'
    case 502:
      return 'BAD_GATEWAY'
    case 503:
      return 'SERVICE_UNAVAILABLE'
    case 504:
      return 'GATEWAY_TIMEOUT'
    default:
      return 'HTTP_ERROR'
  }
}

export function mapStatusToMessage(status: number): string {
  switch (status) {
    case 400:
      return '请求参数错误。'
    case 401:
      return '未登录或登录已过期。'
    case 403:
      return '没有访问权限。'
    case 404:
      return '资源不存在或已被删除。'
    case 409:
      return '状态冲突，请刷新后重试。'
    case 415:
      return '不支持的文件类型。'
    case 422:
      return '请求参数校验失败。'
    case 500:
      return '服务器内部错误，请稍后重试。'
    case 502:
      return '上游服务协议错误。'
    case 503:
      return '服务暂不可用。'
    case 504:
      return '上游请求超时。'
    default:
      return status > 0 ? `请求失败（${status}）。` : '请求失败。'
  }
}

/**
 * 将任意异常转换为 AppError。优先保留后端返回的 error.code / message / fieldErrors 与 requestId。
 */
export function toAppError(error: unknown): AppError {
  if (error instanceof AppError) return error

  if (isAxiosError(error)) {
    const status = error.response?.status ?? 0
    const body = error.response?.data as ApiEnvelope | undefined
    const envError = body?.error
    const requestId = body?.requestId

    if (envError && envError.message) {
      return new AppError({
        message: envError.message,
        code: envError.code ?? mapStatusToCode(status),
        status,
        requestId,
        fieldErrors: envError.fieldErrors ?? [],
      })
    }

    if (status === 0 || !error.response) {
      return new AppError({
        message: '网络连接失败，请检查网络或后端服务是否可用。',
        code: 'NETWORK_ERROR',
        status: 0,
      })
    }

    return new AppError({
      message: mapStatusToMessage(status),
      code: mapStatusToCode(status),
      status,
      requestId,
    })
  }

  if (error instanceof Error) {
    return new AppError({ message: error.message, code: 'UNKNOWN', status: 0 })
  }

  return new AppError({ message: '未知错误。', code: 'UNKNOWN', status: 0 })
}
