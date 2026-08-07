import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  request: vi.fn(),
}))

vi.mock('../client', () => mocks)

import { fetchImageBlobUrl, listImages, uploadImage } from './assets'

describe('image asset endpoints', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('uses a timeout that covers synchronous automatic inference', async () => {
    const file = new File(['image'], 'wall.jpg', { type: 'image/jpeg' })
    mocks.apiPost.mockResolvedValue({ assetId: 'asset-1' })

    await uploadImage({
      file,
      businessType: 'INSPECTION_TASK',
      businessId: 'task-1',
      bindingRole: 'INSPECTION_PHOTO',
    })

    expect(mocks.apiPost).toHaveBeenCalledWith(
      '/api/v1/assets/images',
      expect.any(FormData),
      { timeout: 45_000 },
    )
  })

  it('normalizes the array returned by the asset collection endpoint', async () => {
    mocks.apiGet.mockResolvedValue([
      { assetId: 'asset-1', originalFilename: 'wall.jpg' },
    ])

    const result = await listImages({ businessType: 'INSPECTION_TASK', businessId: 'task-1' })

    expect(mocks.apiGet).toHaveBeenCalledWith('/api/v1/assets', {
      businessType: 'INSPECTION_TASK',
      businessId: 'task-1',
    })
    expect(result.page.totalElements).toBe(1)
    expect(result.content[0]).toMatchObject({ assetId: 'asset-1', id: 'asset-1' })
  })

  it('keeps compatibility with a paged asset response', async () => {
    mocks.apiGet.mockResolvedValue({
      content: [{ assetId: 'asset-2', originalFilename: 'column.jpg' }],
      page: { totalElements: 1 },
    })

    const result = await listImages({ businessType: 'INSPECTION_TASK', businessId: 'task-1' })

    expect(result.page.totalElements).toBe(1)
    expect(result.content[0]).toMatchObject({ assetId: 'asset-2', id: 'asset-2' })
  })

  it('downloads image content through the asset content endpoint', async () => {
    const blob = new Blob(['image-bytes'], { type: 'image/jpeg' })
    const createObjectURL = vi.fn().mockReturnValue('blob:asset-1')
    Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: createObjectURL })
    mocks.request.mockResolvedValue(blob)

    await expect(fetchImageBlobUrl('asset-1')).resolves.toBe('blob:asset-1')
    expect(mocks.request).toHaveBeenCalledWith({
      method: 'get',
      url: '/api/v1/assets/asset-1/content',
      responseType: 'blob',
    })
    expect(createObjectURL).toHaveBeenCalledWith(blob)
  })

  it('rejects an empty image response', async () => {
    mocks.request.mockResolvedValue(new Blob([], { type: 'image/jpeg' }))

    await expect(fetchImageBlobUrl('asset-1')).rejects.toThrow('图片内容为空')
  })
})
