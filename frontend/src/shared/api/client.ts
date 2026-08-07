import axios, { type AxiosRequestConfig } from 'axios'
import { getApiBaseUrl } from './types'
import { installInterceptors } from './interceptors'
import type { ApiEnvelope } from './types'

/**
 * 统一 Axios 客户端。页面与业务组件不得直接使用此实例，
 * 一律通过 endpoints 中的领域函数访问后端。
 */
export const httpClient = axios.create({
  baseURL: getApiBaseUrl(),
  timeout: 15_000,
})

installInterceptors(httpClient)

function isEnvelope(body: unknown): body is ApiEnvelope {
  return !!body && typeof body === 'object' && typeof (body as ApiEnvelope).success === 'boolean'
}

/**
 * 统一请求入口：剥离后端统一响应外壳，成功时返回 data；
 * 失败时由响应拦截器抛出 AppError。FormData 自动交由浏览器设置 multipart 边界。
 */
export async function request<T = unknown>(config: AxiosRequestConfig): Promise<T> {
  const hasData = config.data !== undefined && config.data !== null
  const merged: AxiosRequestConfig = { ...config }
  if (hasData && !(config.data instanceof FormData)) {
    merged.headers = {
      'Content-Type': 'application/json',
      ...merged.headers,
    }
  }
  const response = await httpClient.request<ApiEnvelope<T>>(merged)
  const body = response.data
  if (!isEnvelope(body)) {
    return body as T
  }
  return body.data as T
}

export const apiGet = <T = unknown>(url: string, params?: Record<string, unknown>): Promise<T> =>
  request<T>({ method: 'get', url, params })

export const apiPost = <T = unknown>(
  url: string,
  data?: unknown,
  config: AxiosRequestConfig = {},
): Promise<T> => request<T>({ ...config, method: 'post', url, data })

export const apiPut = <T = unknown>(url: string, data?: unknown): Promise<T> =>
  request<T>({ method: 'put', url, data })

export const apiDelete = <T = unknown>(url: string): Promise<T> =>
  request<T>({ method: 'delete', url })
