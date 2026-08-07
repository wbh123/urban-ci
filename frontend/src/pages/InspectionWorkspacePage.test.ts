import { describe, expect, it } from 'vitest'
import source from './InspectionWorkspacePage.vue?raw'

describe('InspectionWorkspacePage image and inference workflow', () => {
  it('creates and revokes a local preview URL for the selected image', () => {
    expect(source).toContain("const photoPreviewUrl = ref('')")
    expect(source).toContain('URL.createObjectURL(file)')
    expect(source).toContain('URL.revokeObjectURL(photoPreviewUrl.value)')
    expect(source).toContain('class="pending-photo-preview"')
  })

  it('renders Dify structured inference details', () => {
    expect(source).toContain('structuredResult')
    expect(source).toContain('分析摘要')
    expect(source).toContain('候选病害')
    expect(source).toContain('风险信号')
    expect(source).toContain('处置与补拍建议')
    expect(source).toContain('限制与警告')
  })

  it('loads the automatically created inference after upload', () => {
    expect(source).toContain('result.autoInference')
    expect(source).toContain('displayInferenceResult(result.assetId, automatic.inferenceId')
    expect(source).toContain('图片上传完成并已自动识别')
  })
})
