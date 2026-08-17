import { describe, expect, it } from 'vitest'
import { parseAiAnalysisAnswer } from './ai-analysis-structure'

describe('parseAiAnalysisAnswer', () => {
  it('parses the fixed analysis sections, tables and bullet lists', () => {
    const document = parseAiAnalysisAnswer(`
## 核心结论
本次发现两处疑似裂缝，均需人工复核。

## 楼栋概况
| 项目 | 内容 |
|---|---|
| 结构 | 框架结构 |
| 建成年代 | 2000 年 |

## 巡检证据
- 巡检任务 9 次
- 证据 3 条

## 风险与优先级
暂无正式风险等级。

## 视觉病害
- 疑似裂缝，置信度 0.478

## 判断依据
1. 视觉模型低置信度检出
2. 现场证据量偏少

## 人工复核建议
- 现场量测裂缝宽度和走向

## 能力限制
- 结论仅用于辅助研判
`)

    expect(document.structured).toBe(true)
    expect(document.sections.map((section) => section.key)).toEqual([
      'core',
      'building',
      'inspection',
      'risk',
      'vision',
      'basis',
      'review',
      'limits',
    ])
    const building = document.sections.find((section) => section.key === 'building')
    expect(building?.blocks[0]).toEqual({
      type: 'table',
      headers: ['项目', '内容'],
      rows: [
        ['结构', '框架结构'],
        ['建成年代', '2000 年'],
      ],
    })
    const inspection = document.sections.find((section) => section.key === 'inspection')
    expect(inspection?.blocks[0]).toEqual({
      type: 'list',
      ordered: false,
      items: ['巡检任务 9 次', '证据 3 条'],
    })
  })

  it('maps numbered legacy headings to stable section keys', () => {
    const document = parseAiAnalysisAnswer(`
## 一、楼栋基本信息
楼栋名称：测试楼栋

## 二、巡检证据概况
- 证据不足

## 三、历史正式风险与更新优先级（关键信息缺失）
暂无正式风险。

## 四、本次实时视觉分析结果（疑似病害）
- 疑似裂缝

## 五、判断依据小结
- 视觉模型输出

## 六、人工复核建议
- 现场复核

## 七、能力与限制说明（重要）
仅辅助研判。
`)

    expect(document.sections.map((section) => section.key)).toEqual([
      'building',
      'inspection',
      'risk',
      'vision',
      'basis',
      'review',
      'limits',
    ])
  })

  it('falls back to a safe general section when the model ignores the heading protocol', () => {
    const answer = '当前只有一段普通文本。\n仍然需要完整展示，不能让页面空白。'
    const document = parseAiAnalysisAnswer(answer)

    expect(document.structured).toBe(false)
    expect(document.sections).toHaveLength(1)
    expect(document.sections[0].key).toBe('other')
    expect(document.sections[0].title).toBe('综合说明')
    expect(document.sections[0].blocks).toEqual([
      { type: 'paragraph', text: '当前只有一段普通文本。 仍然需要完整展示，不能让页面空白。' },
    ])
    expect(document.raw).toBe(answer)
  })

  it('strips inline markdown markers instead of exposing raw formatting tokens', () => {
    const document = parseAiAnalysisAnswer(`
## 核心结论
**疑似裂缝**仅作辅助，模型为 \`AI-VISION-LOCAL-001\`。
`)

    expect(document.sections[0].blocks).toEqual([
      { type: 'paragraph', text: '疑似裂缝仅作辅助，模型为 AI-VISION-LOCAL-001。' },
    ])
  })
})
