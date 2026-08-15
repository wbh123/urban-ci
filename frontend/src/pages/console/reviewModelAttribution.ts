/** 专业复核页模型来源标注；必须按真实模式/版本展示，MOCK 不得伪装成 CUDA REAL。 */
export function resolveModelAttribution(input: {
  mode?: string | null
  providerCode?: string | null
  modelId?: string | null
  modelName?: string | null
  modelVersion?: string | null
}): string {
  if (input.mode === 'MOCK') {
    return '测试模拟结果 · MOCK'
  }
  if (
    input.mode === 'REAL'
    && input.providerCode === 'FAST_API'
    && input.modelId === 'AI-VISION-LOCAL-001'
  ) {
    if (input.modelVersion === '1.1.0') {
      return '本地视觉模型 · Grounding DINO Base + SAM 2.1 Hiera Base+ · 精度优先 · PyTorch · CUDA'
    }
    return '本地视觉模型 · Grounding DINO Tiny + SAM 2.1 Hiera Tiny · PyTorch · CUDA'
  }
  const name = input.modelName || '未知模型'
  const version = input.modelVersion ? ` v${input.modelVersion}` : ''
  const provider = input.providerCode ? ` · ${input.providerCode}` : ''
  return `${name}${version}${provider}`
}
