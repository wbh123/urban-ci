import type { BuildingListRow } from '@/shared/api/endpoints/buildings'
import type { CommunityListRow } from '@/shared/api/endpoints/communities'

export type ArchiveHintAction =
  | 'SELECT_COMMUNITY'
  | 'SELECT_BUILDING'
  | 'COMPLETE_ARCHIVE'
  | 'COMPLETE_SPATIAL'
  | 'CREATE_INSPECTION'

export interface ArchiveHint {
  level: 'INFO' | 'ATTENTION'
  title: string
  detail: string
  action: ArchiveHintAction
}

function missingBuildingFields(building: BuildingListRow): string[] {
  const missing: string[] = []
  if (building.constructionYear === null || building.constructionYear === undefined) missing.push('建成年份')
  if (building.floorCount === null || building.floorCount === undefined) missing.push('层数')
  if (building.residentCount === null || building.residentCount === undefined) missing.push('居民数')
  return missing
}

export function buildArchiveHints(input: {
  community?: CommunityListRow | null
  building?: BuildingListRow | null
}): ArchiveHint[] {
  if (!input.community) {
    return [{
      level: 'INFO',
      title: '先确定治理对象',
      detail: '请选择需要维护的小区，再继续查看楼栋档案完整性与后续治理动作。',
      action: 'SELECT_COMMUNITY',
    }]
  }

  if (!input.building) {
    return [{
      level: 'INFO',
      title: '继续选择楼栋',
      detail: `已选择 ${input.community.communityName || '当前小区'}，请从楼栋清单选择现有楼栋，或在确认缺失后新增楼栋。`,
      action: 'SELECT_BUILDING',
    }]
  }

  const missing = missingBuildingFields(input.building)
  if (missing.length > 0) {
    return [{
      level: 'ATTENTION',
      title: '基础档案仍有缺项',
      detail: `当前楼栋缺少：${missing.join('、')}。建议先补齐核心档案，避免后续风险解释和治理判断缺少基础背景。`,
      action: 'COMPLETE_ARCHIVE',
    }]
  }

  return [
    {
      level: 'INFO',
      title: '核对空间档案',
      detail: '核心基础档案已较完整，建议继续核对楼栋空间边界是否已维护并完成人工确认。',
      action: 'COMPLETE_SPATIAL',
    },
    {
      level: 'INFO',
      title: '进入现场治理链路',
      detail: '基础档案可支撑后续治理，建议结合实际计划创建或查看巡检任务，持续补充现场证据。',
      action: 'CREATE_INSPECTION',
    },
  ]
}
