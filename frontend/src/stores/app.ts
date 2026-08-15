import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getApiMode, type ApiMode } from '@/shared/api'

export type NoticeType = 'info' | 'success' | 'warning' | 'error'

export interface AppNotice {
  id: number
  type: NoticeType
  message: string
}

let noticeSeq = 0
const DEFAULT_NOTICE_DURATION = 3000

/**
 * 全局应用 store：全局通知、全局加载状态与 API 模式等运行时配置。
 * 不存放页面业务数据。
 */
export const useAppStore = defineStore('app', () => {
  const globalLoading = ref(false)
  const apiMode = ref<ApiMode>(getApiMode())
  const notices = ref<AppNotice[]>([])

  function dismissNotice(id: number): void {
    notices.value = notices.value.filter((n) => n.id !== id)
  }

  function notify(message: string, type: NoticeType = 'info', duration = DEFAULT_NOTICE_DURATION): number {
    noticeSeq += 1
    const id = noticeSeq
    notices.value.push({ id, type, message })

    if (duration > 0) {
      globalThis.setTimeout(() => dismissNotice(id), duration)
    }
    return id
  }

  function clearNotices(): void {
    notices.value = []
  }

  function setGlobalLoading(value: boolean): void {
    globalLoading.value = value
  }

  return { globalLoading, apiMode, notices, notify, dismissNotice, clearNotices, setGlobalLoading }
})
