export type AiAnalysisSectionKey =
  | 'core'
  | 'building'
  | 'inspection'
  | 'risk'
  | 'vision'
  | 'basis'
  | 'review'
  | 'limits'
  | 'other'

export interface AiAnalysisParagraphBlock {
  type: 'paragraph'
  text: string
}

export interface AiAnalysisListBlock {
  type: 'list'
  ordered: boolean
  items: string[]
}

export interface AiAnalysisTableBlock {
  type: 'table'
  headers: string[]
  rows: string[][]
}

export type AiAnalysisBlock =
  | AiAnalysisParagraphBlock
  | AiAnalysisListBlock
  | AiAnalysisTableBlock

export interface AiAnalysisSection {
  key: AiAnalysisSectionKey
  title: string
  blocks: AiAnalysisBlock[]
}

export interface AiAnalysisDocument {
  structured: boolean
  sections: AiAnalysisSection[]
  raw: string
}

const HEADING_PATTERN = /^##\s+(.+?)\s*$/
const UNORDERED_LIST_PATTERN = /^[-*]\s+(.+)$/
const ORDERED_LIST_PATTERN = /^\d+[.)、]\s*(.+)$/
const TABLE_SEPARATOR_CELL = /^:?-{3,}:?$/

const SECTION_MATCHERS: Array<[AiAnalysisSectionKey, RegExp]> = [
  ['core', /(核心结论|综合结论|当前结论)/],
  ['building', /(楼栋概况|楼栋基本信息|楼栋信息)/],
  ['inspection', /(巡检证据|巡检证据概况|数据获取情况)/],
  ['risk', /(风险与优先级|历史正式风险|风险和优先级|正式风险.*优先级)/],
  ['vision', /(视觉病害|实时视觉分析|疑似病害|视觉分析结果)/],
  ['basis', /(判断依据|研判依据|依据小结)/],
  ['review', /(人工复核建议|复核建议|人工建议)/],
  ['limits', /(能力限制|能力与限制|限制说明|信息不足)/],
]

export function parseAiAnalysisAnswer(answer: string): AiAnalysisDocument {
  const raw = answer ?? ''
  const lines = normalizeLines(raw)
  const headingIndexes = lines
    .map((line, index) => (HEADING_PATTERN.test(line) ? index : -1))
    .filter((index) => index >= 0)

  if (!headingIndexes.length) {
    return {
      structured: false,
      sections: [
        {
          key: 'other',
          title: '综合说明',
          blocks: parseBlocks(lines),
        },
      ],
      raw,
    }
  }

  const sections: AiAnalysisSection[] = []
  for (let position = 0; position < headingIndexes.length; position += 1) {
    const headingIndex = headingIndexes[position]
    const nextHeadingIndex = headingIndexes[position + 1] ?? lines.length
    const match = lines[headingIndex].match(HEADING_PATTERN)
    if (!match) continue
    const title = cleanInlineMarkdown(match[1])
    const blocks = parseBlocks(lines.slice(headingIndex + 1, nextHeadingIndex))
    if (!blocks.length) continue
    sections.push({
      key: resolveSectionKey(title),
      title: normalizeDisplayTitle(title),
      blocks,
    })
  }

  const preamble = parseBlocks(lines.slice(0, headingIndexes[0]))
  if (preamble.length && !sections.some((section) => section.key === 'core')) {
    sections.unshift({ key: 'core', title: '核心结论', blocks: preamble })
  }

  if (!sections.length) {
    return {
      structured: false,
      sections: [{ key: 'other', title: '综合说明', blocks: parseBlocks(lines) }],
      raw,
    }
  }

  return { structured: true, sections, raw }
}

export function sectionTone(key: AiAnalysisSectionKey): 'primary' | 'warning' | 'danger' | 'muted' | 'normal' {
  if (key === 'core') return 'primary'
  if (key === 'vision' || key === 'review') return 'warning'
  if (key === 'limits') return 'muted'
  if (key === 'risk') return 'danger'
  return 'normal'
}

function normalizeLines(value: string): string[] {
  return value
    .replace(/\r\n?/g, '\n')
    .split('\n')
    .map((line) => line.trim())
}

function resolveSectionKey(title: string): AiAnalysisSectionKey {
  const comparable = stripHeadingNumber(title)
  for (const [key, matcher] of SECTION_MATCHERS) {
    if (matcher.test(comparable)) return key
  }
  return 'other'
}

function normalizeDisplayTitle(title: string): string {
  return stripHeadingNumber(title)
    .replace(/[（(](?:重要|关键信息缺失)[）)]/g, '')
    .trim()
}

function stripHeadingNumber(title: string): string {
  return title
    .replace(/^第?[一二三四五六七八九十]+[、.．：:]\s*/, '')
    .replace(/^\d+[、.．：:]\s*/, '')
    .trim()
}

function parseBlocks(lines: string[]): AiAnalysisBlock[] {
  const blocks: AiAnalysisBlock[] = []
  let index = 0

  while (index < lines.length) {
    const line = lines[index]
    if (!line || isHorizontalRule(line)) {
      index += 1
      continue
    }

    if (isTableRow(line) && isTableSeparator(lines[index + 1])) {
      const table = parseTable(lines, index)
      blocks.push(table.block)
      index = table.nextIndex
      continue
    }

    const unordered = line.match(UNORDERED_LIST_PATTERN)
    const ordered = line.match(ORDERED_LIST_PATTERN)
    if (unordered || ordered) {
      const orderedList = Boolean(ordered)
      const items: string[] = []
      while (index < lines.length) {
        const itemLine = lines[index]
        const match = orderedList
          ? itemLine.match(ORDERED_LIST_PATTERN)
          : itemLine.match(UNORDERED_LIST_PATTERN)
        if (!match) break
        items.push(cleanInlineMarkdown(match[1]))
        index += 1
      }
      blocks.push({ type: 'list', ordered: orderedList, items })
      continue
    }

    const paragraphLines: string[] = []
    while (index < lines.length) {
      const current = lines[index]
      if (!current || isHorizontalRule(current) || HEADING_PATTERN.test(current)) break
      if (isTableRow(current) && isTableSeparator(lines[index + 1])) break
      if (UNORDERED_LIST_PATTERN.test(current) || ORDERED_LIST_PATTERN.test(current)) break
      paragraphLines.push(cleanInlineMarkdown(current))
      index += 1
    }
    if (paragraphLines.length) {
      blocks.push({ type: 'paragraph', text: paragraphLines.join(' ') })
      continue
    }
    index += 1
  }

  return blocks
}

function parseTable(lines: string[], startIndex: number): { block: AiAnalysisTableBlock; nextIndex: number } {
  const headers = parseTableCells(lines[startIndex])
  const rows: string[][] = []
  let index = startIndex + 2
  while (index < lines.length && isTableRow(lines[index])) {
    rows.push(parseTableCells(lines[index]))
    index += 1
  }
  return {
    block: { type: 'table', headers, rows },
    nextIndex: index,
  }
}

function parseTableCells(line: string): string[] {
  const normalized = line.trim().replace(/^\|/, '').replace(/\|$/, '')
  return normalized.split('|').map((cell) => cleanInlineMarkdown(cell.trim()))
}

function isTableRow(line?: string): boolean {
  if (!line) return false
  const trimmed = line.trim()
  return trimmed.startsWith('|') && trimmed.endsWith('|') && trimmed.split('|').length >= 4
}

function isTableSeparator(line?: string): boolean {
  if (!isTableRow(line)) return false
  return parseTableCells(line ?? '').every((cell) => TABLE_SEPARATOR_CELL.test(cell))
}

function isHorizontalRule(line: string): boolean {
  return /^-{3,}$/.test(line)
}

function cleanInlineMarkdown(text: string): string {
  return text
    .replace(/\*\*(.+?)\*\*/g, '$1')
    .replace(/__(.+?)__/g, '$1')
    .replace(/`([^`]+)`/g, '$1')
    .replace(/\[([^\]]+)]\([^\s)]+\)/g, '$1')
    .trim()
}
