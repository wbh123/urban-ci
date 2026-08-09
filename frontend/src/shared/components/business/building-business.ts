export interface BuildingSummaryView {
  buildingId: string
  buildingCode: string
  buildingName: string
  communityName: string
  address?: string
  constructionYear?: number
  floorCount?: number
  residentCount?: number
  spatialStatus?: 'VERIFIED' | 'UNVERIFIED' | 'REJECTED' | 'NONE'
}

export interface BuildingRiskSummaryView {
  freshness: 'CURRENT' | 'STALE' | 'NO_RESULT'
  riskScore?: number
  riskLevel?: string
  confidenceScore?: number
  completenessScore?: number
  priorityScore?: number
  priorityLevel?: string
  needManualReview?: boolean
  recommendations?: string[]
  assessedAt?: string
}

export interface BuildingEvidenceGalleryItem {
  id: string
  title: string
  previewUrl?: string
  sourceLabel: string
  reviewStatus?: string
  reliabilityLabel?: string
  aiAssisted?: boolean
  capturedAt?: string
}
