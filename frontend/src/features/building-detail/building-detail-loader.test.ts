import { describe, expect, it, vi } from 'vitest'
import { loadBuildingDetail, type BuildingDetailSources } from './building-detail-loader'

function sources(overrides: Partial<BuildingDetailSources> = {}): BuildingDetailSources {
  return {
    getBuilding: vi.fn().mockResolvedValue({
      id: 'b-1',
      communityId: 'c-1',
      buildingCode: 'B-001',
      buildingName: '1号楼',
      address: '示范路1号',
      constructionYear: 2008,
      floorCount: 18,
      residentCount: 360,
    }),
    getCommunity: vi.fn().mockResolvedValue({ id: 'c-1', communityName: '示范小区' }),
    getBuildingBoundary: vi.fn().mockResolvedValue({ status: 'VERIFIED' }),
    listInspectionTasks: vi.fn().mockResolvedValue([
      { taskId: 't-1', status: 'COMPLETED', updatedAt: '2026-08-01T08:00:00Z' },
      { taskId: 't-2', status: 'IN_PROGRESS', updatedAt: '2026-08-09T08:00:00Z' },
    ]),
    listAiInferences: vi.fn().mockResolvedValue({
      content: [
        {
          inferenceId: 'ai-1',
          assetId: 'asset-1',
          status: 'SUCCEEDED',
          reviewStatus: 'UNREVIEWED',
          evidenceReliability: 'MODEL_UNREVIEWED',
          structuredResult: { summary: '发现疑似裂缝，需要人工复核。' },
          completedAt: '2026-08-09T08:10:00Z',
        },
      ],
      page: { page: 0, size: 50, totalElements: 1, totalPages: 1 },
    }),
    getCurrentBuildingAssessment: vi.fn().mockResolvedValue({
      buildingId: 'b-1',
      buildingCode: 'B-001',
      buildingName: '1号楼',
      communityId: 'c-1',
      communityName: '示范小区',
      freshness: 'CURRENT',
      completeness: { completenessScore: 88 },
      risk: {
        riskScore: 72.5,
        riskLevel: 'HIGH',
        confidenceScore: 81,
        needManualReview: true,
        recommendations: ['安排专业人员复核'],
        assessedAt: '2026-08-09T08:20:00Z',
      },
      renewalPriorities: [{ priorityScore: 76, priorityLevel: 'P1', generatedAt: '2026-08-09T08:21:00Z' }],
      inputSummary: {},
      disclaimer: '正式评分说明',
    }),
    listRiskReports: vi.fn().mockResolvedValue({
      content: [{ reportId: 'r-1', reportStatus: 'GENERATED', createdAt: '2026-08-09T08:30:00Z' }],
      page: { page: 0, size: 20, totalElements: 1, totalPages: 1 },
    }),
    ...overrides,
  } as BuildingDetailSources
}

describe('loadBuildingDetail', () => {
  it('aggregates existing domains into R4-2 view models without inventing disposal or reinspection data', async () => {
    const model = await loadBuildingDetail('b-1', sources())

    expect(model.summary).toMatchObject({
      buildingId: 'b-1',
      buildingCode: 'B-001',
      buildingName: '1号楼',
      communityName: '示范小区',
      floorCount: 18,
      residentCount: 360,
      spatialStatus: 'VERIFIED',
    })
    expect(model.lifecycle.find((node) => node.stage === 'INSPECTION')?.status).toBe('IN_PROGRESS')
    expect(model.lifecycle.find((node) => node.stage === 'ANALYSIS')?.status).toBe('COMPLETED')
    expect(model.lifecycle.find((node) => node.stage === 'REVIEW')?.status).toBe('PENDING')
    expect(model.lifecycle.find((node) => node.stage === 'ASSESSMENT')?.status).toBe('ATTENTION')
    expect(model.lifecycle.find((node) => node.stage === 'DISPOSAL')?.status).toBe('NOT_STARTED')
    expect(model.lifecycle.find((node) => node.stage === 'REINSPECTION')?.status).toBe('NOT_STARTED')
    expect(model.lifecycle.find((node) => node.stage === 'REPORT')?.status).toBe('COMPLETED')
    expect(model.risk).toMatchObject({
      freshness: 'CURRENT',
      riskScore: 72.5,
      riskLevel: 'HIGH',
      completenessScore: 88,
      priorityScore: 76,
      priorityLevel: 'P1',
      needManualReview: true,
    })
    expect(model.evidence).toEqual([
      expect.objectContaining({
        id: 'asset-1',
        reviewStatus: 'UNREVIEWED',
        aiAssisted: true,
      }),
    ])
    expect(model.warnings).toEqual([])
  })

  it('keeps the page usable when optional domains fail and records domain warnings', async () => {
    const model = await loadBuildingDetail('b-1', sources({
      getCommunity: vi.fn().mockRejectedValue(new Error('community unavailable')),
      getBuildingBoundary: vi.fn().mockRejectedValue(new Error('boundary unavailable')),
      listInspectionTasks: vi.fn().mockRejectedValue(new Error('inspection unavailable')),
      listAiInferences: vi.fn().mockRejectedValue(new Error('ai unavailable')),
      getCurrentBuildingAssessment: vi.fn().mockRejectedValue(new Error('assessment unavailable')),
      listRiskReports: vi.fn().mockRejectedValue(new Error('report unavailable')),
    }))

    expect(model.summary.buildingName).toBe('1号楼')
    expect(model.summary.communityName).toBe('所属小区')
    expect(model.summary.spatialStatus).toBe('NONE')
    expect(model.risk.freshness).toBe('NO_RESULT')
    expect(model.evidence).toEqual([])
    expect(model.lifecycle.find((node) => node.stage === 'INSPECTION')?.status).toBe('NOT_STARTED')
    expect(model.warnings.map((warning) => warning.domain)).toEqual(expect.arrayContaining([
      'COMMUNITY', 'SPATIAL', 'INSPECTION', 'ANALYSIS', 'ASSESSMENT', 'REPORT',
    ]))
  })

  it('does not swallow a core building archive failure', async () => {
    await expect(loadBuildingDetail('missing', sources({
      getBuilding: vi.fn().mockRejectedValue(new Error('building missing')),
    }))).rejects.toThrow('building missing')
  })

  it('deduplicates repeated AI analyses for the same asset and keeps the newest task', async () => {
    const model = await loadBuildingDetail('b-1', sources({
      listAiInferences: vi.fn().mockResolvedValue({
        content: [
          { inferenceId: 'ai-old', assetId: 'asset-1', status: 'SUCCEEDED', reviewStatus: 'CONFIRMED', evidenceReliability: 'PROFESSIONAL_REVIEWED', completedAt: '2026-08-01T00:00:00Z' },
          { inferenceId: 'ai-new', assetId: 'asset-1', status: 'SUCCEEDED', reviewStatus: 'CORRECTED', evidenceReliability: 'PROFESSIONAL_REVIEWED', completedAt: '2026-08-09T00:00:00Z' },
        ],
        page: { page: 0, size: 50, totalElements: 2, totalPages: 1 },
      }),
    }))

    expect(model.evidence).toHaveLength(1)
    expect(model.evidence[0]).toMatchObject({ id: 'asset-1', reviewStatus: 'CORRECTED' })
  })
})
