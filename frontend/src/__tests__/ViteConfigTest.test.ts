import { describe, expect, it } from 'vitest'
import frontendEnvExample from '../../.env.example?raw'
import envDeclarationSource from '../env.d.ts?raw'
import viteConfigSource from '../../vite.config.ts?raw'

describe('vite config', () => {
  it('keeps AMap proxy on the fixed first-level route and security code server-side', () => {
    expect(viteConfigSource).toContain("'/_AMapService'")
    expect(viteConfigSource).toContain("target: 'https://restapi.amap.com'")
    expect(viteConfigSource).toContain("path.replace(/^\\/_AMapService/, '')")
    expect(viteConfigSource).toContain('URBAN_SAFE_AMAP_SECURITY_JS_CODE')
    expect(viteConfigSource).toContain('appendAmapSecurityCode')

    expect(frontendEnvExample).toContain('VITE_MAP_SERVICE_HOST=/_AMapService')
    expect(frontendEnvExample).not.toContain('VITE_MAP_SECURITY_MODE')
    expect(frontendEnvExample).not.toContain('VITE_AMAP_SECURITY_JS_CODE')
    expect(envDeclarationSource).toContain('VITE_MAP_SERVICE_HOST?: string')
    expect(envDeclarationSource).not.toContain('VITE_MAP_SECURITY_MODE')
    expect(envDeclarationSource).not.toContain('VITE_AMAP_SECURITY_JS_CODE')
  })
})
