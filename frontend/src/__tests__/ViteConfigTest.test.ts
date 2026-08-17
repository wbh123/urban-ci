import { readFileSync } from 'node:fs'
import { fileURLToPath, URL } from 'node:url'
import { describe, expect, it } from 'vitest'
import viteConfigSource from '../../vite.config.ts?raw'

const frontendEnvExample = readFileSync(
  fileURLToPath(new URL('../../.env.example', import.meta.url)),
  'utf8',
)
const envDeclarationSource = readFileSync(
  fileURLToPath(new URL('../env.d.ts', import.meta.url)),
  'utf8',
)

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
