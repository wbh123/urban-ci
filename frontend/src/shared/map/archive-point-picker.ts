import type { MapRuntimeConfig } from '@/shared/api/endpoints/map'
import { loadAmap, type AmapLoadOptions } from './amap-loader'

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

export type ArchiveAmapLoadOptions = AmapLoadOptions
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

const defaultLoader: ArchiveAmapLoader = async (options) => (
  await loadAmap(options) as unknown as AmapNamespaceLike
)

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

    const namespace = await loader({
      key: config.jsApiKey,
      version: '2.0',
      serviceHost: config.serviceHost || undefined,
    })
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
