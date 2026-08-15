import { describe, expect, it } from 'vitest'
import source from './ConsoleReviewDetailPage.vue?raw'

describe('ConsoleReviewDetailPage error isolation', () => {
  it('uses a separate image error state instead of replacing the task error', () => {
    expect(source).toContain("const imageErrorMessage = ref('')")
    expect(source).toContain('async function loadImage(): Promise<void>')
    expect(source).toContain('imageErrorMessage.value = toAppError(error).message')
    expect(source).toContain('v-if="imageErrorMessage"')
  })
})

describe('ConsoleReviewDetailPage 运行体验与布局', () => {
  it('uses the shared page header and keeps the return action at the top', () => {
    expect(source).toContain('AppPageHeader')
    expect(source).toContain('AI 发现专业复核')
    expect(source).toContain('← 返回复核中心')
    expect(source).not.toContain('<header class="detail-toolbar">')
  })

  it('disables comprehensive analysis while inference is running and shows progress', () => {
    expect(source).toContain('const agentAnalysisLoading = ref(false)')
    expect(source).toContain(':loading="agentAnalysisLoading"')
    expect(source).toContain(':disabled="taskRunning"')
    expect(source).toContain('class="running-panel"')
    expect(source).toContain("const taskRunning = computed(() => task.value?.status === 'PENDING' || task.value?.status === 'RUNNING')")
  })

  it('uses three business columns on desktop and degrades cleanly on narrow screens', () => {
    expect(source).toContain('AI判断')
    expect(source).toContain('原始证据')
    expect(source).toContain('人工复核')
    expect(source).toContain('grid-template-columns:minmax(280px,.82fr) minmax(360px,1.18fr) minmax(290px,.86fr)')
    expect(source).toContain('@media(max-width:760px)')
    expect(source).toContain('.review-layout{grid-template-columns:1fr}')
  })
})
