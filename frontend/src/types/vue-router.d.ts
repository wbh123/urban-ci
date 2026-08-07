import 'vue-router'
import type { ClientType, RoleCode } from '@/shared/auth/access'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    requiresAuth?: boolean
    clientType?: ClientType
    allowedRoles?: RoleCode[]
    requiredPermissions?: string[]
  }
}

export {}
