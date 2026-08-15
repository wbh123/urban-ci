import { describe, expect, it } from 'vitest'
import source from './AiWallMap.vue?raw'

describe('AiWallMap presentation contract', () => {
  it('presents business-first AI building information with explicit close controls', () => {
    expect(source).toContain('楼栋 AI 治理摘要')
    expect(source).toContain('✦ AI 最近发现')
    expect(source).toContain('AI 综合判断')
    expect(source.match(/class="popup-close"/g)).toHaveLength(2)
    expect(source).not.toContain('AI_PROVIDER_NOT_CONFIGURED')
  })

  it('fully resets building interaction from map blank clicks', () => {
    expect(source).toContain('onMapBlankClick: resetMapInteraction')
    expect(source).toContain('function resetMapInteraction(): void')
    expect(source).toContain('clearBuildingFocus(false)')
    expect(source).toContain('driver.restoreOverview()')
  })

  it('switches the overview camera even when no building is selected', () => {
    expect(source).toContain("driver.setOverviewPresentation(mode === '3D')")
    expect(source).toContain('aria-label="地图视角"')
    expect(source).toContain('>2D 俯视</button>')
    expect(source).toContain('>3D 视角</button>')
  })

  it('passes formal risk, priority and freshness into native building map points', () => {
    expect(source).toContain('riskLevel: item.riskLevel')
    expect(source).toContain('priorityLevel: item.priorityLevel')
    expect(source).toContain('freshness: item.freshness')
    expect(source).toContain('riskLevel: riskRow?.riskLevel')
    expect(source).toContain('priorityLevel: riskRow?.priorityLevel')
    expect(source).toContain('freshness: riskRow?.freshness')
  })

  it('keeps AI layers as display-only point decoration', () => {
    expect(source).toContain('function decorateBuildingPoint')
    expect(source).toContain("props.layerMode === 'AI_DEFECT'")
    expect(source).toContain("props.layerMode === 'AI_ATTENTION'")
    expect(source).not.toContain('updateRisk')
    expect(source).not.toContain('saveRisk')
  })
})
