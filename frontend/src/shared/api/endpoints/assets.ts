import { apiGet, apiPost, request } from '../client'

export interface AssetAutoInferenceResult {
  enabled: boolean
  triggered: boolean
  queued?: boolean
  modelId: string
  executionTaskId?: string | null
  inferenceId?: string | null
  status?: string | null
  message: string
}

// 适配类型：phase2 图片上传响应在 openapi-phase2.yaml 中仅有 example，尚无正式 schema。
export interface AssetImageUploadResult {
  assetId: string
  originalFilename: string
  contentType: string
  storageProvider: string
  autoInference?: AssetAutoInferenceResult
}

export interface AssetImageRow {
  assetId: string
  id: string
  originalFilename: string
  contentType: string
  fileSize: number
  storageProvider: string
  bindingRole?: string
  createdAt: string
  previewUrl?: string
}

export interface UploadImageInput {
  file: File
  businessType: string
  businessId: string
  bindingRole?: string
}

type RawAssetImageRow = Omit<AssetImageRow, 'id'> & { id?: string }

type RawAssetImageResponse =
  | RawAssetImageRow[]
  | {
      content?: RawAssetImageRow[]
      page?: { totalElements?: number }
    }

export function uploadImage(input: UploadImageInput): Promise<AssetImageUploadResult> {
  const form = new FormData()
  form.append('file', input.file)
  form.append('businessType', input.businessType)
  form.append('businessId', input.businessId)
  if (input.bindingRole) form.append('bindingRole', input.bindingRole)
  // 图片上传只负责持久化资产并可选创建后台 AI 任务，不等待分钟级模型执行。
  // FormData 不显式设置 Content-Type，交由浏览器附加 multipart 边界。
  return apiPost<AssetImageUploadResult>('/api/v1/assets/images', form)
}

/** 按业务对象查询已上传图片列表。后端第二阶段接口当前直接返回数组，同时兼容未来分页结构。 */
export async function listImages(params: {
  businessType?: string
  businessId?: string
} = {}): Promise<{ content: AssetImageRow[]; page: { totalElements: number } }> {
  const response = await apiGet<RawAssetImageResponse>(
    '/api/v1/assets',
    params as Record<string, unknown>,
  )
  const rows = Array.isArray(response) ? response : response.content ?? []
  const content = rows.map((item) => ({
    ...item,
    assetId: item.assetId ?? item.id ?? '',
    id: item.id ?? item.assetId ?? '',
  }))
  return {
    content,
    page: {
      totalElements: Array.isArray(response)
        ? content.length
        : response.page?.totalElements ?? content.length,
    },
  }
}

/** 获取图片内容，返回 Blob URL 供 <img> 显示；调用方负责在组件卸载时释放。 */
export async function fetchImageBlobUrl(assetId: string): Promise<string> {
  const response = await request<Blob>({
    method: 'get',
    url: `/api/v1/assets/${encodeURIComponent(assetId)}/content`,
    responseType: 'blob',
  })
  if (!(response instanceof Blob) || response.size === 0) {
    throw new Error('图片内容为空或已不可用')
  }
  return URL.createObjectURL(response)
}
