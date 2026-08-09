import { afterEach, describe, expect, it, vi } from 'vitest'
import { loadAmap } from './amap-loader'

describe('shared AMap loader', () => {
  afterEach(() => {
    delete (window as unknown as Record<string, unknown>).AMap
    delete (window as unknown as Record<string, unknown>)._AMapSecurityConfig
  })

  it('reuses an existing namespace and loads requested plugins on demand', async () => {
    const namespace = {
      Map: class {},
      plugin: vi.fn((_plugins: string[], done: () => void) => done()),
    }
    ;(window as unknown as Record<string, unknown>).AMap = namespace

    const result = await loadAmap({
      key: 'test-key',
      version: '2.0',
      plugins: ['AMap.MouseTool', 'AMap.PolygonEditor'],
      serviceHost: '/_AMapService',
    })

    expect(result).toBe(namespace)
    expect(namespace.plugin).toHaveBeenCalledWith(
      ['AMap.MouseTool', 'AMap.PolygonEditor'],
      expect.any(Function),
    )
    expect((window as unknown as Record<string, unknown>)._AMapSecurityConfig)
      .toEqual({ serviceHost: '/_AMapService' })
  })

  it('does not request plugins when none are required', async () => {
    const namespace = {
      Map: class {},
      plugin: vi.fn(),
    }
    ;(window as unknown as Record<string, unknown>).AMap = namespace

    expect(await loadAmap({ key: 'test-key', version: '2.0' })).toBe(namespace)
    expect(namespace.plugin).not.toHaveBeenCalled()
  })
})
