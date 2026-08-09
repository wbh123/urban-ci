import { describe, expect, it } from 'vitest'
import type { RouteRecordRaw } from 'vue-router'
import { routes } from './routes'

function consoleChildren(): RouteRecordRaw[] {
  const consoleRoute = routes.find((route) => route.path === '/console')
  return consoleRoute?.children ?? []
}

describe('R4-3 building detail routes', () => {
  it('registers the unified building detail page', () => {
    const detail = consoleChildren().find((route) => route.name === 'console-building-detail')
    expect(detail).toBeDefined()
    expect(detail?.path).toBe('buildings/:buildingId')
  })

  it('redirects the legacy assessment URL to the risk tab of the unified detail page', () => {
    const legacy = consoleChildren().find((route) => route.name === 'console-building-assessment')
    expect(legacy).toBeDefined()
    expect(typeof legacy?.redirect).toBe('function')

    const redirect = legacy?.redirect
    if (typeof redirect !== 'function') throw new Error('legacy assessment route must use a redirect function')

    const target = redirect({
      params: { buildingId: 'b-1' },
      query: { from: 'legacy' },
    } as never, {} as never)

    expect(target).toEqual({
      name: 'console-building-detail',
      params: { buildingId: 'b-1' },
      query: { from: 'legacy', tab: 'risk' },
    })
  })
})
