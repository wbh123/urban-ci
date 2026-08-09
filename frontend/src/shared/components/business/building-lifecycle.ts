export type BuildingLifecycleStage =
  | 'ARCHIVE'
  | 'INSPECTION'
  | 'ANALYSIS'
  | 'REVIEW'
  | 'ASSESSMENT'
  | 'DISPOSAL'
  | 'REINSPECTION'
  | 'REPORT'

export type BuildingLifecycleStatus =
  | 'NOT_STARTED'
  | 'PENDING'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'ATTENTION'
  | 'STALE'

export interface BuildingIdentitySnapshot {
  id: string
  code: string
  name: string
  communityName: string
  address?: string
  constructionYear?: number
  floorCount?: number
  residentCount?: number
}

export interface InspectionLifecycleItem {
  status: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'
  updatedAt?: string
}

export interface AnalysisLifecycleItem {
  status: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'REJECTED' | 'CANCELLED'
  reviewStatus?: 'UNREVIEWED' | 'CONFIRMED' | 'CORRECTED' | 'REJECTED'
  updatedAt?: string
}

export interface AssessmentLifecycleSnapshot {
  freshness: 'CURRENT' | 'STALE' | 'NO_RESULT'
  needManualReview?: boolean
  updatedAt?: string
}

export interface GenericWorkflowSnapshot {
  total: number
  pending?: number
  inProgress?: number
  completed?: number
  attention?: number
  stale?: number
  updatedAt?: string
}

export interface ReportLifecycleItem {
  reportStatus: 'GENERATING' | 'GENERATED' | 'FAILED' | 'STALE'
  updatedAt?: string
}

export interface BuildingBusinessSnapshot {
  building: BuildingIdentitySnapshot
  inspections: InspectionLifecycleItem[]
  analyses: AnalysisLifecycleItem[]
  assessment: AssessmentLifecycleSnapshot
  disposal?: GenericWorkflowSnapshot
  reinspection?: GenericWorkflowSnapshot
  reports: ReportLifecycleItem[]
}

export interface BuildingLifecycleNode {
  stage: BuildingLifecycleStage
  label: string
  status: BuildingLifecycleStatus
  count: number
  description: string
  updatedAt?: string
}

const STAGE_LABELS: Record<BuildingLifecycleStage, string> = {
  ARCHIVE: '基础档案',
  INSPECTION: '现场巡检',
  ANALYSIS: '辅助分析',
  REVIEW: '人工复核',
  ASSESSMENT: '正式评分',
  DISPOSAL: '处置整改',
  REINSPECTION: '复查复验',
  REPORT: '报告归档',
}

const STATUS_TEXT: Record<BuildingLifecycleStatus, string> = {
  NOT_STARTED: '尚未开始',
  PENDING: '等待处理',
  IN_PROGRESS: '处理中',
  COMPLETED: '已完成',
  ATTENTION: '需要关注',
  STALE: '结果已过期',
}

export function buildBuildingLifecycle(snapshot: BuildingBusinessSnapshot): BuildingLifecycleNode[] {
  const inspection = inspectionNode(snapshot.inspections)
  const analysis = analysisNode(snapshot.analyses)
  const review = reviewNode(snapshot.analyses)
  const assessment = assessmentNode(snapshot.assessment)
  const disposal = genericNode('DISPOSAL', snapshot.disposal)
  const reinspection = genericNode('REINSPECTION', snapshot.reinspection)
  const report = reportNode(snapshot.reports)

  return [
    node('ARCHIVE', 'COMPLETED', 1, '楼栋主档已建立。'),
    inspection,
    analysis,
    review,
    assessment,
    disposal,
    reinspection,
    report,
  ]
}

function inspectionNode(items: InspectionLifecycleItem[]): BuildingLifecycleNode {
  if (!items.length) return node('INSPECTION', 'NOT_STARTED', 0)
  if (items.some((item) => item.status === 'IN_PROGRESS')) {
    return node('INSPECTION', 'IN_PROGRESS', items.length, undefined, latest(items))
  }
  if (items.some((item) => item.status === 'PENDING')) {
    return node('INSPECTION', 'PENDING', items.length, undefined, latest(items))
  }
  if (items.some((item) => item.status === 'COMPLETED')) {
    return node('INSPECTION', 'COMPLETED', items.length, undefined, latest(items))
  }
  return node('INSPECTION', 'NOT_STARTED', items.length, '当前仅有已取消巡检任务。', latest(items))
}

function analysisNode(items: AnalysisLifecycleItem[]): BuildingLifecycleNode {
  if (!items.length) return node('ANALYSIS', 'NOT_STARTED', 0)
  if (items.some((item) => item.status === 'FAILED')) {
    return node('ANALYSIS', 'ATTENTION', items.length, '存在辅助分析执行失败，需要检查后再处理。', latest(items))
  }
  if (items.some((item) => item.status === 'RUNNING')) {
    return node('ANALYSIS', 'IN_PROGRESS', items.length, '辅助分析正在执行；结果不作为正式鉴定结论。', latest(items))
  }
  if (items.some((item) => item.status === 'PENDING')) {
    return node('ANALYSIS', 'PENDING', items.length, '辅助分析等待执行；结果需结合人工复核。', latest(items))
  }
  if (items.some((item) => item.status === 'SUCCEEDED' || item.status === 'REJECTED')) {
    return node('ANALYSIS', 'COMPLETED', items.length, '已有稳定辅助分析终态；结果不作为正式鉴定结论。', latest(items))
  }
  return node('ANALYSIS', 'NOT_STARTED', items.length, '当前仅有已取消辅助分析任务。', latest(items))
}

function reviewNode(items: AnalysisLifecycleItem[]): BuildingLifecycleNode {
  const reviewable = items.filter((item) => item.status === 'SUCCEEDED' || item.status === 'REJECTED')
  if (!reviewable.length) return node('REVIEW', 'NOT_STARTED', 0)
  const unreviewed = reviewable.filter((item) => !item.reviewStatus || item.reviewStatus === 'UNREVIEWED')
  if (unreviewed.length) {
    return node('REVIEW', 'PENDING', reviewable.length, `${unreviewed.length} 项辅助分析等待人工复核。`, latest(reviewable))
  }
  return node('REVIEW', 'COMPLETED', reviewable.length, '当前稳定辅助分析结果已完成人工复核。', latest(reviewable))
}

function assessmentNode(snapshot: AssessmentLifecycleSnapshot): BuildingLifecycleNode {
  if (snapshot.freshness === 'NO_RESULT') return node('ASSESSMENT', 'NOT_STARTED', 0)
  if (snapshot.freshness === 'STALE') {
    return node('ASSESSMENT', 'STALE', 1, '现有正式评分已过期，需要重新计算后再用于研判。', snapshot.updatedAt)
  }
  if (snapshot.needManualReview) {
    return node('ASSESSMENT', 'ATTENTION', 1, '当前正式评分提示需要人工复核。', snapshot.updatedAt)
  }
  return node('ASSESSMENT', 'COMPLETED', 1, '当前正式评分结果有效。', snapshot.updatedAt)
}

function genericNode(stage: 'DISPOSAL' | 'REINSPECTION', workflow?: GenericWorkflowSnapshot): BuildingLifecycleNode {
  if (!workflow || workflow.total <= 0) return node(stage, 'NOT_STARTED', 0)
  if ((workflow.attention ?? 0) > 0) return node(stage, 'ATTENTION', workflow.total, undefined, workflow.updatedAt)
  if ((workflow.stale ?? 0) > 0) return node(stage, 'STALE', workflow.total, undefined, workflow.updatedAt)
  if ((workflow.inProgress ?? 0) > 0) return node(stage, 'IN_PROGRESS', workflow.total, undefined, workflow.updatedAt)
  if ((workflow.pending ?? 0) > 0) return node(stage, 'PENDING', workflow.total, undefined, workflow.updatedAt)
  const completed = workflow.completed ?? 0
  if (completed >= workflow.total) return node(stage, 'COMPLETED', workflow.total, undefined, workflow.updatedAt)
  if (completed > 0) return node(stage, 'IN_PROGRESS', workflow.total, '已有部分事项完成，其余事项仍待推进。', workflow.updatedAt)
  return node(stage, 'NOT_STARTED', workflow.total, undefined, workflow.updatedAt)
}

function reportNode(items: ReportLifecycleItem[]): BuildingLifecycleNode {
  if (!items.length) return node('REPORT', 'NOT_STARTED', 0)
  if (items.some((item) => item.reportStatus === 'FAILED')) {
    return node('REPORT', 'ATTENTION', items.length, '存在报告生成失败，需要重新处理。', latest(items))
  }
  if (items.some((item) => item.reportStatus === 'GENERATING')) {
    return node('REPORT', 'IN_PROGRESS', items.length, undefined, latest(items))
  }
  if (items.some((item) => item.reportStatus === 'STALE')) {
    return node('REPORT', 'STALE', items.length, '现有报告已过期，应基于最新正式评分重新生成。', latest(items))
  }
  return node('REPORT', 'COMPLETED', items.length, '已有可用报告归档。', latest(items))
}

function node(
  stage: BuildingLifecycleStage,
  status: BuildingLifecycleStatus,
  count: number,
  description = STATUS_TEXT[status],
  updatedAt?: string,
): BuildingLifecycleNode {
  return {
    stage,
    label: STAGE_LABELS[stage],
    status,
    count,
    description,
    ...(updatedAt ? { updatedAt } : {}),
  }
}

function latest(items: Array<{ updatedAt?: string }>): string | undefined {
  return items
    .map((item) => item.updatedAt)
    .filter((value): value is string => Boolean(value))
    .sort()
    .at(-1)
}
