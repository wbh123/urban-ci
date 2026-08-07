import { beforeEach, describe, expect, it, vi } from 'vitest'

const { confirm } = vi.hoisted(() => ({ confirm: vi.fn() }))
vi.mock('element-plus', () => ({
  ElMessageBox: { confirm },
}))

import { confirmAction } from './useAppConfirm'

describe('confirmAction', () => {
  beforeEach(() => confirm.mockReset())

  it('使用统一确认文案并在确认时返回 true', async () => {
    confirm.mockResolvedValueOnce('confirm')
    await expect(
      confirmAction({ title: '确认提交复核结果？', message: '提交后将进入风险评分流程。' }),
    ).resolves.toBe(true)
    expect(confirm).toHaveBeenCalledWith(
      '提交后将进入风险评分流程。',
      '确认提交复核结果？',
      expect.objectContaining({
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        closeOnClickModal: false,
      }),
    )
  })

  it('取消或关闭确认框返回 false', async () => {
    confirm.mockRejectedValueOnce('cancel')
    await expect(confirmAction({ title: '确认删除？', message: '删除后无法恢复。' })).resolves.toBe(false)
  })

  it('非取消类异常继续抛出', async () => {
    const error = new Error('unexpected')
    confirm.mockRejectedValueOnce(error)
    await expect(confirmAction({ title: '确认操作？', message: '执行操作。' })).rejects.toBe(error)
  })
})
