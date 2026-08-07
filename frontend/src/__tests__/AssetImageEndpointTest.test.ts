import { http } from 'msw'
import { describe, expect, it } from 'vitest'
import { server } from '@/mocks/server'
import { ensureMockServer } from '@/tests/setup'
import { okResponse } from '@/mocks/handlers/helpers'
import { listImages } from '@/shared/api'
import { httpClient } from '@/shared/api/client'

describe('AssetImageEndpointTest', () => {
  it('兼容后端图片列表返回 assetId 字段，供工作台直接使用 id', async () => {
    await ensureMockServer()
    server.use(
      http.get('/api/v1/assets', () => okResponse({
        content: [
          {
            assetId: '8a000000-0000-4000-8000-000000000001',
            originalFilename: 'inspection.jpg',
            contentType: 'image/jpeg',
            fileSize: 128,
            storageProvider: 'LOCAL',
            createdAt: '2026-08-01T00:00:00Z',
          },
        ],
        page: { totalElements: 1 },
      })),
    )

    const previousBaseUrl = httpClient.defaults.baseURL
    httpClient.defaults.baseURL = 'http://localhost'
    try {
      const result = await listImages({ businessType: 'INSPECTION_TASK', businessId: 'task-1' })

      expect(result.content[0].assetId).toBe('8a000000-0000-4000-8000-000000000001')
      expect(result.content[0].id).toBe('8a000000-0000-4000-8000-000000000001')
    } finally {
      httpClient.defaults.baseURL = previousBaseUrl
    }
  })
})
