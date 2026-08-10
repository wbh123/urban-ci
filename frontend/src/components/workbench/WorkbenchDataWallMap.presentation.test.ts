import { describe, expect, it } from 'vitest'
import source from './WorkbenchDataWallMap.vue?raw'

describe('WorkbenchDataWallMap presentation contract', () => {
  it('uses the information overlay background for risk tone and keeps close controls hidden for future use', () => {
    expect(source).toContain(':data-tone="popupRiskTone"')
    expect(source).toContain(".map-popup[data-tone='very-high']")
    expect(source).toContain(".map-popup[data-tone='high']")
    expect(source).toContain(".map-popup[data-tone='medium']")
    expect(source).toContain(".map-popup[data-tone='low']")
    expect(source.match(/class="popup-close"/g)).toHaveLength(2)
    expect(source).toContain('.popup-close { display:none;')
  })

  it('shows DOM outline fallback only in outline mode and fully resets building interaction from map blank clicks', () => {
    expect(source).toContain('v-if="buildingFocusMode === \'OUTLINE\' && selectionOutlinePath"')
    expect(source).toContain('v-if="buildingFocusMode === \'OUTLINE\' && selectionHalo"')
    expect(source).toContain('onMapBlankClick: resetMapInteraction')
    expect(source).toContain('function resetMapInteraction(): void')
    expect(source).toContain('clearBuildingFocus(false)')
    expect(source).toContain('driver.restoreOverview()')
  })

  it('switches the overview camera even when no building is selected', () => {
    expect(source).toContain("driver.setOverviewPresentation(mode === '3D')")
    expect(source).toContain('aria-label="地图展示模式"')
    expect(source).toContain('>2D 俯视</button>')
    expect(source).toContain('>3D 视角</button>')
  })

  it('passes risk, priority and freshness into native building map points', () => {
    expect(source).toContain('riskLevel: item.riskLevel')
    expect(source).toContain('priorityLevel: item.priorityLevel')
    expect(source).toContain('freshness: item.freshness')
    expect(source).toContain('riskLevel: riskRow?.riskLevel')
    expect(source).toContain('priorityLevel: riskRow?.priorityLevel')
    expect(source).toContain('freshness: riskRow?.freshness')
  })

  it('does not cover the AMap canvas with the retired visual tint layer', () => {
    expect(source).not.toContain('class="wall-map-tint"')
    expect(source).not.toContain('.wall-map-tint')
  })
})
