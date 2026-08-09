import type { RoleCode } from '@/shared/auth/access'

export type WorkspaceMetricKey = 'buildings' | 'inspections' | 'feedback' | 'reviews' | 'risk' | 'reports'
export type WorkspaceMapMode = 'GLOBAL' | 'AREA' | 'REVIEW'

export interface WorkspaceMetric {
  key: WorkspaceMetricKey
  label: string
  description: string
  path: string
}

export interface WorkspaceTodo {
  key: string
  label: string
  description: string
  path: string
  priority: 'HIGH' | 'NORMAL'
}

export interface WorkspaceConfig {
  role: RoleCode
  title: string
  description: string
  metrics: WorkspaceMetric[]
  todos: WorkspaceTodo[]
  enableRisk: boolean
  mapMode: WorkspaceMapMode
  trendTitle: string
}

const CONSOLE_ROLE_PRIORITY: readonly RoleCode[] = [
  'ADMIN',
  'COMMUNITY_MANAGER',
  'GOVERNMENT_MANAGER',
  'EXPERT',
  'PROFESSIONAL_REVIEWER',
]

function can(permissions: readonly string[], permission: string): boolean {
  return permissions.includes('*') || permissions.includes(permission)
}

function resolvePrimaryRole(roles: readonly RoleCode[]): RoleCode {
  return CONSOLE_ROLE_PRIORITY.find((role) => roles.includes(role)) ?? roles[0] ?? 'GOVERNMENT_MANAGER'
}

function riskEnabled(permissions: readonly string[]): boolean {
  return can(permissions, 'risk:read') || can(permissions, 'risk:review')
}

function riskMetric(): WorkspaceMetric {
  return {
    key: 'risk',
    label: '风险与优先级',
    description: '查看当前授权范围内的风险分层与更新优先级。',
    path: '/console/renewal-priorities',
  }
}

export function resolveWorkspaceConfig(
  roles: readonly RoleCode[],
  permissions: readonly string[],
): WorkspaceConfig {
  const role = resolvePrimaryRole(roles)
  const enableRisk = riskEnabled(permissions)

  if (role === 'ADMIN') {
    return {
      role,
      title: '系统管理工作台',
      description: '汇总全局空间档案、巡检、专业复核、公众反馈与风险业务，优先处理跨模块待办。',
      enableRisk,
      mapMode: 'GLOBAL',
      trendTitle: '全局业务与风险趋势',
      metrics: [
        { key: 'buildings', label: '空间档案', description: '小区、楼栋与确认边界', path: '/console/archive-management' },
        { key: 'inspections', label: '巡检组织', description: '巡检任务与现场进度', path: '/console/inspections' },
        { key: 'reviews', label: '专业复核', description: '待复核辅助分析结果', path: '/console/review?status=PENDING' },
        ...(enableRisk ? [riskMetric()] : []),
      ],
      todos: [
        { key: 'pending-feedback', label: '处理待受理公众反馈', description: '进入公众反馈管理并直接筛选待处理记录。', path: '/console/feedback?status=PENDING', priority: 'HIGH' },
        { key: 'pending-review', label: '处理待专业复核结果', description: '进入复核队列并直接筛选待处理结果。', path: '/console/review?status=PENDING', priority: 'HIGH' },
        { key: 'inspection-progress', label: '查看进行中巡检', description: '检查现场任务执行进度与异常情况。', path: '/console/inspections?status=IN_PROGRESS', priority: 'NORMAL' },
      ],
    }
  }

  if (role === 'COMMUNITY_MANAGER') {
    return {
      role,
      title: '社区巡检工作台',
      description: '围绕辖区楼栋档案、巡检组织和公众反馈开展日常管理。',
      enableRisk,
      mapMode: 'AREA',
      trendTitle: '辖区巡检与反馈趋势',
      metrics: [
        { key: 'buildings', label: '辖区楼栋', description: '维护小区、楼栋与已确认空间档案', path: '/console/archive-management' },
        { key: 'inspections', label: '巡检任务', description: '跟进现场巡检组织与执行进度', path: '/console/inspections' },
        { key: 'feedback', label: '公众反馈', description: '受理并跟进公众问题反馈', path: '/console/feedback?status=PENDING' },
        ...(enableRisk ? [riskMetric()] : []),
      ],
      todos: [
        { key: 'inspection-progress', label: '跟进进行中巡检', description: '直接查看当前仍在现场执行的巡检任务。', path: '/console/inspections?status=IN_PROGRESS', priority: 'HIGH' },
        { key: 'pending-feedback', label: '受理待处理公众反馈', description: '直接筛选当前尚未完成受理的公众反馈。', path: '/console/feedback?status=PENDING', priority: 'HIGH' },
      ],
    }
  }

  if (role === 'GOVERNMENT_MANAGER') {
    return {
      role,
      title: '区域风险工作台',
      description: '面向区域监管查看空间档案、公众反馈、风险分层和楼栋报告。',
      enableRisk,
      mapMode: 'GLOBAL',
      trendTitle: enableRisk ? '区域风险与报告趋势' : '区域业务趋势',
      metrics: [
        { key: 'buildings', label: '区域楼栋', description: '查看授权区域内的小区和楼栋档案', path: '/console/archive-management' },
        { key: 'feedback', label: '公众反馈', description: '查看区域公众问题反馈与处理状态', path: '/console/feedback' },
        ...(enableRisk ? [riskMetric()] : []),
        ...(can(permissions, 'report:read') ? [{ key: 'reports' as const, label: '楼栋报告', description: '查看风险总览并预览、生成和下载报告', path: '/console/renewal-priorities' }] : []),
      ],
      todos: [
        ...(enableRisk ? [{ key: 'risk-overview', label: '查看区域风险总览', description: '按风险等级检查重点楼栋和更新优先级。', path: '/console/renewal-priorities', priority: 'HIGH' as const }] : []),
        { key: 'feedback-overview', label: '查看区域公众反馈', description: '进入公众反馈管理掌握问题处置情况。', path: '/console/feedback', priority: 'NORMAL' },
      ],
    }
  }

  return {
    role,
    title: '专业复核工作台',
    description: '集中处理辅助分析结果复核，结合现场证据和楼栋空间档案完成专业判断。',
    enableRisk,
    mapMode: 'REVIEW',
    trendTitle: '复核任务趋势',
    metrics: [
      { key: 'reviews', label: '专业复核', description: '集中查看待处理和已完成的复核结果', path: '/console/review?status=PENDING' },
      { key: 'buildings', label: '楼栋档案', description: '从空间地图核对楼栋基础档案与现场证据', path: '/console/map' },
      ...(enableRisk ? [{ ...riskMetric(), path: '/console/map' }] : []),
    ],
    todos: [
      { key: 'pending-review', label: '处理待专业复核结果', description: '直接进入待处理辅助分析复核队列。', path: '/console/review?status=PENDING', priority: 'HIGH' },
      { key: 'map-review', label: '从地图核对楼栋证据', description: '在统一空间地图中打开楼栋档案进行交叉核对。', path: '/console/map', priority: 'NORMAL' },
    ],
  }
}
