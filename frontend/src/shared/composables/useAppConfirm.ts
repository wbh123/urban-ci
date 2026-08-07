import { ElMessageBox } from 'element-plus'

export interface AppConfirmOptions {
  title: string
  message: string
  confirmText?: string
  cancelText?: string
  type?: 'warning' | 'error' | 'info' | 'success'
}

export async function confirmAction(options: AppConfirmOptions): Promise<boolean> {
  try {
    await ElMessageBox.confirm(options.message, options.title, {
      confirmButtonText: options.confirmText ?? '确认',
      cancelButtonText: options.cancelText ?? '取消',
      type: options.type ?? 'warning',
      closeOnClickModal: false,
      closeOnPressEscape: true,
      distinguishCancelAndClose: true,
      autofocus: false,
    })
    return true
  } catch (reason: unknown) {
    if (reason === 'cancel' || reason === 'close') return false
    throw reason
  }
}
