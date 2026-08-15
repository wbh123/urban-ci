import { describe, expect, it } from 'vitest'
import inspectionSource from '@/pages/console/ConsoleInspectionPage.vue?raw'
import mobileTaskSource from '@/pages/mobile/MobileTaskDetailPage.vue?raw'
import reviewQueueSource from '@/pages/console/ConsoleReviewQueuePage.vue?raw'
import reviewDetailSource from '@/pages/console/ConsoleReviewDetailPage.vue?raw'
import spatialMapSource from '@/pages/console/ConsoleSpatialMapPage.vue?raw'

const businessPages = [
  inspectionSource,
  mobileTaskSource,
  reviewQueueSource,
  reviewDetailSource,
  spatialMapSource,
]

describe('连续业务操作提示', () => {
  it('核心业务页面不再渲染业务闭环大卡片', () => {
    businessPages.forEach((source) => {
      expect(source).not.toContain('GovernanceJourney')
      expect(source).not.toContain('<GovernanceJourney')
    })
  })

  it('巡检和移动作业用具体下一步说明替代流程卡片', () => {
    expect(inspectionSource).toContain('下一步')
    expect(inspectionSource).toContain('移动作业端')
    expect(mobileTaskSource).toContain('nextStepText')
    expect(mobileTaskSource).toContain('下一步')
    expect(mobileTaskSource).toContain('result.autoInference')
    expect(mobileTaskSource).toContain('aiTriggered.value = Boolean(automatic?.triggered)')
  })

  it('专业复核完成后仍可直接进入风险地图', () => {
    expect(reviewDetailSource).toContain("router.push('/console/map')")
    expect(reviewDetailSource).toContain('@click="openRiskMap"')
    expect(reviewDetailSource).toContain('查看地图')
  })

  it('复核队列通过队列本身说明下一操作，不额外堆叠流程卡片', () => {
    expect(reviewQueueSource).toContain('先看发生了什么，再看 AI 怎么判断，最后由专业人员形成结论。')
    expect(reviewQueueSource).toContain('AI 发现')
    expect(reviewQueueSource).toContain('证据数量')
    expect(reviewQueueSource).toContain('复核')
    expect(reviewQueueSource).not.toContain('GovernanceJourney')
  })
})
