import { describe, expect, it } from 'vitest'
import source from './ConsoleSystemStatusPage.vue?raw'

describe('ConsoleSystemStatusPage AI routing contract', () => {
  it('uses FastAPI vision readiness for upload automation', () => {
    expect(source).toContain("item.providerCode === 'FAST_API'")
    expect(source).toContain("item.capabilities.includes('VISION_INFERENCE')")
    expect(source).toContain('visionReady')
    expect(source).not.toContain('difyReady')
  })

  it('shows business-friendly provider and capability labels', () => {
    expect(source).toContain("FAST_API: '本地视觉模型'")
    expect(source).toContain("SPRING_AI: 'DeepSeek 文本模型'")
    expect(source).toContain("DIFY: '智能工作流'")
    expect(source).toContain("VISION_INFERENCE: '视觉识别'")
    expect(source).toContain("TEXT_GENERATION: '文本研判'")
    expect(source).toContain("WORKFLOW: '工作流'")
  })

  it('explains that local visual readiness controls automatic image inference', () => {
    expect(source).toContain('本地视觉模型服务尚未就绪')
    expect(source).toContain('识别失败不会回滚图片上传')
  })
})
