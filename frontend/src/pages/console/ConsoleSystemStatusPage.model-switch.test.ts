import { describe, expect, it } from 'vitest'
import pageSource from './ConsoleSystemStatusPage.vue?raw'

describe('AI 运行状态默认视觉模型切换', () => {
  it('加载模型目录并展示默认视觉模型选择器', () => {
    expect(pageSource).toContain('listAiModels')
    expect(pageSource).toContain('默认视觉模型')
    expect(pageSource).toContain('defaultVisionModels')
  })

  it('运行时未就绪模型仍展示但不能选为默认', () => {
    expect(pageSource).toContain(':disabled="!model.selectable"')
    expect(pageSource).toContain('运行时未就绪')
    expect(pageSource).toContain('VALIDATING')
  })

  it('保存默认模型时把 modelId 与现有业务开关一起提交', () => {
    expect(pageSource).toContain('modelId: automationSettings.value.modelId')
    expect(pageSource).toContain('已切换默认视觉模型')
  })
})
