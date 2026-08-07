export const ROLE_CODES = [
  'ADMIN',
  'GOVERNMENT_MANAGER',
  'COMMUNITY_MANAGER',
  'PROPERTY_INSPECTOR',
  'EXPERT',
  'PROFESSIONAL_REVIEWER',
  'DISPOSAL_OPERATOR',
] as const

export type RoleCode = (typeof ROLE_CODES)[number]
export type ClientType = 'MOBILE' | 'CONSOLE' | 'PUBLIC'

export interface AuthPrincipal {
  roles: RoleCode[]
  permissions: string[]
}

export interface RoleEntry {
  clientType: Exclude<ClientType, 'PUBLIC'>
  path: string
  label: string
  description: string
}

const ROLE_SET = new Set<string>(ROLE_CODES)

const ROLE_PERMISSIONS: Record<RoleCode, readonly string[]> = {
  ADMIN: ['*'],
  GOVERNMENT_MANAGER: ['community:read', 'building:read', 'risk:read', 'report:read'],
  COMMUNITY_MANAGER: ['community:manage', 'building:manage', 'inspection:manage'],
  PROPERTY_INSPECTOR: ['inspection:execute', 'asset:upload'],
  EXPERT: ['inference:review', 'risk:review'],
  PROFESSIONAL_REVIEWER: ['inference:review', 'risk:review'],
  DISPOSAL_OPERATOR: ['issue:read_assigned', 'issue:handle', 'rectification:submit', 'asset:upload'],
}

const MOBILE_ROLES = new Set<RoleCode>(['PROPERTY_INSPECTOR', 'DISPOSAL_OPERATOR', 'ADMIN'])
const CONSOLE_ROLES = new Set<RoleCode>([
  'EXPERT',
  'PROFESSIONAL_REVIEWER',
  'COMMUNITY_MANAGER',
  'GOVERNMENT_MANAGER',
  'ADMIN',
])

export function normalizeRoleCodes(values: readonly string[] | null | undefined): RoleCode[] {
  if (!values) return []
  return [...new Set(values.filter((value): value is RoleCode => ROLE_SET.has(value)))]
}

export function permissionsForRoles(roles: readonly RoleCode[]): string[] {
  const permissions = new Set<string>()
  for (const role of roles) {
    for (const permission of ROLE_PERMISSIONS[role]) permissions.add(permission)
  }
  return [...permissions]
}

export function hasRole(principal: AuthPrincipal | null | undefined, role: RoleCode): boolean {
  return principal?.roles.includes(role) ?? false
}

export function hasAnyRole(
  principal: AuthPrincipal | null | undefined,
  roles: readonly RoleCode[],
): boolean {
  return roles.length === 0 || roles.some((role) => hasRole(principal, role))
}

export function hasPermission(
  principal: AuthPrincipal | null | undefined,
  permission: string,
): boolean {
  if (!principal) return false
  return principal.permissions.includes('*') || principal.permissions.includes(permission)
}

export function hasAllPermissions(
  principal: AuthPrincipal | null | undefined,
  permissions: readonly string[],
): boolean {
  return permissions.every((permission) => hasPermission(principal, permission))
}

export function canEnterClient(
  principal: AuthPrincipal | null | undefined,
  clientType: ClientType,
): boolean {
  if (clientType === 'PUBLIC') return true
  if (!principal) return false
  const allowed = clientType === 'MOBILE' ? MOBILE_ROLES : CONSOLE_ROLES
  return principal.roles.some((role) => allowed.has(role))
}

export function availableEntries(principal: AuthPrincipal | null | undefined): RoleEntry[] {
  if (!principal) return []
  const entries = new Map<string, RoleEntry>()

  if (hasAnyRole(principal, ['ADMIN', 'GOVERNMENT_MANAGER', 'COMMUNITY_MANAGER', 'EXPERT', 'PROFESSIONAL_REVIEWER'])) {
    entries.set('/console', {
      clientType: 'CONSOLE',
      path: resolveDefaultEntry(principal, 'CONSOLE'),
      label: '电脑审核管理端',
      description: '用于任务组织、专业复核、风险分析和系统管理。',
    })
  }
  if (hasAnyRole(principal, ['ADMIN', 'PROPERTY_INSPECTOR', 'DISPOSAL_OPERATOR'])) {
    entries.set('/mobile', {
      clientType: 'MOBILE',
      path: resolveDefaultEntry(principal, 'MOBILE'),
      label: '移动作业端',
      description: '用于现场采集、拍照上传和问题处置。',
    })
  }
  return [...entries.values()]
}

export function resolveDefaultEntry(
  principal: AuthPrincipal | null | undefined,
  preferredClient?: Exclude<ClientType, 'PUBLIC'>,
): string {
  if (!principal || principal.roles.length === 0) return '/unauthorized'

  if (preferredClient === 'MOBILE') {
    if (!canEnterClient(principal, 'MOBILE')) return '/client-mismatch?expected=MOBILE'
    if (hasRole(principal, 'PROPERTY_INSPECTOR')) return '/mobile/tasks'
    if (hasRole(principal, 'DISPOSAL_OPERATOR')) return '/mobile/disposal'
    return '/mobile'
  }

  if (preferredClient === 'CONSOLE') {
    if (!canEnterClient(principal, 'CONSOLE')) return '/client-mismatch?expected=CONSOLE'
    if (hasAnyRole(principal, ['EXPERT', 'PROFESSIONAL_REVIEWER']) && !hasAnyRole(principal, ['ADMIN', 'COMMUNITY_MANAGER'])) {
      return '/console/review'
    }
    if (hasRole(principal, 'COMMUNITY_MANAGER')) return '/console/inspections'
    return '/console'
  }

  if (canEnterClient(principal, 'CONSOLE')) return resolveDefaultEntry(principal, 'CONSOLE')
  if (canEnterClient(principal, 'MOBILE')) return resolveDefaultEntry(principal, 'MOBILE')
  return '/unauthorized'
}
