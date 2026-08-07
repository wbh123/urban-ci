import { describe, expect, it } from 'vitest'
import viteConfigSource from '../../vite.config.ts?raw'

describe('vite config', () => {
  it('proxies AMap security service requests in dev server', () => {
    expect(viteConfigSource).toContain("'/_AMapService'")
    expect(viteConfigSource).toContain("target: 'https://restapi.amap.com'")
    expect(viteConfigSource).toContain("path.replace(/^\\/_AMapService/, '')")
    expect(viteConfigSource).toContain('URBAN_SAFE_AMAP_SECURITY_JS_CODE')
    expect(viteConfigSource).toContain('appendAmapSecurityCode')
  })
})
