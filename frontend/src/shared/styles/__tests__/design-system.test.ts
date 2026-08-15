import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

function source(path: string): string {
  return readFileSync(resolve(process.cwd(), path), 'utf8')
}

describe('R1 设计系统基线', () => {
  it('Design Token 覆盖品牌、风险、排版、间距、圆角、阴影和布局尺寸', () => {
    const tokens = source('src/shared/styles/tokens.scss')
    for (const token of [
      '--usp-color-primary',
      '--usp-color-primary-strong',
      '--usp-color-risk-low',
      '--usp-color-risk-medium',
      '--usp-color-risk-high',
      '--usp-color-text',
      '--usp-color-text-secondary',
      '--usp-color-bg',
      '--usp-color-surface',
      '--usp-space-1',
      '--usp-space-6',
      '--usp-radius-sm',
      '--usp-radius-lg',
      '--usp-shadow-sm',
      '--usp-shadow-lg',
      '--usp-console-aside-width',
      '--usp-console-aside-collapsed-width',
      '--usp-header-height',
    ]) {
      expect(tokens).toContain(token)
    }
  })

  it('电脑端和移动端 Layout 不再硬编码十六进制颜色', () => {
    for (const path of ['src/layouts/ConsoleLayout.vue', 'src/layouts/MobileLayout.vue']) {
      expect(source(path)).not.toMatch(/#[0-9a-fA-F]{3,8}\b/)
    }
  })

  it('全局样式入口继续按 token -> base -> layout 顺序加载', () => {
    const index = source('src/shared/styles/index.scss')
    expect(index.indexOf("@use './tokens';")).toBeLessThan(index.indexOf("@use './base';"))
    expect(index.indexOf("@use './base';")).toBeLessThan(index.indexOf("@use './layout';"))
  })

  it('全局圆角覆盖原生按钮、选择框弹层与组合输入控件', () => {
    const base = source('src/shared/styles/base.scss')
    for (const selector of [
      'button:not(.el-button)',
      '.el-select-dropdown',
      '.el-dropdown-menu',
      '.el-picker-panel',
      '.el-segmented',
      '.el-input-number .el-input__wrapper',
      '.el-radio-button:first-child .el-radio-button__inner',
    ]) {
      expect(base).toContain(selector)
    }
  })
})
