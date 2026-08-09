import type { MapRuntimeConfig } from '@/shared/api/endpoints/map'

export interface ArchiveMapPoint {
  longitude: number
  latitude: number
}

interface AmapLngLatLike {
  getLng?: () => number
  getLat?: () => number
  lng?: number
  lat?: number
}

interface AmapClickEventLike {
  lnglat?: AmapLngLatLike
}

interface AmapMapLike {
  on: (event: string, handler: (event: AmapClickEventLike) => void) => void
  setCenter: (position: [number, number]) => void
  destroy: () => void
}

interface AmapMarkerLike {
  setPosition: (position: [number, number]) => void
  setMap: (map: AmapMapLike | null) => void
}

interface AmapNamespaceLike {
  Map: new (container: HTMLElement, options: Record<string, unknown>) => AmapMapLike
  Marker: new (options: Record<string, unknown>) => AmapMarkerLike
}

interface AmapWindow extends Window {
  AMap?: AmapNamespaceLike
  _AMapSecurityConfig?: { serviceHost?: string }
}

export interface ArchiveAmapLoadOptions {
  key: string
  version: string
}

export type ArchiveAmapLoader = (options: ArchiveAmapLoadOptions) => Promise<AmapNamespaceLike>

export interface ArchivePointPickerHandlers {
  onSelect?: (point: ArchiveMapPoint) => void
}

export interface ArchivePointPicker {
  mount: (
    container: HTMLElement,
    config: MapRuntimeConfig,
    handlers?: ArchivePointPickerHandlers,
  ) => Promise<boolean>
  setPoint: (point: ArchiveMapPoint) => void
  destroy: () => void
}

let loaderPromise: Promise<AmapNamespaceLike> | null = null

const defaultLoader: ArchiveAmapLoader = async ({ key, version }) => {
  if (typeof window === 'undefined' || typeof document === 'undefined') {
    throw new Error('当前运行环境不支持高德地图')
  }
  const target = window as unknown as AmapWindow
  const callbacks = target as unknown as Record<string, unknown>
  if (target.AMap) return target.AMap
  if (loaderPromise) return loaderPromise

  loaderPromise = new Promise<AmapNamespaceLike>((resolve, reject) => {
    const callbackName = `__urbanSafeArchiveAmapLoaded_${Date.now()}_${Math.random().toString(36).slice(2)}`
    callbacks[callbackName] = () => {
      delete callbacks[callbackName]
      if (target.AMap) resolve(target.AMap)
      else reject(new Error('高德地图脚本已加载，但 AMap 对象不可用'))
    }

    const params = new URLSearchParams({ v: version, key, callback: callbackName })
    const script = document.createElement('script')
    script.async = true
    script.src = `https://webapi.amap.com/maps?${params.toString()}`
    script.onerror = () => {
      delete callbacks[callbackName]
      loaderPromise = null
      reject(new Error('高德地图 JavaScript API 加载失败'))
    }
    document.head.appendChild(script)
  })

  return loaderPromise
}

export function createArchivePointPicker(
  options: { loader?: ArchiveAmapLoader } = {},
): ArchivePointPicker {
  const loader = options.loader ?? defaultLoader
  let map: AmapMapLike | null = null
  let marker: AmapMarkerLike | null = null
  let handlers: ArchivePointPickerHandlers = {}

  async function mount(
    container: HTMLElement,
    config: MapRuntimeConfig,
    nextHandlers: ArchivePointPickerHandlers = {},
  ): Promise<boolean> {
    destroy()
    handlers = nextHandlers
    if (!config.enabled || config.mode !== 'LIVE' || !config.jsApiKey) return false

    if (config.serviceHost && typeof window !== 'undefined') {
      const target = window as unknown as AmapWindow
      target._AMapSecurityConfig = { serviceHost: config.serviceHost }
    }

    const namespace = await loader({ key: config.jsApiKey, version: '2.0' })
    map = new namespace.Map(container, {
      viewMode: '2D',
      resizeEnable: true,
      zoom: config.defaultZoom,
      center: [config.defaultCenter.longitude, config.defaultCenter.latitude],
    })
    marker = new namespace.Marker({})
    marker.setMap(map)

    map.on('click', (event) => {
      const point = readPoint(event.lnglat)
      if (!point) return
      setPoint(point)
      handlers.onSelect?.(point)
    })
    return true
  }

  function setPoint(point: ArchiveMapPoint): void {
    if (!Number.isFinite(point.longitude) || !Number.isFinite(point.latitude)) return
    const position: [number, number] = [point.longitude, point.latitude]
    marker?.setPosition(position)
    map?.setCenter(position)
  }

  function destroy(): void {
    marker?.setMap(null)
    marker = null
    map?.destroy()
    map = null
    handlers = {}
  }

  return { mount, setPoint, destroy }
}

function readPoint(value: AmapLngLatLike | undefined): ArchiveMapPoint | null {
  if (!value) return null
  const longitude = typeof value.getLng === 'function' ? value.getLng() : Number(value.lng)
  const latitude = typeof value.getLat === 'function' ? value.getLat() : Number(value.lat)
  if (!Number.isFinite(longitude) || !Number.isFinite(latitude)) return null
  return { longitude, latitude }
}
