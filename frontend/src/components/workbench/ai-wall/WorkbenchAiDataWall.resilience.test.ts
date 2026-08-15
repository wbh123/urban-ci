import { describe, expect, it } from 'vitest'
import source from './WorkbenchAiDataWall.vue?raw'

describe('AI 态势大屏数据恢复能力', () => {
  it('AI 楼栋聚合失败后定时重试且卸载时清理定时器', () => {
    expect(source).toContain('BUILDINGS_RETRY_MS')
    expect(source).toContain('buildingsRetryTimer')
    expect(source).toContain('scheduleBuildingsRetry')
    expect(source).toContain('clearBuildingsRetryTimer')
    expect(source).toContain('clearBuildingsRetryTimer()')
  })

  it('楼栋聚合恢复成功后取消降级重试并重新开放 AI 图层', () => {
    expect(source).toContain('aiBuildingsError.value = false')
    expect(source).toContain('clearBuildingsRetryTimer()')
    expect(source).toContain('aiLayersAvailable')
  })

  it('组件卸载后不允许异步请求重新创建楼栋重试或活动轮询', () => {
    expect(source).toContain('let disposed = false')
    expect(source).toContain('disposed = true')
    expect(source).toContain('if (!disposed) scheduleBuildingsRetry()')
    expect(source).toContain('if (!disposed) {\n      activityTimer = setTimeout')
  })
})
