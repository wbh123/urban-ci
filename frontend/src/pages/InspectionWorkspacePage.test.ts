import { describe, expect, it } from 'vitest'
import source from './InspectionWorkspacePage.vue?raw'

describe('InspectionWorkspacePage image and inference workflow', () => {
  it('creates and revokes a local preview URL for the selected image', () => {
    expect(source).toContain("const photoPreviewUrl = ref('')")
    expect(source).toContain('URL.createObjectURL(file)')
    expect(source).toContain('URL.revokeObjectURL(photoPreviewUrl.value)')
    expect(source).toContain('class="pending-photo-preview"')
  })

  it('uses the shared historical image gallery instead of a second local image list', () => {
    expect(source).toContain('InspectionImageGallery')
    expect(source).toContain(':task-id="selectedTask"')
    expect(source).toContain('@result-selected="displayInferenceResult"')
    expect(source).not.toContain('v-for="img in aiImages"')
    expect(source).not.toContain('runAiInference(img.id)')
  })

  it('treats automatic inference as a background task after upload', () => {
    expect(source).toContain('automatic?.triggered && automatic.executionTaskId')
    expect(source).toContain('galleryRef.value?.trackExecution')
    expect(source).toContain('AI 正在后台分析')
    expect(source).not.toContain('displayInferenceResult(result.assetId, automatic.inferenceId')
  })

  it('keeps structured rich inference result display with irregular detection overlays', () => {
    expect(source).toContain('getInspectionImageRichResult')
    expect(source).toContain('AiDetectionOverlay')
    expect(source).toContain('resultDetections')
    expect(source).toContain('分析摘要')
    expect(source).toContain('候选病害')
  })

  it('does not render the legacy Marker map and points users to the formal spatial map', () => {
    expect(source).not.toContain("useAmap from '@/shared/composables/useAmap'")
    expect(source).not.toContain('amap.render(')
    expect(source).toContain("router.push('/console/map')")
    expect(source).toContain('正式空间地图')
  })
})
