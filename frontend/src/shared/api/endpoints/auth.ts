import type { Schema } from '../schema'
import { apiGet, apiPost } from '../client'

export type LoginRequest = Schema<'LoginRequest'>
export type LoginResponse = Schema<'LoginResponse'>
export type LoginUser = Schema<'LoginUser'>
export type CurrentUserResponse = Schema<'CurrentUserResponse'>
export type LogoutResponse = Schema<'LogoutResponse'>

export function login(payload: LoginRequest): Promise<LoginResponse> {
  return apiPost<LoginResponse>('/api/v1/auth/login', payload)
}

export function logout(): Promise<LogoutResponse> {
  return apiPost<LogoutResponse>('/api/v1/auth/logout')
}

export function getCurrentUser(): Promise<CurrentUserResponse> {
  return apiGet<CurrentUserResponse>('/api/v1/auth/me')
}
