import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as api from '@/shared/api'
import {
  canEnterClient,
  hasAnyRole,
  hasPermission,
  hasRole,
  normalizeRoleCodes,
  permissionsForRoles,
  resolveDefaultEntry,
  type ClientType,
  type RoleCode,
} from '@/shared/auth/access'

const TOKEN_KEY = 'urban-safe-token'
const USER_KEY = 'urban-safe-user'

export interface AuthUser {
  id: string
  username: string
  realName?: string
  roles: RoleCode[]
  permissions: string[]
}

type UnknownRecord = Record<string, unknown>

function asRecord(value: unknown): UnknownRecord {
  return value && typeof value === 'object' ? (value as UnknownRecord) : {}
}

function extractRoles(value: unknown): RoleCode[] {
  if (!Array.isArray(value)) return []
  const codes = value.map((item) => {
    if (typeof item === 'string') return item
    const record = asRecord(item)
    return typeof record.roleCode === 'string' ? record.roleCode : ''
  })
  return normalizeRoleCodes(codes)
}

function extractPermissions(value: unknown, roles: RoleCode[]): string[] {
  if (Array.isArray(value)) {
    const permissions = value.filter((item): item is string => typeof item === 'string')
    if (permissions.length) return [...new Set(permissions)]
  }
  return permissionsForRoles(roles)
}

function toAuthUser(value: unknown): AuthUser {
  const record = asRecord(value)
  const roles = extractRoles(record.roles)
  return {
    id: typeof record.id === 'string' ? record.id : '',
    username: typeof record.username === 'string' ? record.username : '',
    realName: typeof record.realName === 'string' ? record.realName : undefined,
    roles,
    permissions: extractPermissions(record.permissions, roles),
  }
}

function loadUser(): AuthUser | null {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? toAuthUser(JSON.parse(raw)) : null
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string>(localStorage.getItem(TOKEN_KEY) ?? '')
  const user = ref<AuthUser | null>(loadUser())

  const isAuthenticated = computed(() => Boolean(accessToken.value))

  function persist(): void {
    if (accessToken.value) localStorage.setItem(TOKEN_KEY, accessToken.value)
    else localStorage.removeItem(TOKEN_KEY)
    if (user.value) localStorage.setItem(USER_KEY, JSON.stringify(user.value))
    else localStorage.removeItem(USER_KEY)
  }

  async function login(username: string, password: string) {
    const response = await api.login({ username, password })
    accessToken.value = response.accessToken ?? ''
    user.value = toAuthUser(response.user)
    persist()
    return response
  }

  async function fetchCurrentUser() {
    const currentUser = await api.getCurrentUser()
    user.value = toAuthUser(currentUser)
    persist()
    return currentUser
  }

  function setToken(token: string): void {
    accessToken.value = token
    persist()
  }

  function clearSession(): void {
    accessToken.value = ''
    user.value = null
    persist()
  }

  async function logout(): Promise<void> {
    try {
      if (accessToken.value) await api.logout()
    } catch {
      // 退出接口失败不阻塞本地会话清理。
    } finally {
      clearSession()
    }
  }

  async function restore(): Promise<void> {
    if (!accessToken.value) return
    try {
      await fetchCurrentUser()
    } catch {
      clearSession()
    }
  }

  function userHasRole(role: RoleCode): boolean {
    return hasRole(user.value, role)
  }

  function userHasAnyRole(roles: readonly RoleCode[]): boolean {
    return hasAnyRole(user.value, roles)
  }

  function userHasPermission(permission: string): boolean {
    return hasPermission(user.value, permission)
  }

  function userCanEnterClient(clientType: ClientType): boolean {
    return canEnterClient(user.value, clientType)
  }

  function defaultEntry(clientType?: Exclude<ClientType, 'PUBLIC'>): string {
    return resolveDefaultEntry(user.value, clientType)
  }

  return {
    accessToken,
    user,
    isAuthenticated,
    login,
    logout,
    fetchCurrentUser,
    setToken,
    clearSession,
    restore,
    hasRole: userHasRole,
    hasAnyRole: userHasAnyRole,
    hasPermission: userHasPermission,
    canEnterClient: userCanEnterClient,
    defaultEntry,
  }
})
