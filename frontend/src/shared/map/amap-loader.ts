export interface AmapLoadOptions {
  key: string
  version: string
  plugins?: string[]
  serviceHost?: string
}

export interface SharedAmapNamespace {
  plugin?: (plugins: string[], done: () => void) => void
  [key: string]: unknown
}

interface AmapWindow extends Window {
  AMap?: SharedAmapNamespace
  _AMapSecurityConfig?: { serviceHost?: string }
}

let loaderPromise: Promise<SharedAmapNamespace> | null = null

/**
 * 全站共享高德 JavaScript API 加载器。
 * 基础脚本最多加载一次；不同业务模块需要的插件通过 AMap.plugin 按需补载。
 */
export async function loadAmap(options: AmapLoadOptions): Promise<SharedAmapNamespace> {
  if (typeof window === 'undefined' || typeof document === 'undefined') {
    throw new Error('当前运行环境不支持高德地图')
  }
  const target = window as unknown as AmapWindow
  if (options.serviceHost) target._AMapSecurityConfig = { serviceHost: options.serviceHost }

  const namespace = target.AMap ?? await loadBaseScript(target, options)
  await loadPlugins(namespace, options.plugins ?? [])
  return namespace
}

async function loadBaseScript(
  target: AmapWindow,
  options: AmapLoadOptions,
): Promise<SharedAmapNamespace> {
  if (loaderPromise) return loaderPromise
  const callbacks = target as unknown as Record<string, unknown>

  loaderPromise = new Promise<SharedAmapNamespace>((resolve, reject) => {
    const callbackName = `__urbanSafeAmapLoaded_${Date.now()}_${Math.random().toString(36).slice(2)}`
    const script = document.createElement('script')
    callbacks[callbackName] = () => {
      delete callbacks[callbackName]
      if (target.AMap) {
        resolve(target.AMap)
        return
      }
      loaderPromise = null
      script.remove()
      reject(new Error('高德地图脚本已加载，但 AMap 对象不可用'))
    }

    const params = new URLSearchParams({
      v: options.version,
      key: options.key,
      callback: callbackName,
    })
    script.async = true
    script.dataset.urbanSafeAmap = 'true'
    script.src = `https://webapi.amap.com/maps?${params.toString()}`
    script.onerror = () => {
      delete callbacks[callbackName]
      loaderPromise = null
      script.remove()
      reject(new Error('高德地图 JavaScript API 加载失败'))
    }
    document.head.appendChild(script)
  })

  return loaderPromise
}

async function loadPlugins(namespace: SharedAmapNamespace, plugins: string[]): Promise<void> {
  const uniquePlugins = [...new Set(plugins.map((item) => item.trim()).filter(Boolean))]
  if (uniquePlugins.length === 0) return
  if (typeof namespace.plugin !== 'function') {
    throw new Error('高德地图插件加载能力不可用')
  }
  await new Promise<void>((resolve) => namespace.plugin!(uniquePlugins, resolve))
}
