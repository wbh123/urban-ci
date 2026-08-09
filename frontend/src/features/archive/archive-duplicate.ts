export type ArchiveDuplicateReason = 'CODE' | 'NAME' | 'ADDRESS'

export interface ArchiveDuplicateValue {
  code?: string | null
  name?: string | null
  address?: string | null
}

export interface ArchiveDirectoryCandidate extends ArchiveDuplicateValue {
  id: string
}

export interface ArchiveDuplicateMatch {
  id: string
  reasons: ArchiveDuplicateReason[]
}

/**
 * 只生成疑似重复提示，不承担业务唯一性校验，也不会阻断创建。
 * 最终唯一约束和权限仍以后端为准。
 */
export function findArchiveDuplicates(
  candidate: ArchiveDuplicateValue,
  rows: ArchiveDirectoryCandidate[],
): ArchiveDuplicateMatch[] {
  const candidateCode = normalize(candidate.code)
  const candidateName = normalize(candidate.name)
  const candidateAddress = normalize(candidate.address)

  return rows.flatMap((row) => {
    const reasons: ArchiveDuplicateReason[] = []
    if (candidateCode && candidateCode === normalize(row.code)) reasons.push('CODE')
    if (candidateName && candidateName === normalize(row.name)) reasons.push('NAME')
    if (candidateAddress && candidateAddress === normalize(row.address)) reasons.push('ADDRESS')
    return reasons.length > 0 ? [{ id: row.id, reasons }] : []
  })
}

function normalize(value: string | null | undefined): string {
  return String(value ?? '')
    .trim()
    .toLowerCase()
    .replace(/[\s\-—_，,。.;；:：()（）【】\[\]]+/g, '')
}
