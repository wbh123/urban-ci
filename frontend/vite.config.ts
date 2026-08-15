import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

const workspaceRoot = fileURLToPath(new URL('..', import.meta.url))
const sourceDir = fileURLToPath(new URL('./src', import.meta.url))

function appendAmapSecurityCode(path: string, securityJsCode: string): string {
  if (!securityJsCode) return path
  const separator = path.includes('?') ? '&' : '?'
  return `${path}${separator}jscode=${encodeURIComponent(securityJsCode)}`
}

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, workspaceRoot, 'URBAN_SAFE_')
  const amapSecurityJsCode =
    process.env.URBAN_SAFE_AMAP_SECURITY_JS_CODE ?? env.URBAN_SAFE_AMAP_SECURITY_JS_CODE ?? ''
  const configuredPort = Number.parseInt(
    process.env.URBAN_SAFE_FRONTEND_PORT ?? env.URBAN_SAFE_FRONTEND_PORT ?? '5173',
    10,
  )
  const frontendPort = Number.isFinite(configuredPort) ? configuredPort : 5173

  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': sourceDir,
      },
    },
    server: {
      host: true,
      port: frontendPort,
      proxy: {
        '/_AMapService': {
          target: 'https://restapi.amap.com',
          changeOrigin: true,
          secure: true,
          rewrite: (path) =>
            appendAmapSecurityCode(path.replace(/^\/_AMapService/, ''), amapSecurityJsCode),
        },
      },
    },
  }
})