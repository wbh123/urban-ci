import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  httpGet: vi.fn(),
}))

vi.mock('../client', () => ({
  apiGet: mocks.apiGet,
  apiPost: mocks.apiPost,
  httpClient: { get: mocks.httpGet },
}))

import {
  downloadRiskReport,
  generateBuildingRiskReport,
  getRiskOverview,
  listRiskReports,
  previewBuildingRiskReport,
} from './reports'

describe('risk report endpoints', () => {
  beforeEach(() => vi.clearAllMocks())

  it('uses the frozen fifth-stage dashboard paths', async () => {
    mocks.apiGet.mockResolvedValue({})
    await getRiskOverview('ALL')
    await listRiskReports({ page: 0, size: 10 })
    await previewBuildingRiskReport('building-1')

    expect(mocks.apiGet).toHaveBeenNthCalledWith(1, '/api/v1/dashboard/risk-overview', {
      scopeType: 'ALL',
      scopeId: undefined,
    })
    expect(mocks.apiGet).toHaveBeenNthCalledWith(2, '/api/v1/risk-reports', { page: 0, size: 10 })
    expect(mocks.apiGet).toHaveBeenNthCalledWith(3, '/api/v1/risk-reports/buildings/building-1/preview')
  })

  it('generates and downloads a building report', async () => {
    mocks.apiPost.mockResolvedValue({ reportId: 'report-1' })
    mocks.httpGet.mockResolvedValue({ data: new Blob(['pdf'], { type: 'application/pdf' }) })

    await generateBuildingRiskReport('building-1', false)
    await downloadRiskReport('report-1')

    expect(mocks.apiPost).toHaveBeenCalledWith('/api/v1/risk-reports/buildings/building-1/generate', {
      force: false,
      includeEvidenceImages: true,
    })
    expect(mocks.httpGet).toHaveBeenCalledWith('/api/v1/risk-reports/report-1/download', {
      responseType: 'blob',
    })
  })
})
