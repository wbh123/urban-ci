export interface AiInspectionFindingInput {
  className?: string | null
  confidence?: number | null
}

export interface AiInspectionFindingCount {
  name: string
  count: number
}

export interface AiInspectionSummaryView {
  total: number
  findings: AiInspectionFindingCount[]
  suggestion: string
}

export function buildAiInspectionSummary(items: AiInspectionFindingInput[]): AiInspectionSummaryView {
  const counts = new Map<string, number>()
  for (const item of items) {
    const name = item.className?.trim()
    if (!name) continue
    counts.set(name, (counts.get(name) ?? 0) + 1)
  }

  const findings = [...counts.entries()]
    .map(([name, count]) => ({ name, count }))
    .sort((left, right) => right.count - left.count || left.name.localeCompare(right.name, 'zh-CN'))

  return {
    total: items.length,
    findings,
    suggestion: findings.length
      ? '建议结合原图和标注区域进行人工复核；如现场证据不足，可补充近距离照片后再次分析。'
      : '当前已展示的 AI 视觉识别结果未发现明确疑似病害，仍应结合现场巡检记录进行判断。',
  }
}
