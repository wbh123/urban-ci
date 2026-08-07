import { HttpResponse, http } from 'msw'
import { okResponse, errorResponse, requireAuth } from './helpers'

const ALLOWED_CONTENT_TYPES = ['image/jpeg', 'image/png', 'image/webp']
const DEMO_ASSET_IDS = new Set([
  '44444444-4444-4444-4444-444444444444',
  '80000000-0000-0000-0000-000000000001',
  '80000000-0000-0000-0000-000000000002',
])

// 1x1 PNG，用于 mock 图片预览；避免依赖外部静态资源或真实对象存储。
const PNG_BYTES = Uint8Array.from([
  137, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82,
  0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0, 31, 21, 196,
  137, 0, 0, 0, 13, 73, 68, 65, 84, 120, 156, 99, 248, 207, 192,
  240, 31, 0, 5, 0, 1, 255, 137, 153, 61, 29, 0, 0, 0, 0,
  73, 69, 78, 68, 174, 66, 96, 130,
])

export const assetHandlers = [
  http.post('/api/v1/assets/images', async ({ request }) => {
    const unauth = requireAuth(request)
    if (unauth) return unauth
    const formData = await request.formData().catch(() => null)
    if (!formData) return errorResponse('BAD_REQUEST', '缺少图片表单。', 400)
    const file = formData.get('file')
    const businessType = formData.get('businessType')
    const businessId = formData.get('businessId')
    if (!file || !(file instanceof File)) {
      return errorResponse('BAD_REQUEST', '缺少图片文件。', 400)
    }
    if (!businessType || !businessId) {
      return errorResponse('BAD_REQUEST', '缺少业务绑定信息。', 400)
    }
    if (!ALLOWED_CONTENT_TYPES.includes(file.type)) {
      return errorResponse('UNSUPPORTED_MEDIA_TYPE', '仅支持 JPEG、PNG 或 WebP 图片。', 415)
    }
    return okResponse(
      {
        assetId: '44444444-4444-4444-4444-444444444444',
        originalFilename: file.name,
        contentType: file.type,
        storageProvider: 'LOCAL',
      },
      201,
    )
  }),

  http.get('/api/v1/assets/images', ({ request }) => {
    const unauth = requireAuth(request)
    if (unauth) return unauth
    return okResponse({
      content: [
        {
          assetId: '80000000-0000-0000-0000-000000000001',
          originalFilename: 'mock-ai-review-001.png',
          contentType: 'image/png',
          fileSize: PNG_BYTES.byteLength,
          storageProvider: 'MOCK',
          createdAt: new Date().toISOString(),
        },
      ],
      page: { totalElements: 1 },
    })
  }),

  http.get('/api/v1/assets/images/:assetId/content', ({ request, params }) => {
    const unauth = requireAuth(request)
    if (unauth) return unauth
    const assetId = String(params.assetId)
    if (!DEMO_ASSET_IDS.has(assetId)) return errorResponse('ASSET_NOT_FOUND', '图片不存在。', 404)
    return new HttpResponse(PNG_BYTES, {
      status: 200,
      headers: {
        'Content-Type': 'image/png',
        'Content-Length': String(PNG_BYTES.byteLength),
      },
    })
  }),

]
