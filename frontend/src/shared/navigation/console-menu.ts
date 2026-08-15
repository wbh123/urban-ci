import type { RoleCode } from '@/shared/auth/access'

export interface ConsoleMenuItem {
  path: string
  label: string
  icon: string
  allowedRoles?: readonly RoleCode[]
}

export interface ConsoleMenuGroup {
  key: 'workspace' | 'spatial' | 'inspection' | 'risk' | 'intelligence' | 'system'
  label: string
  items: ConsoleMenuItem[]
}

const MAP_READ_ROLES: readonly RoleCode[] = [
  'EXPERT',
  'PROFESSIONAL_REVIEWER',
  'COMMUNITY_MANAGER',
  'GOVERNMENT_MANAGER',
  'ADMIN',
]
const SPATIAL_ARCHIVE_ROLES: readonly RoleCode[] = [
  'COMMUNITY_MANAGER',
  'GOVERNMENT_MANAGER',
  'ADMIN',
]
const ASSESSMENT_RULE_ROLES: readonly RoleCode[] = [
  'EXPERT',
  'PROFESSIONAL_REVIEWER',
  'COMMUNITY_MANAGER',
  'GOVERNMENT_MANAGER',
  'ADMIN',
]

const GROUPS: readonly ConsoleMenuGroup[] = [
  {
    key: 'workspace',
    label: 'AI 工作台',
    items: [
      { path: '/console', label: '管理总览', icon: '▦' },
    ],
  },
  {
    key: 'spatial',
    label: '空间治理',
    items: [
      {
        path: '/console/map',
        label: '城市地图',
        icon: '⌖',
        allowedRoles: MAP_READ_ROLES,
      },
      {
        path: '/console/archive-management',
        label: '小区与楼栋',
        icon: '▤',
        allowedRoles: SPATIAL_ARCHIVE_ROLES,
      },
      {
        path: '/console/spatial-archive',
        label: '空间档案',
        icon: '⬡',
        allowedRoles: SPATIAL_ARCHIVE_ROLES,
      },
    ],
  },
  {
    key: 'inspection',
    label: '巡检治理',
    items: [
      {
        path: '/console/inspections',
        label: '巡检管理',
        icon: '⌁',
        allowedRoles: ['COMMUNITY_MANAGER', 'ADMIN'],
      },
      {
        path: '/console/feedback',
        label: '公众反馈',
        icon: '◇',
        allowedRoles: ['COMMUNITY_MANAGER', 'GOVERNMENT_MANAGER', 'ADMIN'],
      },
      {
        path: '/console/review',
        label: 'AI 人工复核',
        icon: '✓',
        allowedRoles: ['EXPERT', 'PROFESSIONAL_REVIEWER', 'ADMIN'],
      },
    ],
  },
  {
    key: 'risk',
    label: '风险治理',
    items: [
      {
        path: '/console/renewal-priorities',
        label: '风险总览与报告',
        icon: '△',
        allowedRoles: ['GOVERNMENT_MANAGER', 'ADMIN'],
      },
      {
        path: '/console/assessment-rules',
        label: '评分规则',
        icon: '≡',
        allowedRoles: ASSESSMENT_RULE_ROLES,
      },
    ],
  },
  {
    key: 'intelligence',
    label: '智能服务',
    items: [
      {
        path: '/console/knowledge',
        label: '知识助手',
        icon: '?',
        allowedRoles: ['EXPERT', 'ADMIN'],
      },
    ],
  },
  {
    key: 'system',
    label: '系统管理',
    items: [
      {
        path: '/console/system-status',
        label: 'AI 运行状态',
        icon: '◎',
        allowedRoles: ['ADMIN'],
      },
    ],
  },
]

function canSee(item: ConsoleMenuItem, roles: readonly RoleCode[]): boolean {
  if (!item.allowedRoles || item.allowedRoles.length === 0) return true
  return item.allowedRoles.some((role) => roles.includes(role))
}

export function buildConsoleMenu(roles: readonly RoleCode[]): ConsoleMenuGroup[] {
  return GROUPS.map((group) => ({
    ...group,
    items: group.items.filter((item) => canSee(item, roles)),
  })).filter((group) => group.items.length > 0)
}

export function resolveActiveConsoleMenuPath(
  currentPath: string,
  groups: readonly ConsoleMenuGroup[],
): string {
  const matches = groups
    .flatMap((group) => group.items)
    .filter((item) => {
      if (item.path === '/console') return currentPath === '/console'
      return currentPath === item.path || currentPath.startsWith(`${item.path}/`)
    })
    .sort((left, right) => right.path.length - left.path.length)

  return matches[0]?.path ?? '/console'
}
