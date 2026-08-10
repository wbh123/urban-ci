// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createPinia } from 'pinia'
import { useSpatialMapStore } from '@/stores/spatial-map'
import type { DashboardBuilding } from '@/shared/api/endpoints/reports'
import {
  disposeWorkbenchStatusRingOverlay,
  installWorkbenchStatusRingOverlay,
} from './workbench-status-ring-overlay'

class FakeResizeObserver {
  observe(): void {}
  unobserve(): void {}
  disconnect(): void {}
}

describe('Workbench status ring overlay', () => {
  afterEach(() => {
    disposeWorkbenchStatusRingOverlay()
    document.body.innerHTML = ''
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('renders priority as the outer ring and risk as the inner circle', () => {
    vi.stubGlobal('ResizeObserver', FakeResizeObserver)
    vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => {
      callback(0)
      return 1
    })
    vi.stubGlobal('cancelAnimationFrame', () => undefined)

    document.body.innerHTML = '<section class="wall-map-background"></section>'
    const root = document.querySelector<HTMLElement>('.wall-map-background')!
    vi.spyOn(root, 'getBoundingClientRect').mockReturnValue({
      x: 0,
      y: 0,
      left: 0,
      top: 0,
      right: 1000,
      bottom: 800,
      width: 1000,
      height: 800,
      toJSON: () => ({}),
    } as DOMRect)

    const pinia = createPinia()
    const store = useSpatialMapStore(pinia)
    store.viewport = { west: 114.20, south: 30.45, east: 114.40, north: 30.65, zoom: 13 }
    store.riskRows = [{
      buildingId: 'building-1',
      buildingCode: 'B-01',
      buildingName: '测试楼栋',
      communityId: 'community-1',
      communityName: '测试小区',
      longitude: 114.30,
      latitude: 30.55,
      riskLevel: 'VERY_HIGH',
      priorityLevel: 'P1',
      freshness: 'CURRENT',
      needManualReview: false,
    } as DashboardBuilding]

    installWorkbenchStatusRingOverlay(pinia)

    const marker = root.querySelector<HTMLElement>('.workbench-status-ring-marker')
    expect(marker).not.toBeNull()
    expect(marker?.dataset.risk).toBe('very-high')
    expect(marker?.dataset.priority).toBe('p1')
    expect(marker?.querySelector('.workbench-status-ring-priority')).not.toBeNull()
    expect(marker?.querySelector('.workbench-status-ring-risk')).not.toBeNull()
    expect(root.textContent).toContain('内圆 · 风险')
    expect(root.textContent).toContain('外圈 · 优先级')
  })
})
