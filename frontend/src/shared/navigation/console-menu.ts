import type { RoleCode } from '@/shared/auth/access'

export interface ConsoleMenuItem {
  path: string
  label: string
  icon: string
  allowedRoles?: readonly RoleCode[]
}

export interface ConsoleMenuGroup {
  key: 'workspace' | 'housing' | 'risk' | 'decision' | 'system'
  label: string
  items: ConsoleMenuItem[]
}

const GROUPS: readonly ConsoleMenuGroup[] = [
  {
    key: 'workspace',
    label: '工作台',
    items: [{ path: '/console', label: '管理总览', icon: '▦' }],
  },
  {
    key: 'housing',
    label: '房屋治理',
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
        path: '/console/renewal-priorities',
        label: '风险总览',
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
      {
        path: '/console/legacy-workspace',
        label: '兼容工作台',
        icon: '↗',
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
