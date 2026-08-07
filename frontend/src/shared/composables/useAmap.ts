import { ref } from 'vue'
import type { CommunityPoint, MapRuntimeConfig } from '@/shared/api'

/** 高德地图第三方全局对象，在边界文件中以受控类型隔离，避免 any 扩散到业务页面。 */
interface AMapMapInstance {
  destroy?: () => void
}

interface AMapStatic {
  Map: new (el: string | HTMLElement, opts: unknown) => AMapMapInstance
  Marker: new (opts: unknown) => { setMap: (map: unknown) => void }
}

let loaderPromise: Promise<AMapStatic> | null = null

function getAmapGlobal(): AMapStatic | undefined {
  return (window as unknown as { AMap?: AMapStatic }).AMap
}

function loadAmapScript(jsApiKey: string): Promise<void> {
  return new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = `https://webapi.amap.com/maps?v=2.0&key=${encodeURIComponent(jsApiKey)}`
    script.async = true
    script.onload = () => resolve()
    script.onerror = () => reject(new Error('高德地图脚本加载失败'))
    document.head.appendChild(script)
  })
}

function ensureAmap(jsApiKey: string): Promise<AMapStatic> {
  const existing = getAmapGlobal()
  if (existing) return Promise.resolve(existing)
  if (!loaderPromise) {
    loaderPromise = loadAmapScript(jsApiKey).then(() => {
      const AMap = getAmapGlobal()
      if (!AMap) throw new Error('高德地图对象未就绪')
      return AMap
    })
  }
  return loaderPromise
}

/**
 * 高德地图边界 composable。业务页面只调用 render/destroy，
 * 不直接接触 window.AMap 与 securityJsCode。
 */
export function useAmap() {
  const instance = ref<AMapMapInstance | null>(null)

  async function render(
    config: MapRuntimeConfig,
    points: CommunityPoint[],
    containerId = 'map',
  ): Promise<boolean> {
    if (config.mode !== 'LIVE' || !config.jsApiKey) return false
    const win = window as unknown as { _AMapSecurityConfig?: { serviceHost?: string } }
    win._AMapSecurityConfig = { serviceHost: config.serviceHost }
    try {
      const AMap = await ensureAmap(config.jsApiKey)
      instance.value?.destroy?.()
      const map = new AMap.Map(containerId, {
        zoom: config.defaultZoom,
        center: [config.defaultCenter.longitude, config.defaultCenter.latitude],
      })
      instance.value = map
      points
        .filter((p) => p.longitude != null && p.latitude != null)
        .forEach((p) => {
          const marker = new AMap.Marker({
            position: [p.longitude as number, p.latitude as number],
            title: p.communityName,
          })
          marker.setMap(map)
        })
      return true
    } catch {
      return false
    }
  }

  function destroy(): void {
    instance.value?.destroy?.()
    instance.value = null
  }

  return { render, destroy }
}
