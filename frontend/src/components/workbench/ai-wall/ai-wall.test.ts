import { describe, expect, it } from 'vitest'
import headerSource from './AiWallHeader.vue?raw'
import metricsSource from './AiWallMetrics.vue?raw'
import discoverySource from './AiWallDiscoveryPanel.vue?raw'
import attentionSource from './AiWallAttentionList.vue?raw'
import activitySource from './AiWallActivityFeed.vue?raw'
import drawerSource from './AiWallBuildingDrawer.vue?raw'
import layersSource from './AiWallMapLayers.vue?raw'
import wallSource from './WorkbenchAiDataWall.vue?raw'

describe('AI 态势大屏组件化', () => {
  it('使用正式标题和六个 AI 核心指标', () => {
    expect(headerSource).toContain('城安智序 · AI 城市建筑安全智能研判中心')
    for (const label of ['纳管楼栋', 'AI 已分析', 'AI 发现病害', '高风险楼栋', '待人工复核', 'AI 分析覆盖率']) {
      expect(metricsSource).toContain(label)
    }
  })

  it('标题仅保留放大的居中主标题，并保证地图视角切换器可点击', () => {
    expect(headerSource).toContain('justify-content:center')
    expect(headerSource).toContain('background:transparent')
    expect(headerSource).toContain('text-align:center')
    expect(headerSource).toContain('font-size:28px')
    expect(headerSource).not.toContain('brand-mark')
    expect(headerSource).not.toContain('AI 发现与解释辅助治理')
    expect(headerSource).toContain(':global(.data-wall .building-focus-switch){z-index:60!important;pointer-events:auto!important}')
    expect(wallSource).toContain(':deep(.building-focus-switch){top:154px;right:18px;left:auto;transform:none}')
  })

  it('拆分今日发现、重点关注、活动流和楼栋详情', () => {
    expect(discoverySource).toContain('AI 今日发现')
    expect(attentionSource).toContain('AI 重点关注')
    expect(activitySource).toContain('AI 实时研判动态')
    expect(drawerSource).toContain('AI 综合判断')
    expect(wallSource).toContain('AiWallDiscoveryPanel')
    expect(wallSource).toContain('AiWallAttentionList')
    expect(wallSource).toContain('AiWallActivityFeed')
  })

  it('提供五种地图展示模式且明确 AI 关注不是正式风险', () => {
    for (const label of ['风险', 'AI 病害', 'AI 关注', '待复核', '治理优先级']) {
      expect(layersSource).toContain(label)
    }
    expect(layersSource).toContain('不是正式风险')
  })
})
