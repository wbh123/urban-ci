import { describe, expect, it } from 'vitest'
import { buildBuildingLifecycle, type BuildingBusinessSnapshot } from './building-lifecycle'

function baseSnapshot(overrides: Partial<BuildingBusinessSnapshot> = {}): BuildingBusinessSnapshot {
  return {
    building: {
      id: 'b-1',
      code: 'B-001',
      name: '1号楼',
      communityName: '示范小区',
    },
    inspections: [],
    analyses: [],
    assessment: { freshness: 'NO_RESULT' },
    reports: [],
    ...overrides,
  }
}

describe('buildBuildingLifecycle', () => {
  it('keeps the fixed eight-stage order and treats an existing building archive as completed', () => {
    const nodes = buildBuildingLifecycle(baseSnapshot())

    expect(nodes.map((node) => node.stage)).toEqual([
      'ARCHIVE',
      'INSPECTION',
      'ANALYSIS',
      'REVIEW',
      'ASSESSMENT',
      'DISPOSAL',
      'REINSPECTION',
      'REPORT',
    ])
    expect(nodes[0]).toMatchObject({ status: 'COMPLETED', label: '基础档案' })
    expect(nodes.slice(1).every((node) => node.status === 'NOT_STARTED')).toBe(true)
  })

  it('maps inspection tasks by active work before completed history', () => {
    const nodes = buildBuildingLifecycle(baseSnapshot({
      inspections: [
        { status: 'COMPLETED', updatedAt: '2026-08-01T08:00:00Z' },
        { status: 'IN_PROGRESS', updatedAt: '2026-08-09T08:00:00Z' },
      ],
    }))

    expect(nodes.find((node) => node.stage === 'INSPECTION')).toMatchObject({
      status: 'IN_PROGRESS',
      count: 2,
    })
  })

  it('treats AI rejected results as stable analysis terminals while failures require attention', () => {
    const stable = buildBuildingLifecycle(baseSnapshot({
      analyses: [
        { status: 'SUCCEEDED', reviewStatus: 'CONFIRMED' },
        { status: 'REJECTED', reviewStatus: 'REJECTED' },
      ],
    }))
    expect(stable.find((node) => node.stage === 'ANALYSIS')?.status).toBe('COMPLETED')
    expect(stable.find((node) => node.stage === 'REVIEW')?.status).toBe('COMPLETED')

    const failed = buildBuildingLifecycle(baseSnapshot({
      analyses: [{ status: 'FAILED', reviewStatus: 'UNREVIEWED' }],
    }))
    expect(failed.find((node) => node.stage === 'ANALYSIS')?.status).toBe('ATTENTION')
    expect(failed.find((node) => node.stage === 'REVIEW')?.status).toBe('NOT_STARTED')
  })

  it('marks unreviewed successful AI results as pending review', () => {
    const nodes = buildBuildingLifecycle(baseSnapshot({
      analyses: [{ status: 'SUCCEEDED', reviewStatus: 'UNREVIEWED' }],
    }))

    expect(nodes.find((node) => node.stage === 'ANALYSIS')?.status).toBe('COMPLETED')
    expect(nodes.find((node) => node.stage === 'REVIEW')?.status).toBe('PENDING')
  })

  it('preserves stale assessment as STALE and current manual-review demand as ATTENTION', () => {
    const stale = buildBuildingLifecycle(baseSnapshot({ assessment: { freshness: 'STALE' } }))
    expect(stale.find((node) => node.stage === 'ASSESSMENT')?.status).toBe('STALE')

    const attention = buildBuildingLifecycle(baseSnapshot({
      assessment: { freshness: 'CURRENT', needManualReview: true },
    }))
    expect(attention.find((node) => node.stage === 'ASSESSMENT')?.status).toBe('ATTENTION')
  })

  it('maps optional disposal and reinspection workflow snapshots without inventing data', () => {
    const nodes = buildBuildingLifecycle(baseSnapshot({
      disposal: { total: 2, inProgress: 1, completed: 1 },
      reinspection: { total: 1, pending: 1 },
    }))

    expect(nodes.find((node) => node.stage === 'DISPOSAL')).toMatchObject({
      status: 'IN_PROGRESS',
      count: 2,
    })
    expect(nodes.find((node) => node.stage === 'REINSPECTION')).toMatchObject({
      status: 'PENDING',
      count: 1,
    })
  })

  it('maps report generating/failed/stale/generated states with safety-first precedence', () => {
    const generating = buildBuildingLifecycle(baseSnapshot({ reports: [{ reportStatus: 'GENERATING' }] }))
    expect(generating.find((node) => node.stage === 'REPORT')?.status).toBe('IN_PROGRESS')

    const failed = buildBuildingLifecycle(baseSnapshot({ reports: [{ reportStatus: 'FAILED' }] }))
    expect(failed.find((node) => node.stage === 'REPORT')?.status).toBe('ATTENTION')

    const stale = buildBuildingLifecycle(baseSnapshot({ reports: [{ reportStatus: 'STALE' }] }))
    expect(stale.find((node) => node.stage === 'REPORT')?.status).toBe('STALE')

    const done = buildBuildingLifecycle(baseSnapshot({ reports: [{ reportStatus: 'GENERATED' }] }))
    expect(done.find((node) => node.stage === 'REPORT')?.status).toBe('COMPLETED')
  })
})
