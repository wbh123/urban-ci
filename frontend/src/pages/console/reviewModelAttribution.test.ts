import { describe, expect, it } from 'vitest'
import { resolveModelAttribution } from '@/pages/console/reviewModelAttribution'

describe('resolveModelAttribution', () => {
  it('1.1.0 REAL 显示 Base/Base+ 精度优先运行时', () => {
    expect(resolveModelAttribution({
      mode: 'REAL',
      providerCode: 'FAST_API',
      modelId: 'AI-VISION-LOCAL-001',
      modelName: 'AI-VISION-LOCAL-001',
      modelVersion: '1.1.0',
    })).toBe('本地视觉模型 · Grounding DINO Base + SAM 2.1 Hiera Base+ · 精度优先 · PyTorch · CUDA')
  })

  it('1.0.0 REAL 继续正确显示 Tiny 回滚运行时', () => {
    expect(resolveModelAttribution({
      mode: 'REAL',
      providerCode: 'FAST_API',
      modelId: 'AI-VISION-LOCAL-001',
      modelName: 'AI-VISION-LOCAL-001',
      modelVersion: '1.0.0',
    })).toBe('本地视觉模型 · Grounding DINO Tiny + SAM 2.1 Hiera Tiny · PyTorch · CUDA')
  })

  it('MOCK 绝不显示 PyTorch · CUDA', () => {
    expect(resolveModelAttribution({
      mode: 'MOCK',
      providerCode: 'FAST_API',
      modelId: 'AI-DEFECT-MOCK-001',
      modelName: 'Mock Detector',
      modelVersion: '0.1.0',
    })).toBe('测试模拟结果 · MOCK')
  })

  it('其他历史模型显示真实 modelName/modelVersion/providerCode', () => {
    expect(resolveModelAttribution({
      mode: 'REAL',
      providerCode: 'DIFY',
      modelId: 'AI-DIFY-WORKFLOW-001',
      modelName: 'Dify Image Analysis',
      modelVersion: '1.1.0',
    })).toBe('Dify Image Analysis v1.1.0 · DIFY')
  })

  it('缺失字段时兜底显示', () => {
    expect(resolveModelAttribution({ mode: 'REAL', modelId: 'X' })).toBe('未知模型')
  })
})
