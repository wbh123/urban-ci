export type AiStatusTone = 'success' | 'warning' | 'danger' | 'info'

const TASK_LABELS: Record<string, string> = {
  PENDING: '等待分析',
  RUNNING: '分析中',
  SUCCEEDED: '分析完成',
  FAILED: '分析失败',
  REJECTED: '图片不适用',
  CANCELLED: '已取消',
}

const REVIEW_LABELS: Record<string, string> = {
  UNREVIEWED: '待人工复核',
  PENDING: '待人工复核',
  NEED_REVIEW: '待人工复核',
  CONFIRMED: '人工已确认',
  APPROVED: '人工已确认',
  CORRECTED: '人工已修正',
  REJECTED: '人工已排除',
}

export function aiTaskStatusLabel(status?: string | null): string {
  if (!status) return '状态待确认'
  return TASK_LABELS[status.toUpperCase()] ?? '状态待确认'
}

export function aiReviewStatusLabel(status?: string | null): string {
  if (!status) return '待人工复核'
  return REVIEW_LABELS[status.toUpperCase()] ?? '待人工复核'
}

export function aiTaskStatusTone(status?: string | null): AiStatusTone {
  switch (status?.toUpperCase()) {
    case 'SUCCEEDED':
      return 'success'
    case 'PENDING':
    case 'RUNNING':
      return 'warning'
    case 'FAILED':
      return 'danger'
    default:
      return 'info'
  }
}
