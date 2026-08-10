import type { RoleCode } from '@/shared/auth/access'

export interface ConsoleMenuItem {
  path: string
  label: string
  icon: string
  allowedRoles?: readonly RoleCode[]
}

export interface ConsoleMenuGroup {
  key: 'workspace' | 'archive' | 'inspection' | 'risk' | 'decision' | 'system'
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

const GROUPS: readonly ConsoleMenuGroup[] = [
  {
    key: 'workspace',
    label: '工作台',
    items: [
      { path: '/console', label: '管理总览', icon: '▦' },
    ],
  },
  {
    key: 'archive',
    label: '基础建档',
    items: [
      {
        path: '/console/archive-management',
        label: '小区与楼栋管理',
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
    ],
  },
  {
    key: 'risk',
    label: '风险研判',
    items: [
      {
        path: '/console/review',
        label: '专业复核',
        icon: '✓',
        allowedRoles: ['EXPERT', 'PROFESSIONAL_REVIEWER', 'ADMIN'],
      },
      {
        path: '/console/map',
        label: '地图展示',
        icon: '⌖',
        allowedRoles: MAP_READ_ROLES,
      },
      {
        path: '/console/renewal-priorities',
        label: '更新优先级',
        icon: '△',
        allowedRoles: ['GOVERNMENT_MANAGER', 'ADMIN'],
      },
    ],
  },
  {
    key: 'decision',
    label: '辅助决策',
    items: [
      {
        path: '/console/assessment-rules',
        label: '评分规则',
        icon: '≡',
        allowedRoles: [
          'EXPERT',
          'PROFESSIONAL_REVIEWER',
          'COMMUNITY_MANAGER',
          'GOVERNMENT_MANAGER',
          'ADMIN',
        ],
      },
      {
        path: '/console/knowledge',
        label: '知识问答',
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
        label: '系统状态',
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
