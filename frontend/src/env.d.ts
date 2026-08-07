/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** API 运行模式：mock 启用 Mock Service Worker，real 调用真实 Spring Boot。 */
  readonly VITE_API_MODE: 'mock' | 'real'
  /** 后端 API 基础地址。 */
  readonly VITE_API_BASE_URL: string
  /** 地图运行配置接口路径。 */
  readonly VITE_MAP_CONFIG_ENDPOINT?: string
  readonly VITE_MAP_PROVIDER?: string
  readonly VITE_MAP_SECURITY_MODE?: string
  readonly VITE_MAP_SERVICE_HOST?: string
  readonly VITE_AMAP_JS_API_VERSION?: string
  readonly VITE_MAP_DEFAULT_CENTER_LONGITUDE?: string
  readonly VITE_MAP_DEFAULT_CENTER_LATITUDE?: string
  readonly VITE_MAP_DEFAULT_ZOOM?: string
  readonly VITE_AMAP_JS_API_KEY?: string
  readonly VITE_AMAP_SECURITY_JS_CODE?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
