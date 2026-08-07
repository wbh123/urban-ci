export type ApiMode = 'mock' | 'real'

/** 后端统一响应外壳。运行时解析用，不是对 OpenAPI 业务类型的重复定义。 */
export interface ApiEnvelope<T = unknown> {
  success: boolean
  data?: T | null
  error?: {
    code: string
    message: string
    fieldErrors?: AppFieldErrorRaw[]
  } | null
  requestId?: string
  timestamp?: string
}

export interface AppFieldErrorRaw {
  field: string
  message: string
  rejectedValue?: string | null
}

/** 当前 API 模式：仅 real 调用真实后端，其余（含未设置）一律视为 mock。 */
export function getApiMode(): ApiMode {
  return import.meta.env.VITE_API_MODE === 'real' ? 'real' : 'mock'
}

/**
 * API 基础地址，仅在此处读取，页面不得直接读取。
 * mock 模式返回空串（同源相对路径），便于 Mock Service Worker 在浏览器侧无 CORS 拦截；
 * real 模式返回真实 Spring Boot 地址。
 */
export function getApiBaseUrl(): string {
  if (getApiMode() === 'mock') return ''
  return import.meta.env.VITE_API_BASE_URL || 'http://localhost:8888'
}
